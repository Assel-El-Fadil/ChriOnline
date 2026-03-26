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
        imageView.setFitWidth(216);
        imageView.setFitHeight(148);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setImage(ProductImageHelper.loadLocalImage(p.imagePath));

        Label badge = createCatalogBadge(p);
        StackPane badgeWrap = new StackPane(badge);
        StackPane.setAlignment(badgeWrap, Pos.TOP_LEFT);
        StackPane.setMargin(badgeWrap, new Insets(10, 0, 0, 10));

        imageStack.getChildren().addAll(imageBg, imageView, badgeWrap);
        StackPane.setAlignment(imageView, Pos.CENTER);

        Label subtitle = new Label(truncateDescription(p.description, 52));
        subtitle.getStyleClass().add("catalog-card-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(236);

        Label name = new Label(p.name);
        name.getStyleClass().add("catalog-card-name");
        name.setWrapText(true);
        name.setMaxWidth(236);

        int reviewCount = 200 + Math.abs(p.id * 47) % 9800;
        double ratingStars = 4.0 + (Math.abs(p.id) % 10) / 10.0;
        Label rating = new Label(String.format("★ %.1f  (%d reviews)", ratingStars, reviewCount));
        rating.getStyleClass().add("catalog-card-rating");

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

        VBox body = new VBox(8, subtitle, name, rating, priceRow, detailsBtn);
        body.setPadding(new Insets(12, 10, 14, 10));

        card.getChildren().addAll(imageStack, body);
        return card;
    }

    private static Label createCatalogBadge(ProductDTO p) {
        Label l = new Label();
        l.getStyleClass().add("catalog-card-badge");
        if (p.stock == 0) {
            l.setText("Unavailable");
            l.getStyleClass().add("catalog-badge-unavailable");
            return l;
        }
        switch (Math.abs(p.id) % 4) {
            case 0:
                l.setText("Best Seller");
                l.getStyleClass().add("catalog-badge-success");
                break;
            case 1:
                l.setText("Top Rated");
                l.getStyleClass().add("catalog-badge-teal");
                break;
            case 2:
                l.setText("Sale");
                l.getStyleClass().add("catalog-badge-sale");
                break;
            default:
                l.setText("New");
                l.getStyleClass().add("catalog-badge-new");
                break;
        }
        return l;
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
        if (socketClient == null || primaryStage == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/productDetails.fxml"));
            Parent root = loader.load();
            ProductDetailsController detailsController = loader.getController();
            detailsController.setSocketClient(socketClient);
            detailsController.setPrimaryStage(primaryStage);
            detailsController.initData(selected);

            primaryStage.setTitle("ChriOnline — " + selected.name);
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Could not open product details.", true);
        }
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
            }
        });
    }

}
