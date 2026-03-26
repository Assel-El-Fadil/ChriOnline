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
    private List<CartItemDTO>       cartItems;   // passed from CartController
    private Runnable                onSuccess;   // callback → switch to Order History tab
    private Runnable                onBack;      // callback → switch back to Cart tab

    // ──────────────────────────────────────────────────────────────
    // Setters
    // ──────────────────────────────────────────────────────────────
    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    /** Pre-load cart items from CartController so we can show the summary. */
    public void setCartItems(List<CartItemDTO> items) {
        this.cartItems = items;
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

        // Populate order summary if cart items were pre-loaded
        if (cartItems != null && !cartItems.isEmpty()) {
            ObservableList<CartItemDTO> list = FXCollections.observableArrayList(cartItems);
            summaryTable.setItems(list);

            double total = cartItems.stream().mapToDouble(i -> i.subtotal).sum();
            totalLabel.setText(String.format("%.2f MAD", total));
        }
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

        // ── Client-side validation (same rules as PaymentService) ──
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

        // Disable button to prevent double-click
        placeOrderButton.setDisable(true);
        hideError();

        // Build CHECKOUT command
        // CHECKOUT|token|CARD|cardNum|holder|expiry|cvv
        String command = "CHECKOUT|" + AppState.getToken()
                + "|CARD|" + cardNum
                + "|" + holder
                + "|" + expiry
                + "|" + cvv;

        // Run on background thread
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        // ── On OK : parse orderId + refCode, show confirmation ─────
        task.setOnSucceeded(event -> {
            String response = task.getValue();

            if (ResponseBuilder.isOk(response)) {
                // Response format: OK|orderId|refCode
                String payload = ResponseBuilder.extractPayload(response);
                String[] parts = payload.split("\\|", 2);

                String orderId = parts.length > 0 ? parts[0] : "?";
                String refCode = parts.length > 1 ? parts[1] : "?";

                // Show confirmation dialog
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Order Confirmed");
                alert.setHeaderText("Your order has been placed!");
                alert.setContentText(
                        "Reference: " + refCode + "\n" +
                                "Order ID: " + orderId + "\n" +
                                "Total: " + totalLabel.getText()
                );
                alert.showAndWait();

                // Switch to Order History tab (if callback was set)
                if (onSuccess != null) {
                    onSuccess.run();
                }

            } else {
                // ERR — display error in red Label
                placeOrderButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        // ── On failure : network error ─────────────────────────────
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