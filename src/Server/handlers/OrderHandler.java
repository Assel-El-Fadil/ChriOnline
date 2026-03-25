package Server.handlers;

import Server.DAO.ConnectionPool;
import Server.DAO.OrderDAO;
import Server.DAO.ProductDAO;
import Server.SessionManager;
import Server.UDPServer;
import Server.service.Cart;
import Server.service.CartService;
import Server.service.PaymentResult;
import Server.service.PaymentService;
import Shared.Command;
import Shared.DTO.OrderDTO;
import Shared.ResponseBuilder;
import Shared.SessionData;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderHandler {

    // ─── Dependencies injected via constructor ─────────────────────
    private final OrderDAO       orderDAO;
    private final CartService    cartService;
    private final PaymentService paymentService;
    private final SessionManager sessionManager;
    private final UDPServer      udpBroadcaster;
    private final ProductDAO     productDAO;

    // ──────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────
    public OrderHandler(OrderDAO orderDAO, CartService cartService,
                        PaymentService paymentService, SessionManager sessionManager,
                        UDPServer udpBroadcaster, ProductDAO productDAO) {
        this.orderDAO       = orderDAO;
        this.cartService    = cartService;
        this.paymentService = paymentService;
        this.sessionManager = sessionManager;
        this.udpBroadcaster = udpBroadcaster;
        this.productDAO     = productDAO;
    }

    // ──────────────────────────────────────────────────────────────
    // Main entry point — called by ClientHandler dispatch switch
    // ──────────────────────────────────────────────────────────────
    public String handle(Command cmd, String[] params) {
        switch (cmd) {
            case CHECKOUT:      return handleCheckout(params);
            case ORDER_HISTORY: return handleOrderHistory(params);
            default:            return ResponseBuilder.error("Unknown order command");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CHECKOUT
    // params: 0=token, 1=method, 2=cardNum, 3=holder, 4=expiry, 5=cvv
    // Command: CHECKOUT|token|CARD|cardNum|holder|expiry|cvv
    //
    // 8-step transaction flow:
    //   1. Validate session
    //   2. Check cart not empty
    //   3. Validate payment (PaymentService)
    //   4. Open JDBC transaction (setAutoCommit false)
    //   5. createOrder() → get orderId
    //   6. For each item → addOrderItem() + deductStock() — rollback if stock fails
    //   7. commit(), generate refCode, clearCart()
    //   8. UDP notify → return OK|orderId|ref
    // ──────────────────────────────────────────────────────────────
    private String handleCheckout(String[] params) {

        if (params.length < 6) {
            return ResponseBuilder.error("Missing checkout parameters");
        }

        String token         = params[0];
        String paymentMethod = params[1];
        String cardNum       = params[2];
        String holder        = params[3];
        String expiry        = params[4];
        String cvv           = params[5];

        // ── Step 1 : Validate session ──────────────────────────────
        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }
        int    userId    = session.getUserId();
        String clientIP  = session.getClientIP();
        int    udpPort   = session.getClientUdpPort();

        // ── Step 2 : Check cart is not empty ──────────────────────
        Cart cart = cartService.getOrCreateCart(token);
        if (cart.getItems().isEmpty()) {
            return ResponseBuilder.error("Cart is empty");
        }

        // ── Step 3 : Validate payment ─────────────────────────────
        PaymentResult pr = PaymentService.validate(cardNum, holder, expiry, cvv);
        if (!pr.isSuccess()) {
            return ResponseBuilder.error(pr.getMessage());
        }

        // ── Step 4 : Calculate total & open JDBC transaction ──────
        double total = cart.calculateTotal(productDAO);

        // Generate reference code before the transaction
        String refCode = UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);  // BEGIN transaction

            // ── Step 5 : Insert order row ──────────────────────────
            int orderId = orderDAO.createOrder(conn, userId, total, paymentMethod, refCode);

            // ── Step 6 : Process each cart item ───────────────────
            for (Map.Entry<Integer, Integer> entry : cart.getItems().entrySet()) {
                int productId = entry.getKey();
                int qty       = entry.getValue();

                // Get unit price from DB (price at time of purchase)
                double unitPrice = 0.0;
                try {
                    var product = productDAO.findById(productId);
                    if (product != null) unitPrice = product.price;
                } catch (SQLException e) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                    return ResponseBuilder.error("Product lookup failed during checkout");
                }

                // Insert line item
                orderDAO.addOrderItem(conn, orderId, productId, qty, unitPrice);

                // Deduct stock — rollback if stock ran out
                boolean stocked = orderDAO.deductStock(conn, productId, qty);
                if (!stocked) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                    return ResponseBuilder.error("Product out of stock during checkout");
                }
            }

            // ── Step 7 : Commit, clear cart ───────────────────────
            conn.commit();
            conn.setAutoCommit(true);

            // Clear cart in memory and DB
            try {
                cartService.clearCart(token, userId);
            } catch (SQLException e) {
                // Non-fatal — order is already committed
                System.err.println("[OrderHandler] Cart clear failed after commit: "
                        + e.getMessage());
            }

            System.out.println("[OrderHandler] CHECKOUT success — orderId=" + orderId
                    + " ref=" + refCode + " total=" + total + " user=" + userId);

            // ── Step 8 : UDP notification (fire-and-forget) ───────
            udpBroadcaster.notify(
                    clientIP,
                    udpPort,
                    "ORDER_CONFIRMED|" + refCode + "|" + String.format("%.2f", total)
            );

            // Return orderId and refCode to the client
            return ResponseBuilder.ok(orderId + "|" + refCode);

        } catch (SQLException e) {
            // Unexpected DB error — try to rollback
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
    // params: 0=token
    // returns: OK|order1_str;order2_str;...  or  ERR|message
    // ──────────────────────────────────────────────────────────────
    private String handleOrderHistory(String[] params) {

        if (params.length < 1) {
            return ResponseBuilder.error("Missing token");
        }

        String token = params[0];

        // Validate session
        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        int userId = session.getUserId();

        // Fetch orders from DB
        List<OrderDTO> orders = orderDAO.findByUser(userId);

        if (orders.isEmpty()) {
            return ResponseBuilder.ok("");
        }

        // Serialize each OrderDTO and join with ';'
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            sb.append(orders.get(i).toProtocolString());
            if (i < orders.size() - 1) sb.append(";");
        }

        return ResponseBuilder.ok(sb.toString());
    }
}