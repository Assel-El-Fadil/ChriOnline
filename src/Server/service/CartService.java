package Server.service;

import Server.DAO.CartDAO;
import Server.DAO.ProductDAO;
import Shared.DTO.CartItemDTO;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CartService {

    private final ConcurrentHashMap<String, Cart> carts = new ConcurrentHashMap<>();
    private final CartDAO cartDAO;
    private final ProductDAO productDAO;

    public CartService(CartDAO cartDAO, ProductDAO productDAO) {
        this.cartDAO = cartDAO;
        this.productDAO = productDAO;
    }

    public void loadFromDB(String token, int userId) throws SQLException {
        // Ensure a carts row exists
        cartDAO.getOrCreateCartId(userId);

        // Load items from DB
        List<CartItemDTO> dbItems = cartDAO.loadItems(userId);

        // Populate memory cart
        Cart cart = new Cart();
        for (CartItemDTO dto : dbItems) {
            cart.addItem(dto.productId, dto.quantity);
        }

        // Store in memory map
        carts.put(token, cart);
    }

    public Cart getOrCreateCart(String token) {
        return carts.computeIfAbsent(token, k -> new Cart());
    }

    public void addItem(String token, int userId, int productId, int qty) throws SQLException {
        // [Fix] Update DB first, then memory to ensure consistency
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.upsert(cartId, productId, qty); // Persist

        Cart cart = getOrCreateCart(token);
        cart.addItem(productId, qty); // Update memory
    }

    public void removeItem(String token, int userId, int productId) throws SQLException {
        // [Fix] Update DB first, then memory to ensure consistency
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.removeItem(cartId, productId); // Persist

        Cart cart = getOrCreateCart(token);
        cart.removeItem(productId); // Update memory
    }

    public void clearCart(String token, int userId) throws SQLException {
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.clearItems(cartId); // Wipe DB rows
        carts.remove(token); // Wipe memory
    }

    public boolean hasCart(String token) {
        return carts.containsKey(token);
    }
}
