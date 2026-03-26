package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.UserDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileController {

    // ── FXML injections ──────────────────────────────────────────
    @FXML private Label     avatarLabel;
    @FXML private Label     fullNameLabel;
    @FXML private Label     roleLabel;
    @FXML private Label     statusLabel;
    @FXML private javafx.scene.image.ImageView avatarImageView;

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField addressField;

    @FXML private Button    saveButton;

    // ── Dependencies ─────────────────────────────────────────────
    private SocketClient socketClient;
    private Stage        primaryStage;

    // Snapshot of original values before editing — used to detect changes
    private final Map<String, String> originalValues = new LinkedHashMap<>();

    // ── Setters ──────────────────────────────────────────────────

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
        loadProfile();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    // ────────────────────────────────────────────────────────────
    //  Load profile from server
    // ────────────────────────────────────────────────────────────

    private void loadProfile() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }
                return socketClient.sendCommand("GET_PROFILE|" + AppState.getToken());
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                UserDTO user = UserDTO.fromProtocolString(payload);
                populateFields(user);
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> showStatus("Network error — could not load profile", true));

        new Thread(task).start();
    }

    private void populateFields(UserDTO user) {
        Platform.runLater(() -> {
            // Avatar logic — use image if present, otherwise initial
            if (user.profilePhoto != null && !user.profilePhoto.isBlank()) {
                Image img = Client.util.ProductImageHelper.loadLocalImage(user.profilePhoto);
                if (img != null) {
                    avatarImageView.setImage(img);
                    avatarLabel.setVisible(false);
                } else {
                    setInitialAvatar(user);
                }
            } else {
                setInitialAvatar(user);
            }

            fullNameLabel.setText(user.getFullName());
            roleLabel.setText(user.role != null ? user.role : "USER");

            firstNameField.setText(user.firstName != null ? user.firstName : "");
            lastNameField.setText(user.lastName != null ? user.lastName : "");
            usernameField.setText(user.username != null ? user.username : "");
            emailField.setText(user.email != null ? user.email : "");
            addressField.setText(user.address != null ? user.address : "");

            // Snapshot original values
            snapshotOriginals();
        });
    }

    private void setInitialAvatar(UserDTO user) {
        String initial = (user.firstName != null && !user.firstName.isEmpty())
                ? user.firstName.substring(0, 1).toUpperCase()
                : "?";
        avatarLabel.setText(initial);
        avatarLabel.setVisible(true);
        avatarImageView.setImage(null);
    }

    private void snapshotOriginals() {
        originalValues.clear();
        originalValues.put("firstName", firstNameField.getText());
        originalValues.put("lastName", lastNameField.getText());
        originalValues.put("email", emailField.getText());
        originalValues.put("address", addressField.getText());
    }

    // ────────────────────────────────────────────────────────────
    //  Photo Upload / Save
    // ────────────────────────────────────────────────────────────

    @FXML
    private void handleChangePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                // Ensure directory exists
                File destDir = new File("src/Client/assets/images/profiles");
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }

                // Create a unique filename based on the current time
                String ext = "";
                String name = selectedFile.getName();
                int i = name.lastIndexOf('.');
                if (i > 0) {
                    ext = name.substring(i);
                }
                String destName = "user_" + System.currentTimeMillis() + ext;
                File destFile = new File(destDir, destName);

                Files.copy(selectedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Quick preview update on client side
                Image img = new Image(destFile.toURI().toString());
                avatarImageView.setImage(img);
                avatarLabel.setVisible(false);

                // Automatically save it to the server
                savePhotoToServer("src/Client/assets/images/profiles/" + destName);

            } catch (Exception e) {
                e.printStackTrace();
                showStatus("Error copying photo locally", true);
            }
        }
    }

    private void savePhotoToServer(String dbPath) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }
                return socketClient.sendCommand("EDIT_PROFILE|" + AppState.getToken()
                        + "|profilePhoto|" + dbPath);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                showStatus("Profile photo updated successfully!", false);
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
        });

        new Thread(task).start();
    }

    @FXML
    private void handleSave() {
        // Collect changed fields
        Map<String, String> changes = new LinkedHashMap<>();

        checkChange(changes, "firstName", firstNameField.getText().trim());
        checkChange(changes, "lastName", lastNameField.getText().trim());
        checkChange(changes, "email", emailField.getText().trim());
        checkChange(changes, "address", addressField.getText().trim());

        if (changes.isEmpty()) {
            showStatus("No changes to save", false);
            return;
        }

        // Disable button while saving
        saveButton.setDisable(true);
        showStatus("Saving...", false);

        // Send each changed field as a separate EDIT_PROFILE command
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (!socketClient.isConnected()) {
                    socketClient.reconnect();
                }

                for (Map.Entry<String, String> entry : changes.entrySet()) {
                    String cmd = "EDIT_PROFILE|" + AppState.getToken()
                            + "|" + entry.getKey()
                            + "|" + entry.getValue();

                    String response = socketClient.sendCommand(cmd);
                    if (!ResponseBuilder.isOk(response)) {
                        return response; // Return first error
                    }
                }
                return "OK";
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if ("OK".equals(response) || ResponseBuilder.isOk(response)) {
                showStatus("Profile updated successfully!", false);
                snapshotOriginals(); // Update snapshot to new values
                // Refresh header display
                fullNameLabel.setText(firstNameField.getText() + " " + lastNameField.getText());
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
            saveButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            showStatus("Network error — could not save", true);
            saveButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private void checkChange(Map<String, String> changes, String field, String newValue) {
        String original = originalValues.getOrDefault(field, "");
        if (!newValue.equals(original)) {
            changes.put(field, newValue);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Navigation
    // ────────────────────────────────────────────────────────────

    @FXML
    private void handleBack() {
        if (socketClient == null || primaryStage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/catalog.fxml"));
            Parent root = loader.load();
            CatalogController cc = loader.getController();
            cc.setSocketClient(socketClient);
            cc.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        if (socketClient != null && socketClient.isConnected()) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    socketClient.sendCommand("LOGOUT|" + AppState.getToken());
                    return null;
                }
            };
            new Thread(task).start();
        }
        
        AppState.clear();
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/login.fxml"));
            Parent root = loader.load();
            Client.Controllers.LoginController lc = loader.getController();
            lc.setSocketClient(socketClient);
            lc.setUdpPort(8085);
            lc.setPrimaryStage(primaryStage);

            primaryStage.setTitle("ChriOnline");
            primaryStage.setScene(new Scene(root, 400, 300));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Failed to load login screen.", true);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  UI Helpers
    // ────────────────────────────────────────────────────────────

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isError
                    ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                    : "-fx-text-fill: #10B981; -fx-font-weight: bold;");
            statusLabel.setVisible(true);
        });
    }

    private void hideStatus() {
        Platform.runLater(() -> {
            statusLabel.setVisible(false);
            statusLabel.setText("");
        });
    }
}
