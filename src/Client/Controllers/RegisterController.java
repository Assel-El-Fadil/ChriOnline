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

    // ── FXML injections ───────────────────────────────────────────
    @FXML private TextField     firstNameField;
    @FXML private TextField     lastNameField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     emailField;
    @FXML private Label         errorLabel;
    @FXML private Button        registerButton;

    // ── Injected by LoginController before the scene is shown ─────
    private SocketClient socketClient;
    private int          udpPort;
    private Stage        primaryStage;

    // ──────────────────────────────────────────────────────────────
    // Setters — called before showing the scene
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

        // ── Client-side validation ─────────────────────────────────
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

        // Disable button to prevent double-click
        registerButton.setDisable(true);
        hideError();

        // Build REGISTER command
        // REGISTER|firstName|lastName|username|password|email
        String command = "REGISTER|" + firstName + "|" + lastName + "|" + username + "|" + password + "|" + email;

        // Run on background thread — NEVER call sendCommand() on the UI thread
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        // ── On OK : redirect to login with success message ─────────
        task.setOnSucceeded(event -> {
            String response = task.getValue();

            if (ResponseBuilder.isOk(response)) {
                // Registration successful — go back to login screen
                loadLoginScreen("Registration successful! Please log in.");
            } else {
                // ERR|message — show the specific server error
                registerButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        // ── On failure : network error ─────────────────────────────
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
    // Load login screen — optionally pre-fill a success message
    // ──────────────────────────────────────────────────────────────
    private void loadLoginScreen(String successMessage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/UI/login.fxml")
            );
            Parent root = loader.load();

            // Pass shared dependencies back to LoginController
            LoginController lc = loader.getController();
            lc.setSocketClient(socketClient);
            lc.setUdpPort(udpPort);
            lc.setPrimaryStage(primaryStage);

            // If registration succeeded, show the success message on the login screen
            if (successMessage != null) {
                lc.showSuccessMessage(successMessage);
            }

            primaryStage.setTitle("ChriOnline — Login");
            primaryStage.setScene(new Scene(root));

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