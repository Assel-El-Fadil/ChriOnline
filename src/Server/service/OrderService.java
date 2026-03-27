package Server.service;

import Server.DAO.ConnectionPool;
import Server.DAO.OrderDAO;
import Shared.DTO.OrderDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class OrderService {

    private final OrderDAO orderDAO;

    public OrderService(OrderDAO orderDAO) {
        this.orderDAO = orderDAO;
    }

    // ────────────────────────────────────────────────────────────
    //  Order creation operations
    // ────────────────────────────────────────────────────────────

    public int createOrder(Connection conn, int userId, double total, 
                          String paymentMethod, String referenceCode) throws SQLException {
        return orderDAO.createOrder(conn, userId, total, paymentMethod, referenceCode);
    }

    public void addOrderItem(Connection conn, int orderId, int productId, 
                            int quantity, double unitPrice) throws SQLException {
        orderDAO.addOrderItem(conn, orderId, productId, quantity, unitPrice);
    }

    public boolean deductStock(Connection conn, int productId, int quantity) throws SQLException {
        return orderDAO.deductStock(conn, productId, quantity);
    }

    // ────────────────────────────────────────────────────────────
    //  Order retrieval operations
    // ────────────────────────────────────────────────────────────

    public List<OrderDTO> getUserOrders(int userId) {
        return orderDAO.findByUser(userId);
    }

    public List<OrderDTO> getAllOrders() {
        return orderDAO.findAll();
    }

    // ────────────────────────────────────────────────────────────
    //  Order status management
    // ────────────────────────────────────────────────────────────

    public void updateOrderStatus(int orderId, String newStatus) {
        validateStatus(newStatus);

        OrderDTO currentOrder = getOrderById(orderId);
        if (currentOrder == null) {
            throw new OrderNotFoundException("Order not found with ID: " + orderId);
        }

        validateStatusTransition(currentOrder.status, newStatus);

        boolean updated = orderDAO.updateStatus(orderId, newStatus);
        if (!updated) {
            throw new OrderUpdateException("Failed to update order status for order ID: " + orderId);
        }
    }

    public OrderDTO getOrderById(int orderId) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            String sql = "SELECT id, payment_ref, user_id, status, total_amount, payment_method, created_at " +
                        "FROM orders WHERE id = ?";
            
            var ps = conn.prepareStatement(sql);
            ps.setInt(1, orderId);
            var rs = ps.executeQuery();
            
            if (rs.next()) {
                return new OrderDTO(
                    rs.getInt("id"),
                    rs.getString("payment_ref"),
                    rs.getInt("user_id"),
                    rs.getString("status"),
                    rs.getDouble("total_amount"),
                    rs.getString("payment_method"),
                    rs.getTimestamp("created_at")
                );
            }
            return null;
            
        } catch (SQLException e) {
            throw new OrderServiceException("Error retrieving order: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Private validation methods
    // ────────────────────────────────────────────────────────────

    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        
        String upperStatus = status.toUpperCase();
        switch (upperStatus) {
            case "PENDING":
            case "VALIDATED":
            case "SHIPPED":
            case "DELIVERED":
            case "CANCELLED":
                break;
            default:
                throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        String current = currentStatus.toUpperCase();
        String newStat = newStatus.toUpperCase();

        if ("CANCELLED".equals(newStat)) {
            if (!"PENDING".equals(current) && !"VALIDATED".equals(current)) {
                throw new IllegalStatusTransitionException(
                    "Cannot cancel order from status: " + currentStatus);
            }
            return;
        }

        switch (current) {
            case "PENDING":
                if (!"VALIDATED".equals(newStat)) {
                    throw new IllegalStatusTransitionException(
                        "Order must go from PENDING to VALIDATED, not to: " + newStatus);
                }
                break;
            case "VALIDATED":
                if (!"SHIPPED".equals(newStat)) {
                    throw new IllegalStatusTransitionException(
                        "Order must go from VALIDATED to SHIPPED, not to: " + newStatus);
                }
                break;
            case "SHIPPED":
                if (!"DELIVERED".equals(newStat)) {
                    throw new IllegalStatusTransitionException(
                        "Order must go from SHIPPED to DELIVERED, not to: " + newStatus);
                }
                break;
            case "DELIVERED":
                throw new IllegalStatusTransitionException(
                    "DELIVERED order status cannot be changed");
            case "CANCELLED":
                throw new IllegalStatusTransitionException(
                    "CANCELLED order status cannot be changed");
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Service-specific exceptions
    // ────────────────────────────────────────────────────────────

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) { super(message); }
    }

    public static class OrderUpdateException extends RuntimeException {
        public OrderUpdateException(String message) { super(message); }
    }

    public static class IllegalStatusTransitionException extends RuntimeException {
        public IllegalStatusTransitionException(String message) { super(message); }
    }

    public static class OrderServiceException extends RuntimeException {
        public OrderServiceException(String message) { super(message); }
        public OrderServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
