package Client.Controllers;

import Client.network.SocketClient;
import Shared.ResponseBuilder;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    @FXML private TextField     firstNameField;
    @FXML private TextField     lastNameField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     emailField;
    @FXML private Label         errorLabel;
    @FXML private Button        registerButton;

    private SocketClient socketClient;
    private int          udpPort;
    private Stage        primaryStage;

    // ──────────────────────────────────────────────────────────────
    // Setters
    // ──────────────────────────────────────────────────────────────
    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    // ──────────────────────────────────────────────────────────────
    // Register button handler
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handleRegister() {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String username  = usernameField.getText().trim();
        String password  = passwordField.getText();
        String email     = emailField.getText().trim();

        if (firstName.isBlank()) {
            showError("First name cannot be empty.");
            return;
        }
        if (lastName.isBlank()) {
            showError("Last name cannot be empty.");
            return;
        }
        if (username.isBlank()) {
            showError("Username cannot be empty.");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }
        if (!email.contains("@")) {
            showError("Please enter a valid email address.");
            return;
        }

        registerButton.setDisable(true);
        hideError();

        String command = "REGISTER|" + firstName + "|" + lastName + "|" + username + "|" + password + "|" + email;

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        task.setOnSucceeded(event -> {
            String response = task.getValue();

            if (ResponseBuilder.isOk(response)) {
                loadLoginScreen("Registration successful! Please log in.");
            } else {
                registerButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        task.setOnFailed(event -> {
            registerButton.setDisable(false);
            showError("Cannot reach server. Check your connection.");
        });

        new Thread(task).start();
    }

    // ──────────────────────────────────────────────────────────────
    // Back to Login button
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handleBackToLogin() {
        loadLoginScreen(null);
    }

    // ──────────────────────────────────────────────────────────────
    // Load login screen
    // ──────────────────────────────────────────────────────────────
    private void loadLoginScreen(String successMessage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/UI/login.fxml")
            );
            Parent root = loader.load();

            LoginController lc = loader.getController();
            lc.setSocketClient(socketClient);
            lc.setUdpPort(udpPort);
            lc.setPrimaryStage(primaryStage);

            if (successMessage != null) {
                lc.showSuccessMessage(successMessage);
            }

            primaryStage.setTitle("ChriOnline — Login");
            primaryStage.setScene(new Scene(root, 1100, 750));

        } catch (Exception e) {
            showError("Could not load login screen.");
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }
}