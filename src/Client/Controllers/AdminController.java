package Client.Controllers;

import Client.session.AppState;
import Client.network.SocketClient;
import Shared.*;
import Shared.DTO.OrderDTO;
import Shared.DTO.ProductDTO;
import Shared.DTO.UserDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class AdminController {

    // ── FXML injections ──────────────────────────────────────────

    // Products tab
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TableColumn<ProductDTO, String> colProductId;
    @FXML private TableColumn<ProductDTO, String> colProductName;
    @FXML private TableColumn<ProductDTO, String> colProductCategory;
    @FXML private TableColumn<ProductDTO, String> colProductPrice;
    @FXML private TableColumn<ProductDTO, String> colProductStock;
    @FXML private Button btnAddProduct;
    @FXML private Button btnEditProduct;
    @FXML private Button btnDeleteProduct;
    @FXML private Button btnRefreshProducts;
    @FXML private Label  lblProductsStatus;

    // Orders tab
    @FXML private TableView<OrderDTO> ordersTable;
    @FXML private TableColumn<OrderDTO, String> colOrderId;
    @FXML private TableColumn<OrderDTO, String> colOrderRef;
    @FXML private TableColumn<OrderDTO, String> colOrderUserId;
    @FXML private TableColumn<OrderDTO, String> colOrderStatus;
    @FXML private TableColumn<OrderDTO, String> colOrderTotal;
    @FXML private TableColumn<OrderDTO, String> colOrderDate;
    @FXML private Button btnUpdateStatus;
    @FXML private Button btnRefreshOrders;
    @FXML private Label  lblOrdersStatus;

    // Users tab
    @FXML private TableView<UserDTO> usersTable;
    @FXML private TableColumn<UserDTO, String> colUserId;
    @FXML private TableColumn<UserDTO, String> colUserUsername;
    @FXML private TableColumn<UserDTO, String> colUserFullName;
    @FXML private TableColumn<UserDTO, String> colUserEmail;
    @FXML private TableColumn<UserDTO, String> colUserRole;
    @FXML private TableColumn<UserDTO, String> colUserActive;
    @FXML private Button btnDeleteUser;
    @FXML private Button btnRefreshUsers;
    @FXML private Label lblUsersStatus;

    // ── Internal state ────────────────────────────────────────────
    private SocketClient socketClient;

    private final ObservableList<ProductDTO> productList = FXCollections.observableArrayList();
    private final ObservableList<OrderDTO> orderList = FXCollections.observableArrayList();
    private final ObservableList<UserDTO> userList = FXCollections.observableArrayList();

    // Valid order statuses for the update dialog
    private static final List<String> ORDER_STATUSES =
            List.of("PENDING", "VALIDATED", "SHIPPED", "DELIVERED", "CANCELLED");

    // Valid product categories matching the DB ENUM
    private static final List<String> CATEGORIES = List.of(
            "ELECTRONIQUES", "VETEMENTS", "ELECTROMENAGER",
            "BEAUTE_ET_COSMETIQUES", "JEUX_VIDEO", "SANTE", "FITNESS");


    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    // ────────────────────────────────────────────────────────────
    //  Initialization — called by FXMLLoader after FXML is loaded
    // ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupProductsTable();
        setupOrdersTable();
        setupUsersTable();
        wireButtons();

        // Load all three tabs immediately on open
        loadProducts();
        loadOrders();
        loadUsers();
    }

    // ────────────────────────────────────────────────────────────
    //  Table setup
    // ────────────────────────────────────────────────────────────

    private void setupProductsTable() {
        colProductId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().id)));
        colProductName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colProductCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category));
        colProductPrice.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f MAD", c.getValue().price)));
        colProductStock.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().stock)));

        // Colour low-stock rows orange, zero-stock rows red
        productsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ProductDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.stock == 0) {
                    setStyle("-fx-background-color: #FADBD8;"); // red tint
                } else if (item.stock < 5) {
                    setStyle("-fx-background-color: #FDEBD0;"); // orange tint
                } else {
                    setStyle("");
                }
            }
        });

        productsTable.setItems(productList);
        productsTable.setPlaceholder(new Label("No products found"));
    }

    private void setupOrdersTable() {
        colOrderId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().id)));
        colOrderRef.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().referenceCode));
        colOrderUserId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().userId)));
        colOrderStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        colOrderTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f MAD", c.getValue().totalAmount)));
        colOrderDate.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().createdAt)));

        // Colour-code order rows by status
        ordersTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(OrderDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                switch (item.status) {
                    case "PENDING" -> setStyle("-fx-background-color: #FEF9E7;");
                    case "VALIDATED" -> setStyle("-fx-background-color: #EAF2FB;");
                    case "SHIPPED" -> setStyle("-fx-background-color: #D1F2EB;");
                    case "DELIVERED" -> setStyle("-fx-background-color: #D5F5E3;");
                    case "CANCELLED" -> setStyle("-fx-background-color: #FADBD8;");
                    default -> setStyle("");
                }
            }
        });

        ordersTable.setItems(orderList);
        ordersTable.setPlaceholder(new Label("No orders found"));
    }

    private void setupUsersTable() {
        colUserId      .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().id)));
        colUserUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        colUserFullName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colUserEmail   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email));
        colUserRole    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        colUserActive  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().active == 1 ? "Active" : "Deactivated"));

        // Highlight deactivated accounts
        usersTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(UserDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.active == 0) {
                    setStyle("-fx-background-color: #FADBD8; -fx-text-fill: #922B21;");
                } else if ("ADMIN".equals(item.role)) {
                    setStyle("-fx-background-color: #D6E4F0;");
                } else {
                    setStyle("");
                }
            }
        });

        usersTable.setItems(userList);
        usersTable.setPlaceholder(new Label("No users found"));
    }

    // ────────────────────────────────────────────────────────────
    //  Button wiring
    // ────────────────────────────────────────────────────────────

    private void wireButtons() {
        btnRefreshProducts.setOnAction(e -> loadProducts());
        btnRefreshOrders  .setOnAction(e -> loadOrders());
        btnRefreshUsers   .setOnAction(e -> loadUsers());

        btnAddProduct   .setOnAction(e -> showAddProductDialog());
        btnEditProduct  .setOnAction(e -> showEditProductDialog());
        btnDeleteProduct.setOnAction(e -> handleDeleteProduct());

        btnUpdateStatus.setOnAction(e -> showUpdateStatusDialog());

        btnDeleteUser.setOnAction(e -> handleDeleteUser());

        // Enable Edit/Delete product only when a row is selected
        btnEditProduct  .disableProperty().bind(
                productsTable.getSelectionModel().selectedItemProperty().isNull());
        btnDeleteProduct.disableProperty().bind(
                productsTable.getSelectionModel().selectedItemProperty().isNull());

        // Enable Update Status only when an order is selected
        btnUpdateStatus.disableProperty().bind(
                ordersTable.getSelectionModel().selectedItemProperty().isNull());

        // Enable Delete User only when a user is selected
        btnDeleteUser.disableProperty().bind(
                usersTable.getSelectionModel().selectedItemProperty().isNull());
    }

    // ────────────────────────────────────────────────────────────
    //  Products — network calls
    // ────────────────────────────────────────────────────────────

    private void loadProducts() {
        setStatus(lblProductsStatus, "Loading products...", false);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("GET_PRODUCTS|");
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                productList.clear();
                if (!payload.isBlank()) {
                    Arrays.stream(payload.split(";"))
                            .map(ProductDTO::fromProtocolString)
                            .forEach(productList::add);
                }
                setStatus(lblProductsStatus,
                        productList.size() + " product(s) loaded", false);
            } else {
                setStatus(lblProductsStatus,
                        ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> setStatus(lblProductsStatus,
                "Network error — could not load products", true));

        new Thread(task).start();
    }

    private void showAddProductDialog() {
        // Build the dialog form
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New Product");
        dialog.setHeaderText("Fill in the product details");
        dialog.getDialogPane().getButtonTypes()
                .addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField    tfName        = new TextField();
        ComboBox<String> cbCategory = new ComboBox<>(
                FXCollections.observableArrayList(CATEGORIES));
        TextField    tfPrice       = new TextField();
        TextField    tfStock       = new TextField();
        TextArea     taDescription = new TextArea();
        taDescription.setPrefRowCount(3);
        taDescription.setWrapText(true);

        cbCategory.getSelectionModel().selectFirst();

        grid.add(new Label("Name:"),        0, 0); grid.add(tfName,        1, 0);
        grid.add(new Label("Category:"),    0, 1); grid.add(cbCategory,    1, 1);
        grid.add(new Label("Price (MAD):"), 0, 2); grid.add(tfPrice,       1, 2);
        grid.add(new Label("Stock:"),       0, 3); grid.add(tfStock,       1, 3);
        grid.add(new Label("Description:"), 0, 4); grid.add(taDescription, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        String name        = tfName.getText().trim();
        String category    = cbCategory.getValue();
        String priceStr    = tfPrice.getText().trim();
        String stockStr    = tfStock.getText().trim();
        String description = taDescription.getText().trim();

        if (name.isBlank() || priceStr.isBlank() || stockStr.isBlank()) {
            showError("Name, price, and stock are required.");
            return;
        }

        // Build the command
        // ADMIN_ADD_PRODUCT|token|name|category|price|stock|description
        String command = "ADMIN_ADD_PRODUCT|" + AppState.getToken()
                + "|" + name
                + "|" + category
                + "|" + priceStr
                + "|" + stockStr
                + "|" + description;

        runAdminCommand(command, lblProductsStatus,
                "Product added successfully", this::loadProducts);
    }

    private void showEditProductDialog() {
        ProductDTO selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Let the admin pick which field to edit
        List<String> editableFields = List.of("name", "category", "price", "stock", "description");

        ChoiceDialog<String> fieldDialog = new ChoiceDialog<>("name", editableFields);
        fieldDialog.setTitle("Edit Product");
        fieldDialog.setHeaderText("Product: " + selected.name);
        fieldDialog.setContentText("Select field to edit:");

        Optional<String> fieldResult = fieldDialog.showAndWait();
        if (fieldResult.isEmpty()) return;

        String field = fieldResult.get();

        // For category, show a ComboBox; for others, a plain text field
        String newValue;
        if ("category".equals(field)) {
            ChoiceDialog<String> catDialog = new ChoiceDialog<>(selected.category, CATEGORIES);
            catDialog.setTitle("Edit Category");
            catDialog.setHeaderText("Select new category");
            Optional<String> catResult = catDialog.showAndWait();
            if (catResult.isEmpty()) return;
            newValue = catResult.get();
        } else {
            String currentValue = switch (field) {
                case "name"        -> selected.name;
                case "price"       -> String.valueOf(selected.price);
                case "stock"       -> String.valueOf(selected.stock);
                case "description" -> selected.description != null ? selected.description : "";
                default            -> "";
            };

            TextInputDialog valueDialog = new TextInputDialog(currentValue);
            valueDialog.setTitle("Edit " + field);
            valueDialog.setHeaderText("Product: " + selected.name);
            valueDialog.setContentText("New value for " + field + ":");

            Optional<String> valueResult = valueDialog.showAndWait();
            if (valueResult.isEmpty() || valueResult.get().isBlank()) return;
            newValue = valueResult.get().trim();
        }

        // ADMIN_EDIT_PRODUCT|token|productId|field|value
        String command = "ADMIN_EDIT_PRODUCT|" + AppState.getToken()
                + "|" + selected.id
                + "|" + field
                + "|" + newValue;

        runAdminCommand(command, lblProductsStatus,
                "Product updated successfully", this::loadProducts);
    }

    private void handleDeleteProduct() {
        ProductDTO selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Product");
        confirm.setHeaderText("Delete \"" + selected.name + "\"?");
        confirm.setContentText(
                "This will hide the product from the catalogue.\n"
                        + "It cannot be deleted if it is in an active cart.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // ADMIN_DELETE_PRODUCT|token|productId
        String command = "ADMIN_DELETE_PRODUCT|" + AppState.getToken()
                + "|" + selected.id;

        runAdminCommand(command, lblProductsStatus,
                "Product deleted successfully", this::loadProducts);
    }

    // ────────────────────────────────────────────────────────────
    //  Orders — network calls
    // ────────────────────────────────────────────────────────────

    private void loadOrders() {
        setStatus(lblOrdersStatus, "Loading orders...", false);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("ADMIN_LIST_ORDERS|" + AppState.getToken());
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
                setStatus(lblOrdersStatus,
                        orderList.size() + " order(s) loaded", false);
            } else {
                setStatus(lblOrdersStatus,
                        ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> setStatus(lblOrdersStatus,
                "Network error — could not load orders", true));

        new Thread(task).start();
    }

    private void showUpdateStatusDialog() {
        OrderDTO selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                selected.status, ORDER_STATUSES);
        dialog.setTitle("Update Order Status");
        dialog.setHeaderText("Order ref: " + selected.referenceCode);
        dialog.setContentText("Select new status:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().equals(selected.status)) return;

        // ADMIN_UPDATE_STATUS|token|orderId|newStatus
        String command = "ADMIN_UPDATE_STATUS|" + AppState.getToken()
                + "|" + selected.id
                + "|" + result.get();

        runAdminCommand(command, lblOrdersStatus,
                "Order status updated", this::loadOrders);
    }

    // ────────────────────────────────────────────────────────────
    //  Users — network calls
    // ────────────────────────────────────────────────────────────

    private void loadUsers() {
        setStatus(lblUsersStatus, "Loading users...", false);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand("ADMIN_LIST_USERS|" + AppState.getToken());
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                String payload = ResponseBuilder.extractPayload(response);
                userList.clear();
                if (!payload.isBlank()) {
                    Arrays.stream(payload.split(";"))
                            .map(UserDTO::fromProtocolString)
                            .forEach(userList::add);
                }
                setStatus(lblUsersStatus,
                        userList.size() + " user(s) loaded", false);
            } else {
                setStatus(lblUsersStatus,
                        ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> setStatus(lblUsersStatus,
                "Network error — could not load users", true));

        new Thread(task).start();
    }

    private void handleDeleteUser() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Prevent deleting the currently logged-in admin
        if (selected.username.equals(AppState.getUsername())) {
            showError("You cannot delete your own account.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete user \"" + selected.username + "\"?");
        confirm.setContentText(
                "Users with order history will be deactivated, not removed.\n"
                        + "Users with no orders will be permanently deleted.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // ADMIN_DELETE_USER|token|userId
        String command = "ADMIN_DELETE_USER|" + AppState.getToken()
                + "|" + selected.id;

        runAdminCommand(command, lblUsersStatus,
                "User deleted successfully", this::loadUsers);
    }

    // ────────────────────────────────────────────────────────────
    //  Shared helpers
    // ────────────────────────────────────────────────────────────

    private void runAdminCommand(String command, Label statusLabel,
                                 String successMessage, Runnable onSuccess) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return socketClient.sendCommand(command);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (ResponseBuilder.isOk(response)) {
                setStatus(statusLabel, successMessage, false);
                onSuccess.run();  // refresh the table
            } else {
                setStatus(statusLabel,
                        ResponseBuilder.extractError(response), true);
            }
        });

        task.setOnFailed(e -> setStatus(statusLabel,
                "Network error — command failed", true));

        new Thread(task).start();
    }

    private void setStatus(Label label, String message, boolean isError) {
        Platform.runLater(() -> {
            label.setText(message);
            label.setStyle(isError
                    ? "-fx-text-fill: #922B21; -fx-font-weight: bold;"
                    : "-fx-text-fill: #1E6B3A;");
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}