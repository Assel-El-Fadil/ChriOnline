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
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
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
    @FXML private Button btnDeactivateUser;
    @FXML private Button btnActivateUser;
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
        // Load all three tabs immediately after socketClient is injected
        loadProducts();
        loadOrders();
        loadUsers();
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

        btnDeactivateUser.setOnAction(e -> handleDeactivateUser());
        btnActivateUser.setOnAction(e -> handleActivateUser());
        btnDeleteUser.setOnAction(e -> handleDeleteUser());

        // Enable Edit/Delete product only when a row is selected
        btnEditProduct  .disableProperty().bind(
                productsTable.getSelectionModel().selectedItemProperty().isNull());
        btnDeleteProduct.disableProperty().bind(
                productsTable.getSelectionModel().selectedItemProperty().isNull());

        // Enable Update Status only when an order is selected
        btnUpdateStatus.disableProperty().bind(
                ordersTable.getSelectionModel().selectedItemProperty().isNull());

        // Enable Delete/Deactivate/Activate User only when a user is selected
        btnDeactivateUser.disableProperty().bind(
                usersTable.getSelectionModel().selectedItemProperty().isNull());
        btnActivateUser.disableProperty().bind(
                usersTable.getSelectionModel().selectedItemProperty().isNull());
        btnDeleteUser.disableProperty().bind(
                usersTable.getSelectionModel().selectedItemProperty().isNull());

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.active == 0) {
                    btnDeactivateUser.setVisible(false);  btnDeactivateUser.setManaged(false);
                    btnActivateUser.setVisible(true);     btnActivateUser.setManaged(true);
                } else {
                    btnDeactivateUser.setVisible(true);   btnDeactivateUser.setManaged(true);
                    btnActivateUser.setVisible(false);    btnActivateUser.setManaged(false);
                }
            } else {
                btnDeactivateUser.setVisible(true);   btnDeactivateUser.setManaged(true);
                btnActivateUser.setVisible(false);    btnActivateUser.setManaged(false);
            }
        });
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

        Button btnPhoto = new Button("Select Photo...");
        Label lblPhotoPath = new Label("No photo selected");
        final String[] selectedPhotoPath = {null};

        btnPhoto.setOnAction(ev -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Product Photo");
            fileChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            java.io.File selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null) {
                try {
                    java.io.File destDir = new java.io.File("src/Client/assets/images/products");
                    if (!destDir.exists()) destDir.mkdirs();
                    String ext = "";
                    String n = selectedFile.getName();
                    int i = n.lastIndexOf('.');
                    if (i > 0) ext = n.substring(i);
                    String destName = "prod_" + System.currentTimeMillis() + ext;
                    java.io.File destFile = new java.io.File(destDir, destName);
                    java.nio.file.Files.copy(selectedFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    selectedPhotoPath[0] = "assets/images/products/" + destName;
                    lblPhotoPath.setText(destName);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Error copying photo locally");
                }
            }
        });

        javafx.scene.layout.HBox photoBox = new javafx.scene.layout.HBox(10, btnPhoto, lblPhotoPath);
        photoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        cbCategory.getSelectionModel().selectFirst();

        grid.add(new Label("Name:"),        0, 0); grid.add(tfName,        1, 0);
        grid.add(new Label("Category:"),    0, 1); grid.add(cbCategory,    1, 1);
        grid.add(new Label("Price (MAD):"), 0, 2); grid.add(tfPrice,       1, 2);
        grid.add(new Label("Stock:"),       0, 3); grid.add(tfStock,       1, 3);
        grid.add(new Label("Description:"), 0, 4); grid.add(taDescription, 1, 4);
        grid.add(new Label("Photo:"),       0, 5); grid.add(photoBox,      1, 5);

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
        // ADMIN_ADD_PRODUCT|token|name|category|price|stock|description|photoPath
        String photoPathStr = selectedPhotoPath[0] != null ? selectedPhotoPath[0] : "";
        String command = "ADMIN_ADD_PRODUCT|" + AppState.getToken()
                + "|" + name
                + "|" + category
                + "|" + priceStr
                + "|" + stockStr
                + "|" + description
                + "|" + photoPathStr;

        runAdminCommand(command, lblProductsStatus,
                "Product added successfully", this::loadProducts);
    }

    private void showEditProductDialog() {
        ProductDTO selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Product");
        dialog.setHeaderText("Edit details for: " + selected.name);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField    tfName        = new TextField(selected.name);
        ComboBox<String> cbCategory = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        if (CATEGORIES.contains(selected.category)) cbCategory.setValue(selected.category);
        else cbCategory.getSelectionModel().selectFirst();
        
        TextField    tfPrice       = new TextField(String.valueOf(selected.price));
        TextField    tfStock       = new TextField(String.valueOf(selected.stock));
        TextArea     taDescription = new TextArea(selected.description != null ? selected.description : "");
        taDescription.setPrefRowCount(3);
        taDescription.setWrapText(true);

        Button btnPhoto = new Button("Change Photo...");
        Label lblPhotoPath = new Label(selected.imagePath != null ? selected.imagePath : "No photo");
        final String[] selectedPhotoPath = {selected.imagePath};

        btnPhoto.setOnAction(ev -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Select Product Photo");
            fileChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            java.io.File selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null) {
                try {
                    java.io.File destDir = new java.io.File("src/Client/assets/images/products");
                    if (!destDir.exists()) destDir.mkdirs();
                    String ext = "";
                    String n = selectedFile.getName();
                    int i = n.lastIndexOf('.');
                    if (i > 0) ext = n.substring(i);
                    String destName = "prod_" + System.currentTimeMillis() + ext;
                    java.io.File destFile = new java.io.File(destDir, destName);
                    java.nio.file.Files.copy(selectedFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    selectedPhotoPath[0] = "assets/images/products/" + destName;
                    lblPhotoPath.setText(destName);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Error copying photo locally");
                }
            }
        });

        javafx.scene.layout.HBox photoBox = new javafx.scene.layout.HBox(10, btnPhoto, lblPhotoPath);
        photoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        grid.add(new Label("Name:"),        0, 0); grid.add(tfName,        1, 0);
        grid.add(new Label("Category:"),    0, 1); grid.add(cbCategory,    1, 1);
        grid.add(new Label("Price (MAD):"), 0, 2); grid.add(tfPrice,       1, 2);
        grid.add(new Label("Stock:"),       0, 3); grid.add(tfStock,       1, 3);
        grid.add(new Label("Description:"), 0, 4); grid.add(taDescription, 1, 4);
        grid.add(new Label("Photo:"),       0, 5); grid.add(photoBox,      1, 5);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        java.util.Map<String, String> changes = new java.util.LinkedHashMap<>();
        
        String newName = tfName.getText().trim();
        String newCat = cbCategory.getValue();
        String newPrice = tfPrice.getText().trim();
        String newStock = tfStock.getText().trim();
        String newDesc = taDescription.getText().trim();
        String newPhoto = selectedPhotoPath[0];

        if (!newName.equals(selected.name)) changes.put("name", newName);
        if (!newCat.equals(selected.category)) changes.put("category", newCat);
        if (!newPrice.equals(String.valueOf(selected.price))) changes.put("price", newPrice);
        if (!newStock.equals(String.valueOf(selected.stock))) changes.put("stock", newStock);
        if (!newDesc.equals(selected.description != null ? selected.description : "")) changes.put("description", newDesc);
        if (newPhoto != null && !newPhoto.equals(selected.imagePath)) changes.put("imagePath", newPhoto);

        if (changes.isEmpty()) {
            setStatus(lblProductsStatus, "No changes to save", false);
            return;
        }

        setStatus(lblProductsStatus, "Saving...", false);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                for (java.util.Map.Entry<String, String> entry : changes.entrySet()) {
                    String cmd = "ADMIN_EDIT_PRODUCT|" + AppState.getToken()
                            + "|" + selected.id
                            + "|" + entry.getKey()
                            + "|" + entry.getValue();
                    String response = socketClient.sendCommand(cmd);
                    if (!ResponseBuilder.isOk(response)) return response;
                }
                return "OK";
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if ("OK".equals(response) || ResponseBuilder.isOk(response)) {
                setStatus(lblProductsStatus, "Product updated successfully", false);
                loadProducts();
            } else {
                setStatus(lblProductsStatus, ResponseBuilder.extractError(response), true);
            }
        });
        task.setOnFailed(e -> setStatus(lblProductsStatus, "Network error during edit", true));
        new Thread(task).start();
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
                            .filter(u -> !"ADMIN".equals(u.role))
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
        confirm.setHeaderText("Delete user \"" + selected.username + "\" permanently?");
        confirm.setContentText(
                "This action cannot be undone.\n"
                        + "This will fail if the user has any order history.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // ADMIN_HARD_DELETE_USER|token|userId
        String command = "ADMIN_HARD_DELETE_USER|" + AppState.getToken()
                + "|" + selected.id;

        runAdminCommand(command, lblUsersStatus,
                "User deleted successfully", this::loadUsers);
    }

    private void handleDeactivateUser() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Prevent deactivating the currently logged-in admin
        if (selected.username.equals(AppState.getUsername())) {
            showError("You cannot deactivate your own account.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate User");
        confirm.setHeaderText("Deactivate user \"" + selected.username + "\"?");
        confirm.setContentText("This will prevent the user from logging in.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // ADMIN_DEACTIVATE_USER|token|userId
        String command = "ADMIN_DEACTIVATE_USER|" + AppState.getToken()
                + "|" + selected.id;

        runAdminCommand(command, lblUsersStatus,
                "User deactivated successfully", this::loadUsers);
    }

    private void handleActivateUser() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.username.equals(AppState.getUsername())) {
            showError("You cannot activate your own account from here.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Activate User");
        confirm.setHeaderText("Activate user \"" + selected.username + "\"?");
        confirm.setContentText("This will restore the user's ability to log in.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // ADMIN_ACTIVATE_USER|token|userId
        String command = "ADMIN_ACTIVATE_USER|" + AppState.getToken()
                + "|" + selected.id;

        runAdminCommand(command, lblUsersStatus,
                "User activated successfully", this::loadUsers);
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
            label.setStyle(isError ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                    : "-fx-text-fill: #10B981; -fx-font-weight: bold;");
        });
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
            Stage stage = (Stage) btnRefreshUsers.getScene().getWindow();
            lc.setPrimaryStage(stage);

            stage.setTitle("ChriOnline");
            stage.setScene(new Scene(root, 1100, 750));
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load login screen.");
        }
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