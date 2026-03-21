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
        return productDAO.create(p);
    }

    public boolean update(int id, String field, Object value) throws SQLException {
        return productDAO.update(id, field, value);
    }

    public boolean delete(int id) throws SQLException {
        return productDAO.delete(id);
    }
}
