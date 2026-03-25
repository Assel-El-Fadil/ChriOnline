package Server;

import Server.DAO.*;
import Server.service.*;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP server entry point and accept loop.
 *
 * Responsibilities:
 *   - Bind ServerSocket on TCP_PORT (5000)
 *   - Maintain a fixed ExecutorService thread pool (20 threads)
 *   - Construct and wire the full dependency graph once at startup:
 *       ConnectionPool → DAOs → Services → Handlers
 *   - Accept incoming client connections in a blocking loop
 *   - Wrap each accepted Socket in a ClientHandler and submit to pool
 *   - Shut down cleanly on SIGTERM / Ctrl+C via a shutdown hook
 *
 * What Server does NOT do:
 *   - Business logic              → Service classes
 *   - SQL                         → DAO classes
 *   - Protocol parsing/formatting → chri.shared classes
 *   - Per-client I/O and dispatch → ClientHandler
 *
 * Threading model:
 *   The main thread runs the accept() loop — it blocks on accept()
 *   and does nothing else. Each accepted connection is handed off
 *   immediately to the thread pool. The main thread never touches
 *   stream I/O. This means the server can accept a new connection
 *   in microseconds regardless of what the 20 worker threads are doing.
 */
public class Server {

    // ── Network configuration ─────────────────────────────────────
    private static final int TCP_PORT         = 5000;
    private static final int THREAD_POOL_SIZE = 20;

    // ── Core server infrastructure ────────────────────────────────
    private final ServerSocket   serverSocket;
    private final ExecutorService pool;

    // ── Server-wide singletons (shared across all ClientHandlers) ─
    private final SessionManager  sessionManager;
    private final UDPServer udpServer;

    // ── DAOs ──────────────────────────────────────────────────────
    private final UserDAO    userDAO;
    private final ProductDAO productDAO;
    private final CartDAO    cartDAO;
    private final OrderDAO   orderDAO;

    // ── Services ──────────────────────────────────────────────────
    private final UserService    userService;
    private final ProductService productService;
    private final CartService    cartService;
    private final OrderService   orderService;

    // ── Handlers ──────────────────────────────────────────────────
    private final AuthHandler    authHandler;
    private final ProductHandler productHandler;
    private final CartHandler    cartHandler;
    private final OrderHandler   orderHandler;
    private final AdminHandler   adminHandler;

    // ────────────────────────────────────────────────────────────
    //  Constructor — builds the full dependency graph
    // ────────────────────────────────────────────────────────────

    /**
     * Wires every dependency from ConnectionPool up to Handlers.
     * If any step fails (e.g. port already bound, DB unreachable)
     * the constructor throws and main() exits with a clear message.
     *
     * Dependency wiring order:
     *   1. Network infrastructure (ServerSocket, thread pool)
     *   2. Server-wide singletons (SessionManager, UDPBroadcaster)
     *   3. DAOs  — depend only on ConnectionPool (initialized statically)
     *   4. Services — depend on DAOs
     *   5. Handlers — depend on Services and SessionManager
     *
     * @throws IOException if the ServerSocket cannot bind to TCP_PORT
     */
    public Server() throws IOException {

        // ── 1. Network infrastructure ─────────────────────────────
        this.serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("[Server] Bound to TCP port " + TCP_PORT);

        this.pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        System.out.println("[Server] Thread pool ready ("
                + THREAD_POOL_SIZE + " threads)");

        // ── 2. Server-wide singletons ─────────────────────────────
        this.sessionManager = new SessionManager();
        this.udpServer = new UDPServer();   // throws RuntimeException if socket fails

        // ── 3. DAOs ───────────────────────────────────────────────
        // ConnectionPool initializes itself statically on first class load.
        // Constructing a DAO here triggers that initialization if not done yet.
        this.userDAO    = new UserDAO();
        this.productDAO = new ProductDAO();
        this.cartDAO    = new CartDAO();
        this.orderDAO   = new OrderDAO();

        // ── 4. Services ───────────────────────────────────────────
        this.userService    = new UserService(userDAO);
        this.productService = new ProductService(productDAO);
        this.cartService    = new CartService(cartDAO, productDAO);
        this.orderService   = new OrderService(orderDAO, cartService, productDAO);

        // ── 5. Handlers ───────────────────────────────────────────
        this.authHandler    = new AuthHandler(userService, sessionManager);
        this.productHandler = new ProductHandler(productService);
        this.cartHandler    = new CartHandler(cartService, sessionManager);
        this.orderHandler   = new OrderHandler(orderService, sessionManager, udpServer);
        this.adminHandler   = new AdminHandler(userService, productService,
                orderService, sessionManager);

        System.out.println("[Server] All dependencies wired — ready to accept connections.");
    }

    // ────────────────────────────────────────────────────────────
    //  Accept loop — runs on the main thread
    // ────────────────────────────────────────────────────────────

    /**
     * Blocks on accept() until the server is shut down.
     *
     * Each time a client connects:
     *   1. accept() returns a new Socket for that specific client
     *   2. A ClientHandler is constructed with the socket and all handlers
     *   3. The handler is submitted to the thread pool (non-blocking)
     *   4. The loop immediately calls accept() again for the next client
     *
     * The main thread never does I/O itself. It only accepts and delegates.
     * This means a slow client never blocks the next client from connecting.
     */
    public void start() {
        System.out.println("[Server] Listening — waiting for client connections...\n");

        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();

                String clientAddress = clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort();
                System.out.println("[Server] Client connected: " + clientAddress
                        + "  | Active sessions: "
                        + sessionManager.getActiveSessionCount());

                // Build one ClientHandler per connected client and submit it
                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        sessionManager,
                        udpServer,
                        authHandler,
                        productHandler,
                        cartHandler,
                        orderHandler,
                        adminHandler
                );

                pool.submit(handler);

            } catch (SocketException e) {
                // serverSocket.close() in shutdown() throws SocketException
                // from the blocking accept() call — this is the expected exit path
                if (serverSocket.isClosed()) {
                    System.out.println("[Server] Server socket closed — exiting accept loop.");
                    break;
                }
                // Unexpected SocketException — log and keep running
                System.err.println("[Server] SocketException in accept loop: "
                        + e.getMessage());

            } catch (IOException e) {
                // Individual connection failure — log and keep running.
                // One bad client should never stop other clients from connecting.
                System.err.println("[Server] IOException accepting connection: "
                        + e.getMessage());
            }
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Graceful shutdown
    // ────────────────────────────────────────────────────────────

    /**
     * Shuts down the server cleanly.
     * Called by the shutdown hook registered in main().
     *
     * Shutdown sequence:
     *   1. Close ServerSocket — unblocks accept(), exits the start() loop
     *   2. Invalidate all sessions — tokens become invalid immediately
     *   3. Close UDP socket
     *   4. Shut down thread pool — waits up to 10 seconds for
     *      in-flight ClientHandlers to finish, then forces shutdown
     */
    public void shutdown() {
        System.out.println("\n[Server] Shutdown initiated...");

        // Step 1 — Close the server socket (unblocks accept())
        try {
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        // Step 2 — Invalidate all active sessions
        sessionManager.clearAll();

        // Step 3 — Close UDP socket
        udpServer.close();

        // Step 4 — Drain the thread pool
        pool.shutdown();
        try {
            boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);
            if (!finished) {
                System.out.println("[Server] Timeout — forcing pool shutdown.");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[Server] Shutdown complete.");
    }

    // ────────────────────────────────────────────────────────────
    //  main()
    // ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Server server;

        try {
            server = new Server();
        } catch (BindException e) {
            System.err.println("[Server] FATAL: Port " + TCP_PORT
                    + " is already in use. Is another instance running?");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("[Server] FATAL: Could not start — " + e.getMessage());
            System.exit(1);
            return;
        } catch (RuntimeException e) {
            // Catches UDPBroadcaster init failure and ConnectionPool init failure
            System.err.println("[Server] FATAL: Startup error — " + e.getMessage());
            System.exit(1);
            return;
        }

        // Register shutdown hook — runs on Ctrl+C or SIGTERM
        final Server serverRef = server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverRef.shutdown();
        }, "shutdown-hook"));

        // Blocks here on the accept() loop until serverSocket is closed
        server.start();
    }
}
