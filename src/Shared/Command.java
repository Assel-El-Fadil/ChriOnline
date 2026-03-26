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
    ADMIN_ADD_PRODUCT,
    ADMIN_EDIT_PRODUCT,
    ADMIN_DELETE_PRODUCT,
    ADMIN_LIST_ORDERS,
    ADMIN_UPDATE_STATUS,
    ADMIN_LIST_USERS,
    ADMIN_DELETE_USER,
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
