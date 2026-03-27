package Server;

import Shared.*;
import Server.handlers.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final SessionManager sessionManager;
    private final UDPServer udpServer;
    private final AuthHandler authHandler;
    private final ProductHandler productHandler;
    private final CartHandler cartHandler;
    private final OrderHandler orderHandler;
    private final AdminHandler adminHandler;
    private final UserHandler userHandler;

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
            AdminHandler adminHandler,
            UserHandler userHandler) {
        this.socket = socket;
        this.sessionManager = sessionManager;
        this.udpServer = udpServer;
        this.authHandler = authHandler;
        this.productHandler = productHandler;
        this.cartHandler = cartHandler;
        this.orderHandler = orderHandler;
        this.adminHandler = adminHandler;
        this.userHandler = userHandler;
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
            reader = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));

            writer = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            // ── Read-dispatch-respond loop ─────────────────────────
            String line;
            while ((line = reader.readLine()) != null) {

                String response;
                try {
                    ParsedRequest req = RequestParser.parse(line);
                    response = dispatch(req);

                } catch (RequestParser.InvalidRequestException e) {
                    response = ResponseBuilder.error("Unknown command");
                    System.err.println("[ClientHandler] Bad command from "
                            + clientAddress + ": '" + line + "'");

                } catch (Exception e) {
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
            System.out.println("[ClientHandler] Client disconnected abruptly: "
                    + clientAddress + " — " + e.getMessage());
        } finally {
            cleanup(reader, writer, clientAddress);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Command dispatcher
    // ────────────────────────────────────────────────────────────

    private String dispatch(ParsedRequest req) {
        Command cmd = req.getCommand();
        String[] params = req.getParams();

        switch (cmd) {

            // ── Authentication ────────────────────────────────────
            case REGISTER:
                return authHandler.handle(cmd, params, socket);

            case LOGIN: {
                String response = authHandler.handle(cmd, params, socket);
                if (ResponseBuilder.isOk(response)) {
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

            case GET_ORDER_STATUS:
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
            case ADMIN_HARD_DELETE_USER:
                return adminHandler.handleHardDeleteUser(params);

            case ADMIN_DEACTIVATE_USER:
                return adminHandler.handleDeactivateUser(params);

            case ADMIN_ACTIVATE_USER:
                return adminHandler.handleActivateUser(params);

            // ── User Profile ───────────────────────────────────────
            case GET_PROFILE:
                return userHandler.handleGetProfile(params);

            case EDIT_PROFILE:
                return userHandler.handleEditProfile(params);

            default:
                return ResponseBuilder.error("Command not implemented: " + cmd);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Cleanup
    // ────────────────────────────────────────────────────────────

    private void cleanup(BufferedReader reader,
            PrintWriter writer,
            String clientAddress) {

        if (currentToken != null) {
            sessionManager.removeSession(currentToken);
            currentToken = null;
        }

        if (writer != null) {
            writer.close();
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }

        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        System.out.println("[ClientHandler] Resources released for: " + clientAddress);
    }
}