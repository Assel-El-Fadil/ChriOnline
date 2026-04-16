package Server;

import Server.DAO.*;
import Server.service.*;
import Server.handlers.*;
import Server.service.PaymentService;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {

    // ── Network configuration ─────────────────────────────────────
    private static final int TCP_PORT = 8084;
    private static final int THREAD_POOL_SIZE = 20;

    // ── Core server infrastructure ────────────────────────────────
    private final ServerSocket serverSocket;
    private final ExecutorService pool;

    // ── Server-wide singletons ─
    private final SessionManager sessionManager;
    private final UDPServer udpServer;

    // ── DAOs ──────────────────────────────────────────────────────
    private final UserDAO userDAO;
    private final ProductDAO productDAO;
    private final CartDAO cartDAO;
    private final OrderDAO orderDAO;
    private final TransactionDAO transactionDAO;

    // ── Services ──────────────────────────────────────────────────
    private final UserService userService;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    // ── Handlers ──────────────────────────────────────────────────
    private final AuthHandler authHandler;
    private final ProductHandler productHandler;
    private final CartHandler cartHandler;
    private final OrderHandler orderHandler;
    private final AdminHandler adminHandler;
    private final UserHandler userHandler;

    // ────────────────────────────────────────────────────────────
    //  Constructor
    // ────────────────────────────────────────────────────────────

    public Server() throws IOException {

        this.serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("[Server] Bound to TCP port " + TCP_PORT);

        this.pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        System.out.println("[Server] Thread pool ready ("
                + THREAD_POOL_SIZE + " threads)");

        this.sessionManager = new SessionManager();
        this.udpServer = new UDPServer();


        this.userDAO = new UserDAO();
        this.productDAO = new ProductDAO();
        this.cartDAO = new CartDAO();
        this.orderDAO = new OrderDAO();
        this.transactionDAO = new TransactionDAO();

        this.userService = new UserService(userDAO);
        this.productService = new ProductService(productDAO);
        this.cartService = new CartService(cartDAO, productDAO);
        this.orderService = new OrderService(orderDAO);
        this.paymentService = new PaymentService();

        this.authHandler = new AuthHandler(userService, cartService, sessionManager);
        this.productHandler = new ProductHandler(productService);
        this.cartHandler = new CartHandler(cartService, productService, sessionManager);
        this.orderHandler = new OrderHandler(orderService, cartService, paymentService, sessionManager, udpServer, productService, transactionDAO);
        this.adminHandler = new AdminHandler(userService, productService, orderService, sessionManager);
        this.userHandler = new UserHandler(userService, sessionManager);

        System.out.println("[Server] All dependencies wired — ready to accept connections.");
    }

    // ────────────────────────────────────────────────────────────
    //  Accept loop
    // ────────────────────────────────────────────────────────────

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

                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        sessionManager,
                        udpServer,
                        authHandler,
                        productHandler,
                        cartHandler,
                        orderHandler,
                        adminHandler,
                        userHandler
                );

                pool.submit(handler);

            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    System.out.println("[Server] Server socket closed — exiting accept loop.");
                    break;
                }

                System.err.println("[Server] SocketException in accept loop: " + e.getMessage());

            } catch (IOException e) {
                System.err.println("[Server] IOException accepting connection: " + e.getMessage());
            }
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Graceful shutdown
    // ────────────────────────────────────────────────────────────

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

        sessionManager.clearAll();

        udpServer.close();

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
            System.err.println("[Server] FATAL: Startup error — " + e.getMessage());
            System.exit(1);
            return;
        }

        // Register shutdown hook — runs on Ctrl+C
        final Server serverRef = server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverRef.shutdown();
        }, "shutdown-hook"));

        server.start();
    }
}
