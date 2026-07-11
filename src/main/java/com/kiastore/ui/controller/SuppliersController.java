package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.Supplier;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SuppliersController implements MainShellController.Searchable {

    @FXML private TableView<Supplier> suppliersTable;
    @FXML private TableColumn<Supplier, String> colName;
    @FXML private TableColumn<Supplier, String> colPhone;
    @FXML private TableColumn<Supplier, String> colAddress;
    @FXML private TableColumn<Supplier, String> colNotes;

    @FXML private Label formHeaderLabel;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private TextArea notesArea;

    @FXML private Button btnAddSupplier;
    @FXML private Button btnDelete;
    @FXML private Button btnSave;

    private final ObservableList<Supplier> observableSuppliersList = FXCollections.observableArrayList();
    private Supplier selectedSupplier;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));

        // Refresh list
        refreshTable(AppContext.get().supplierService.all());

        // Listen for selection changes
        suppliersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadSupplierDetails(newVal);
            }
        });

        // Initialize state
        onAddNewSupplier();

        // Apply role restrictions
        applyRoleRestrictions();
    }

    private void applyRoleRestrictions() {
        User u = Session.current();
        if (u != null) {
            boolean isAdmin = u.getRole() == Role.ADMIN;
            boolean canEdit = u.getRole() == Role.ADMIN || u.getRole() == Role.WAREHOUSE;

            btnDelete.setVisible(isAdmin);
            btnDelete.setManaged(isAdmin);

            btnSave.setVisible(canEdit);
            btnSave.setManaged(canEdit);
            btnAddSupplier.setVisible(canEdit);
            btnAddSupplier.setManaged(canEdit);
        }
    }

    private void refreshTable(List<Supplier> list) {
        observableSuppliersList.setAll(list);
        suppliersTable.setItems(observableSuppliersList);
    }

    @Override
    public void search(String query) {
        if (query == null || query.isBlank()) {
            refreshTable(AppContext.get().supplierService.all());
            return;
        }
        
        String term = query.toLowerCase().trim();
        List<Supplier> filtered = AppContext.get().supplierService.all().stream()
                .filter(s -> s.getName().toLowerCase().contains(term) 
                        || (s.getPhone() != null && s.getPhone().contains(term)))
                .collect(Collectors.toList());
        refreshTable(filtered);
    }

    private void loadSupplierDetails(Supplier s) {
        selectedSupplier = s;
        formHeaderLabel.setText("تعديل بيانات المورد");

        nameField.setText(s.getName());
        phoneField.setText(s.getPhone());
        addressField.setText(s.getAddress());
        notesArea.setText(s.getNotes());
    }

    @FXML
    private void onAddNewSupplier() {
        selectedSupplier = null;
        formHeaderLabel.setText("إضافة مورد جديد");

        nameField.clear();
        phoneField.clear();
        addressField.clear();
        notesArea.clear();

        suppliersTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSaveSupplier() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            new Alert(Alert.AlertType.ERROR, "اسم المورد مطلوب").showAndWait();
            return;
        }

        try {
            Supplier s = (selectedSupplier == null) ? new Supplier() : selectedSupplier;
            s.setName(nameField.getText().trim());
            s.setPhone(phoneField.getText() != null ? phoneField.getText().trim() : null);
            s.setAddress(addressField.getText() != null ? addressField.getText().trim() : null);
            s.setNotes(notesArea.getText() != null ? notesArea.getText().trim() : null);

            Supplier saved = AppContext.get().supplierService.save(s);
            User u = Session.current();

            // Log action
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                selectedSupplier == null ? "CREATE" : "UPDATE",
                "suppliers",
                saved.getId(),
                null,
                "Saved supplier: " + saved.getName()
            );

            new Alert(Alert.AlertType.INFORMATION, "تم حفظ بيانات المورد بنجاح").showAndWait();
            refreshTable(AppContext.get().supplierService.all());
            onAddNewSupplier();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء الحفظ: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onDeleteSupplier() {
        if (selectedSupplier == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText("هل أنت متأكد من رغبتك في حذف المورد؟");
        confirm.setContentText(selectedSupplier.getName());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean ok = AppContext.get().supplierService.delete(selectedSupplier.getId());
                if (ok) {
                    User u = Session.current();
                    AppContext.get().auditLogService.log(
                        u != null ? u.getId() : null,
                        u != null ? u.getUsername() : "system",
                        "DELETE",
                        "suppliers",
                        selectedSupplier.getId(),
                        null,
                        "Deleted supplier: " + selectedSupplier.getName()
                    );
                    new Alert(Alert.AlertType.INFORMATION, "تم حذف المورد بنجاح").showAndWait();
                    refreshTable(AppContext.get().supplierService.all());
                    onAddNewSupplier();
                } else {
                    new Alert(Alert.AlertType.ERROR, "فشل حذف المورد، قد يكون مرتبطاً بقطع غيار مسجلة").showAndWait();
                }
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "فشل حذف المورد: قد يكون مرتبطاً بقطع غيار مسجلة بالداخل").showAndWait();
            }
        }
    }

    @FXML
    private void onCancelEdit() {
        onAddNewSupplier();
    }
}
