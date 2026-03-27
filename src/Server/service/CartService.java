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
        cartDAO.getOrCreateCartId(userId);

        List<CartItemDTO> dbItems = cartDAO.loadItems(userId);

        Cart cart = new Cart();
        for (CartItemDTO dto : dbItems) {
            cart.addItem(dto.productId, dto.quantity);
        }

        carts.put(token, cart);
    }

    public Cart getOrCreateCart(String token) {
        return carts.computeIfAbsent(token, k -> new Cart());
    }

    public void addItem(String token, int userId, int productId, int qty) throws SQLException {
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.upsert(cartId, productId, qty);

        Cart cart = getOrCreateCart(token);
        cart.addItem(productId, qty);
    }

    public void removeItem(String token, int userId, int productId) throws SQLException {
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.removeItem(cartId, productId);

        Cart cart = getOrCreateCart(token);
        cart.removeItem(productId);
    }

    public void clearCart(String token, int userId) throws SQLException {
        int cartId = cartDAO.getOrCreateCartId(userId);
        cartDAO.clearItems(cartId);
        carts.remove(token);
    }

    public boolean hasCart(String token) {
        return carts.containsKey(token);
    }
}
