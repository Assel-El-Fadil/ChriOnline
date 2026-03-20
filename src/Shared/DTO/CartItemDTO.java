package Shared.DTO;

import java.util.Date;

public class CartItemDTO {

    private int id;
    private int cartId;
    public int productId;
    public int quantity;
    private Date addedAt;

    public CartItemDTO() {}

    public CartItemDTO(int id, int cartId, int productId, int quantity) {
        this.id = id;
        this.cartId = cartId;
        this.productId   = productId;
        this.quantity    = quantity;
    }

    public String toProtocolString() {
        return "id=" + id +
                ",cartId=" + cartId +
                ",productId=" + productId
                + ",qty="      + quantity;
    }

    public static CartItemDTO fromProtocolString(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank CartItemDTO string");
        }

        CartItemDTO dto = new CartItemDTO();
        String[] pairs = s.split(",");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();

            switch (key) {
                case "id": dto.id = Integer.parseInt(val); break;
                case "cartId": dto.cartId = Integer.parseInt(val); break;
                case "productId": dto.productId   = Integer.parseInt(val);   break;
                case "qty":       dto.quantity    = Integer.parseInt(val);   break;
            }
        }
        return dto;
    }

    @Override
    public String toString() {
        return "CartItemDTO{id=" + id + ", cartId=" + cartId + ", productId=" + productId
                + ", qty=" + quantity + "}";
    }
}