package Server.handlers;

import Server.SessionManager;
import Server.service.Cart;
import Server.service.CartService;
import Server.service.ProductService;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;
import Shared.SessionData;

import java.util.stream.Collectors;

public class CartHandler {

    private final CartService cartService;
    private final ProductService productService;
    private final SessionManager sessionManager;

    public CartHandler(CartService cartService,
                        ProductService productService,
                        SessionManager sessionManager) {
        this.cartService = cartService;
        this.productService = productService;
        this.sessionManager = sessionManager;
    }

    private SessionData requireSession(String token) {
        if (token == null || token.isBlank()) return null;
        return sessionManager.getSession(token);
    }

    // params: token|productId|qty
    public String handleAdd(String[] params) {
        try {
            if (params == null || params.length < 3) {
                return ResponseBuilder.error("CART_ADD requires: token|productId|qty");
            }

            String token = params[0];
            SessionData session = requireSession(token);
            if (session == null) {
                return ResponseBuilder.error("Not logged in");
            }
            int userId = session.getUserId();

            int productId;
            try {
                productId = Integer.parseInt(params[1].trim());
            } catch (NumberFormatException e) {
                return ResponseBuilder.error("Invalid product ID");
            }

            int qty;
            try {
                qty = Integer.parseInt(params[2].trim());
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
            // Re-fetch the cart after addItem for total calculation
            double total = cartService.getOrCreateCart(token)
                    .calculateTotal(productService);
            return ResponseBuilder.ok(String.valueOf(total));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseBuilder.error(e.getMessage());
        }
    }

    // params: token|productId
    public String handleRemove(String[] params) {
        try {
            if (params == null || params.length < 2) {
                return ResponseBuilder.error("CART_REMOVE requires: token|productId");
            }

            String token = params[0];
            SessionData session = requireSession(token);
            if (session == null) {
                return ResponseBuilder.error("Not logged in");
            }
            int userId = session.getUserId();

            int productId;
            try {
                productId = Integer.parseInt(params[1].trim());
            } catch (NumberFormatException e) {
                return ResponseBuilder.error("Invalid product ID");
            }

            cartService.removeItem(token, userId, productId);
            Cart cart = cartService.getOrCreateCart(token);
            double newTotal = cart.calculateTotal(productService);
            return ResponseBuilder.ok(String.valueOf(newTotal));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseBuilder.error(e.getMessage());
        }
    }

    // params: token
    public String handleView(String[] params) {
        try {
            if (params == null || params.length < 1) {
                return ResponseBuilder.error("CART_VIEW requires: token");
            }

            String token = params[0];
            SessionData session = requireSession(token);
            if (session == null) {
                return ResponseBuilder.error("Not logged in");
            }

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
                        // Protocol string for the view must omit `id` and `cartId`
                        // (so the client doesn't receive serialized `0` values).
                        return "productId=" + productId
                                + ",qty=" + qty
                                + ",name=" + product.name
                                + ",unitPrice=" + product.price
                                + ",subtotal=" + (product.price * qty);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return "";
            }).filter(s -> !s.isEmpty()).collect(Collectors.joining(";"));

            return ResponseBuilder.ok(payload);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseBuilder.error(e.getMessage());
        }
    }

    // params: token
    public String handleClear(String[] params) {
        try {
            if (params == null || params.length < 1) {
                return ResponseBuilder.error("CART_CLEAR requires: token");
            }

            String token = params[0];
            SessionData session = requireSession(token);
            if (session == null) {
                return ResponseBuilder.error("Not logged in");
            }
            int userId = session.getUserId();

            cartService.clearCart(token, userId);
            return ResponseBuilder.ok("");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseBuilder.error(e.getMessage());
        }
    }
}
