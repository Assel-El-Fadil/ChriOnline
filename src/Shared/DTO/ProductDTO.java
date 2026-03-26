package Shared.DTO;

public class ProductDTO {

    public int    id;
    public String name;
    public String description;
    public double price;
    public int    stock;
    public String category;   // one of the ENUM values, e.g. "ELECTRONIQUES"
    public int active;     // 1 = visible, 0 = soft-deleted
    /** Local file path for product image; may be null if not set. */
    public String imagePath;

    public ProductDTO() {}

    public ProductDTO(int id, String name, String description,
                      double price, int stock, String category, int active,
                      String imagePath) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.stock       = stock;
        this.category    = category;
        this.active      = active;
        this.imagePath   = imagePath;
    }

    public int getId() {
        return id;
    }

    public String toProtocolString() {
        String safeDesc = (description == null ? "" : description.replace(",", " "));
        String img = imagePath != null ? imagePath : "";
        return "id="       + id
                + ",name="    + name
                + ",cat="     + category
                + ",price="   + String.format("%.2f", price)
                + ",stock="   + stock
                + ",active="  + active
                + ",desc="    + safeDesc
                + "|" + img;
    }

    public static ProductDTO fromProtocolString(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank ProductDTO string");
        }

        int pipe = s.lastIndexOf('|');
        String mainPart = pipe >= 0 ? s.substring(0, pipe) : s;
        String imgPart = pipe >= 0 && pipe + 1 <= s.length()
                ? s.substring(pipe + 1)
                : "";

        ProductDTO dto = new ProductDTO();
        dto.imagePath = imgPart;

        String[] pairs = mainPart.split(",");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();

            switch (key) {
                case "id":     dto.id          = Integer.parseInt(val);    break;
                case "name":   dto.name        = val;                      break;
                case "cat":    dto.category    = val;                      break;
                case "price":  dto.price       = Double.parseDouble(val);  break;
                case "stock":  dto.stock       = Integer.parseInt(val);    break;
                case "active": dto.active      = Integer.parseInt(val);    break;
                case "desc":   dto.description = val;                      break;
            }
        }
        return dto;
    }

    @Override
    public String toString() {
        return "ProductDTO{id=" + id + ", name='" + name + "', category='" + category
                + "', price=" + price + ", stock=" + stock + "}";
    }
}