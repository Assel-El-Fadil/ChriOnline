package Client.Controllers;

import Client.network.SocketClient;
import Client.session.AppState;
import Shared.DTO.OrderDTO;
import Shared.DTO.OrderItemDTO;
import Shared.ResponseBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;

public class InvoiceViewController {

    @FXML private Label lblInvoiceRef;
    @FXML private Label lblCustomerName;
    @FXML private Label lblCustomerEmail;
    @FXML private Label lblIssueDate;
    @FXML private Label lblPaymentMethod;
    @FXML private Label lblTotalAmount;

    @FXML private TableView<OrderItemDTO> itemsTable;
    @FXML private TableColumn<OrderItemDTO, String> colName;
    @FXML private TableColumn<OrderItemDTO, String> colQty;
    @FXML private TableColumn<OrderItemDTO, String> colPrice;
    @FXML private TableColumn<OrderItemDTO, String> colSubtotal;

    private SocketClient socketClient;
    private OrderDTO order;
    private final ObservableList<OrderItemDTO> itemsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().productName));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().quantity)));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f MAD", c.getValue().unitPrice)));
        colSubtotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f MAD", c.getValue().subtotal)));

        itemsTable.setItems(itemsList);
    }

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void setOrder(OrderDTO order) {
        this.order = order;
        loadOrderDetails();
    }

    private void loadOrderDetails() {
        if (order == null || socketClient == null) return;

        lblInvoiceRef.setText("REF: #" + order.referenceCode);
        lblIssueDate.setText("Date: " + order.createdAt);
        lblPaymentMethod.setText("Payment: " + order.paymentMethod);
        lblTotalAmount.setText(String.format("%.2f MAD", order.totalAmount));

        // Set customer info from AppState
        lblCustomerName.setText(AppState.getUsername() != null ? AppState.getUsername() : "Valued Customer");
        lblCustomerEmail.setText(""); // Remove Account ID as requested

        fetchItems();
    }

    private void fetchItems() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("GET_ORDER_ITEMS|" + AppState.getToken() + "|" + order.id);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                itemsList.clear();
                if (!payload.isBlank()) {
                    Arrays.stream(payload.split(";"))
                            .map(OrderItemDTO::fromProtocolString)
                            .forEach(itemsList::add);
                }
            }
        });

        new Thread(task).start();
    }

    @FXML
    private void handleDownload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Invoice PDF");
        fileChooser.setInitialFileName("Facture_" + order.referenceCode + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = fileChooser.showSaveDialog(lblInvoiceRef.getScene().getWindow());
        if (file != null) {
            try {
                Client.util.PdfGenerator.generateInvoice(file, order, itemsList, AppState.getUsername());
                System.out.println("PDF Invoice generated: " + file.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                // Optionally show an alert to the user here
            }
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) lblInvoiceRef.getScene().getWindow()).close();
    }
}
