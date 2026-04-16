package Server.handlers;

import Server.DAO.ConnectionPool;
import Server.SessionManager;
import Server.UDPServer;
import Server.service.Cart;
import Server.service.CartService;
import Server.service.OrderService;
import Server.service.PaymentResult;
import Server.service.PaymentService;
import Server.service.ProductService;
import Shared.Command;
import Shared.DTO.OrderDTO;
import Shared.ResponseBuilder;
import Shared.SessionData;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OrderHandler {

    private final Map<String, PendingCheckout> pendingCheckouts = new ConcurrentHashMap<>();

    private final OrderService orderService;
    private final CartService cartService;
    private final PaymentService paymentService;
    private final SessionManager sessionManager;
    private final UDPServer udpServer;
    private final ProductService productService;
    private final Server.DAO.TransactionDAO transactionDAO;

    // ──────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────
    public OrderHandler(OrderService orderService, CartService cartService,
                        PaymentService paymentService, SessionManager sessionManager,
                        UDPServer udpServer, ProductService productService,
                        Server.DAO.TransactionDAO transactionDAO) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.paymentService = paymentService;
        this.sessionManager = sessionManager;
        this.udpServer = udpServer;
        this.productService = productService;
        this.transactionDAO = transactionDAO;
    }

    public String handle(Command cmd, String[] params) {
        switch (cmd) {
            case CHECKOUT: return handleCheckout(params);
            case CHECKOUT_INIT: return handleCheckoutInit(params);
            case CHECKOUT_CONFIRM: return handleCheckoutConfirm(params);
            case ORDER_HISTORY: return handleOrderHistory(params);
            case GET_ORDER_STATUS: return handleGetOrderStatus(params);
            case GET_ORDER_ITEMS: return handleGetOrderItems(params);
            default: return ResponseBuilder.error("Unknown order command");
        }
    }

    private String handleGetOrderItems(String[] params) {
        if (params.length < 2) {
            return ResponseBuilder.error("Missing order ID");
        }

        String token = params[0];
        int orderId;
        try {
            orderId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid order ID");
        }

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        // Authorization check
        OrderDTO order = orderService.getOrderById(orderId);
        if (order == null) {
            return ResponseBuilder.error("Order not found");
        }
        if (order.userId != session.getUserId() && !session.isAdmin()) {
            return ResponseBuilder.error("Unauthorized");
        }

        List<Shared.DTO.OrderItemDTO> items = orderService.getOrderItems(orderId);
        if (items.isEmpty()) {
            return ResponseBuilder.ok("");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i).toProtocolString());
            if (i < items.size() - 1) sb.append(";");
        }
        return ResponseBuilder.ok(sb.toString());
    }

    private String handleCheckoutInit(String[] params) {
        if (params.length < 6) {
            return ResponseBuilder.error("Missing checkout parameters");
        }

        String token = params[0];
        String paymentMethod = params[1];
        String cardNum = params[2];
        String holder = params[3];
        String expiry = params[4];
        String cvv = params[5];

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        // Validate card before sending 2FA
        PaymentResult pr = paymentService.validate(cardNum, holder, expiry, cvv);
        if (!pr.isSuccess()) {
            return ResponseBuilder.error(pr.getMessage());
        }

        // Generate 6-digit code and transaction UUID
        String code = String.format("%06d", new Random().nextInt(1000000));
        String transactionId = UUID.randomUUID().toString();
        
        // Log to database as PENDING
        transactionDAO.logTransaction(session.getUserId(), transactionId);

        // Store pending checkout in memory
        pendingCheckouts.put(token, new PendingCheckout(paymentMethod, cardNum, holder, expiry, cvv, code, transactionId));

        // Simulate Email
        System.out.println("==========================================");
        System.out.println("SIMULATED EMAIL TO: (User ID " + session.getUserId() + ")");
        System.out.println("Subject: Payment Verification Code");
        System.out.println("Transaction ID: " + transactionId);
        System.out.println("Your ChriOnline verification code is: " + code);
        System.out.println("==========================================");

        return ResponseBuilder.ok("2FA_REQUIRED|" + transactionId + "|" + System.currentTimeMillis());
    }

    private String handleCheckoutConfirm(String[] params) {
        if (params.length < 3) {
            return ResponseBuilder.error("Missing verification parameters");
        }

        String token = params[0];
        String code = params[1];
        String transactionId = params[2];

        PendingCheckout pc = pendingCheckouts.get(token);
        if (pc == null) {
            return ResponseBuilder.error("No pending checkout found. Please restart the process.");
        }

        if (!pc.verificationCode.equals(code)) {
            return ResponseBuilder.error("Invalid verification code");
        }

        if (!pc.transactionId.equals(transactionId)) {
            return ResponseBuilder.error("Transaction ID mismatch. Security breach detected.");
        }

        // Use stored info to complete checkout
        String[] checkoutParams = {
            token, pc.paymentMethod, pc.cardNum, pc.holder, pc.expiry, pc.cvv
        };
        
        String result = handleCheckout(checkoutParams);
        
        if (ResponseBuilder.isOk(result)) {
            pendingCheckouts.remove(token);
            transactionDAO.updateStatus(transactionId, "SUCCESS");
        } else {
            transactionDAO.updateStatus(transactionId, "FAILED");
        }
        
        return result;
    }

    private String handleCheckout(String[] params) {

        if (params.length < 6) {
            return ResponseBuilder.error("Missing checkout parameters");
        }

        String token = params[0];
        String paymentMethod = params[1];
        String cardNum = params[2];
        String holder = params[3];
        String expiry = params[4];
        String cvv = params[5];

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }
        int userId = session.getUserId();
        String clientIP = session.getClientIP();
        int udpPort = session.getClientUdpPort();

        Cart cart = cartService.getOrCreateCart(token);
        if (cart.getItems().isEmpty()) {
            return ResponseBuilder.error("Cart is empty");
        }

        PaymentResult pr = paymentService.validate(cardNum, holder, expiry, cvv);
        if (!pr.isSuccess()) {
            return ResponseBuilder.error(pr.getMessage());
        }

        double total = cart.calculateTotal(productService);

        String refCode = UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            int orderId = orderService.createOrder(conn, userId, total, paymentMethod, refCode);

            for (Map.Entry<Integer, Integer> entry : cart.getItems().entrySet()) {
                int productId = entry.getKey();
                int qty       = entry.getValue();

                double unitPrice = 0.0;
                try {
                    var product = productService.getById(productId);
                    if (product != null) unitPrice = product.price;
                } catch (SQLException e) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                    return ResponseBuilder.error("Product lookup failed during checkout");
                }

                orderService.addOrderItem(conn, orderId, productId, qty, unitPrice);

                boolean stocked = orderService.deductStock(conn, productId, qty);
                if (!stocked) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                    return ResponseBuilder.error("Product out of stock during checkout");
                }
            }

            conn.commit();
            conn.setAutoCommit(true);

            try {
                clientIP = sessionManager.getClientIP(token);
                int clientPort = sessionManager.getClientUdpPort(token);
                if (clientIP != null && clientPort > 0) {
                    String msg = "ORDER_CONFIRMED|" + refCode + "|" + String.format("%.2f", total);
                    udpServer.notify(clientIP, clientPort, msg);
                }
            } catch (Exception e) {
                System.err.println("[OrderHandler] UDP notification failed: " + e.getMessage());
            }

            try {
                cartService.clearCart(token, userId);
            } catch (SQLException e) {
                System.err.println("[OrderHandler] Cart clear failed after commit: "
                        + e.getMessage());
            }

            System.out.println("[OrderHandler] CHECKOUT success — orderId=" + orderId
                    + " ref=" + refCode + " total=" + total + " user=" + userId);

            udpServer.notify(
                    clientIP,
                    udpPort,
                    "ORDER_CONFIRMED|" + refCode + "|" + String.format("%.2f", total)
            );

            return ResponseBuilder.ok(orderId + "|" + refCode);

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
            } catch (SQLException rollbackEx) {
                System.err.println("[OrderHandler] Rollback failed: " + rollbackEx.getMessage());
            }
            System.err.println("[OrderHandler] CHECKOUT DB error: " + e.getMessage());
            return ResponseBuilder.error("Checkout failed — please try again");

        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ORDER_HISTORY
    // ──────────────────────────────────────────────────────────────
    private String handleOrderHistory(String[] params) {

        if (params.length < 1) {
            return ResponseBuilder.error("Missing token");
        }

        String token = params[0];

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        int userId = session.getUserId();

        List<OrderDTO> orders = orderService.getUserOrders(userId);

        if (orders.isEmpty()) {
            return ResponseBuilder.ok("");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            sb.append(orders.get(i).toProtocolString());
            if (i < orders.size() - 1) sb.append(";");
        }

        return ResponseBuilder.ok(sb.toString());
    }

    // ──────────────────────────────────────────────────────────────
    // GET_ORDER_STATUS
    // ──────────────────────────────────────────────────────────────
    private String handleGetOrderStatus(String[] params) {
        if (params.length < 2) {
            return ResponseBuilder.error("Missing order ID");
        }

        String token = params[0];
        int orderId;
        try {
            orderId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid order ID");
        }

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        OrderDTO order = orderService.getOrderById(orderId);
        if (order == null) {
            return ResponseBuilder.error("Order not found");
        }

        if (order.userId != session.getUserId() && !session.isAdmin()) {
            return ResponseBuilder.error("Unauthorized to view this order");
        }

        return ResponseBuilder.ok(order.toProtocolString());
    }
}