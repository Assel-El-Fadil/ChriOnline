package Admin.Controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Admin.network.AdminSocket;
import Admin.session.AdminAppState;
import Shared.ResponseBuilder;
import Shared.Security.RSAKeyPairGenerator;
import Shared.Security.Signer;
import Client.util.AnimationUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.security.PrivateKey;
import java.util.Base64;

public class AdminLoginController {
    private static final Logger logger = LogManager.getLogger(AdminLoginController.class);

    @FXML private TextField usernameField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private AdminSocket socketClient;
    private int udpPort;
    private Stage primaryStage;

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            if (loginButton != null) {
                AnimationUtils.makePulsingOnHover(loginButton);
            }
            if (usernameField != null && usernameField.getParent() != null) {
                AnimationUtils.popIn(usernameField.getParent().getParent(), 100);
            }
        });
    }

    public void setAdminSocket(AdminSocket socketClient) {
        this.socketClient = socketClient;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

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
                    AdminAppState.setSession(parts[0], username, parts[1], 0);
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

    private void loadMainWindow() {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/admin.fxml"));
                Parent root = loader.load();

                AdminController adminController = loader.getController();
                adminController.setAdminSocket(socketClient);

                primaryStage.setTitle("ChriOnline — Admin Panel");
                primaryStage.setScene(new Scene(root, 1100, 750));
                primaryStage.show();

            } catch (Exception e) {
                showError("Could not load main window.");
                logger.error("Exception occurred", e);
            }
        });
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }

    public void setSocketClient(AdminSocket socketClient) {
        this.socketClient = socketClient;
    }
}
