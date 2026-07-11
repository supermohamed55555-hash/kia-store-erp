package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.model.AuditLog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogController implements MainShellController.Searchable {

    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, Integer> colId;
    @FXML private TableColumn<AuditLog, String> colUser;
    @FXML private TableColumn<AuditLog, String> colAction;
    @FXML private TableColumn<AuditLog, String> colTable;
    @FXML private TableColumn<AuditLog, Integer> colRecordId;
    @FXML private TableColumn<AuditLog, String> colIp;
    @FXML private TableColumn<AuditLog, String> colDate;

    @FXML private ComboBox<String> actionFilterComboBox;
    @FXML private TextField searchField;

    @FXML private TextArea oldDataArea;
    @FXML private TextArea newDataArea;

    private final ObservableList<AuditLog> observableLogsList = FXCollections.observableArrayList();
    private List<AuditLog> allLogs;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colTable.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        colRecordId.setCellValueFactory(new PropertyValueFactory<>("recordId"));
        colIp.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm:ss a", new java.util.Locale("ar"));
        colDate.setCellValueFactory(cellData -> {
            AuditLog log = cellData.getValue();
            if (log.getCreatedAt() != null) {
                return new SimpleStringProperty(log.getCreatedAt().format(dtf));
            }
            return new SimpleStringProperty("");
        });

        // Initialize Filter Dropdown
        actionFilterComboBox.setItems(FXCollections.observableArrayList(
            "الكل", "CREATE", "UPDATE", "DELETE", "LOGIN", "RECEIVE", "RETURN", "INVOICE_CREATE", "INVOICE_CANCEL"
        ));
        actionFilterComboBox.setValue("الكل");

        // Load data
        refreshLogs();

        // Listen for table selection
        auditTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                oldDataArea.setText(newVal.getOldData() != null ? newVal.getOldData() : "- لا يوجد بيانات سابقة -");
                newDataArea.setText(newVal.getNewData() != null ? newVal.getNewData() : "- لا يوجد بيانات تفصيلية -");
            } else {
                oldDataArea.clear();
                newDataArea.clear();
            }
        });
    }

    private void refreshLogs() {
        allLogs = AppContext.get().auditLogService.all();
        // Sort descending by date
        allLogs.sort((l1, l2) -> {
            if (l1.getCreatedAt() == null || l2.getCreatedAt() == null) return 0;
            return l2.getCreatedAt().compareTo(l1.getCreatedAt());
        });
        applyFilters();
    }

    private void applyFilters() {
        String filter = actionFilterComboBox.getValue();
        String search = searchField.getText();

        List<AuditLog> filtered = allLogs.stream()
                .filter(log -> {
                    // Action filter
                    if (filter != null && !filter.equals("الكل")) {
                        if (!log.getAction().equalsIgnoreCase(filter)) return false;
                    }
                    // Search text filter
                    if (search != null && !search.isBlank()) {
                        String term = search.toLowerCase().trim();
                        boolean matchUser = log.getUserName() != null && log.getUserName().toLowerCase().contains(term);
                        boolean matchTable = log.getTableName() != null && log.getTableName().toLowerCase().contains(term);
                        boolean matchAction = log.getAction() != null && log.getAction().toLowerCase().contains(term);
                        return matchUser || matchTable || matchAction;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        observableLogsList.setAll(filtered);
        auditTable.setItems(observableLogsList);
    }

    @Override
    public void search(String query) {
        searchField.setText(query);
        applyFilters();
    }

    @FXML
    private void onFilterChanged() {
        applyFilters();
    }

    @FXML
    private void onSearchKeyReleased() {
        applyFilters();
    }
}
