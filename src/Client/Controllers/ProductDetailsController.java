package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Client.util.ProductImageHelper;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;

public class ProductDetailsController {

    @FXML private Label   lblName;
    @FXML private Label   lblCategory;
    @FXML private Label   lblDescription;
    @FXML private Label   lblPrice;
    @FXML private Label   cartStatusLabel;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button  addToCartButton;
    @FXML private Button  btnBackCatalog;
    @FXML private ImageView imgProduct;

    private ProductDTO  currentProduct;
    private SocketClient socketClient;
    private Stage       primaryStage;

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    public void initialize() {
        if (cartStatusLabel != null) {
            cartStatusLabel.setVisible(false);
            cartStatusLabel.setText("");
        }
    }

    /**
     * Called by {@link CatalogController} before the scene is shown.
     */
    public void initData(ProductDTO product) {
        this.currentProduct = product;

        lblName.setText(product.name);
        lblCategory.setText(product.category != null ? product.category : "—");
        lblDescription.setText(product.description != null && !product.description.isBlank()
                ? product.description : "—");
        lblPrice.setText(String.format("%.2f MAD", product.price));

        if (product.stock == 0) {
            addToCartButton.setDisable(true);
            quantitySpinner.setDisable(true);
        } else {
            quantitySpinner.setDisable(false);
            quantitySpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, product.stock, 1));
            addToCartButton.setDisable(false);
        }

        imgProduct.setImage(ProductImageHelper.loadLocalImage(product.imagePath));

        hideCartStatus();
    }

    @FXML
    private void handleBackToCatalog() {
        if (socketClient == null || primaryStage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/catalog.fxml"));
            Parent root = loader.load();
            CatalogController catalogController = loader.getController();
            catalogController.setSocketClient(socketClient);
            catalogController.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showCartStatus("Could not return to catalog.", true);
        }
    }

    @FXML
    private void handleAddToCart() {
        if (currentProduct == null || socketClient == null) return;
        if (currentProduct.stock == 0) return;

        int qty = quantitySpinner.getValue();
        if (qty < 1 || qty > currentProduct.stock) {
            showCartStatus("Invalid quantity.", true);
            return;
        }

        addToCartButton.setDisable(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }
                String token = AppState.getToken();
                return socketClient.sendCommand(
                        "CART_ADD|" + token + "|" + currentProduct.getId() + "|" + qty);
            }
        };

        task.setOnSucceeded(e -> {
            addToCartButton.setDisable(false);
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String newTotal = ResponseBuilder.extractPayload(response);
                showCartStatus("Added! Cart total: " + newTotal + " MAD", false);
            } else {
                showCartStatus(ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> {
            addToCartButton.setDisable(false);
            showCartStatus("Add to cart failed. Check your connection.", true);
        });

        new Thread(task).start();
    }

    private void hideCartStatus() {
        Platform.runLater(() -> {
            if (cartStatusLabel != null) {
                cartStatusLabel.setVisible(false);
                cartStatusLabel.setText("");
            }
        });
    }

    private void showCartStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            if (cartStatusLabel != null) {
                cartStatusLabel.setText(message);
                cartStatusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
                cartStatusLabel.setVisible(true);
            }
        });
    }
}
