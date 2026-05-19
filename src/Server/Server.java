package Server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Server.DAO.*;
import Server.service.*;
import Server.handlers.*;
import Server.service.PaymentService;
import Server.security.SecureHandshake;
import Shared.Security.CryptoConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class Server {
    private static final Logger logger = LogManager.getLogger(Server.class);


    // ── Network configuration ─────────────────────────────────────
    private static final int TCP_PORT = CryptoConfig.SSL_PORT;
    private static final int THREAD_POOL_SIZE = 20;
    private static final int MAX_CONNECTIONS_PER_MINUTE = 50;
    private static final int HANDSHAKE_TIMEOUT_MS = 60_000 * 2;

    // ── IP Rate limiter records first-connection timestamp + count ──
    private final ConcurrentHashMap<String, long[]> ipConnections = new ConcurrentHashMap<>();

    // ── Core server infrastructure ────────────────────────────────
    private final SSLServerSocket serverSocket;
    private final ExecutorService pool;

    // ── RSA KeyPair for application-layer handshake ────────────────
    private final KeyPair serverKeyPair;

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

    public Server() throws Exception {

        // ── Load KeyStore and create SSLServerSocket ──────────────
        KeyStore keyStore = KeyStore.getInstance(CryptoConfig.KEYSTORE_TYPE);
        try (FileInputStream fis = new FileInputStream(CryptoConfig.KEYSTORE_PATH)) {
            keyStore.load(fis, CryptoConfig.KEYSTORE_PASSWORD.toCharArray());
        }
        logger.info("[Server] KeyStore loaded: " + CryptoConfig.KEYSTORE_PATH);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, CryptoConfig.KEYSTORE_PASSWORD.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        this.serverSocket = (SSLServerSocket) ssf.createServerSocket(TCP_PORT);
        logger.info("[Server] SSLServerSocket bound to port " + TCP_PORT + " (TLS enabled)");

        // ── Load RSA KeyPair for application-layer handshake ─────
        this.serverKeyPair = SecureHandshake.loadKeyPairFromKeyStore();
        logger.info("[Server] RSA KeyPair loaded for secure handshake");

        this.pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        logger.info("[Server] Thread pool ready ("
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
        this.orderHandler = new OrderHandler(orderService, cartService, paymentService, sessionManager, udpServer, productService, transactionDAO, userService);
        this.adminHandler = new AdminHandler(userService, productService, orderService, sessionManager);
        this.userHandler = new UserHandler(userService, sessionManager);

        logger.info("[Server] All dependencies wired - ready to accept SSL connections.");
    }

    // ────────────────────────────────────────────────────────────
    //  Accept loop
    // ────────────────────────────────────────────────────────────

    public void start() {
        logger.info("[Server] Listening on SSL port " + TCP_PORT + " - waiting for client connections...\n");

        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();

                String clientIP = clientSocket.getInetAddress().getHostAddress();
                String clientAddress = clientIP + ":" + clientSocket.getPort();

                if (isRateLimited(clientIP)) {
                    logger.warn("[Server] TCP Flood: Too many connections from " + clientIP + ". Dropping.");
                    clientSocket.close();
                    continue;
                }

                clientSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                logger.info("[Server] SSL client connected: " + clientAddress
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
                        userHandler,
                        serverKeyPair
                );

                pool.submit(handler);

            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    logger.info("[Server] Server socket closed - exiting accept loop.");
                    break;
                }

                logger.error("[Server] SocketException in accept loop: " + e.getMessage());

            } catch (IOException e) {
                logger.error("[Server] IOException accepting connection: " + e.getMessage());
            }
        }
    }

    // ────────────────────────────────────────────────────────────
    //  TCP Rate Limiter helper
    // ────────────────────────────────────────────────────────────

    private boolean isRateLimited(String ip) {
        long now = Instant.now().toEpochMilli();
        long windowMs = 60_000L; // 1 minute window

        ipConnections.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > windowMs) {
                // Reset window: [windowStart, count]
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });

        long[] data = ipConnections.get(ip);
        return data != null && data[1] > MAX_CONNECTIONS_PER_MINUTE;
    }

    // ────────────────────────────────────────────────────────────
    //  Graceful shutdown
    // ────────────────────────────────────────────────────────────

    public void shutdown() {
        logger.info("\n[Server] Shutdown initiated...");

        try {
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("[Server] Error closing server socket: " + e.getMessage());
        }

        sessionManager.clearAll();

        udpServer.close();

        pool.shutdown();
        try {
            boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);
            if (!finished) {
                logger.info("[Server] Timeout - forcing pool shutdown.");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("[Server] Shutdown complete.");
    }

    // ────────────────────────────────────────────────────────────
    //  main()
    // ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Server server;

        try {
            server = new Server();
        } catch (BindException e) {
            logger.error("[Server] FATAL: Port " + TCP_PORT
                    + " is already in use. Is another instance running?");
            System.exit(1);
            return;
        } catch (Exception e) {
            logger.error("[Server] FATAL: Could not start - " + e.getMessage());
            System.exit(1);
            return;
        }

        server.start();
    }
}
