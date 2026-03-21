package Server.handlers;

import Server.service.ProductService;
import Shared.Command;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;

import java.util.List;
import java.util.stream.Collectors;

public class ProductHandler {

    private final ProductService productService;

    public ProductHandler(ProductService productService) {
        this.productService = productService;
    }

    public String handle(Command cmd, String[] params, String token) {
        try {
            switch (cmd) {
                case GET_PRODUCTS:
                    return handleGetProducts(params);
                case GET_PRODUCT:
                    return handleGetProduct(params);
                case GET_CATEGORIES:
                    return handleGetCategories();
                default:
                    return ResponseBuilder.error("Unknown product command");
            }
        } catch (Exception e) {
            return ResponseBuilder.error(e.getMessage());
        }
    }

    private String handleGetProducts(String[] params) throws Exception {
        List<ProductDTO> products;

        if (params == null || params.length == 0 || params[0] == null || params[0].isEmpty()) {
            products = productService.getAll();
        } else {
            products = productService.getByCategory(params[0]);
        }

        String payload = products.stream()
                .map(ProductDTO::toProtocolString)
                .collect(Collectors.joining(";"));

        return ResponseBuilder.ok(payload);
    }

    private String handleGetProduct(String[] params) throws Exception {
        int id;
        try {
            id = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid product ID");
        }

        ProductDTO product = productService.getById(id);
        if (product == null) {
            return ResponseBuilder.error("Not found");
        }

        return ResponseBuilder.ok(product.toProtocolString());
    }

    private String handleGetCategories() {
        return ResponseBuilder.ok(productService.getCategories());
    }
}
