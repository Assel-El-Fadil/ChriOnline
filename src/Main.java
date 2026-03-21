package chri.client;

import chri.client.controllers.LoginController;
import chri.client.network.NotificationCallback;
import chri.client.network.SocketClient;
import chri.client.network.UDPListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application implements NotificationCallback {

    // ─── Constants ────────────────────────────────────────────────
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int    SERVER_PORT  = 8084;       // TCP port
    private static final int    UDP_PORT     = 5002;       // UDP listen port

    // ─── Shared instances (passed to every controller) ────────────
    private SocketClient socketClient;
    private UDPListener  udpListener;
    private Thread       udpThread;

    // Reference to the main window controller (to show the banner)
    private MainWindowController mainWindowController;

    // ──────────────────────────────────────────────────────────────
    @Override
    public void start(Stage primaryStage) {

        // ── Step 1 : Create the TCP client ────────────────────────
        socketClient = new SocketClient(SERVER_HOST, SERVER_PORT);

        try {
            socketClient.connect();
        } catch (Exception e) {
            System.err.println("[Main] Cannot connect to server: " + e.getMessage());
            // Show error dialog and exit gracefully
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle("Connection Error");
            alert.setHeaderText("Cannot connect to server");
            alert.setContentText("Make sure the server is running on "
                    + SERVER_HOST + ":" + SERVER_PORT);
            alert.showAndWait();
            Platform.exit();
            return;
        }

        // ── Step 2 : Start UDPListener as a daemon thread ─────────
        // 'this' implements NotificationCallback — so when a UDP
        // packet arrives, handleNotification() below is called.
        udpListener = new UDPListener(UDP_PORT, this);

        udpThread = new Thread(udpListener);
        udpThread.setDaemon(true);                      // dies when app closes
        udpThread.setName("UDP-Client-Listener");
        udpThread.start();

        System.out.println("[Main] UDP listener started on port " + UDP_PORT);

        // ── Step 3 : Load the Login screen ────────────────────────
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml")
            );
            Parent root = loader.load();

            // Pass the shared SocketClient to LoginController
            LoginController loginController = loader.getController();
            loginController.setSocketClient(socketClient);
            loginController.setUdpPort(UDP_PORT);       // sent as params[2] in LOGIN
            loginController.setPrimaryStage(primaryStage);

            primaryStage.setTitle("ChriOnline — Login");
            primaryStage.setScene(new Scene(root, 400, 300));
            primaryStage.setResizable(false);

            // ── Step 4 : Clean shutdown on window close ────────────
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("[Main] Application closing...");

                // Stop the UDP listener gracefully
                if (udpListener != null) {
                    udpListener.stop();         // sets running=false + closes socket
                }

                // Disconnect TCP client
                if (socketClient != null) {
                    socketClient.disconnect();
                }

                Platform.exit();
                System.exit(0);
            });

            primaryStage.show();

        } catch (Exception e) {
            System.err.println("[Main] Failed to load login screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // NotificationCallback implementation
    // Called by UDPListener (background thread) when a UDP packet arrives.
    // MUST use Platform.runLater() — never touch JavaFX UI from this thread!
    // ──────────────────────────────────────────────────────────────
    @Override
    public void onOrderConfirmed(String refCode, String total) {
        System.out.println("[UDP] Order confirmed: " + refCode + " | total: " + total);

        Platform.runLater(() -> {
            // If the main window is open, tell its controller to show the banner
            if (mainWindowController != null) {
                mainWindowController.showNotificationBanner(
                    "Order " + refCode + " confirmed! Total: " + total + " MAD"
                );
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    // Called by LoginController after a successful login,
    // so Main knows which controller to forward UDP notifications to.
    // ──────────────────────────────────────────────────────────────
    public void setMainWindowController(MainWindowController controller) {
        this.mainWindowController = controller;
    }

    // ──────────────────────────────────────────────────────────────
    // Getters — controllers that need the shared client call these
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