package Server.DAO;

import Shared.DTO.UserDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        public final String profilePhoto;
        public final String role;
        public final int active;
        public final String passwordHash;

        public AuthUser(int id, String username, String firstName, String lastName,
                        String email, String address, String profilePhoto,
                        String role, int active, String passwordHash) {
            this.id           = id;
            this.username     = username;
            this.firstName    = firstName;
            this.lastName     = lastName;
            this.email        = email;
            this.address      = address;
            this.profilePhoto = profilePhoto;
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
    //  Update operations
    // ────────────────────────────────────────────────────────────

    /**
     * Whitelist of columns that may be updated via the generic updateProfile method.
     * This prevents SQL injection through the field name parameter.
     */
    private static final Set<String> UPDATABLE_COLUMNS = Set.of(
            "first_name", "last_name", "email", "address", "profile_photo"
    );

    /**
     * Updates a single column for a given user.
     * The column name is validated against a whitelist before being used in SQL.
     *
     * @return true if exactly one row was updated
     */
    public boolean updateProfile(int userId, String column, String value) {
        if (!UPDATABLE_COLUMNS.contains(column)) {
            throw new DAOException("Column '" + column + "' is not updatable");
        }

        // Column name is safe (from whitelist), so string concatenation is OK here
        final String sql = "UPDATE users SET " + column + " = ? WHERE id = ? AND active = 1";

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            PreparedStatement ps = conn.prepareStatement(sql);
            if (value == null || value.isBlank()) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, value);
            }
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;

        } catch (SQLIntegrityConstraintViolationException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("email")) {
                throw new DuplicateEmailException("Email already registered");
            }
            throw new DAOException("updateProfile constraint violation: " + e.getMessage(), e);

        } catch (SQLException e) {
            throw new DAOException("updateProfile failed for userId=" + userId
                    + ", column=" + column + ": " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Read operations
    // ────────────────────────────────────────────────────────────

    public AuthUser findByUsernameForAuth(String username) {
        final String sql =
                "SELECT id, first_name, last_name, username, email, address, "
                        + "       profile_photo, role, active, password_hash "
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
                        rs.getString("address"),
                        rs.getString("profile_photo"),
                        rs.getString("role"),
                        rs.getInt("active"),
                        rs.getString("password_hash")
                );
            }
            return null;
        } catch (SQLException e) {
            throw new DAOException("findByUsernameForAuth failed: " + e.getMessage(), e);
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    public UserDTO findById(int userId) {
        final String sql =
                "SELECT id, first_name, last_name, username, email, address, profile_photo, role, active "
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
                "SELECT id, first_name, last_name, username, email, address, profile_photo, role, active "
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
                rs.getString("address"),
                rs.getString("profile_photo"),
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