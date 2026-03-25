package Server.handlers;

import Server.SessionManager;
import Server.service.ProductService;
import Server.service.OrderService;
import Server.service.UserService;
import Shared.*;
import Shared.DTO.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Commands handled:
 *   ADMIN_ADD_PRODUCT      params: token|name|category|price|stock|description?
 *   ADMIN_EDIT_PRODUCT     params: token|productId|field|value
 *   ADMIN_DELETE_PRODUCT   params: token|productId
 *   ADMIN_LIST_ORDERS      params: token
 *   ADMIN_UPDATE_STATUS    params: token|orderId|newStatus
 *   ADMIN_LIST_USERS       params: token
 *   ADMIN_DELETE_USER      params: token|userId
 */
public class AdminHandler {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final SessionManager sessionManager;

    public AdminHandler(UserService userService,
                        ProductService productService,
                        OrderService orderService,
                        SessionManager sessionManager) {
        this.userService = userService;
        this.productService = productService;
        this.orderService = orderService;
        this.sessionManager = sessionManager;
    }

    // ────────────────────────────────────────────────────────────
    //  Role guard
    // ────────────────────────────────────────────────────────────

    private SessionData requireAdmin(String token) {
        if (token == null || token.isBlank()) return null;
        SessionData session = sessionManager.getSession(token);
        if (session == null) return null;
        if (!session.isAdmin()) return null;
        return session;
    }

    // ────────────────────────────────────────────────────────────
    //  Product management
    // ────────────────────────────────────────────────────────────

    /**
     * ADMIN_ADD_PRODUCT|token|name|category|price|stock|description
     *
     * description is optional — params[5] may be absent or blank.
     * price must be a valid positive decimal.
     * stock must be a valid non-negative integer.
     * category must match one of the ENUM values defined in the schema.
     *
     * Response: OK|newProductId
     */
    public String handleAddProduct(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 5) {
            return ResponseBuilder.error(
                    "ADMIN_ADD_PRODUCT requires: name|category|price|stock[|description]");
        }

        String name        = params[1].trim();
        String category    = params[2].trim();
        String priceStr    = params[3].trim();
        String stockStr    = params[4].trim();
        String description = params.length > 5 ? params[5].trim() : null;

        double price;
        int    stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Price must be a decimal number and stock must be an integer");
        }

        try {
            int newId = productService.createProduct(name, category, description, price, stock);
            return ResponseBuilder.ok(String.valueOf(newId));

        } catch (ProductService.ValidationException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (ProductService.InvalidCategoryException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    /**
     * ADMIN_EDIT_PRODUCT|token|productId|field|value
     *
     * field must be one of: name, category, price, stock, description
     * value is always sent as a String — ProductService validates
     * and converts to the correct type for that field.
     *
     * Response: OK|
     */
    public String handleEditProduct(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 4) {
            return ResponseBuilder.error(
                    "ADMIN_EDIT_PRODUCT requires: productId|field|value");
        }

        int    productId;
        String field = params[2].trim();
        String value = params[3].trim();

        try {
            productId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Product id must be an integer");
        }

        try {
            productService.updateProduct(productId, field, value);
            return ResponseBuilder.ok();

        } catch (ProductService.ProductNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (ProductService.ValidationException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (ProductService.InvalidFieldException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    /**
     * ADMIN_DELETE_PRODUCT|token|productId
     *
     * Soft-deletes the product (sets active = 0).
     * Will fail if the product exists in any active cart_items row
     * because of the ON DELETE RESTRICT FK — ProductService checks
     * this first and throws a clean exception.
     *
     * Response: OK|
     */
    public String handleDeleteProduct(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 2) {
            return ResponseBuilder.error("ADMIN_DELETE_PRODUCT requires: productId");
        }

        int productId;
        try {
            productId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Product id must be an integer");
        }

        try {
            productService.deleteProduct(productId);
            return ResponseBuilder.ok();

        } catch (ProductService.ProductNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (ProductService.ProductInActiveCartException e) {
            return ResponseBuilder.error(
                    "Cannot delete — this product is in one or more active carts");
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Order management
    // ────────────────────────────────────────────────────────────

    /**
     * ADMIN_LIST_ORDERS|token
     *
     * Returns all orders across all users, most recent first.
     * Each OrderDTO is serialized with toProtocolString() and the
     * list is joined with semicolons.
     *
     * Response: OK|order1_str;order2_str;...
     *           OK|  (empty string if no orders exist yet)
     */
    public String handleListOrders(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }

        List<OrderDTO> orders = orderService.getAllOrders();

        if (orders.isEmpty()) {
            return ResponseBuilder.ok();
        }

        String serialized = orders.stream()
                .map(OrderDTO::toProtocolString)
                .collect(Collectors.joining(";"));

        return ResponseBuilder.ok(serialized);
    }

    /**
     * ADMIN_UPDATE_STATUS|token|orderId|newStatus
     *
     * newStatus must be one of: PENDING, VALIDATED, SHIPPED, DELIVERED, CANCELLED
     * OrderService enforces forward-only transitions
     * (e.g. DELIVERED cannot go back to SHIPPED).
     *
     * Response: OK|
     */
    public String handleUpdateStatus(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 3) {
            return ResponseBuilder.error(
                    "ADMIN_UPDATE_STATUS requires: orderId|newStatus");
        }

        int    orderId;
        String newStatus = params[2].trim().toUpperCase();

        try {
            orderId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Order id must be an integer");
        }

        try {
            orderService.updateOrderStatus(orderId, newStatus);
            return ResponseBuilder.ok();

        } catch (OrderService.OrderNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (OrderService.IllegalStatusTransitionException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseBuilder.error("Invalid status value: " + newStatus);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  User management
    // ────────────────────────────────────────────────────────────

    /**
     * ADMIN_LIST_USERS|token
     *
     * Returns all users, most recently created first.
     * No password hashes — UserDTO never carries them.
     *
     * Response: OK|user1_str;user2_str;...
     *           OK|  (empty if no users — impossible in practice since admin exists)
     */
    public String handleListUsers(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }

        List<UserDTO> users = userService.findAll();

        if (users.isEmpty()) {
            return ResponseBuilder.ok();
        }

        String serialized = users.stream()
                .map(UserDTO::toProtocolString)
                .collect(Collectors.joining(";"));

        return ResponseBuilder.ok(serialized);
    }

    /**
     * ADMIN_DELETE_USER|token|userId
     *
     * Calls UserService.delete() which automatically chooses
     * soft-delete (has orders) or hard-delete (no orders).
     * An admin cannot delete themselves — this guard prevents
     * accidentally locking out all admin access.
     *
     * Response: OK|
     */
    public String handleDeleteUser(String[] params) {
        SessionData admin = requireAdmin(params[0]);
        if (admin == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 2) {
            return ResponseBuilder.error("ADMIN_DELETE_USER requires: userId");
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("User id must be an integer");
        }

        // Prevent self-deletion — an admin cannot delete their own account
        if (targetUserId == admin.getUserId()) {
            return ResponseBuilder.error("You cannot delete your own admin account");
        }

        try {
            userService.delete(targetUserId);
            return ResponseBuilder.ok();

        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }
}