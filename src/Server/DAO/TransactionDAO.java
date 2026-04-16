package Server.DAO;

import java.sql.*;

public class TransactionDAO {

    public void logTransaction(int userId, String uuid) {
        final String sql = "INSERT INTO payment_transactions (user_id, transaction_uuid, status) VALUES (?, ?, 'PENDING')";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("logTransaction failed: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public void updateStatus(String uuid, String status) {
        final String sql = "UPDATE payment_transactions SET status = ? WHERE transaction_uuid = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed for " + uuid + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }
}
