package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.Batch;
import com.kiastore.model.Part;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.Optional;

public class PartsController implements MainShellController.Searchable {

    @FXML private StackPane partsRootStack;
    @FXML private TableView<Part> partsTable;
    @FXML private TableColumn<Part, String> colInternalCode;
    @FXML private TableColumn<Part, String> colFullName;
    @FXML private TableColumn<Part, Double> colPrice;
    @FXML private TableColumn<Part, Integer> colStock;
    @FXML private TableColumn<Part, Integer> colMinStock;

    @FXML private Label formHeaderLabel;
    @FXML private Label lowStockAlertBadge;
    @FXML private Label generatedNamePreview;

    // Form inputs
    @FXML private TextField partTypeField;
    @FXML private TextField locationField;
    @FXML private TextField carNameField;
    @FXML private TextField carModelField;
    @FXML private TextField manufacturerField;
    
    @FXML private TextField partNumberField;
    @FXML private TextField internalCodeField;
    @FXML private TextField barcodeField;
    @FXML private TextField salePriceField;
    @FXML private TextField purchasePriceField;
    @FXML private TextField minStockField;
    @FXML private TextField currentStockField;

    @FXML private TextArea compatibleCarsArea;
    @FXML private TextArea alternativesArea;
    @FXML private TextArea descriptionArea;

    // Quick info panel
    @FXML private VBox quickInfoVBox;
    @FXML private Label infoLocationLabel;
    @FXML private Label infoPurchasePriceLabel;
    @FXML private Label infoProfitLabel;

    @FXML private Button btnAddPart;
    @FXML private Button btnDelete;
    @FXML private Button btnSave;

    private final ObservableList<Part> observablePartsList = FXCollections.observableArrayList();
    private Part selectedPart;
    private PartDetailPanel detailPanel;

    @FXML
    public void initialize() {
        // Table Columns Binding
        colInternalCode.setCellValueFactory(new PropertyValueFactory<>("internalCode"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("salePrice"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colMinStock.setCellValueFactory(new PropertyValueFactory<>("minStock"));

        // Highlight low stock rows in the table
        partsTable.setRowFactory(tv -> new TableRow<Part>() {
            @Override
            protected void updateItem(Part item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isLowStock()) {
                    setStyle("-fx-background-color: rgba(239, 68, 68, 0.08);");
                } else {
                    setStyle("");
                }
            }
        });

        // Instantiate programmatic side panel
        detailPanel = new PartDetailPanel(partsRootStack, this);

        // Load data
        refreshTable(AppContext.get().partService.all());

        // Listen for table selection
        partsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPartDetails(newVal);
                detailPanel.show(newVal);
            }
        });

        // Initialize state
        onAddNewPart();

        // Apply role boundaries
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
            btnAddPart.setVisible(canEdit);
            btnAddPart.setManaged(canEdit);
        }
    }

    private void refreshTable(List<Part> list) {
        observablePartsList.setAll(list);
        partsTable.setItems(observablePartsList);
    }

    @Override
    public void search(String query) {
        List<Part> matched = AppContext.get().partService.searchSmart(query);
        refreshTable(matched);
    }

    /**
     * Filters the table to show only parts where current_stock <= min_stock.
     * Called when the user clicks the low-stock notification popup.
     */
    public void filterLowStockOnly() {
        List<Part> lowStock = AppContext.get().partService.lowStock();
        refreshTable(lowStock);
        if (lowStockAlertBadge != null) {
            lowStockAlertBadge.setText("يتم عرض الأصناف منخفضة المخزون فقط (" + lowStock.size() + ")");
            lowStockAlertBadge.setVisible(true);
        }
    }

    /**
     * Highlights and opens detail panel for the given part.
     * Called after a successful barcode scan from the global search bar.
     */
    public void openPartDetail(Part part) {
        // Reload full list first so the part is visible in the table
        refreshTable(AppContext.get().partService.all());
        partsTable.getSelectionModel().clearSelection();
        // Select the matching row
        for (Part p : observablePartsList) {
            if (p.getId() == part.getId()) {
                partsTable.getSelectionModel().select(p);
                partsTable.scrollTo(p);
                detailPanel.show(p);
                break;
            }
        }
    }

    private void loadPartDetails(Part p) {
        selectedPart = p;
        formHeaderLabel.setText("تعديل الصنف");

        // Structured Name fields
        partTypeField.setText(p.getPartType());
        locationField.setText(p.getLocation());
        carNameField.setText(p.getCarName());
        carModelField.setText(p.getCarModel());
        manufacturerField.setText(p.getManufacturer());
        generatedNamePreview.setText(p.getFullName());

        // Codes & Details
        partNumberField.setText(p.getPartNumber());
        internalCodeField.setText(p.getInternalCode());
        barcodeField.setText(p.getBarcode());
        salePriceField.setText(String.valueOf(p.getSalePrice()));
        purchasePriceField.setText(String.valueOf(p.getPurchasePrice()));
        minStockField.setText(String.valueOf(p.getMinStock()));
        currentStockField.setText(String.valueOf(p.getCurrentStock()));

        compatibleCarsArea.setText(p.getCompatibleCars());
        alternativesArea.setText(p.getAlternatives());
        descriptionArea.setText(p.getDescription());

        // Low stock alert badge
        boolean isLow = p.isLowStock();
        lowStockAlertBadge.setVisible(isLow);
        lowStockAlertBadge.setManaged(isLow);

        // Display quick info panel details
        infoLocationLabel.setText(p.getLocation() != null && !p.getLocation().isBlank() ? p.getLocation() : "غير محدد");
        
        // Retrieve last batch purchase price or use part purchase price
        List<Batch> batches = AppContext.get().stockService.getBatchesForPart(p.getId());
        double lastCost = p.getPurchasePrice();
        if (!batches.isEmpty()) {
            lastCost = batches.get(0).getPurchasePrice();
        }
        double profit = p.getSalePrice() - lastCost;
        infoPurchasePriceLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", lastCost));
        infoProfitLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", profit));
        
        quickInfoVBox.setVisible(true);
        quickInfoVBox.setManaged(true);
    }

    @FXML
    private void onAddNewPart() {
        selectedPart = null;
        formHeaderLabel.setText("إضافة صنف جديد");
        lowStockAlertBadge.setVisible(false);
        lowStockAlertBadge.setManaged(false);

        // Reset inputs
        partTypeField.clear();
        locationField.clear();
        carNameField.clear();
        carModelField.clear();
        manufacturerField.clear();
        generatedNamePreview.setText("-");

        partNumberField.clear();
        internalCodeField.clear();
        barcodeField.clear();
        salePriceField.clear();
        purchasePriceField.clear();
        minStockField.setText("5");
        currentStockField.setText("0");

        compatibleCarsArea.clear();
        alternativesArea.clear();
        descriptionArea.clear();

        quickInfoVBox.setVisible(false);
        quickInfoVBox.setManaged(false);
        
        partsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onFormFieldsChanged() {
        StringBuilder sb = new StringBuilder();
        if (partTypeField.getText() != null) sb.append(partTypeField.getText().trim()).append(" ");
        if (locationField.getText() != null) sb.append(locationField.getText().trim()).append(" ");
        if (carNameField.getText() != null) sb.append(carNameField.getText().trim()).append(" ");
        if (carModelField.getText() != null) sb.append(carModelField.getText().trim()).append(" ");
        if (manufacturerField.getText() != null) sb.append(manufacturerField.getText().trim());

        String res = sb.toString().trim();
        generatedNamePreview.setText(res.isEmpty() ? "-" : res);
    }

    @FXML
    private void onSavePart() {
        try {
            // Build the Part object from inputs
            Part p = (selectedPart == null) ? new Part() : selectedPart;
            p.setPartType(partTypeField.getText());
            p.setLocation(locationField.getText());
            p.setCarName(carNameField.getText());
            p.setCarModel(carModelField.getText());
            p.setManufacturer(manufacturerField.getText());
            
            p.setPartNumber(partNumberField.getText());
            p.setInternalCode(internalCodeField.getText());
            p.setBarcode(barcodeField.getText());

            double price = salePriceField.getText().isBlank() ? 0.0 : Double.parseDouble(salePriceField.getText());
            double purchasePrice = purchasePriceField.getText().isBlank() ? 0.0 : Double.parseDouble(purchasePriceField.getText());
            int minStock = minStockField.getText().isBlank() ? 5 : Integer.parseInt(minStockField.getText());
            int currentStock = currentStockField.getText().isBlank() ? 0 : Integer.parseInt(currentStockField.getText());

            p.setSalePrice(price);
            p.setPurchasePrice(purchasePrice);
            p.setMinStock(minStock);
            p.setCurrentStock(currentStock);

            p.setCompatibleCars(compatibleCarsArea.getText());
            p.setAlternatives(alternativesArea.getText());
            p.setDescription(descriptionArea.getText());

            User u = Session.current();
            if (u != null) p.setCreatedBy(u.getId());

            // 1. DUPLICATE PREVENTION: Look for similar parts before creating a new one
            if (selectedPart == null) {
                List<Part> matches = AppContext.get().stockService.findSimilarParts(p);
                if (!matches.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("هذا الصنف قد يكون موجوداً بالفعل في النظام:\n\n");
                    for (Part match : matches) {
                        sb.append("- ").append(match.getFullName())
                          .append(" (كود: ").append(match.getInternalCode()).append(")\n");
                    }
                    sb.append("\nهل تريد إضافة هذا الصنف كصنف جديد بالرغم من التشابه؟");

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("تنبيه بتكرار محتمل");
                    confirm.setHeaderText("صنف مشابه متوفر بالفعل!");
                    confirm.setContentText(sb.toString());
                    
                    // Arabic buttons
                    ButtonType btnNew = new ButtonType("نعم، أضف صنف جديد");
                    ButtonType btnCancel = new ButtonType("إلغاء وتعديل", ButtonBar.ButtonData.CANCEL_CLOSE);
                    confirm.getButtonTypes().setAll(btnNew, btnCancel);

                    Optional<ButtonType> opt = confirm.showAndWait();
                    if (opt.isEmpty() || opt.get() == btnCancel) {
                        return; // Cancel creation
                    }
                }
            }

            // 2. Save
            Part saved = AppContext.get().partService.save(p);
            
            // Log audit trail
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                selectedPart == null ? "CREATE" : "UPDATE",
                "parts",
                saved.getId(),
                null,
                "Saved part: " + saved.getFullName()
            );

            new Alert(Alert.AlertType.INFORMATION, "تم حفظ الصنف بنجاح").showAndWait();
            
            // Refresh table and reset form
            refreshTable(AppContext.get().partService.all());
            onAddNewPart();

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "الرجاء التأكد من صحة الحقول الرقمية (السعر، المخزون، الحد الأدنى)").showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء حفظ البيانات: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onDeletePart() {
        if (selectedPart == null) return;
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText("هل أنت متأكد من رغبتك في حذف الصنف؟");
        confirm.setContentText(selectedPart.getFullName());
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Soft delete
                selectedPart.setActive(false);
                AppContext.get().partService.save(selectedPart);

                User u = Session.current();
                AppContext.get().auditLogService.log(
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : "system",
                    "DELETE",
                    "parts",
                    selectedPart.getId(),
                    null,
                    "Soft deleted part: " + selectedPart.getFullName()
                );

                new Alert(Alert.AlertType.INFORMATION, "تم حذف الصنف بنجاح").showAndWait();
                refreshTable(AppContext.get().partService.all());
                onAddNewPart();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "خطأ أثناء الحذف: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void onCancelEdit() {
        onAddNewPart();
    }

    public void focusEditForm() {
        partTypeField.requestFocus();
    }

    @FXML
    private void onExportExcel() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("تصدير المخزون إلى Excel");
        fileChooser.setInitialFileName("inventory_" + java.time.LocalDate.now() + ".xlsx");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));

        javafx.stage.Window window = partsTable.getScene().getWindow();
        java.io.File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            try {
                com.kiastore.util.ExcelExporter.exportParts(observablePartsList, file);
                new Alert(Alert.AlertType.INFORMATION, "تم تصدير المخزون بنجاح إلى ملف Excel").showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "فشل تصدير ملف Excel: " + e.getMessage()).showAndWait();
                e.printStackTrace();
            }
        }
    }
}
