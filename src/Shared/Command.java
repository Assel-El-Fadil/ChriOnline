package Shared;

import Shared.RequestParser.InvalidRequestException;

public enum Command {
    REGISTER,       // REGISTER|firstName|lastName|username|password|email
    LOGIN,          // LOGIN|username|password|udpPort
    LOGOUT,         //LOGOUT|token
    GET_PRODUCTS,
    GET_PRODUCT,
    GET_CATEGORIES,
    CART_ADD,       // CART_ADD|token|productId|qty
    CART_REMOVE,    // CART-REMOVE|token|productId
    CART_VIEW,      // CART_VIEW|token
    CART_CLEAR,     // CART_CLEAR|token
    CHECKOUT,       // CHECKOUT|token|CARD|cardNum|holder|expiry|cvv
    ORDER_HISTORY,      // ORDER_HISTORY|token
    ADMIN_ADD_PRODUCT,        // ADMIN_ADD_PRODUCT|token|name|category|price|stock|description|photoPath?
    ADMIN_EDIT_PRODUCT,      // ADMIN_EDIT_PRODUCT|token|productId|field|value
    ADMIN_DELETE_PRODUCT,       // ADMIN_DELETE_PRODUCT|token|productId
    ADMIN_LIST_ORDERS,      //  ADMIN_LIST_ORDERS|token
    ADMIN_UPDATE_STATUS,    // ADMIN_UPDATE_STATUS|token|orderId|newStatus
    ADMIN_LIST_USERS,       // ADMIN_LIST_USERS|token
    ADMIN_DELETE_USER,      // ADMIN_DELETE_USER|token|userId
    ADMIN_DEACTIVATE_USER,      // ADMIN_DEACTIVATE_USER|token|userId
    ADMIN_ACTIVATE_USER,        // ADMIN_ACTIVATE_USER|token|userId
    ADMIN_HARD_DELETE_USER,     // ADMIN_HARD_DELETE_USER|token|userId
    GET_PROFILE,        // GET_PROFILE|token
    EDIT_PROFILE,       // EDIT_PROFILE|token|field|value
    GET_ORDER_STATUS;   // GET_ORDER_STATUS|token|orderId

    public static Command fromString(String commandToken) throws InvalidRequestException {
        if (commandToken == null) {
            throw new InvalidRequestException("Invalid Command");
        }

        try {
            return Command.valueOf(commandToken.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid Command");
        }
    }
}
