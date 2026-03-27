package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Client.util.ProductImageHelper;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CatalogController {

    @FXML private ComboBox<String> categoryComboBox;
    @FXML private Button              showAllButton;
    @FXML private Button              refreshButton;
    @FXML private ScrollPane          productScroll;
    @FXML private FlowPane            productGrid;
    @FXML private Label               statusLabel;
    @FXML private Label               lblProductCount;

    private SocketClient socketClient;
    private Stage        primaryStage;

    @FXML
    public void initialize() {
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        productGrid.prefWrapLengthProperty().bind(
                productScroll.widthProperty().subtract(48));

        categoryComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if ("Show All".equals(newVal)) {
                    loadProducts(null);
                } else {
                    loadProducts(newVal);
                }
            }
        });
    }

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
        loadCategories();
        loadProducts(null);
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    private void loadCategories() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }
                return socketClient.sendCommand("GET_CATEGORIES|");
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                if (!payload.isEmpty()) {
                    List<String> categories = Arrays.stream(payload.split(";")).collect(Collectors.toList());
                    categories.add(0, "Show All");
                    categoryComboBox.setItems(FXCollections.observableArrayList(categories));
                }
            }
        });

        new Thread(task).start();
    }

    private void loadProducts(String filterCategory) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }
                String cmd = "GET_PRODUCTS|";
                if (filterCategory != null && !"Show All".equals(filterCategory)) {
                    cmd += filterCategory;
                }
                return socketClient.sendCommand(cmd);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                ObservableList<ProductDTO> products = FXCollections.observableArrayList();
                if (!payload.isEmpty()) {
                    String[] segments = payload.split(";");
                    for (String seg : segments) {
                        try {
                            products.add(ProductDTO.fromProtocolString(seg));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                lblProductCount.setText(products.size() + " products found");
                rebuildProductGrid(products);
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
        });

        new Thread(task).start();
    }

    private void rebuildProductGrid(ObservableList<ProductDTO> products) {
        productGrid.getChildren().clear();
        for (ProductDTO p : products) {
            productGrid.getChildren().add(createProductCard(p));
        }
    }

    private VBox createProductCard(ProductDTO p) {
        VBox card = new VBox(0);
        card.getStyleClass().add("catalog-product-card");
        card.setMaxWidth(260);
        card.setPrefWidth(260);
        card.setFillWidth(true);

        StackPane imageStack = new StackPane();
        imageStack.setPadding(new Insets(0));
        imageStack.setMinHeight(172);
        imageStack.setPrefHeight(172);
        imageStack.getStyleClass().add("catalog-card-image-stack");

        Region imageBg = new Region();
        imageBg.getStyleClass().add("catalog-card-image-bg");
        imageBg.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(260);
        imageView.setFitHeight(172);
        imageView.setPreserveRatio(false); // Fill the entire pane
        imageView.setSmooth(true);
        imageView.setImage(ProductImageHelper.loadLocalImage(p.imagePath));

        imageStack.getChildren().addAll(imageBg, imageView);
        StackPane.setAlignment(imageView, Pos.CENTER);

        Label subtitle = new Label(truncateDescription(p.description, 52));
        subtitle.getStyleClass().add("catalog-card-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(236);

        Label name = new Label(p.name);
        name.getStyleClass().add("catalog-card-name");
        name.setWrapText(true);
        name.setMaxWidth(236);

        Label price = new Label(String.format("%.2f MAD", p.price));
        price.getStyleClass().add("catalog-card-price");

        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("catalog-card-add-btn");
        addBtn.setMinSize(40, 40);
        addBtn.setPrefSize(40, 40);
        addBtn.setOnAction(ev -> promptAddToCart(p));

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox priceRow = new HBox(10, price, grow, addBtn);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        Button detailsBtn = new Button("View details");
        detailsBtn.getStyleClass().add("catalog-card-details-btn");
        detailsBtn.setMaxWidth(Double.MAX_VALUE);
        detailsBtn.setOnAction(ev -> openProductDetails(p));

        VBox body = new VBox(8, subtitle, name, priceRow, detailsBtn);
        body.setPadding(new Insets(12, 10, 14, 10));

        card.getChildren().addAll(imageStack, body);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(ev -> {
            System.out.println("[CatalogController] Card clicked for: " + p.name);
            openProductDetails(p);
        });

        // Prevent button clicks from double-triggering or conflicting if needed
        detailsBtn.setOnAction(ev -> {
            ev.consume();
            openProductDetails(p);
        });

        return card;
    }

    private static String truncateDescription(String desc, int maxChars) {
        if (desc == null || desc.isBlank()) {
            return " ";
        }
        String t = desc.trim().replaceAll("\\s+", " ");
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars - 1) + "…";
    }

    @FXML
    private void handleShowAll(ActionEvent event) {
        categoryComboBox.getSelectionModel().select("Show All");
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        String filter = categoryComboBox.getSelectionModel().getSelectedItem();
        loadProducts(filter);
    }

    @FXML
    private void handleOpenCart() {
        if (socketClient == null || primaryStage == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/cart.fxml"));
            Parent root = loader.load();
            CartController cartController = loader.getController();
            cartController.setSocketClient(socketClient);
            cartController.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline — Cart");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Could not open cart.", true);
        }
    }

    @FXML
    private void handleOpenProfile() {
        if (socketClient == null || primaryStage == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/profile.fxml"));
            Parent root = loader.load();
            ProfileController profileController = loader.getController();
            profileController.setSocketClient(socketClient);
            profileController.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline — My Profile");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Could not open profile.", true);
        }
    }

    @FXML
    private void handleOpenOrderHistory() {
        if (socketClient == null || primaryStage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/orderHistory.fxml"));
            Parent root = loader.load();
            OrderHistoryController controller = loader.getController();
            controller.setSocketClient(socketClient);
            controller.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline — Order History");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Could not open order history.", true);
        }
    }

    private void promptAddToCart(ProductDTO selected) {
        if (selected.stock == 0) {
            showStatus("Product out of stock", true);
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Add to Cart");
        dialog.setHeaderText("Enter quantity for " + selected.name);
        dialog.setContentText("Quantity:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(qtyStr -> {
            try {
                int qty = Integer.parseInt(qtyStr);
                if (qty <= 0) {
                    showStatus("Quantity must be a positive integer", true);
                    return;
                }

                Task<String> task = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        if (!socketClient.isConnected()) {
                            socketClient.reconnect();
                        }
                        String token = AppState.getToken();
                        return socketClient.sendCommand(
                                "CART_ADD|" + token + "|" + selected.id + "|" + qty);
                    }
                };

                task.setOnSucceeded(ev -> {
                    String response = task.getValue();
                    if (ResponseBuilder.isOk(response)) {
                        String newTotal = ResponseBuilder.extractPayload(response);
                        showStatus("Added! Cart total: " + newTotal + " MAD", false);
                        handleRefresh(null);
                    } else {
                        showStatus(ResponseBuilder.extractError(response), true);
                    }
                });

                new Thread(task).start();

            } catch (NumberFormatException ex) {
                showStatus("Invalid quantity input", true);
            }
        });
    }

    private void openProductDetails(ProductDTO selected) {
        System.out.println("[CatalogController] Attempting to open details for: " + selected.name);

        // Fallback: If injected primaryStage is null, try to get it from the grid
        Stage targetStage = primaryStage;
        if (targetStage == null && productGrid.getScene() != null) {
            targetStage = (Stage) productGrid.getScene().getWindow();
        }

        if (socketClient == null || targetStage == null) {
            String msg = "Navigation error: " + (socketClient == null ? "socketClient " : "") + (targetStage == null ? "targetStage " : "") + "is null!";
            System.err.println("[CatalogController] " + msg);
            showStatus(msg, true);
            return;
        }

        try {
            String fxmlPath = "/UI/productDetails.fxml";
            var fxmlResource = getClass().getResource(fxmlPath);
            if (fxmlResource == null) {
                showStatus("FXML not found: " + fxmlPath, true);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlResource);
            Parent root = loader.load();
            ProductDetailsController detailsController = loader.getController();
            detailsController.setSocketClient(socketClient);
            detailsController.setPrimaryStage(targetStage);
            detailsController.initData(selected);

            targetStage.setTitle("ChriOnline — " + selected.name);
            targetStage.setScene(new Scene(root, 1100, 750));
            System.out.println("[CatalogController] Navigation successful.");
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Error opening details: " + e.getMessage(), true);
        }
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
                statusLabel.setVisible(true);
                statusLabel.setManaged(true);
                System.out.println("[CatalogController Status] " + (isError ? "ERROR: " : "INFO: ") + message);
            }
        });
    }

}
