package Server.service;

import Server.DAO.ProductDAO;
import Shared.DTO.ProductDTO;

import java.sql.SQLException;
import java.util.List;

public class ProductService {

    private final ProductDAO productDAO;

    public ProductService(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    public List<ProductDTO> getAll() throws SQLException {
        return productDAO.findAll();
    }

    public List<ProductDTO> getByCategory(String category) throws SQLException {
        return productDAO.findByCategory(category);
    }

    public ProductDTO getById(int id) throws SQLException {
        return productDAO.findById(id);
    }

    public String getCategories() {
        return "ELECTRONIQUES;VETEMENTS;ELECTROMENAGER;BEAUTE_ET_COSMETIQUES;JEUX_VIDEO;SANTE;FITNESS";
    }

    public int create(ProductDTO p) throws SQLException {
        validateProduct(p);
        return productDAO.create(p);
    }

    public int create() throws SQLException {
        throw new UnsupportedOperationException("Use create(ProductDTO) method instead");
    }

    public boolean update(int id, String field, Object value) throws SQLException {
        validateField(field, value);
        return productDAO.update(id, field, value);
    }

    public boolean delete(int id) throws SQLException {
        return productDAO.delete(id);
    }

    // ────────────────────────────────────────────────────────────
    //  Validation methods
    // ────────────────────────────────────────────────────────────

    private void validateProduct(ProductDTO product) {
        if (product == null) {
            throw new ValidationException("Product cannot be null");
        }
        if (product.name == null || product.name.trim().isEmpty()) {
            throw new ValidationException("Product name cannot be empty");
        }
        if (product.category == null || product.category.trim().isEmpty()) {
            throw new ValidationException("Product category cannot be empty");
        }
        validateCategory(product.category);
        if (product.price < 0) {
            throw new ValidationException("Product price cannot be negative");
        }
        if (product.stock < 0) {
            throw new ValidationException("Product stock cannot be negative");
        }
    }

    private void validateField(String field, Object value) {
        if (field == null || field.trim().isEmpty()) {
            throw new InvalidFieldException("Field name cannot be empty");
        }

        switch (field.toLowerCase()) {
            case "name":
                if (!(value instanceof String)) {
                    throw new InvalidFieldException("Name must be a string");
                }
                String name = ((String) value).trim();
                if (name.isEmpty()) {
                    throw new ValidationException("Product name cannot be empty");
                }
                break;
            case "category":
                if (!(value instanceof String)) {
                    throw new InvalidFieldException("Category must be a string");
                }
                validateCategory((String) value);
                break;
            case "price":
                if (!(value instanceof Number)) {
                    throw new InvalidFieldException("Price must be a number");
                }
                double price = ((Number) value).doubleValue();
                if (price < 0) {
                    throw new ValidationException("Price cannot be negative");
                }
                break;
            case "stock":
                if (!(value instanceof Number)) {
                    throw new InvalidFieldException("Stock must be a number");
                }
                int stock = ((Number) value).intValue();
                if (stock < 0) {
                    throw new ValidationException("Stock cannot be negative");
                }
                break;
            case "description":
                if (!(value instanceof String)) {
                    throw new InvalidFieldException("Description must be a string");
                }
                break;
            case "imagepath":
                if (value != null && !(value instanceof String)) {
                    throw new InvalidFieldException("Image path must be a string");
                }
                break;
            default:
                throw new InvalidFieldException("Invalid field: " + field);
        }
    }

    private void validateCategory(String category) {
        String[] validCategories = getCategories().split(";");
        boolean isValid = false;
        for (String validCategory : validCategories) {
            if (validCategory.equals(category)) {
                isValid = true;
                break;
            }
        }
        if (!isValid) {
            throw new InvalidCategoryException("Invalid category: " + category);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Service-specific exceptions
    // ────────────────────────────────────────────────────────────

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) { super(message); }
    }

    public static class InvalidFieldException extends RuntimeException {
        public InvalidFieldException(String message) { super(message); }
    }

    public static class InvalidCategoryException extends RuntimeException {
        public InvalidCategoryException(String message) { super(message); }
    }

    public static class ProductInActiveCartException extends RuntimeException {
        public ProductInActiveCartException(String message) { super(message); }
    }
}
