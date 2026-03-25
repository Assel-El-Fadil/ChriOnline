package Server.DAO;

import Shared.DTO.UserDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // ────────────────────────────────────────────────────────────
    //  Inner class — carries password hash for AuthHandler only
    // ────────────────────────────────────────────────────────────

    public static class AuthUser {
        public final int id;
        public final String username;
        public final String firstName;
        public final String lastName;
        public final String email;
        public final String address;
        public final String role;
        public final int active;
        public final String passwordHash;

        public AuthUser(int id, String username, String firstName, String lastName,
                        String email, String address, String role,
                        int active, String passwordHash) {
            this.id           = id;
            this.username     = username;
            this.firstName    = firstName;
            this.lastName     = lastName;
            this.email        = email;
            this.address      = address;
            this.role         = role;
            this.active       = active;
            this.passwordHash = passwordHash;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Write operations
    // ────────────────────────────────────────────────────────────

    public int createUser(String firstName, String lastName, String username,
                          String passwordHash, String email, String address) {

        final String sql = "INSERT INTO users (first_name, last_name, username, password_hash, email, address) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, username);
            ps.setString(4, passwordHash);
            ps.setString(5, email);

            // address is nullable — use setNull when not provided
            if (address == null || address.isBlank()) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, address);
            }

            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
            throw new DAOException("INSERT succeeded but no generated key was returned");

        } catch (SQLIntegrityConstraintViolationException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("uq_users_username") || msg.contains("username")) {
                throw new DuplicateUsernameException(
                        "Username '" + username + "' is already taken");
            }
            if (msg.contains("uq_users_email") || msg.contains("email")) {
                throw new DuplicateEmailException(
                        "Email '" + email + "' is already registered");
            }
            throw new DAOException("Duplicate entry: " + e.getMessage(), e);

        } catch (SQLException e) {
            throw new DAOException("createUser failed: " + e.getMessage(), e);

        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public boolean softDelete(int userId) {
        final String sql = "UPDATE users SET active = 0 WHERE id = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new DAOException("softDelete failed for userId=" + userId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public boolean hardDelete(int userId) {
        final String sql = "DELETE FROM users WHERE id = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new DAOException("hardDelete failed for userId=" + userId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Read operations
    // ────────────────────────────────────────────────────────────

    public AuthUser findByUsernameForAuth(String username) {
        System.out.println("reached UserDAO");
        final String sql =
                "SELECT id, first_name, last_name, username, email, address, "
                        + "       role, active, password_hash "
                        + "FROM users "
                        + "WHERE username = ? AND active = 1";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, username);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new AuthUser(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("address"),    // may be null — ResultSet returns null fine
                        rs.getString("role"),
                        rs.getInt("active"),
                        rs.getString("password_hash")
                );
            }
            System.out.println("finished UserDAO");
            return null;
        } catch (SQLException e) {
            throw new DAOException("findByUsernameForAuth failed: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public UserDTO findById(int userId) {
        final String sql =
                "SELECT id, first_name, last_name, username, email, address, role, active "
                        + "FROM users "
                        + "WHERE id = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToDTO(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DAOException("findById failed for userId=" + userId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public List<UserDTO> findAll() {
        final String sql =
                "SELECT id, first_name, last_name, username, email, address, role, active "
                        + "FROM users "
                        + "ORDER BY created_at DESC";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            List<UserDTO> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapToDTO(rs));
            }
            return users;

        } catch (SQLException e) {
            throw new DAOException("findAll failed: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public boolean hasOrders(int userId) {
        final String sql = "SELECT COUNT(*) FROM orders WHERE user_id = ?";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;

        } catch (SQLException e) {
            throw new DAOException("hasOrders failed for userId=" + userId
                    + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────

    private UserDTO mapToDTO(ResultSet rs) throws SQLException {
        return new UserDTO(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("address"),   // null if not set — UserDTO.address is nullable
                rs.getString("role"),
                rs.getInt("active")
        );
    }

    // ────────────────────────────────────────────────────────────
    //  DAO-layer exceptions
    // ────────────────────────────────────────────────────────────

    public static class DAOException extends RuntimeException {
        public DAOException(String message) { super(message); }
        public DAOException(String message, Throwable cause) { super(message, cause); }
    }

    public static class DuplicateUsernameException extends DAOException {
        public DuplicateUsernameException(String message) { super(message); }
    }

    public static class DuplicateEmailException extends DAOException {
        public DuplicateEmailException(String message) { super(message); }
    }
}