package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.OrderDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

public class OrderStatusProgressController {

    @FXML private Label lblOrderRef;
    @FXML private Label lblOrderDate;
    @FXML private Label lblStatusText;
    @FXML private Label lblTotalAmount;
    @FXML private Label lblCancelledStatus;

    @FXML private Circle step1Circle; // PENDING
    @FXML private Circle step2Circle; // VALIDATED
    @FXML private Circle step3Circle; // SHIPPED
    @FXML private Circle step4Circle; // DELIVERED

    @FXML private Line line1;
    @FXML private Line line2;
    @FXML private Line line3;

    private SocketClient socketClient;
    private int orderId;
    private Runnable onBack;

    private static final String COLOR_COMPLETED = "#3B82F6"; // Blue
    private static final String COLOR_INACTIVE = "#E5E7EB";  // Gray

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
        refreshStatus();
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML
    private void handleRefresh() {
        refreshStatus();
    }

    @FXML
    private void handleBack() {
        if (onBack != null) {
            onBack.run();
        }
    }

    private void refreshStatus() {
        if (socketClient == null || orderId <= 0) return;

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("GET_ORDER_STATUS|" + AppState.getToken() + "|" + orderId);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                OrderDTO order = OrderDTO.fromProtocolString(payload);
                updateUI(order);
            }
        });

        new Thread(task).start();
    }

    private void updateUI(OrderDTO order) {
        Platform.runLater(() -> {
            lblOrderRef.setText("Order #" + order.referenceCode);
            lblTotalAmount.setText(String.format("%.2f MAD", order.totalAmount));
            lblOrderDate.setText("Placed on " + order.createdAt);

            String status = order.status.toUpperCase();
            resetProgress();

            if ("CANCELLED".equals(status)) {
                lblCancelledStatus.setVisible(true);
                lblCancelledStatus.setManaged(true);
                lblStatusText.setText("This order has been cancelled.");
                return;
            }

            lblCancelledStatus.setVisible(false);
            lblCancelledStatus.setManaged(false);

            // Step 1: PENDING (Always completed if order exists)
            markStepCompleted(step1Circle, null);
            lblStatusText.setText("Your order is being processed.");

            if ("VALIDATED".equals(status) || "SHIPPED".equals(status) || "DELIVERED".equals(status)) {
                markStepCompleted(step2Circle, line1);
                lblStatusText.setText("Your order has been validated.");
            }
            if ("SHIPPED".equals(status) || "DELIVERED".equals(status)) {
                markStepCompleted(step3Circle, line2);
                lblStatusText.setText("Your order is on its way!");
            }
            if ("DELIVERED".equals(status)) {
                markStepCompleted(step4Circle, line3);
                lblStatusText.setText("Your order has been delivered. Thank you!");
            }
        });
    }

    private void resetProgress() {
        step1Circle.setFill(Color.web(COLOR_INACTIVE));
        step2Circle.setFill(Color.web(COLOR_INACTIVE));
        step3Circle.setFill(Color.web(COLOR_INACTIVE));
        step4Circle.setFill(Color.web(COLOR_INACTIVE));
        line1.setStroke(Color.web(COLOR_INACTIVE));
        line2.setStroke(Color.web(COLOR_INACTIVE));
        line3.setStroke(Color.web(COLOR_INACTIVE));
    }

    private void markStepCompleted(Circle circle, Line leadingLine) {
        circle.setFill(Color.web(COLOR_COMPLETED));
        if (leadingLine != null) {
            leadingLine.setStroke(Color.web(COLOR_COMPLETED));
        }
    }
}