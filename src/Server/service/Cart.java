package Server.service;

import Server.DAO.ProductDAO;
import Shared.DTO.ProductDTO;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Cart {

    // Maps productId -> quantity
    private final HashMap<Integer, Integer> items = new HashMap<>();

    public void addItem(int productId, int qty) {
        if (items.containsKey(productId)) {
            items.put(productId, items.get(productId) + qty);
        } else {
            items.put(productId, qty);
        }
    }

    public void removeItem(int productId) {
        items.remove(productId);
    }

    public Map<Integer, Integer> getItems() {
        return items;
    }

    public int getQuantityFor(int productId) {
        return items.getOrDefault(productId, 0);
    }

    public double calculateTotal(ProductDAO dao) {
        double total = 0.0;
        try {
            for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                ProductDTO product = dao.findById(entry.getKey());
                if (product != null) {
                    total += (product.price * entry.getValue());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0.0;
        }
        return total;
    }

    public void clear() {
        items.clear();
    }
}
