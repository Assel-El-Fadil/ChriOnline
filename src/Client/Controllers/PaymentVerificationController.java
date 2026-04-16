package Client.Controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class PaymentVerificationController {

    @FXML private TextField codeField;
    @FXML private Label errorLabel;
    @FXML private Label transactionIdLabel;
    @FXML private Label horodatageLabel;
    @FXML private Button verifyButton;

    private String result = null;
    private boolean cancelled = false;

    @FXML
    public void initialize() {
        // Limit input to 6 characters and digits only
        codeField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                codeField.setText(newValue.replaceAll("[^\\d]", ""));
            }
            if (codeField.getText().length() > 6) {
                codeField.setText(codeField.getText().substring(0, 6));
            }
        });
    }

    @FXML
    private void handleVerify() {
        String code = codeField.getText().trim();
        if (code.length() != 6) {
            showError("Please enter the 6-digit code.");
            return;
        }
        result = code;
        ((Stage) verifyButton.getScene().getWindow()).close();
    }

    @FXML
    private void handleCancel() {
        cancelled = true;
        ((Stage) verifyButton.getScene().getWindow()).close();
    }

    public String getResult() {
        return result;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setTransactionInfo(String transactionId, String timestampStr) {
        if (transactionId != null) {
            transactionIdLabel.setText("ID: " + transactionId.substring(0, 8).toUpperCase());
        }
        if (timestampStr != null) {
            try {
                long ts = Long.parseLong(timestampStr);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                horodatageLabel.setText("Horodatage: " + sdf.format(new java.util.Date(ts)));
            } catch (NumberFormatException e) {
                horodatageLabel.setText("Horodatage: " + timestampStr);
            }
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
