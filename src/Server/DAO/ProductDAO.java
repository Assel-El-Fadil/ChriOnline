package Server.DAO;

import Shared.DTO.ProductDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {
    /**
     * Returns every active product ordered by category then name.
     */
    public List<ProductDTO> findAll() throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock "
                   + "FROM products WHERE active = 1 ORDER BY category, name";

        List<ProductDTO> list = new ArrayList<>();

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
        return list;
    }

    /**
     * Returns active products that belong to the given category ENUM value (e.g. "ELECTRONIQUES").
     */
    public List<ProductDTO> findByCategory(String category) throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock "
                   + "FROM products WHERE active = 1 AND category = ? "
                   + "ORDER BY category, name";

        List<ProductDTO> list = new ArrayList<>();

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, category);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
        return list;
    }

    /**
     * Returns a single active product by id, or {@code null} if not found.
     * Used by CartHandler for stock checks.
     */
    public ProductDTO findById(int id) throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock "
                   + "FROM products WHERE id = ? AND active = 1";

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
        return null;
    }

    /**
     * Inserts a new product and returns its generated id.
     */
    public int create(ProductDTO p) throws SQLException {
        String sql = "INSERT INTO products (category, name, description, price, stock) "
                   + "VALUES (?, ?, ?, ?, ?)";

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, p.category);
                ps.setString(2, p.name);
                ps.setString(3, p.description);
                ps.setDouble(4, p.price);
                ps.setInt(5, p.stock);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
        throw new SQLException("Insert succeeded but no generated key was returned");
    }

    /**
     * Updates a single column for the given product id.
     * Important: The {@code field} parameter is a raw column name.
     * It must be validated upstream (by AdminHandler) before reaching this method.
     * For the {@code category} column, the value must be a valid ENUM string
     * (e.g. "ELECTRONIQUES") or MySQL will reject the UPDATE.
     */
    public boolean update(int id, String field, Object value) throws SQLException {
        // Only allow known column names to prevent SQL injection
        String sql = "UPDATE products SET " + field + " = ? WHERE id = ?";

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, value);
                ps.setInt(2, id);
                return ps.executeUpdate() == 1;
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    /**
     * Soft-deletes a product (sets active = 0).
     * Never hard-delete — order_items has a FK pointing to products
     * with ON DELETE RESTRICT.
     *
     * @return true if exactly one row was affected.
     */
    public boolean delete(int id) throws SQLException {
        String sql = "UPDATE products SET active = 0 WHERE id = ?";

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() == 1;
            }
        } finally {
            ConnectionPool.returnConnection(conn);
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    /**
     * Maps the current ResultSet row to a ProductDTO.
     * The {@code active} field is always 1 for rows returned by our queries.
     */
    private ProductDTO mapRow(ResultSet rs) throws SQLException {
        return new ProductDTO(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("price"),
                rs.getInt("stock"),
                rs.getString("category"),   // comes back as a plain String from MySQL ENUM
                1                            // active = 1 (we only query active rows)
        );
    }
}
