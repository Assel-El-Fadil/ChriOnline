import Client.network.NotificationCallback;
import Client.network.SocketClient;
import Client.network.UDPListener;
import Client.Controllers.LoginController;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Optional;
import java.util.Properties;
import javafx.scene.control.TextInputDialog;

public class Main extends Application implements NotificationCallback {

    private static String SERVER_HOST = "127.0.0.1";
    private static final int    SERVER_PORT  = 8084;    // TCP port
    private static final int    UDP_PORT     = 8085;    // UDP listen port
    private static final String CONFIG_FILE = "server_config.properties";

    private SocketClient socketClient;
    private UDPListener  udpListener;
    private Thread       udpThread;
    private LoginController loginController;

    private void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            SERVER_HOST = props.getProperty("server.host", "127.0.0.1");
            System.out.println("[Main] Loaded server host from config: " + SERVER_HOST);
        } catch (IOException e) {
            System.out.println("[Main] No config file found, using default: " + SERVER_HOST);
            saveConfig();
        }
    }

    private void saveConfig() {
        Properties props = new Properties();
        props.setProperty("server.host", SERVER_HOST);
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "ChriOnline Server Configuration");
        } catch (IOException e) {
            System.err.println("[Main] Could not save config file: " + e.getMessage());
        }
    }

    private boolean tryConnect() {
        socketClient = new SocketClient(SERVER_HOST, SERVER_PORT);
        try {
            socketClient.connect();
            System.out.println("[Main] Connected to server " + SERVER_HOST + ":" + SERVER_PORT);
            return true;
        } catch (Exception e) {
            System.err.println("[Main] Connection failed to " + SERVER_HOST);
            return false;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        loadConfig();

        if (!tryConnect()) {

            TextInputDialog dialog = new TextInputDialog(SERVER_HOST);
            dialog.setTitle("Server Connection");
            dialog.setHeaderText("Cannot connect to server at " + SERVER_HOST);
            dialog.setContentText("Please enter the Server IP address (LAN):");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                SERVER_HOST = result.get().trim();
                if (!tryConnect()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Still cannot connect");
                    alert.setContentText("Could not connect to: " + SERVER_HOST);
                    alert.showAndWait();
                    Platform.exit();
                    return;
                }
                saveConfig();
            } else {
                Platform.exit();
                return;
            }
        }

        try {
            udpListener = new UDPListener(UDP_PORT, this);
        } catch (SocketException e) {
            System.err.println("[Main] Cannot bind UDP port " + UDP_PORT + ": " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("UDP Error");
            alert.setHeaderText("Cannot open UDP port " + UDP_PORT);
            alert.setContentText("Port may already be in use. Restart the application.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        udpThread = new Thread(udpListener);
        udpThread.setDaemon(true);
        udpThread.setName("UDP-Client-Listener");
        udpThread.start();
        System.out.println("[Main] UDP listener started on port " + UDP_PORT);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/login.fxml"));
            Parent root = loader.load();
            loginController = loader.getController();
            loginController.setSocketClient(socketClient);
            loginController.setUdpPort(UDP_PORT);
            loginController.setPrimaryStage(primaryStage);

            primaryStage.setScene(new Scene(root, 1100, 750));
            primaryStage.setTitle("ChriOnline");
            primaryStage.show();

            System.out.println("[Main] Login screen loaded successfully");
        } catch (Exception e) {
            System.err.println("[Main] Failed to load login screen: " + e.getMessage());
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("UI Error");
            alert.setHeaderText("Cannot load login screen");
            alert.setContentText("Failed to load login.fxml: " + e.getMessage());
            alert.showAndWait();

            if (udpListener != null) udpListener.stop();
            if (socketClient != null) socketClient.disconnect();
            Platform.exit();
            return;
        }

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[Main] Application closing...");

            if (udpListener != null) {
                udpListener.stop();
            }
            if (socketClient != null) {
                socketClient.disconnect();
            }

            Platform.exit();
            System.exit(0);
        });

        primaryStage.setTitle("ChriOnline");
        primaryStage.show();
    }

    @Override
    public void onOrderConfirmed(String refCode, String total) {
        System.out.println("[UDP] Order confirmed — ref: " + refCode + " | total: " + total);

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Order Confirmed");
            alert.setHeaderText("Success! Your order has been placed.");
            alert.setContentText("Order Reference: " + refCode + "\nTotal Amount: " + total + " MAD");
            alert.show();
        });
    }

    public SocketClient getSocketClient() {
        return socketClient;
    }

    public int getUdpPort() {
        return UDP_PORT;
    }

    public static void main(String[] args) {
        launch(args);
    }
}