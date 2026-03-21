package Server.DAO;

import Shared.DTO.CartItemDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CartDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/chriOnline";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";   // change if needed

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /**
     * Creates a cart for the user if one doesn't already exist, then returns the cart id.
     * Safe to call on every login.
     */
    public int getOrCreateCartId(int userId) throws SQLException {
        String insertSql = "INSERT IGNORE INTO carts (user_id) VALUES (?)";
        String selectSql = "SELECT id FROM carts WHERE user_id = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }
        throw new SQLException("Failed to get or create cart for user " + userId);
    }

    /**
     * Adds a product to the cart. If the product already exists in the cart,
     * increments the quantity atomically instead of inserting a duplicate.
     */
    public void upsert(int cartId, int productId, int qty) throws SQLException {
        String sql = "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?,?,?) "
                   + "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    /**
     * Sets an exact quantity for a product in the cart.
     */
    public boolean setQuantity(int cartId, int productId, int qty) throws SQLException {
        String sql = "UPDATE cart_items SET quantity = ? WHERE cart_id = ? AND product_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, cartId);
            ps.setInt(3, productId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Removes a product from the cart (hard delete).
     */
    public boolean removeItem(int cartId, int productId) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE cart_id = ? AND product_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Loads all cart items for a user, joining with products to get name and price.
     * Only includes active products.
     */
    public List<CartItemDTO> loadItems(int userId) throws SQLException {
        String sql = "SELECT ci.product_id, p.name, ci.quantity, p.price "
                   + "FROM cart_items ci "
                   + "JOIN products p ON ci.product_id = p.id "
                   + "JOIN carts c ON ci.cart_id = c.id "
                   + "WHERE c.user_id = ? AND p.active = 1";

        List<CartItemDTO> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CartItemDTO item = new CartItemDTO();
                    item.productId   = rs.getInt("product_id");
                    item.productName = rs.getString("name");
                    item.quantity    = rs.getInt("quantity");
                    item.unitPrice   = rs.getDouble("price");
                    item.subtotal    = item.unitPrice * item.quantity;
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Removes all items from a cart. Used at checkout.
     */
    public void clearItems(int cartId) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE cart_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.executeUpdate();
        }
    }
}
