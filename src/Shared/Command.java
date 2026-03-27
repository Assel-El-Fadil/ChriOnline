package Shared;

import Shared.RequestParser.InvalidRequestException;

public enum Command {
    REGISTER,
    LOGIN,
    LOGOUT,
    GET_PRODUCTS,
    GET_PRODUCT,
    GET_CATEGORIES,
    CART_ADD,
    CART_REMOVE,
    CART_VIEW,
    CART_CLEAR,
    CHECKOUT,
    ORDER_HISTORY,
    ADMIN_ADD_PRODUCT,        // ADMIN_ADD_PRODUCT      params: token|name|category|price|stock|description?
    ADMIN_EDIT_PRODUCT,      // ADMIN_EDIT_PRODUCT     params: token|productId|field|value
    ADMIN_DELETE_PRODUCT,       // ADMIN_DELETE_PRODUCT   params: token|productId
    ADMIN_LIST_ORDERS,      //  ADMIN_LIST_ORDERS      params: token
    ADMIN_UPDATE_STATUS,    // ADMIN_UPDATE_STATUS    params: token|orderId|newStatus
    ADMIN_LIST_USERS,       // ADMIN_LIST_USERS       params: token
    ADMIN_DELETE_USER,      // ADMIN_DELETE_USER      params: token|userId
    ADMIN_DEACTIVATE_USER,      // ADMIN_DEACTIVATE_USER|token|userId
    ADMIN_ACTIVATE_USER,        // ADMIN_ACTIVATE_USER|token|userId
    ADMIN_HARD_DELETE_USER,     // ADMIN_HARD_DELETE_USER|token|userId
    GET_PROFILE,
    EDIT_PROFILE,
    GET_ORDER_STATUS;

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
