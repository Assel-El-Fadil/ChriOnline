package Shared.DTO;

public class OrderItemDTO {
    public int id;
    public int orderId;
    public int productId;
    public String productName;
    public int quantity;
    public double unitPrice;
    public double subtotal;

    public OrderItemDTO() {}

    public OrderItemDTO(int id, int orderId, int productId, String productName, int quantity, double unitPrice, double subtotal) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public String toProtocolString() {
        return "id=" + id +
                ",oid=" + orderId +
                ",pid=" + productId +
                ",name=" + productName +
                ",qty=" + quantity +
                ",price=" + unitPrice +
                ",sub=" + subtotal;
    }

    public static OrderItemDTO fromProtocolString(String s) {
        OrderItemDTO dto = new OrderItemDTO();
        String[] pairs = s.split(",");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();

            switch (key) {
                case "id": dto.id = Integer.parseInt(val); break;
                case "oid": dto.orderId = Integer.parseInt(val); break;
                case "pid": dto.productId = Integer.parseInt(val); break;
                case "name": dto.productName = val; break;
                case "qty": dto.quantity = Integer.parseInt(val); break;
                case "price": dto.unitPrice = Double.parseDouble(val); break;
                case "sub": dto.subtotal = Double.parseDouble(val); break;
            }
        }
        return dto;
    }
}
