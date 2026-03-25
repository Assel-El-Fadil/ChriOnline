package Server.handlers;

import Server.service.Cart;
import Server.service.CartService;
import Server.service.ProductService;
import Shared.Command;
import Shared.DTO.CartItemDTO;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;

import java.util.stream.Collectors;

// Temporary interface until SessionManager is provided by M3's AuthHandler
interface SessionManager {
    Session getSession(String token);

    interface Session {
        int getUserId();
    }
}

public class CartHandler {

    private final CartService cartService;
    private final ProductService productService;

    public CartHandler(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    public String handle(Command cmd, String[] params, String token, SessionManager sessions) {
        try {
            // All cases: check session
            SessionManager.Session session = sessions.getSession(token);
            if (session == null) {
                return ResponseBuilder.error("Not logged in");
            }
            int userId = session.getUserId();

            switch (cmd) {
                case CART_ADD:
                    return handleCartAdd(params, token, userId);
                case CART_REMOVE:
                    return handleCartRemove(params, token, userId);
                case CART_VIEW:
                    return handleCartView(token);
                case CART_CLEAR:
                    return handleCartClear(token, userId);
                default:
                    return ResponseBuilder.error("Unknown cart command");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseBuilder.error(e.getMessage());
        }
    }

    private String handleCartAdd(String[] params, String token, int userId) throws Exception {
        int productId;
        try {
            productId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid product ID");
        }

        int qty;
        try {
            qty = Integer.parseInt(params[2]);
            if (qty <= 0) {
                return ResponseBuilder.error("Invalid quantity");
            }
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid quantity");
        }

        // Stock check
        ProductDTO product;
        try {
            product = productService.getById(productId);
        } catch (Exception e) {
            return ResponseBuilder.error("Product lookup failed");
        }
        
        if (product == null) {
            return ResponseBuilder.error("Product not found");
        }

        Cart cart = cartService.getOrCreateCart(token);
        int currentQty = cart.getQuantityFor(productId);

        if (product.stock < qty + currentQty) {
            return ResponseBuilder.error("Not enough stock");
        }

        // Proceed
        cartService.addItem(token, userId, productId, qty);
        double total = cart.calculateTotal(productService);

        return ResponseBuilder.ok(String.valueOf(total));
    }

    private String handleCartRemove(String[] params, String token, int userId) throws Exception {
        int productId;
        try {
            productId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid product ID");
        }

        cartService.removeItem(token, userId, productId);
        Cart cart = cartService.getOrCreateCart(token);
        double newTotal = cart.calculateTotal(productService);

        return ResponseBuilder.ok(String.valueOf(newTotal));
    }

    private String handleCartView(String token) throws Exception {
        Cart cart = cartService.getOrCreateCart(token);

        if (cart.getItems().isEmpty()) {
            return ResponseBuilder.ok("");
        }

        String payload = cart.getItems().entrySet().stream().map(entry -> {
            int productId = entry.getKey();
            int qty = entry.getValue();

            try {
                ProductDTO product = productService.getById(productId);
                if (product != null) {
                    CartItemDTO dto = new CartItemDTO(
                            0, // cartItemId not strictly needed for transport view
                            0, // cartId not strictly needed here
                            productId,
                            qty,
                            product.name,
                            product.price,
                            product.price * qty);
                    return dto.toProtocolString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        }).filter(s -> !s.isEmpty()).collect(Collectors.joining(";"));

        return ResponseBuilder.ok(payload);
    }

    private String handleCartClear(String token, int userId) throws Exception {
        cartService.clearCart(token, userId);
        return ResponseBuilder.ok("");
    }
}
