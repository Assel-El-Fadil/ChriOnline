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

import java.net.SocketException;

public class Main extends Application implements NotificationCallback {

    // ─── Constants ────────────────────────────────────────────────
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int    SERVER_PORT  = 8084;    // TCP port
    private static final int    UDP_PORT     = 8085;    // UDP listen port

    // ─── Shared instances ─────────────────────────────────────────
    private SocketClient socketClient;
    private UDPListener  udpListener;
    private Thread       udpThread;
    private LoginController loginController;

    // ──────────────────────────────────────────────────────────────
    @Override
    public void start(Stage primaryStage) {

        // ── Step 1 : Create and connect the TCP client ────────────
        socketClient = new SocketClient(SERVER_HOST, SERVER_PORT);

        try {
            socketClient.connect();
            System.out.println("[Main] Connected to server " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (Exception e) {
            System.err.println("[Main] Cannot connect to server: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Error");
            alert.setHeaderText("Cannot connect to server");
            alert.setContentText("Make sure the server is running on "
                    + SERVER_HOST + ":" + SERVER_PORT);
            alert.showAndWait();
            Platform.exit();
            return;
        }

        // ── Step 2 : Start UDPListener as a daemon thread ─────────
        // UDPListener constructor throws SocketException — must be caught
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
        udpThread.setDaemon(true);                   // dies automatically when app closes
        udpThread.setName("UDP-Client-Listener");
        udpThread.start();
        System.out.println("[Main] UDP listener started on port " + UDP_PORT);

        // ── Step 3 : Load the Login screen ────────────────────────
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/login.fxml"));
            Parent root = loader.load();
            loginController = loader.getController();
            loginController.setSocketClient(socketClient);
            loginController.setUdpPort(UDP_PORT);
            loginController.setPrimaryStage(primaryStage);

            primaryStage.setScene(new Scene(root, 550, 500));
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
            
            // Clean up before exit
            if (udpListener != null) udpListener.stop();
            if (socketClient != null) socketClient.disconnect();
            Platform.exit();
            return;
        }

        // ── Step 4 : Clean shutdown on window close ────────────────
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[Main] Application closing...");

            if (udpListener != null) {
                udpListener.stop();          // sets running=false + closes socket
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

    // ──────────────────────────────────────────────────────────────
    // NotificationCallback — called by UDPListener background thread
    // when ORDER_CONFIRMED packet arrives.
    // MUST use Platform.runLater() before touching any JavaFX UI.
    // ──────────────────────────────────────────────────────────────
    @Override
    public void onOrderConfirmed(String refCode, String total) {
        System.out.println("[UDP] Order confirmed — ref: " + refCode + " | total: " + total);

        Platform.runLater(() -> {
            // TODO (M3-18): forward to MainWindowController to show the banner
            // if (mainWindowController != null) {
            //     mainWindowController.showNotificationBanner(
            //         "Order " + refCode + " confirmed! Total: " + total + " MAD"
            //     );
            // }
            System.out.println("[Main][UI thread] Banner would show: Order "
                    + refCode + " confirmed! Total: " + total + " MAD");
        });
    }

    // ──────────────────────────────────────────────────────────────
    // Getters — used by controllers that need the shared SocketClient
    // ──────────────────────────────────────────────────────────────
    public SocketClient getSocketClient() {
        return socketClient;
    }

    public int getUdpPort() {
        return UDP_PORT;
    }

    // ──────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        launch(args);
    }
}