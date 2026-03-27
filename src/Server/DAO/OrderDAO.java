package Server.DAO;

import Shared.DTO.OrderDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    // ──────────────────────────────────────────────────────────────
    // WRITE OPERATIONS
    // ──────────────────────────────────────────────────────────────

    public int createOrder(Connection conn, int userId, double total,
                           String paymentMethod, String payment_ref) throws SQLException {

        final String sql =
                "INSERT INTO orders (user_id, total_amount, payment_method, status, payment_ref) " +
                        "VALUES (?, ?, ?, 'PENDING', ?)";

        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, userId);
        ps.setDouble(2, total);
        ps.setString(3, paymentMethod);
        ps.setString(4, payment_ref);
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            return keys.getInt(1);
        }
        throw new SQLException("createOrder: INSERT succeeded but no generated key returned");
    }

    public void addOrderItem(Connection conn, int orderId, int productId,
                             int qty, double unitPrice) throws SQLException {

        final String sql =
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) " +
                        "VALUES (?, ?, ?, ?, ?)";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);
        ps.setInt(2, productId);
        ps.setInt(3, qty);
        ps.setDouble(4, unitPrice);
        ps.setDouble(5, qty * unitPrice);
        ps.executeUpdate();
    }

    public boolean deductStock(Connection conn, int productId, int qty) throws SQLException {

        final String sql =
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, qty);
        ps.setInt(2, productId);
        ps.setInt(3, qty);
        return ps.executeUpdate() == 1;
    }

    public boolean updateStatus(int orderId, String newStatus) {
        final String sql = "UPDATE orders SET status = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, newStatus);
            ps.setInt(2, orderId);
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new DAOException("updateStatus failed for orderId=" + orderId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // READ OPERATIONS
    // ──────────────────────────────────────────────────────────────

    public List<OrderDTO> findByUser(int userId) {
        final String sql =
                "SELECT id, payment_ref, user_id, status, total_amount, payment_method, created_at " +
                        "FROM orders " +
                        "WHERE user_id = ? " +
                        "ORDER BY created_at DESC";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();
            List<OrderDTO> orders = new ArrayList<>();
            while (rs.next()) {
                orders.add(mapRow(rs));
            }
            return orders;

        } catch (SQLException e) {
            throw new DAOException("findByUser failed for userId=" + userId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public List<OrderDTO> findAll() {
        final String sql =
                "SELECT id, payment_ref, user_id, status, total_amount, payment_method, created_at " +
                        "FROM orders " +
                        "ORDER BY created_at DESC";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);

            ResultSet rs = ps.executeQuery();
            List<OrderDTO> orders = new ArrayList<>();
            while (rs.next()) {
                orders.add(mapRow(rs));
            }
            return orders;

        } catch (SQLException e) {
            throw new DAOException("findAll failed: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    private OrderDTO mapRow(ResultSet rs) throws SQLException {
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

    // ──────────────────────────────────────────────────────────────
    // DAO-layer exception
    // ──────────────────────────────────────────────────────────────

    public static class DAOException extends RuntimeException {
        public DAOException(String message) { super(message); }
        public DAOException(String message, Throwable cause) { super(message, cause); }
    }
}