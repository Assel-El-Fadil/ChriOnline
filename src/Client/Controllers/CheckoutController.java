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

    // ── FXML injections ───────────────────────────────────────────
    @FXML private TableView<CartItemDTO>           summaryTable;
    @FXML private TableColumn<CartItemDTO, String> colName;
    @FXML private TableColumn<CartItemDTO, Number> colQty;
    @FXML private TableColumn<CartItemDTO, Number> colUnitPrice;
    @FXML private TableColumn<CartItemDTO, Number> colSubtotal;
    @FXML private Label                            totalLabel;
    @FXML private TextField                        cardNumberField;
    @FXML private TextField                        cardHolderField;
    @FXML private TextField                        expiryField;
    @FXML private TextField                        cvvField;
    @FXML private Label                            errorLabel;
    @FXML private Button                           placeOrderButton;

    // ── Injected by parent controller ─────────────────────────────
    private SocketClient            socketClient;
    private Stage primaryStage;
    private List<CartItemDTO>       cartItems;   // passed from CartController
    private Runnable                onSuccess;   // callback → switch to Order History tab
    private Runnable                onBack;      // callback → switch back to Cart tab

    // ──────────────────────────────────────────────────────────────
    // Setters
    // ──────────────────────────────────────────────────────────────
    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /** Pre-load cart items from CartController so we can show the summary. */
    public void setCartItems(List<CartItemDTO> items) {
        this.cartItems = items;

        // Populate order summary if cart items were pre-loaded
        if (cartItems != null && !cartItems.isEmpty()) {
            ObservableList<CartItemDTO> list = FXCollections.observableArrayList(cartItems);
            summaryTable.setItems(list);

            double total = cartItems.stream().mapToDouble(i -> i.subtotal).sum();
            totalLabel.setText(String.format("%.2f MAD", total));
        }
    }

    /** Called after a successful checkout to switch to the Order History tab. */
    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    /** Called when the user wants to go back to the cart. */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    // ──────────────────────────────────────────────────────────────
    // initialize() — wire columns and populate summary table
    // ──────────────────────────────────────────────────────────────
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

    // ──────────────────────────────────────────────────────────────
    // Place Order button handler
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handlePlaceOrder() {
        String cardNum = cardNumberField.getText().trim();
        String holder  = cardHolderField.getText().trim();
        String expiry  = expiryField.getText().trim();
        String cvv     = cvvField.getText().trim();

        // ── Client-side validation ──
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

        // Step 1: Initialize Checkout (Triggers 2FA code sending)
        // CHECKOUT_INIT|token|CARD|cardNum|holder|expiry|cvv
        String initCommand = "CHECKOUT_INIT|" + AppState.getToken()
                + "|CARD|" + cardNum
                + "|" + holder
                + "|" + expiry
                + "|" + cvv;

        Task<String> initTask = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(initCommand);
            }
        };

        initTask.setOnSucceeded(event -> {
            String response = initTask.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                String[] parts = payload.split("\\|");
                
                if (parts.length >= 1 && "2FA_REQUIRED".equals(parts[0])) {
                    String transactionId = parts.length > 1 ? parts[1] : null;
                    // String creationDate = parts.length > 2 ? parts[2] : null; // Optional: could show this

                    // Step 2: Open Verification Dialog
                    showVerificationDialog(transactionId);
                } else {
                    placeOrderButton.setDisable(false);
                    showError("Unexpected response: " + response);
                }
            } else {
                placeOrderButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        initTask.setOnFailed(event -> {
            placeOrderButton.setDisable(false);
            showError("Server unreachable. Check connection.");
        });

        new Thread(initTask).start();
    }

    private void showVerificationDialog(String transactionId) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/UI/paymentVerification.fxml"));
            javafx.scene.Parent root = loader.load();

            PaymentVerificationController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Verify Payment");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.setScene(new javafx.scene.Scene(root));
            dialogStage.showAndWait();

            if (!controller.isCancelled() && controller.getResult() != null) {
                confirmCheckout(controller.getResult(), transactionId);
            } else {
                placeOrderButton.setDisable(false);
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
            showError("Could not open verification dialog.");
            placeOrderButton.setDisable(false);
        }
    }

    private void confirmCheckout(String code, String transactionId) {
        // Step 3: Confirm Checkout with 2FA code AND transactionId
        // CHECKOUT_CONFIRM|token|code|transactionId
        String confirmCommand = "CHECKOUT_CONFIRM|" + AppState.getToken() + "|" + code + "|" + transactionId;

        Task<String> confirmTask = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(confirmCommand);
            }
        };

        confirmTask.setOnSucceeded(event -> {
            String response = confirmTask.getValue();
            if (ResponseBuilder.isOk(response)) {
                if (onSuccess != null) onSuccess.run();
            } else {
                placeOrderButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        confirmTask.setOnFailed(event -> {
            placeOrderButton.setDisable(false);
            showError("Confirmation failed. Check connection.");
        });

        new Thread(confirmTask).start();
    }

    @FXML
    private void handleBackToCart() {
        if (onBack != null) {
            onBack.run();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }
}