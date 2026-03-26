package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.ResponseBuilder;
import javafx.application.Platform;
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

import java.io.IOException;

public class LoginController {

    // ── FXML injections ───────────────────────────────────────────
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    // ── Injected by Main before the scene is shown ────────────────
    private SocketClient socketClient;
    private int          udpPort;
    private Stage        primaryStage;

    // ──────────────────────────────────────────────────────────────
    // Setters — called by Main.java after FXMLLoader.load()
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
    // Login button handler
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Basic client-side validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        // Disable button to prevent double-click
        loginButton.setDisable(true);
        hideError();

        // Build LOGIN command — include UDP port as 4th param
        // LOGIN|username|password|udpPort
        String command = "LOGIN|" + username + "|" + password + "|" + udpPort;

        // Run on background thread — NEVER call sendCommand() on the UI thread
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        // ── On success : parse response ────────────────────────────
        task.setOnSucceeded(event -> {
            String response = task.getValue();

            if (ResponseBuilder.isOk(response)) {
                // Response format: OK|token|role
                String payload = ResponseBuilder.extractPayload(response);
                String[] parts = payload.split("\\|", 3);

                if (parts.length >= 2) {
                    String token = parts[0];
                    String role  = parts[1];
                    // userId not yet returned by LOGIN — default 0 until ORDER_HISTORY
                    AppState.setSession(token, username, role, 0);

                    loadMainWindow();
                } else {
                    loginButton.setDisable(false);
                    showError("Unexpected server response.");
                }

            } else {
                // ERR|message
                loginButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        // ── On failure : network error ─────────────────────────────
        task.setOnFailed(event -> {
            loginButton.setDisable(false);
            showError("Cannot reach server. Check your connection.");
        });

        new Thread(task).start();
    }

    // ──────────────────────────────────────────────────────────────
    // Register button — switch to register screen
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handleRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/UI/register.fxml")
            );
            Parent root = loader.load();

            // Pass shared dependencies to RegisterController
            RegisterController rc = loader.getController();
            rc.setSocketClient(socketClient);
            rc.setUdpPort(udpPort);
            rc.setPrimaryStage(primaryStage);

            primaryStage.setTitle("ChriOnline — Register");
            primaryStage.setScene(new Scene(root));

        } catch (Exception e) {
            showError("Could not load register screen.");
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Load main window after successful login
    // ──────────────────────────────────────────────────────────────
    private void loadMainWindow() {
        try {
            Platform.runLater(() -> {
                primaryStage.setTitle("ChriOnline");
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/catalog.fxml"));
                Parent root = null;
                try {
                    root = loader.load();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                CatalogController catalogController = loader.getController();
                catalogController.setSocketClient(socketClient);
                catalogController.setPrimaryStage(primaryStage);
                primaryStage.setScene(new Scene(root, 1100, 750));
            });

        } catch (Exception e) {
            showError("Could not load main window.");
            e.printStackTrace();
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

    // ──────────────────────────────────────────────────────────────
    // Called by RegisterController after successful registration
    // Shows a green success message on the login screen
    // ──────────────────────────────────────────────────────────────
    public void showSuccessMessage(String message) {
        errorLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}

