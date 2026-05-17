package Client.Controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger logger = LogManager.getLogger(RegisterController.class);


    @FXML private TextField     firstNameField;
    @FXML private TextField     lastNameField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     passwordVisibleField;
    @FXML private Button        togglePasswordBtn;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     confirmPasswordVisibleField;
    @FXML private Button        toggleConfirmPasswordBtn;
    @FXML private TextField     emailField;
    @FXML private Label         errorLabel;
    @FXML private Button        registerButton;

    private SocketClient socketClient;
    private int          udpPort;
    private Stage        primaryStage;

    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    @FXML
    private void initialize() {
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!passwordVisibleField.getText().equals(n))
                passwordVisibleField.setText(n);
        });
        passwordVisibleField.textProperty().addListener((obs, o, n) -> {
            if (!passwordField.getText().equals(n))
                passwordField.setText(n);
        });

        confirmPasswordField.textProperty().addListener((obs, o, n) -> {
            if (!confirmPasswordVisibleField.getText().equals(n))
                confirmPasswordVisibleField.setText(n);
        });
        confirmPasswordVisibleField.textProperty().addListener((obs, o, n) -> {
            if (!confirmPasswordField.getText().equals(n))
                confirmPasswordField.setText(n);
        });
    }

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

    @FXML
    private void handleToggleConfirmPassword() {
        confirmPasswordVisible = !confirmPasswordVisible;
        if (confirmPasswordVisible) {
            confirmPasswordVisibleField.setText(confirmPasswordField.getText());
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordField.setVisible(false);
            toggleConfirmPasswordBtn.setText("Hide");
            confirmPasswordVisibleField.requestFocus();
            confirmPasswordVisibleField.positionCaret(confirmPasswordVisibleField.getText().length());
        } else {
            confirmPasswordField.setText(confirmPasswordVisibleField.getText());
            confirmPasswordField.setVisible(true);
            confirmPasswordVisibleField.setVisible(false);
            toggleConfirmPasswordBtn.setText("Show");
            confirmPasswordField.requestFocus();
            confirmPasswordField.positionCaret(confirmPasswordField.getText().length());
        }
    }

    @FXML
    private void handleRegister() {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String username  = usernameField.getText().trim();
        String password  = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
        String confirmPw = confirmPasswordVisible ? confirmPasswordVisibleField.getText() : confirmPasswordField.getText();
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
        if (!password.equals(confirmPw)) {
            showError("Passwords do not match.");
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

    @FXML
    private void handleBackToLogin() {
        loadLoginScreen(null);
    }


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
            logger.error("Exception occurred", e);
        }
    }

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