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
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

public class CheckoutController {

    @FXML private TableView<CartItemDTO>           summaryTable;
    @FXML private TableColumn<CartItemDTO, String> colName;
    @FXML private TableColumn<CartItemDTO, Number> colQty;
    @FXML private TableColumn<CartItemDTO, Number> colUnitPrice;
    @FXML private TableColumn<CartItemDTO, Number> colSubtotal;
    @FXML private Label  totalLabel;
    @FXML private TextField  cardNumberField;
    @FXML private TextField cardHolderField;
    @FXML private TextField expiryField;
    @FXML private TextField cvvField;
    @FXML private Label  errorLabel;
    @FXML private Button  placeOrderButton;

    private SocketClient            socketClient;
    private Stage primaryStage;
    private List<CartItemDTO>       cartItems;
    private Runnable                onSuccess;
    private Runnable                onBack;


    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }


    public void setCartItems(List<CartItemDTO> items) {
        this.cartItems = items;

        if (cartItems != null && !cartItems.isEmpty()) {
            ObservableList<CartItemDTO> list = FXCollections.observableArrayList(cartItems);
            summaryTable.setItems(list);

            double total = cartItems.stream().mapToDouble(i -> i.subtotal).sum();
            totalLabel.setText(String.format("%.2f MAD", total));
        }
    }

    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML
    public void initialize() {

        colName.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().productName));

        colQty.setCellValueFactory(
                c -> new SimpleIntegerProperty(c.getValue().quantity));

        colUnitPrice.setCellValueFactory(
                c -> new SimpleDoubleProperty(c.getValue().unitPrice));
        colUnitPrice.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f MAD", v.doubleValue()));
            }
        });

        colSubtotal.setCellValueFactory(
                c -> new SimpleDoubleProperty(c.getValue().subtotal));
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f MAD", v.doubleValue()));
            }
        });
    }

    @FXML
    private void handlePlaceOrder() {
        String cardNum = cardNumberField.getText().trim();
        String holder  = cardHolderField.getText().trim();
        String expiry  = expiryField.getText().trim();
        String cvv     = cvvField.getText().trim();

        if (!cardNum.matches("[0-9]{16}")) {
            showError("Card number must be exactly 16 digits.");
            return;
        }
        if (holder.isBlank()) {
            showError("Card holder name cannot be empty.");
            return;
        }
        if (!expiry.matches("(0[1-9]|1[0-2])/[0-9]{2}")) {
            showError("Expiry date must be in MM/YY format.");
            return;
        }
        if (!cvv.matches("[0-9]{3}")) {
            showError("CVV must be exactly 3 digits.");
            return;
        }

        placeOrderButton.setDisable(true);
        hideError();

        String command = "CHECKOUT|" + AppState.getToken()
                + "|CARD|" + cardNum
                + "|" + holder
                + "|" + expiry
                + "|" + cvv;

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
                String[] parts = payload.split("\\|", 2);

                String orderId = parts.length > 0 ? parts[0] : "?";
                String refCode = parts.length > 1 ? parts[1] : "?";

                if (onSuccess != null) {
                    onSuccess.run();
                }

            } else {
                placeOrderButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        task.setOnFailed(event -> {
            placeOrderButton.setDisable(false);
            showError("Cannot reach server. Check your connection.");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleBackToCart() {
        if (onBack != null) {
            onBack.run();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }
}