package Server.handlers;

import Server.SessionManager;
import Server.service.ProductService;
import Server.service.OrderService;
import Server.service.UserService;
import Shared.*;
import Shared.DTO.*;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

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

    public String handleAddProduct(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 5) {
            return ResponseBuilder.error(
                    "ADMIN_ADD_PRODUCT requires: name|category|price|stock[|description]");
        }

        String name = params[1].trim();
        String category = params[2].trim();
        String priceStr = params[3].trim();
        String stockStr = params[4].trim();
        String description = params.length > 5 ? params[5].trim() : null;
        String photoPath = params.length > 6 && !params[6].isBlank() ? params[6].trim() : null;

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Price must be a decimal number and stock must be an integer");
        }

        try {
            ProductDTO productDTO = new ProductDTO(0, name, description, price, stock, category, 1, photoPath);
            int newId = productService.create(productDTO);
            return ResponseBuilder.ok(String.valueOf(newId));

        } catch (ProductService.ValidationException | ProductService.InvalidCategoryException | SQLException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    public String handleEditProduct(String[] params) {
        if (requireAdmin(params[0]) == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 4) {
            return ResponseBuilder.error(
                    "ADMIN_EDIT_PRODUCT requires: productId|field|value");
        }

        int productId;
        try {
            productId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Product id must be an integer");
        }

        String field = params[2].trim();
        String value = params[3].trim();

        if(field.toLowerCase().equals("price")) {
            Double price = Double.parseDouble(value);
            try {
                productService.update(productId, field, price);
                return ResponseBuilder.ok();

            } catch (ProductService.ProductNotFoundException | ProductService.ValidationException
                    | ProductService.InvalidFieldException | java.sql.SQLException e) {
                return ResponseBuilder.error(e.getMessage());
            }
        } else if(field.toLowerCase().equals("stock")) {
            Integer stock = Integer.parseInt(value);
            try {
                productService.update(productId, field, stock);
                return ResponseBuilder.ok();

            } catch (ProductService.ProductNotFoundException | ProductService.ValidationException
                     | ProductService.InvalidFieldException | java.sql.SQLException e) {
                return ResponseBuilder.error(e.getMessage());
            }
        }

        try {
            productService.update(productId, field, value);
            return ResponseBuilder.ok();

        } catch (ProductService.ProductNotFoundException | ProductService.ValidationException
                 | ProductService.InvalidFieldException | java.sql.SQLException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

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
            productService.delete(productId);
            return ResponseBuilder.ok();

        } catch (ProductService.ProductNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (ProductService.ProductInActiveCartException e) {
            return ResponseBuilder.error(
                    "Cannot delete — this product is in one or more active carts");
        } catch (SQLException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }


    // ────────────────────────────────────────────────────────────
    //  Order management
    // ────────────────────────────────────────────────────────────

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

    public String handleHardDeleteUser(String[] params) {
        SessionData admin = requireAdmin(params[0]);
        if (admin == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 2) {
            return ResponseBuilder.error("ADMIN_HARD_DELETE_USER requires: userId");
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("User id must be an integer");
        }

        if (targetUserId == admin.getUserId()) {
            return ResponseBuilder.error("You cannot delete your own admin account");
        }

        try {
            userService.hardDelete(targetUserId);
            return ResponseBuilder.ok();
        } catch (UserService.UserNotFoundException | IllegalStateException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    public String handleDeactivateUser(String[] params) {
        SessionData admin = requireAdmin(params[0]);
        if (admin == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 2) {
            return ResponseBuilder.error("ADMIN_DEACTIVATE_USER requires: userId");
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("User id must be an integer");
        }

        if (targetUserId == admin.getUserId()) {
            return ResponseBuilder.error("You cannot deactivate your own admin account");
        }

        try {
            userService.deactivate(targetUserId);
            return ResponseBuilder.ok();
        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    public String handleActivateUser(String[] params) {
        SessionData admin = requireAdmin(params[0]);
        if (admin == null) {
            return ResponseBuilder.error("Unauthorized");
        }
        if (params.length < 2) {
            return ResponseBuilder.error("ADMIN_ACTIVATE_USER requires: userId");
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("User id must be an integer");
        }

        if (targetUserId == admin.getUserId()) {
            return ResponseBuilder.error("You cannot activate yourself");
        }

        try {
            userService.activate(targetUserId);
            return ResponseBuilder.ok();
        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }
}