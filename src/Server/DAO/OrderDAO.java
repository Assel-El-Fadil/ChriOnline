package Server.DAO;

import Shared.DTO.OrderDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    // ──────────────────────────────────────────────────────────────
    // WRITE OPERATIONS
    // All write methods receive an open Connection from OrderHandler.
    // They do NOT open or close the connection — that is the
    // caller's responsibility (needed for the JDBC transaction).
    // ──────────────────────────────────────────────────────────────

    /**
     * Inserts a new order row and returns the generated id.
     * Called first inside the checkout transaction.
     *
     * INSERT INTO orders (user_id, total_amount, payment_method, status, reference_code)
     */
    public int createOrder(Connection conn, int userId, double total,
                           String paymentMethod, String referenceCode) throws SQLException {

        final String sql =
                "INSERT INTO orders (user_id, total_amount, payment_method, status, reference_code) " +
                        "VALUES (?, ?, ?, 'PENDING', ?)";

        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, userId);
        ps.setDouble(2, total);
        ps.setString(3, paymentMethod);
        ps.setString(4, referenceCode);
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            return keys.getInt(1);
        }
        throw new SQLException("createOrder: INSERT succeeded but no generated key returned");
    }

    /**
     * Inserts one line item into order_items.
     * Called once per cart item inside the checkout transaction.
     *
     * INSERT INTO order_items (order_id, product_id, quantity, unit_price)
     */
    public void addOrderItem(Connection conn, int orderId, int productId,
                             int qty, double unitPrice) throws SQLException {

        final String sql =
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) " +
                        "VALUES (?, ?, ?, ?)";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);
        ps.setInt(2, productId);
        ps.setInt(3, qty);
        ps.setDouble(4, unitPrice);
        ps.executeUpdate();
    }

    /**
     * Decrements stock atomically.
     * The WHERE stock >= ? clause is the race-condition guard —
     * if another checkout already consumed the stock, executeUpdate()
     * returns 0 and the caller must rollback.
     *
     * UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?
     *
     * @return true if exactly 1 row was updated (stock successfully deducted)
     *         false if stock was insufficient (caller must rollback)
     */
    public boolean deductStock(Connection conn, int productId, int qty) throws SQLException {

        final String sql =
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, qty);
        ps.setInt(2, productId);
        ps.setInt(3, qty);
        return ps.executeUpdate() == 1;
    }

    /**
     * Updates the status of an order.
     * Called by AdminHandler (ADMIN_UPDATE_STATUS command).
     */
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
    // These open their own connection from the pool (no transaction needed).
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns all orders for a specific user, most recent first.
     * Used by OrderHandler for ORDER_HISTORY command.
     *
     * SELECT orders WHERE user_id = ? ORDER BY created_at DESC
     */
    public List<OrderDTO> findByUser(int userId) {
        final String sql =
                "SELECT id, reference_code, user_id, status, total_amount, payment_method, created_at " +
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

    /**
     * Returns all orders in the system, most recent first.
     * Used by AdminHandler for ADMIN_LIST_ORDERS command.
     */
    public List<OrderDTO> findAll() {
        final String sql =
                "SELECT id, reference_code, user_id, status, total_amount, payment_method, created_at " +
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
                rs.getString("reference_code"),
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