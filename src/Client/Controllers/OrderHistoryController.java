package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.OrderDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;

public class OrderHistoryController {

    @FXML private TableView<OrderDTO> ordersTable;
    @FXML private TableColumn<OrderDTO, String> colRef;
    @FXML private TableColumn<OrderDTO, String> colDate;
    @FXML private TableColumn<OrderDTO, String> colTotal;
    @FXML private TableColumn<OrderDTO, String> colStatus;
    @FXML private TableColumn<OrderDTO, Void> colActions;
    @FXML private Label statusLabel;

    private SocketClient socketClient;
    private Stage primaryStage;
    private final ObservableList<OrderDTO> orderList = FXCollections.observableArrayList();

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
        loadOrders();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    public void initialize() {
        setupTable();
    }

    private void setupTable() {
        colRef.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().referenceCode));
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt != null ? c.getValue().createdAt.toString() : "—"));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f MAD", c.getValue().totalAmount)));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));

        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnTrack = new Button("Track Progress");
            {
                btnTrack.getStyleClass().add("btn-primary");
                btnTrack.setPrefWidth(140);
                btnTrack.setOnAction(event -> {
                    OrderDTO order = getTableView().getItems().get(getIndex());
                    openOrderProgress(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnTrack);
                }
            }
        });

        ordersTable.setItems(orderList);
        ordersTable.setPlaceholder(new Label("No orders found."));
    }

    private void loadOrders() {
        if (socketClient == null) return;
        
        showStatus("Loading orders...", false);
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("ORDER_HISTORY|" + AppState.getToken());
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                orderList.clear();
                if (!payload.isBlank()) {
                    Arrays.stream(payload.split(";"))
                            .map(OrderDTO::fromProtocolString)
                            .forEach(orderList::add);
                }
                showStatus(orderList.size() + " order(s) loaded.", false);
            } else {
                showStatus(ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> showStatus("Network error — could not load orders", true));

        new Thread(task).start();
    }

    @FXML
    private void handleRefresh() {
        loadOrders();
    }

    @FXML
    private void handleBackToCatalog() {
        if (socketClient == null || primaryStage == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/catalog.fxml"));
            Parent root = loader.load();
            CatalogController catalogController = loader.getController();
            catalogController.setSocketClient(socketClient);
            catalogController.setPrimaryStage(primaryStage);
            primaryStage.setTitle("ChriOnline");
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openOrderProgress(OrderDTO order) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UI/orderStatusProgress.fxml"));
            Parent root = loader.load();
            
            OrderStatusProgressController controller = loader.getController();
            controller.setSocketClient(socketClient);
            controller.setOrderId(order.id);
            controller.setOnBack(() -> {
                primaryStage.setScene(ordersTable.getScene());
                primaryStage.setTitle("ChriOnline — Order History");
            });

            primaryStage.setTitle("ChriOnline — Track Order #" + order.referenceCode);
            primaryStage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Could not open progress page.", true);
        }
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isError ? "-fx-text-fill: #DC2626;" : "-fx-text-fill: #374151;");
        });
    }
}
