package Shared.DTO;

import java.util.Date;

public class OrderDTO {

    public int    id;
    public String referenceCode;
    public int    userId;
    public String status;
    public double totalAmount;
    public String paymentMethod;
    public Date createdAt;

    public OrderDTO() {}

    public OrderDTO(int id, String referenceCode, int userId, String status,
                    double totalAmount, String paymentMethod, Date createdAt) {
        this.id            = id;
        this.referenceCode = referenceCode;
        this.userId        = userId;
        this.status        = status;
        this.totalAmount   = totalAmount;
        this.paymentMethod = paymentMethod;
        this.createdAt     = createdAt;
    }

    public String toProtocolString() {
        return "id="      + id
                + ",ref="    + referenceCode
                + ",uid="    + userId
                + ",status=" + status
                + ",total="  + String.format("%.2f", totalAmount)
                + ",method=" + paymentMethod
                + ",created="+ (createdAt == null ? "" : createdAt);
    }

    public static OrderDTO fromProtocolString(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank OrderDTO string");
        }

        OrderDTO dto = new OrderDTO();
        String[] pairs = s.split(",");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();

            switch (key) {
                case "id":      dto.id            = Integer.parseInt(val);   break;
                case "ref":     dto.referenceCode = val;                     break;
                case "uid":     dto.userId        = Integer.parseInt(val);   break;
                case "status":  dto.status        = val;                     break;
                case "total":   dto.totalAmount   = Double.parseDouble(val); break;
                case "method":  dto.paymentMethod = val;                     break;
                //case "created": dto.createdAt     = val;                     break;
            }
        }
        return dto;
    }

    @Override
    public String toString() {
        return "OrderDTO{id=" + id + ", ref='" + referenceCode + "', status='" + status
                + "', total=" + totalAmount + "}";
    }
}