package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.Batch;
import com.kiastore.model.Part;
import com.kiastore.model.Supplier;
import com.kiastore.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReceivingController implements MainShellController.Searchable {

    @FXML private TableView<Batch> batchesTable;
    @FXML private TableColumn<Batch, String> colPart;
    @FXML private TableColumn<Batch, String> colSupplier;
    @FXML private TableColumn<Batch, Integer> colQty;
    @FXML private TableColumn<Batch, Double> colPrice;
    @FXML private TableColumn<Batch, String> colInvoice;
    @FXML private TableColumn<Batch, String> colDate;

    // Form inputs
    @FXML private TextField partSearchField;
    @FXML private ComboBox<Part> partComboBox;
    @FXML private ComboBox<Supplier> supplierComboBox;
    @FXML private TextField quantityField;
    @FXML private TextField purchasePriceField;
    @FXML private TextField invoiceNumberField;
    @FXML private TextArea notesArea;
    @FXML private Button btnSave;

    private final ObservableList<Batch> observableBatchesList = FXCollections.observableArrayList();
    private final ObservableList<Part> observablePartsList = FXCollections.observableArrayList();
    private final ObservableList<Supplier> observableSuppliersList = FXCollections.observableArrayList();

    private final Map<Integer, Part> partsCache = new HashMap<>();
    private final Map<Integer, Supplier> suppliersCache = new HashMap<>();

    @FXML
    public void initialize() {
        // Table Columns Binding
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        colInvoice.setCellValueFactory(new PropertyValueFactory<>("purchaseInvoiceNumber"));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("hh:mm a | yyyy/MM/dd", new java.util.Locale("ar"));
        colDate.setCellValueFactory(cellData -> {
            Batch b = cellData.getValue();
            if (b.getReceivedAt() != null) {
                return new SimpleStringProperty(b.getReceivedAt().format(dtf));
            }
            return new SimpleStringProperty("");
        });

        colPart.setCellValueFactory(cellData -> {
            Batch b = cellData.getValue();
            Part p = partsCache.get(b.getPartId());
            return new SimpleStringProperty(p != null ? p.getFullName() : "صنف مجهول");
        });

        colSupplier.setCellValueFactory(cellData -> {
            Batch b = cellData.getValue();
            Supplier s = suppliersCache.get(b.getSupplierId());
            return new SimpleStringProperty(s != null ? s.getName() : "مورد مجهول");
        });

        // Load caches & refresh views
        loadCaches();
        refreshLists();

        // Check user session
        User u = Session.current();
        if (u != null) {
            boolean canEdit = u.getRole() == com.kiastore.model.Role.ADMIN || u.getRole() == com.kiastore.model.Role.WAREHOUSE;
            btnSave.setVisible(canEdit);
            btnSave.setManaged(canEdit);
        }
    }

    private void loadCaches() {
        partsCache.clear();
        for (Part p : AppContext.get().partService.all()) {
            partsCache.put(p.getId(), p);
        }

        suppliersCache.clear();
        for (Supplier s : AppContext.get().supplierService.all()) {
            suppliersCache.put(s.getId(), s);
        }
    }

    public void selectPartDirect(Part p) {
        partComboBox.setValue(p);
    }

    private void refreshLists() {
        loadCaches();

        // 1. TableView list (recent batches)
        List<Batch> allBatches = AppContext.get().batchDao.findAll();
        // Sort by received_at descending
        allBatches.sort((b1, b2) -> {
            if (b1.getReceivedAt() == null || b2.getReceivedAt() == null) return 0;
            return b2.getReceivedAt().compareTo(b1.getReceivedAt());
        });
        observableBatchesList.setAll(allBatches);
        batchesTable.setItems(observableBatchesList);

        // 2. Combo boxes
        observablePartsList.setAll(partsCache.values().stream().filter(Part::isActive).collect(Collectors.toList()));
        partComboBox.setItems(observablePartsList);

        observableSuppliersList.setAll(suppliersCache.values());
        supplierComboBox.setItems(observableSuppliersList);
    }

    @Override
    public void search(String query) {
        if (query == null || query.isBlank()) {
            refreshLists();
            return;
        }

        String term = query.toLowerCase().trim();
        List<Batch> filtered = AppContext.get().batchDao.findAll().stream()
                .filter(b -> {
                    Part p = partsCache.get(b.getPartId());
                    Supplier s = suppliersCache.get(b.getSupplierId());
                    return (p != null && p.getFullName().toLowerCase().contains(term))
                            || (s != null && s.getName().toLowerCase().contains(term))
                            || (b.getPurchaseInvoiceNumber() != null && b.getPurchaseInvoiceNumber().toLowerCase().contains(term));
                })
                .collect(Collectors.toList());

        observableBatchesList.setAll(filtered);
        batchesTable.setItems(observableBatchesList);
    }

    @FXML
    private void onPartSearchKeyReleased() {
        String filter = partSearchField.getText();
        if (filter == null || filter.isBlank()) {
            partComboBox.setItems(observablePartsList);
            partComboBox.show();
            return;
        }

        String normFilter = filter.toLowerCase();
        ObservableList<Part> filteredParts = FXCollections.observableArrayList(
            observablePartsList.stream()
                .filter(p -> p.getFullName().toLowerCase().contains(normFilter) || p.getInternalCode().toLowerCase().contains(normFilter))
                .collect(Collectors.toList())
        );

        partComboBox.setItems(filteredParts);
        partComboBox.show();
    }

    @FXML
    private void onReceiveStock() {
        Part selectedPart = partComboBox.getValue();
        Supplier selectedSupplier = supplierComboBox.getValue();

        if (selectedPart == null) {
            new Alert(Alert.AlertType.ERROR, "يرجى تحديد الصنف").showAndWait();
            return;
        }
        if (selectedSupplier == null) {
            new Alert(Alert.AlertType.ERROR, "يرجى تحديد المورد").showAndWait();
            return;
        }

        try {
            int qty = Integer.parseInt(quantityField.getText());
            double price = Double.parseDouble(purchasePriceField.getText());

            if (qty <= 0) {
                new Alert(Alert.AlertType.ERROR, "الكمية يجب أن تكون أكبر من صفر").showAndWait();
                return;
            }
            if (price < 0) {
                new Alert(Alert.AlertType.ERROR, "سعر الشراء لا يمكن أن يكون سالباً").showAndWait();
                return;
            }

            Batch batch = new Batch();
            batch.setPartId(selectedPart.getId());
            batch.setSupplierId(selectedSupplier.getId());
            batch.setQuantity(qty);
            batch.setPurchasePrice(price);
            batch.setPurchaseInvoiceNumber(invoiceNumberField.getText());
            batch.setNotes(notesArea.getText());
            
            User u = Session.current();
            if (u != null) batch.setReceivedBy(u.getId());

            // Process Stock Ingestion transaction
            AppContext.get().stockService.receiveStock(batch);

            // Audit Log
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                "RECEIVE",
                "batches",
                batch.getId(),
                null,
                "Received " + qty + " units of " + selectedPart.getFullName() + " from " + selectedSupplier.getName()
            );

            new Alert(Alert.AlertType.INFORMATION, "تم استلام الشحنة وتحديث المخزون بنجاح").showAndWait();
            refreshLists();
            onClearForm();

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "يرجى التأكد من كتابة قيم رقمية صحيحة في الكمية وسعر الشراء").showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "فشل عملية استلام الشحنة: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onClearForm() {
        partSearchField.clear();
        partComboBox.setValue(null);
        partComboBox.setItems(observablePartsList);
        supplierComboBox.setValue(null);
        quantityField.clear();
        purchasePriceField.clear();
        invoiceNumberField.clear();
        notesArea.clear();
    }
}
