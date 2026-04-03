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



public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    private SocketClient socketClient;
    private int          udpPort;
    private Stage        primaryStage;

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        loginButton.setDisable(true);
        hideError();

        String command = "LOGIN|" + username + "|" + password + "|" + udpPort;


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
                String[] parts = payload.split("\\|", 3);

                if (parts.length >= 2) {
                    String token = parts[0];
                    String role  = parts[1];
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

        task.setOnFailed(event -> {
            loginButton.setDisable(false);
            showError("Cannot reach server. Check your connection.");
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

    private void loadMainWindow() {
        Platform.runLater(() -> {
            try {
                if (AppState.isAdmin()) {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/UI/admin.fxml"));
                    Parent root = loader.load();

                    AdminController adminController = loader.getController();
                    adminController.setSocketClient(socketClient);

                    primaryStage.setTitle("ChriOnline — Admin Panel");
                    primaryStage.setScene(new Scene(root, 1100, 750));

                } else {
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

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }

    public void showSuccessMessage(String message) {
        errorLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}