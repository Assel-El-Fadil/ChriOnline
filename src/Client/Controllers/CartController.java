package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.CartItemDTO;
import Shared.ResponseBuilder;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class CartController {


    @FXML private TableView<CartItemDTO>              cartTable;
    @FXML private TableColumn<CartItemDTO, String>    colName;
    @FXML private TableColumn<CartItemDTO, Number>    colQty;
    @FXML private TableColumn<CartItemDTO, Number>    colUnitPrice;
    @FXML private TableColumn<CartItemDTO, Number>    colSubtotal;
    @FXML private Label                               totalLabel;
    @FXML private Label                               statusLabel;
    @FXML private Button                              removeButton;
    @FXML private Button                              checkoutButton;


    private SocketClient socketClient;
    private Stage        primaryStage;


    private final ObservableList<CartItemDTO> cartItems = FXCollections.observableArrayList();

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
        loadCart();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void handleBackToCatalog() {
        if (socketClient == null || primaryStage == null) {
            return;
        }
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
            showError("Could not return to catalog.");
        }
    }

    @FXML
    public void initialize() {

        // ── Wire table columns to CartItemDTO fields ───────────────
        colName.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().productName));

        colQty.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().quantity));

        colUnitPrice.setCellValueFactory(
                cell -> new SimpleDoubleProperty(cell.getValue().unitPrice));
        colUnitPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("%.2f MAD", item.doubleValue()));
            }
        });

        colSubtotal.setCellValueFactory(
                cell -> new SimpleDoubleProperty(cell.getValue().subtotal));
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("%.2f MAD", item.doubleValue()));
            }
        });

        removeButton.setDisable(true);
        cartTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> removeButton.setDisable(newVal == null)
        );


        cartTable.setItems(cartItems);
    }

    private void loadCart() {
        String command = "CART_VIEW|" + AppState.getToken();

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        task.setOnSucceeded(event -> {
            String response = task.getValue();

            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                cartItems.clear();

                if (!payload.isBlank()) {
                    String[] parts = payload.split(";");
                    for (String part : parts) {
                        if (!part.isBlank()) {
                            try {
                                cartItems.add(CartItemDTO.fromProtocolString(part));
                            } catch (Exception e) {
                                System.err.println("[CartController] Parse error: " + e.getMessage());
                            }
                        }
                    }
                }

                updateTotal();
                hideStatus();

            } else {
                showError(ResponseBuilder.extractError(response));
            }
        });

        task.setOnFailed(event ->
                showError("Failed to load cart. Check your connection.")
        );

        new Thread(task).start();
    }

    @FXML
    private void handleRemove() {
        CartItemDTO selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        removeButton.setDisable(true);

        String command = "CART_REMOVE|" + AppState.getToken() + "|" + selected.productId;

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        task.setOnSucceeded(event -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                loadCart();
            } else {
                removeButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        task.setOnFailed(event -> {
            removeButton.setDisable(false);
            showError("Remove failed. Check your connection.");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleCheckout() {
        if (cartItems.isEmpty()) {
            showError("Your cart is empty.");
            return;
        }

        if (socketClient == null || primaryStage == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/checkout.fxml"));
            Parent root = loader.load();

            CheckoutController checkoutController = loader.getController();
            checkoutController.setSocketClient(socketClient);
            checkoutController.setPrimaryStage(primaryStage);
            checkoutController.setCartItems(cartItems);


            checkoutController.setOnBack(() -> handleBackToCatalog());

            checkoutController.setOnBack(() -> {
                try {
                    FXMLLoader cartLoader = new FXMLLoader(getClass().getResource("/UI/cart.fxml"));
                    Parent cartRoot = cartLoader.load();
                    CartController cc = cartLoader.getController();
                    cc.setSocketClient(socketClient);
                    cc.setPrimaryStage(primaryStage);
                    primaryStage.setScene(new Scene(cartRoot, 1100, 750));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            checkoutController.setOnSuccess(() -> {
                try {
                    FXMLLoader historyLoader = new FXMLLoader(getClass().getResource("/UI/orderHistory.fxml"));
                    Parent historyRoot = historyLoader.load();
                    OrderHistoryController ohc = historyLoader.getController();
                    ohc.setSocketClient(socketClient);
                    ohc.setPrimaryStage(primaryStage);
                    primaryStage.setTitle("ChriOnline — Order History");
                    primaryStage.setScene(new Scene(historyRoot, 1100, 750));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            primaryStage.setTitle("ChriOnline — Checkout");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showError("Could not open checkout page.");
        }
    }

    private void updateTotal() {
        double total = cartItems.stream()
                .mapToDouble(item -> item.subtotal)
                .sum();
        totalLabel.setText(String.format("%.2f MAD", total));
    }

    private void showError(String message) {
        statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        statusLabel.setText(message);
        statusLabel.setVisible(true);
    }

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setText("");
    }
}