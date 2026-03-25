package Server;

import Shared.*;
import Server.handlers.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles the full I/O lifecycle of one connected TCP client.
 *
 * One ClientHandler instance is created per accepted Socket by Server
 * and submitted to the ExecutorService thread pool. It runs entirely
 * on its own worker thread from the pool for the duration of the
 * client's connection.
 *
 * Responsibilities — exactly three things:
 * 1. Wrap the Socket's InputStream/OutputStream in text-mode streams
 * 2. Loop: read one line → parse with RequestParser → dispatch → write response
 * 3. Clean up streams, socket, and session on disconnect or error
 *
 * What ClientHandler does NOT do:
 * - Business logic → Service classes
 * - SQL → DAO classes
 * - Authentication rules → AuthHandler / UserService
 * - Cart rules → CartHandler / CartService
 * - Anything else → the appropriate Handler class
 *
 * Thread safety:
 * Each ClientHandler instance is used by exactly one thread.
 * The only shared state it touches is SessionManager (thread-safe
 * ConcurrentHashMap) and the Handler instances (stateless — they hold
 * only final references to Services, which are also stateless).
 * There is no shared mutable state inside ClientHandler itself.
 *
 * currentToken field:
 * Tracks the session token of the logged-in user on this connection.
 * Set when a LOGIN succeeds, cleared when LOGOUT is processed.
 * Used exclusively by the finally block to remove the session from
 * SessionManager when the client disconnects — whether cleanly or not.
 */
public class ClientHandler implements Runnable {

    // ── Injected dependencies ─────────────────────────────────────
    private final Socket socket;
    private final SessionManager sessionManager;
    private final UDPServer udpServer;
    private final AuthHandler authHandler;
    private final ProductHandler productHandler;
    private final CartHandler cartHandler;
    private final OrderHandler orderHandler;
    private final AdminHandler adminHandler;

    // ── Per-connection mutable state ──────────────────────────────
    // volatile: although only one thread reads/writes this, the shutdown
    // hook runs on a different thread and may inspect it during diagnostics.
    private volatile String currentToken = null;

    // ────────────────────────────────────────────────────────────
    // Constructor
    // ────────────────────────────────────────────────────────────

    public ClientHandler(Socket socket,
            SessionManager sessionManager,
            UDPServer udpServer,
            AuthHandler authHandler,
            ProductHandler productHandler,
            CartHandler cartHandler,
            OrderHandler orderHandler,
            AdminHandler adminHandler) {
        this.socket = socket;
        this.sessionManager = sessionManager;
        this.udpServer = udpServer;
        this.authHandler = authHandler;
        this.productHandler = productHandler;
        this.cartHandler = cartHandler;
        this.orderHandler = orderHandler;
        this.adminHandler = adminHandler;
    }

    // ────────────────────────────────────────────────────────────
    // Runnable entry point
    // ────────────────────────────────────────────────────────────

    @Override
    public void run() {
        String clientAddress = socket.getInetAddress().getHostAddress()
                + ":" + socket.getPort();

        BufferedReader reader = null;
        PrintWriter writer = null;

        try {
            // ── Open streams ──────────────────────────────────────
            // Always specify UTF-8 explicitly — never rely on platform default
            reader = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));

            // autoFlush = true — every println() sends the line immediately.
            // Without autoFlush the response sits in a buffer until it is
            // full or flushed manually, causing the client to wait forever.
            writer = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            // ── Read-dispatch-respond loop ─────────────────────────
            String line;
            while ((line = reader.readLine()) != null) {
                // readLine() returns null when the client closes the connection.
                // That exits the loop cleanly — no exception, no noise in the log.

                String response;
                try {
                    ParsedRequest req = RequestParser.parse(line);
                    response = dispatch(req);

                } catch (RequestParser.InvalidRequestException e) {
                    // Malformed line or unknown command — reply with an error
                    // but keep the connection alive. The client can send again.
                    response = ResponseBuilder.error("Unknown command");
                    System.err.println("[ClientHandler] Bad command from "
                            + clientAddress + ": '" + line + "'");

                } catch (Exception e) {
                    // Catch-all safety net — no unhandled exception should
                    // kill this thread. Log it and send a generic error.
                    response = ResponseBuilder.error("Internal server error");
                    System.err.println("[ClientHandler] Unexpected error from "
                            + clientAddress + ": " + e.getMessage());
                    e.printStackTrace();
                }

                writer.println(response);
            }

            System.out.println("[ClientHandler] Client disconnected cleanly: "
                    + clientAddress);

        } catch (IOException e) {
            // Client disconnected abruptly (network failure, window closed, etc.)
            // This is normal — not a server bug. Log at info level, not error.
            System.out.println("[ClientHandler] Client disconnected abruptly: "
                    + clientAddress + " — " + e.getMessage());
        } finally {
            // ── Cleanup — always runs, even after an exception ────
            cleanup(reader, writer, clientAddress);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Command dispatcher
    // ────────────────────────────────────────────────────────────

    /**
     * Routes a parsed request to the correct handler method.
     *
     * This switch is the only place in the entire server where Command
     * values are mapped to handler calls. Adding a new command means
     * adding one case here and one method on the appropriate handler.
     *
     * ClientHandler tracks the session token for two commands:
     * LOGIN — on success, extract the token from the response and
     * store it in currentToken for cleanup on disconnect
     * LOGOUT — clear currentToken so cleanup does not double-remove
     *
     * All other commands simply delegate and return the handler's response.
     */
    private String dispatch(ParsedRequest req) {
        Command cmd = req.getCommand();
        String[] params = req.getParams();

        switch (cmd) {

            // ── Authentication ────────────────────────────────────
            case REGISTER:
                return authHandler.handle(cmd, params, socket);

            case LOGIN: {
                String response = authHandler.handle(cmd, params, socket);
                // On successful login, extract and track the session token
                // so the finally block can remove it from SessionManager
                // if the client disconnects without sending LOGOUT.
                if (ResponseBuilder.isOk(response)) {
                    // Response payload: "token|role"
                    // extractPayload("OK|token123|USER") → "token123|USER"
                    String payload = ResponseBuilder.extractPayload(response);
                    String[] parts = payload.split("\\|", -1);
                    if (parts.length >= 1) {
                        currentToken = parts[0];
                    }
                }
                return response;
            }

            case LOGOUT: {
                String response = authHandler.handle(cmd, params, socket);
                // Clear the token regardless of response — if the handler
                // returned ERR (token was already gone), the session is
                // already removed. Either way we should not remove it again.
                currentToken = null;
                return response;
            }

            // ── Product browsing ──────────────────────────────────
            case GET_PRODUCTS:
                return productHandler.handle(cmd, params, currentToken);

            case GET_PRODUCT:
                return productHandler.handle(cmd, params, currentToken);

            case GET_CATEGORIES:
                return productHandler.handle(cmd, params, currentToken);

            // ── Cart ──────────────────────────────────────────────
            case CART_ADD:
                return cartHandler.handleAdd(params);

            case CART_REMOVE:
                return cartHandler.handleRemove(params);

            case CART_VIEW:
                return cartHandler.handleView(params);

            case CART_CLEAR:
                return cartHandler.handleClear(params);

            // ── Orders ────────────────────────────────────────────
            case CHECKOUT:
                return orderHandler.handle(cmd, params);

            case ORDER_HISTORY:
                return orderHandler.handle(cmd, params);

            // ── Admin ─────────────────────────────────────────────
            case ADMIN_ADD_PRODUCT:
                return adminHandler.handleAddProduct(params);

            case ADMIN_EDIT_PRODUCT:
                return adminHandler.handleEditProduct(params);

            case ADMIN_DELETE_PRODUCT:
                return adminHandler.handleDeleteProduct(params);

            case ADMIN_LIST_ORDERS:
                return adminHandler.handleListOrders(params);

            case ADMIN_UPDATE_STATUS:
                return adminHandler.handleUpdateStatus(params);

            case ADMIN_LIST_USERS:
                return adminHandler.handleListUsers(params);

            case ADMIN_DELETE_USER:
                return adminHandler.handleDeleteUser(params);

            default:
                return ResponseBuilder.error("Command not implemented: " + cmd);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Cleanup
    // ────────────────────────────────────────────────────────────

    /**
     * Releases all resources held by this connection.
     * Always runs — in a finally block in run().
     *
     * Order of cleanup:
     * 1. Remove the session from SessionManager — done first so the
     * token is invalidated before any stream is closed. Prevents a
     * race where another thread reads a session whose socket is
     * already half-closed.
     * 2. Close PrintWriter (flushes any buffered output)
     * 3. Close BufferedReader
     * 4. Close the Socket itself
     *
     * All close operations are individually try-caught — if one fails
     * the others still run. We never let one cleanup failure prevent
     * the remaining resources from being released.
     */
    private void cleanup(BufferedReader reader,
            PrintWriter writer,
            String clientAddress) {

        // Step 1 — Invalidate session
        if (currentToken != null) {
            sessionManager.removeSession(currentToken);
            currentToken = null;
        }

        // Step 2 — Close writer (has its own internal flush)
        if (writer != null) {
            writer.close();
        }

        // Step 3 — Close reader
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Nothing meaningful to do if close fails
            }
        }

        // Step 4 — Close socket
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        System.out.println("[ClientHandler] Resources released for: " + clientAddress);
    }
}