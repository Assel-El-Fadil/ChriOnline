package Client.Controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.ResponseBuilder;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;


public class LoginController {
    private static final Logger logger = LogManager.getLogger(LoginController.class);


    // ── FXML injections ───────────────────────────────────────────
    @FXML private TextField usernameField;
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
    private int udpPort;
    private Stage primaryStage;

    private int attempts = 3;

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
        logger.info("handleLogin called");

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

                Random random = new Random();
                int number = 100000 + random.nextInt(900000);

                try{
                    sendMail(new InternetAddress(parts[2]), "Code de vérification", String.valueOf(number));
                } catch (MessagingException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                TextInputDialog dialog = new TextInputDialog("");
                dialog.setTitle("Vérification");
                dialog.setHeaderText("Enter The verification code sent to your email for ");
                dialog.setContentText("Code de vérification:");

                Optional<String> result = dialog.showAndWait();
                result.ifPresent(qtyStr -> {
                    try {
                        int code = Integer.parseInt(qtyStr);
                        if (code <= 0) {
                            showError("Quantity must be a positive integer");
                            return;
                        }

                        if (code != number) {
                            showError("Wrong code.");
                            return;
                        }

                    } catch (NumberFormatException ex) {
                        showError("Invalid quantity input");
                    }
                });

                if (parts.length >= 2) {
                    String token = parts[0];
                    String role  = parts[1];
                    // userId not yet returned by LOGIN — default 0 until ORDER_HISTORY
                    AppState.setSession(token, username, role, 0);
                    attempts = 5;

                    loadMainWindow();
                } else {
                    loginButton.setDisable(false);
                    showError("Unexpected server response.");
                }

            } else {
                // ERR|message
                loginButton.setDisable(false);
                String err = ResponseBuilder.extractError(response);
                if(err.equals("Invalid username or password")){
                    attempts -= 1;
                    showError(err + ".Attempts left : " + attempts);
                    return;
                }
                showError(err);
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
            primaryStage.setScene(new Scene(root, 1100, 750));

        } catch (Exception e) {
            showError("Could not load register screen.");
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Forgot Password Flow
    // ──────────────────────────────────────────────────────────────

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
    private void handleForgotPassword() {
        TextInputDialog usernameDialog = new TextInputDialog("");
        usernameDialog.setTitle("Forgot Password");
        usernameDialog.setHeaderText("Reset your password");
        usernameDialog.setContentText("Enter your email:");

        Optional<String> emailOpt = usernameDialog.showAndWait();
        if (emailOpt.isPresent() && !emailOpt.get().trim().isEmpty()) {
            String email = emailOpt.get().trim();

            Task<String> task = new Task<>() {
                @Override
                protected String call() {
                    return socketClient.sendCommand("FORGOT_PASSWORD|" + email);
                }
            };

            task.setOnSucceeded(event -> {
                String response = task.getValue();
                if (ResponseBuilder.isOk(response)) {
                    showResetPasswordDialog(email);
                } else {
                    showError(ResponseBuilder.extractError(response));
                }
            });

            task.setOnFailed(event -> showError("Cannot reach server."));
            new Thread(task).start();
        }
    }

    private void showResetPasswordDialog(String email) {
        Dialog<String[]> resetDialog = new Dialog<>();
        resetDialog.setTitle("Enter OTP");
        resetDialog.setHeaderText("An OTP was sent to your email. Please enter it below.");

        ButtonType confirmButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        resetDialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField otpField = new TextField();
        otpField.setPromptText("OTP Code");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");

        grid.add(new Label("OTP:"), 0, 0);
        grid.add(otpField, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);

        resetDialog.getDialogPane().setContent(grid);
        Platform.runLater(otpField::requestFocus);

        resetDialog.setResultConverter(dialogButton -> {
            if (dialogButton == confirmButtonType) {
                return new String[]{otpField.getText(), newPasswordField.getText()};
            }
            return null;
        });

        Optional<String[]> result = resetDialog.showAndWait();
        result.ifPresent(data -> {
            String otp = data[0].trim();
            String newPass = data[1];

            if (otp.isEmpty() || newPass.isEmpty()) {
                showError("OTP and New Password cannot be empty.");
                return;
            }

            Task<String> resetTask = new Task<>() {
                @Override
                protected String call() {
                    return socketClient.sendCommand("RESET_PASSWORD|" + email + "|" + otp + "|" + newPass);
                }
            };

            resetTask.setOnSucceeded(e -> {
                String res = resetTask.getValue();
                if (ResponseBuilder.isOk(res)) {
                    showSuccessMessage("Password reset successfully. Please log in.");
                } else {
                    showError(ResponseBuilder.extractError(res));
                }
            });

            resetTask.setOnFailed(e -> showError("Cannot reach server."));
            new Thread(resetTask).start();
        });
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
                logger.error("Exception occurred", e);
            }
        });
    }

    private void sendMail(InternetAddress recepients, String subject, String body)
            throws IOException, AddressException, MessagingException {
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties, null);

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("chrionline@example.com", "NoReply"));
        msg.addRecipient(Message.RecipientType.TO, recepients);
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
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