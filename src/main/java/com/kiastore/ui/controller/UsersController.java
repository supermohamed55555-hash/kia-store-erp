package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import at.favre.lib.crypto.bcrypt.BCrypt;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UsersController implements MainShellController.Searchable {

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;

    @FXML private Label formHeaderLabel;
    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<Role> roleComboBox;

    @FXML private Button btnDelete;
    @FXML private Button btnSave;

    private final ObservableList<User> observableUsersList = FXCollections.observableArrayList();
    private User selectedUser;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));

        colRole.setCellValueFactory(cellData -> {
            Role role = cellData.getValue().getRole();
            String label = switch (role) {
                case ADMIN -> "المدير العام";
                case CASHIER -> "كاشير مبيعات";
                case WAREHOUSE -> "أمين المخزن";
            };
            return new SimpleStringProperty(label);
        });

        // Load roles combo
        roleComboBox.setItems(FXCollections.observableArrayList(Role.values()));
        roleComboBox.setValue(Role.CASHIER);

        // Load users
        refreshTable();

        // Listen for table selection
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadUserDetails(newVal);
            }
        });

        onAddNewUser();
    }

    private void refreshTable() {
        // Filter out soft deleted/inactive users
        List<User> activeUsers = AppContext.get().userService.all().stream()
                .filter(User::isActive)
                .collect(Collectors.toList());
        observableUsersList.setAll(activeUsers);
        usersTable.setItems(observableUsersList);
    }

    @Override
    public void search(String query) {
        if (query == null || query.isBlank()) {
            refreshTable();
            return;
        }

        String term = query.toLowerCase().trim();
        List<User> filtered = AppContext.get().userService.all().stream()
                .filter(User::isActive)
                .filter(u -> u.getName().toLowerCase().contains(term) || u.getUsername().toLowerCase().contains(term))
                .collect(Collectors.toList());

        observableUsersList.setAll(filtered);
        usersTable.setItems(observableUsersList);
    }

    private void loadUserDetails(User u) {
        selectedUser = u;
        formHeaderLabel.setText("تعديل حساب موظف");

        nameField.setText(u.getName());
        usernameField.setText(u.getUsername());
        roleComboBox.setValue(u.getRole());
        passwordField.clear(); // Leave empty unless resetting

        // Disable deleting own account
        User current = Session.current();
        boolean isSelf = current != null && current.getId() == u.getId();
        btnDelete.setDisable(isSelf);
    }

    @FXML
    private void onAddNewUser() {
        selectedUser = null;
        formHeaderLabel.setText("إضافة موظف جديد");

        nameField.clear();
        usernameField.clear();
        passwordField.clear();
        roleComboBox.setValue(Role.CASHIER);

        btnDelete.setDisable(true);
        usersTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSaveUser() {
        String name = nameField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        Role role = roleComboBox.getValue();

        if (name == null || name.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "الاسم الكامل مطلوب").showAndWait();
            return;
        }
        if (username == null || username.isBlank()) {
            new Alert(Alert.AlertType.ERROR, "اسم المستخدم مطلوب").showAndWait();
            return;
        }

        try {
            User u = (selectedUser == null) ? new User() : selectedUser;
            u.setName(name.trim());
            u.setUsername(username.trim().toLowerCase());
            u.setRole(role);

            // Handle password hashing if provided
            if (!password.isEmpty()) {
                // Hash with bcrypt
                String hashed = BCrypt.withDefaults().hashToString(10, password.toCharArray());
                u.setPasswordHash(hashed);
            } else if (selectedUser == null) {
                new Alert(Alert.AlertType.ERROR, "كلمة المرور مطلوبة للموظف الجديد").showAndWait();
                return;
            }

            User saved = AppContext.get().userService.save(u);
            User currentUser = Session.current();

            // Log action
            AppContext.get().auditLogService.log(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "system",
                selectedUser == null ? "CREATE" : "UPDATE",
                "users",
                saved.getId(),
                null,
                "Saved user account: " + saved.getUsername()
            );

            new Alert(Alert.AlertType.INFORMATION, "تم حفظ بيانات الموظف بنجاح").showAndWait();
            refreshTable();
            onAddNewUser();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء الحفظ: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onDeleteUser() {
        if (selectedUser == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تعطيل الحساب");
        confirm.setHeaderText("هل أنت متأكد من رغبتك في تعطيل حساب الموظف؟");
        confirm.setContentText(selectedUser.getName() + " (" + selectedUser.getUsername() + ")");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Soft delete
                selectedUser.setActive(false);
                AppContext.get().userService.save(selectedUser);

                User currentUser = Session.current();
                AppContext.get().auditLogService.log(
                    currentUser != null ? currentUser.getId() : null,
                    currentUser != null ? currentUser.getUsername() : "system",
                    "DELETE",
                    "users",
                    selectedUser.getId(),
                    null,
                    "Soft-deleted user account: " + selectedUser.getUsername()
                );

                new Alert(Alert.AlertType.INFORMATION, "تم تعطيل الحساب بنجاح").showAndWait();
                refreshTable();
                onAddNewUser();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "فشل تعطيل الحساب: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void onCancelEdit() {
        onAddNewUser();
    }
}
