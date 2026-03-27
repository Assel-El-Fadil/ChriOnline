package Server.DAO;

import Shared.DTO.ProductDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {
    public List<ProductDTO> findAll() throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock, image_path "
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

    public List<ProductDTO> findByCategory(String category) throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock, image_path "
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

    public ProductDTO findById(int id) throws SQLException {
        String sql = "SELECT id, category, name, description, price, stock, image_path "
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

    public int create(ProductDTO p) throws SQLException {
        String sql = "INSERT INTO products (category, name, description, price, stock, image_path) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = ConnectionPool.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, p.category);
                ps.setString(2, p.name);
                ps.setString(3, p.description);
                ps.setDouble(4, p.price);
                ps.setInt(5, p.stock);
                if (p.imagePath == null || p.imagePath.isBlank()) {
                    ps.setNull(6, Types.VARCHAR);
                } else {
                    ps.setString(6, p.imagePath);
                }
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

    public boolean update(int id, String field, Object value) throws SQLException {
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

    private ProductDTO mapRow(ResultSet rs) throws SQLException {
        return new ProductDTO(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("price"),
                rs.getInt("stock"),
                rs.getString("category"),
                1,
                rs.getString("image_path")
        );
    }
}
