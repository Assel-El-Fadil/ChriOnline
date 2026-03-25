package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.ProductDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CatalogController {

    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private Button showAllButton;
    @FXML
    private Button refreshButton;
    @FXML
    private TableView<ProductDTO> productTable;
    @FXML
    private TableColumn<ProductDTO, String> colName;
    @FXML
    private TableColumn<ProductDTO, String> colCategory;
    @FXML
    private TableColumn<ProductDTO, String> colPrice;
    @FXML
    private TableColumn<ProductDTO, String> colStock;
    @FXML
    private Button viewDetailsButton;
    @FXML
    private Button addToCartButton;
    @FXML
    private Label statusLabel;

    private SocketClient socketClient;
    private Stage        primaryStage;

    @FXML
    public void initialize() {
        // HGrow for status label to push buttons right
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Setup columns
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category));

        // Price formatting
        colPrice.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("%.2f MAD", data.getValue().price)));

        // Stock formatting with colored text for 0
        colStock.setCellValueFactory(data -> {
            int stock = data.getValue().stock;
            if (stock == 0) {
                return new SimpleStringProperty("Out of stock");
            }
            return new SimpleStringProperty(String.valueOf(stock));
        });

        colStock.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    if ("Out of stock".equals(item)) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });

        // Setup table selection listener
        productTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            addToCartButton.setDisable(!hasSelection);
            viewDetailsButton.setDisable(!hasSelection);
        });

        // Setup category filter listener
        categoryComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if ("Show All".equals(newVal)) {
                    loadProducts(null);
                } else {
                    loadProducts(newVal);
                }
            }
        });

        // Load initial data is now deferred until the socket client is set
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
                if (!socketClient.isConnected())
                    socketClient.reconnect();
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
                if (!socketClient.isConnected())
                    socketClient.reconnect();
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
                productTable.setItems(products);
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
        });

        new Thread(task).start();
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
    private void handleAddToCart(ActionEvent event) {
        ProductDTO selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

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
                        if (!socketClient.isConnected())
                            socketClient.reconnect();
                        String token = AppState.getToken();
                        return socketClient.sendCommand("CART_ADD|" + token + "|" + selected.id + "|" + qty);
                    }
                };

                task.setOnSucceeded(e -> {
                    String response = task.getValue();
                    if (ResponseBuilder.isOk(response)) {
                        String newTotal = ResponseBuilder.extractPayload(response);
                        showStatus("Added! Cart total: " + newTotal + " MAD", false);

                        // Optionally refresh products to update stock locally
                        handleRefresh(null);
                    } else {
                        showStatus(ResponseBuilder.extractError(response), true);
                    }
                });

                new Thread(task).start();

            } catch (NumberFormatException e) {
                showStatus("Invalid quantity input", true);
            }
        });
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        ProductDTO selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (socketClient == null || primaryStage == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/productDetails.fxml"));
            Parent root = loader.load();
            ProductDetailsController detailsController = loader.getController();
            detailsController.setSocketClient(socketClient);
            detailsController.setPrimaryStage(primaryStage);
            detailsController.initData(selected);

            primaryStage.setTitle("ChriOnline — " + selected.name);
            primaryStage.setScene(new Scene(root, 900, 640));
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
