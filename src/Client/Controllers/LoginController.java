package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.ResponseBuilder;
import Shared.Security.RSAKeyPairGenerator;
import Shared.Security.Signer;
import javafx.application.Platform;
import java.security.PrivateKey;
import java.util.Base64;
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



public class LoginController {

    // ── FXML injections ───────────────────────────────────────────
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     passwordVisibleField;
    @FXML private Button        togglePasswordBtn;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    private boolean passwordVisible = false;

    @FXML
    private void initialize() {
        // Sync password field <-> visible text field
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!passwordVisibleField.getText().equals(n))
                passwordVisibleField.setText(n);
        });
        passwordVisibleField.textProperty().addListener((obs, o, n) -> {
            if (!passwordField.getText().equals(n))
                passwordField.setText(n);
        });
    }

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
        String password = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();

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

    @FXML
    private void handleTogglePassword() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.setVisible(true);
            passwordField.setVisible(false);
            togglePasswordBtn.setText("Hide");
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordField.setVisible(true);
            passwordVisibleField.setVisible(false);
            togglePasswordBtn.setText("Show");
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Register button — switch to register screen
    // ──────────────────────────────────────────────────────────────
    @FXML
    private void handleAdminRSALogin() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Please enter your admin username first.");
            return;
        }

        loginButton.setDisable(true);
        hideError();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // 1. Request Challenge
                String challengeResp = socketClient.sendCommand("ADMIN_CHALLENGE|" + username);
                if (!ResponseBuilder.isOk(challengeResp)) return challengeResp;
                
                String challenge = ResponseBuilder.extractPayload(challengeResp);

                // 2. Sign Challenge locally
                // Note: Expects admin_private.key in the app root
                PrivateKey privKey = RSAKeyPairGenerator.loadPrivateKeyFromFile("admin_private.key");
                byte[] signature = Signer.sign(challenge, privKey);
                String signatureB64 = Base64.getEncoder().encodeToString(signature);

                // 3. Verify & Login
                return socketClient.sendCommand("ADMIN_VERIFY|" + username + "|" + signatureB64 + "|" + udpPort);
            }
        };

        task.setOnSucceeded(event -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                String[] parts = payload.split("\\|", 3);
                if (parts.length >= 2) {
                    AppState.setSession(parts[0], username, parts[1], 0);
                    loadMainWindow();
                } else {
                    loginButton.setDisable(false);
                    showError("Unknown server response.");
                }
            } else {
                loginButton.setDisable(false);
                showError(ResponseBuilder.extractError(response));
            }
        });

        task.setOnFailed(event -> {
            loginButton.setDisable(false);
            Throwable e = task.getException();
            if (e instanceof java.io.FileNotFoundException) {
                showError("Admin private key not found locally.");
            } else {
                showError("RSA Login Failed: " + (e != null ? e.getMessage() : "Unknown error"));
                if (e != null) e.printStackTrace();
            }
        });

        new Thread(task).start();
    }
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
            primaryStage.setScene(new Scene(root, 1100, 750));

        } catch (Exception e) {
            showError("Could not load register screen.");
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Load main window after successful login
    // Checks role: ADMIN → admin.fxml, USER → catalog.fxml
    // ──────────────────────────────────────────────────────────────
    private void loadMainWindow() {
        Platform.runLater(() -> {
            try {
                if (AppState.isAdmin()) {
                    // ── ADMIN → load admin dashboard ───────────────
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/UI/admin.fxml"));
                    Parent root = loader.load();

                    AdminController adminController = loader.getController();
                    adminController.setSocketClient(socketClient);

                    primaryStage.setTitle("ChriOnline — Admin Panel");
                    primaryStage.setScene(new Scene(root, 1100, 750));

                } else {
                    // ── USER → load product catalogue ──────────────
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/UI/catalog.fxml"));
                    Parent root = loader.load();

                    CatalogController catalogController = loader.getController();
                    catalogController.setSocketClient(socketClient);
                    catalogController.setPrimaryStage(primaryStage);

                    primaryStage.setTitle("ChriOnline — Welcome, "
                            + AppState.getUsername());
                    primaryStage.setScene(new Scene(root, 1100, 750));
                }

                primaryStage.show();

            } catch (Exception e) {
                showError("Could not load main window.");
                e.printStackTrace();
            }
        });
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