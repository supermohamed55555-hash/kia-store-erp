package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.Invoice;
import com.kiastore.model.InvoiceItem;
import com.kiastore.model.Part;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class InvoicesController implements MainShellController.Searchable {

    @FXML private TableView<Invoice> invoicesTable;
    @FXML private TableColumn<Invoice, String> colInvNumber;
    @FXML private TableColumn<Invoice, String> colCustomer;
    @FXML private TableColumn<Invoice, Double> colTotal;
    @FXML private TableColumn<Invoice, String> colStatus;
    @FXML private TableColumn<Invoice, String> colDate;

    // Issue Invoice inputs
    @FXML private TextField customerNameField;
    @FXML private TextField customerPhoneField;
    @FXML private TextField partSearchField;
    @FXML private ComboBox<Part> partComboBox;
    @FXML private TextField quantityField;

    // Cart Table
    @FXML private TableView<InvoiceItem> cartTable;
    @FXML private TableColumn<InvoiceItem, String> colCartPart;
    @FXML private TableColumn<InvoiceItem, Integer> colCartQty;
    @FXML private TableColumn<InvoiceItem, Double> colCartUnitPrice;
    @FXML private TableColumn<InvoiceItem, Double> colCartTotalPrice;

    // Summary fields
    @FXML private Label subtotalLabel;
    @FXML private TextField discountField;
    @FXML private Label finalAmountLabel;
    @FXML private ComboBox<Invoice.PaymentMethod> paymentMethodComboBox;
    @FXML private HBox creditPaymentBox;
    @FXML private HBox creditRemainingBox;
    @FXML private TextField amountPaidField;
    @FXML private Label amountDueLabel;
    @FXML private TextArea notesArea;

    @FXML private Button btnPrint;
    @FXML private Button btnReturnItem;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmitInvoice;

    private final ObservableList<Invoice> observableInvoicesList = FXCollections.observableArrayList();
    private final ObservableList<InvoiceItem> observableCartList = FXCollections.observableArrayList();
    private final ObservableList<Part> observablePartsList = FXCollections.observableArrayList();

    private Invoice selectedInvoice;
    private final Map<Integer, Part> partsCache = new HashMap<>();

    @FXML
    public void initialize() {
        // Main Invoices Table binding
        colInvNumber.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("finalAmount"));
        
        colStatus.setCellValueFactory(cellData -> {
            Invoice.Status st = cellData.getValue().getStatus();
            String stAr = switch (st) {
                case ACTIVE -> "نشطة";
                case CANCELLED -> "ملغاة";
                case RETURNED -> "مرتجعة بالكامل";
            };
            return new SimpleStringProperty(stAr);
        });

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("hh:mm a | yyyy/MM/dd", new java.util.Locale("ar"));
        colDate.setCellValueFactory(cellData -> {
            Invoice inv = cellData.getValue();
            if (inv.getCreatedAt() != null) {
                return new SimpleStringProperty(inv.getCreatedAt().format(dtf));
            }
            return new SimpleStringProperty("");
        });

        // Cart Table binding
        colCartQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colCartUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colCartTotalPrice.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        colCartPart.setCellValueFactory(cellData -> {
            InvoiceItem item = cellData.getValue();
            Part p = partsCache.get(item.getPartId());
            return new SimpleStringProperty(p != null ? p.getFullName() : "صنف مجهول");
        });

        // Payment dropdown
        paymentMethodComboBox.setItems(FXCollections.observableArrayList(Invoice.PaymentMethod.values()));
        paymentMethodComboBox.setValue(Invoice.PaymentMethod.CASH);
        paymentMethodComboBox.setConverter(new javafx.util.StringConverter<Invoice.PaymentMethod>() {
            @Override public String toString(Invoice.PaymentMethod pm) {
                if (pm == null) return "";
                return switch (pm) {
                    case CASH  -> "نقدي";
                    case CARD  -> "بطاقة";
                    case OTHER -> "أخرى";
                    case CREDIT -> "آجل (دين)";
                };
            }
            @Override public Invoice.PaymentMethod fromString(String s) { return null; }
        });

        // Load data
        loadCaches();
        refreshLists();

        // Listen for table selection
        invoicesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedInvoice = newVal;
                btnCancel.setDisable(newVal.getStatus() != Invoice.Status.ACTIVE);
                btnReturnItem.setDisable(newVal.getStatus() != Invoice.Status.ACTIVE);
                btnPrint.setDisable(false);
            }
        });

        btnCancel.setDisable(true);
        btnReturnItem.setDisable(true);
        btnPrint.setDisable(true);

        // Apply role boundaries
        applyRoleRestrictions();
    }

    private void applyRoleRestrictions() {
        User u = Session.current();
        if (u != null) {
            boolean isAdmin = u.getRole() == Role.ADMIN;
            btnCancel.setVisible(isAdmin);
            btnCancel.setManaged(isAdmin);
        }
    }

    private void loadCaches() {
        partsCache.clear();
        for (Part p : AppContext.get().partService.all()) {
            partsCache.put(p.getId(), p);
        }
    }

    private void refreshLists() {
        loadCaches();

        // Invoices list
        List<Invoice> list = AppContext.get().invoiceService.all();
        // Sort by date descending
        list.sort((i1, i2) -> {
            if (i1.getCreatedAt() == null || i2.getCreatedAt() == null) return 0;
            return i2.getCreatedAt().compareTo(i1.getCreatedAt());
        });
        observableInvoicesList.setAll(list);
        invoicesTable.setItems(observableInvoicesList);

        // Parts lookup combo
        observablePartsList.setAll(partsCache.values().stream().filter(Part::isActive).collect(Collectors.toList()));
        partComboBox.setItems(observablePartsList);
    }

    @Override
    public void search(String query) {
        if (query == null || query.isBlank()) {
            refreshLists();
            return;
        }

        String term = query.toLowerCase().trim();
        List<Invoice> filtered = AppContext.get().invoiceService.all().stream()
                .filter(i -> i.getInvoiceNumber().toLowerCase().contains(term)
                        || (i.getCustomerName() != null && i.getCustomerName().toLowerCase().contains(term))
                        || (i.getCustomerPhone() != null && i.getCustomerPhone().contains(term)))
                .collect(Collectors.toList());

        observableInvoicesList.setAll(filtered);
        invoicesTable.setItems(observableInvoicesList);
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

    public void addPartToCartDirect(Part p, int qty) {
        partComboBox.setValue(p);
        quantityField.setText(String.valueOf(qty));
        onAddToCart();
    }

    @FXML
    private void onAddToCart() {
        Part p = partComboBox.getValue();
        if (p == null) {
            new Alert(Alert.AlertType.ERROR, "يرجى اختيار الصنف أولاً").showAndWait();
            return;
        }

        try {
            int qty = Integer.parseInt(quantityField.getText());
            if (qty <= 0) {
                new Alert(Alert.AlertType.ERROR, "الكمية يجب أن تكون أكبر من الصفر").showAndWait();
                return;
            }

            // Check if cart already has this part
            Optional<InvoiceItem> existing = observableCartList.stream()
                    .filter(item -> item.getPartId() == p.getId())
                    .findFirst();

            int currentRequestedQty = qty;
            if (existing.isPresent()) {
                currentRequestedQty += existing.get().getQuantity();
            }

            // Check if currentRequestedQty exceeds available stock
            if (currentRequestedQty > p.getCurrentStock()) {
                new Alert(Alert.AlertType.ERROR, "الكمية المطلوبة تتجاوز المخزون الحالي المتوفر للصنف (" 
                        + p.getCurrentStock() + " قطعة متوفرة)").showAndWait();
                return;
            }

            if (existing.isPresent()) {
                InvoiceItem item = existing.get();
                item.setQuantity(currentRequestedQty);
                item.setTotalPrice(item.getUnitPrice() * item.getQuantity());
            } else {
                InvoiceItem item = new InvoiceItem();
                item.setPartId(p.getId());
                item.setQuantity(qty);
                item.setUnitPrice(p.getSalePrice());
                item.setTotalPrice(p.getSalePrice() * qty);
                observableCartList.add(item);
            }

            cartTable.setItems(observableCartList);
            cartTable.refresh();
            
            // Clear inputs
            partSearchField.clear();
            partComboBox.setValue(null);
            partComboBox.setItems(observablePartsList);
            quantityField.setText("1");

            calculateTotals();

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "الرجاء إدخال قيمة صحيحة للكمية").showAndWait();
        }
    }

    @FXML
    private void onRemoveFromCart() {
        InvoiceItem selectedItem = cartTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            observableCartList.remove(selectedItem);
            calculateTotals();
        }
    }

    private void calculateTotals() {
        double subtotal = observableCartList.stream().mapToDouble(InvoiceItem::getTotalPrice).sum();
        subtotalLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", subtotal));

        double discount = 0;
        try {
            discount = Double.parseDouble(discountField.getText());
        } catch (NumberFormatException ignore) {}

        double finalAmount = Math.max(0.0, subtotal - discount);
        finalAmountLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", finalAmount));
    }

    @FXML
    private void onDiscountChanged() {
        calculateTotals();
    }

    @FXML
    private void onClearCart() {
        customerNameField.clear();
        customerPhoneField.clear();
        partSearchField.clear();
        partComboBox.setValue(null);
        partComboBox.setItems(observablePartsList);
        quantityField.setText("1");
        observableCartList.clear();
        subtotalLabel.setText("0.00 EGP");
        discountField.setText("0.00");
        finalAmountLabel.setText("0.00 EGP");
        paymentMethodComboBox.setValue(Invoice.PaymentMethod.CASH);
        notesArea.clear();
        if (creditPaymentBox != null) {
            creditPaymentBox.setVisible(false);
            creditPaymentBox.setManaged(false);
            creditRemainingBox.setVisible(false);
            creditRemainingBox.setManaged(false);
            amountPaidField.setText("0.00");
            amountDueLabel.setText("0.00 EGP");
        }
    }

    /** Shows/hides the credit advance-payment row when payment method changes. */
    @FXML
    private void onPaymentMethodChanged() {
        boolean isCredit = paymentMethodComboBox.getValue() == Invoice.PaymentMethod.CREDIT;
        creditPaymentBox.setVisible(isCredit);
        creditPaymentBox.setManaged(isCredit);
        creditRemainingBox.setVisible(isCredit);
        creditRemainingBox.setManaged(isCredit);
        if (isCredit) updateCreditAmounts();
    }

    /** Updates the "amount due" label live based on amountPaid vs finalAmount. */
    private void updateCreditAmounts() {
        double finalAmt = 0;
        try {
            String txt = finalAmountLabel.getText().replace(" EGP", "").trim();
            finalAmt = Double.parseDouble(txt);
        } catch (NumberFormatException ignore) {}
        double paid = 0;
        try {
            paid = Double.parseDouble(amountPaidField.getText().trim());
        } catch (NumberFormatException ignore) {}
        double due = Math.max(0, finalAmt - paid);
        amountDueLabel.setText(String.format("%.2f EGP", due));
    }


    @FXML
    private void onSubmitInvoice() {
        if (observableCartList.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "سلة المشتريات فارغة").showAndWait();
            return;
        }

        Invoice.PaymentMethod pm = paymentMethodComboBox.getValue();

        // Validate: credit invoices must have a customer name and phone
        if (pm == Invoice.PaymentMethod.CREDIT) {
            if (customerNameField.getText().isBlank()) {
                new Alert(Alert.AlertType.WARNING, "فواتير الدفع الآجل تتطلب اسم العميل.").showAndWait();
                return;
            }
            if (customerPhoneField.getText().isBlank()) {
                new Alert(Alert.AlertType.WARNING, "فواتير الدفع الآجل تتطلب رقم هاتف العميل.").showAndWait();
                return;
            }
        }

        try {
            Invoice inv = new Invoice();
            inv.setCustomerName(customerNameField.getText().trim());
            inv.setCustomerPhone(customerPhoneField.getText().trim());
            inv.setPaymentMethod(pm);
            
            double discount = 0;
            try {
                discount = Double.parseDouble(discountField.getText());
            } catch (NumberFormatException ignore) {}
            
            inv.setDiscount(discount);
            inv.setNotes(notesArea.getText());

            // Credit: record amount paid upfront
            if (pm == Invoice.PaymentMethod.CREDIT) {
                double paid = 0;
                try { paid = Double.parseDouble(amountPaidField.getText().trim()); } catch (NumberFormatException ignore) {}
                inv.setAmountPaid(paid);
            }
            
            User u = Session.current();
            if (u != null) inv.setCreatedBy(u.getId());

            // Save via InvoiceService Transaction
            Invoice saved = AppContext.get().invoiceService.createInvoice(inv, observableCartList);

            // Audit Trail
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                "INVOICE_CREATE",
                "invoices",
                saved.getId(),
                null,
                saved.getInvoiceNumber()
            );

            // Credit summary alert
            if (pm == Invoice.PaymentMethod.CREDIT) {
                double due = saved.getFinalAmount() - saved.getAmountPaid();
                String msg = String.format(
                    "تم حفظ الفاتورة الآجلة: %s%nالمدفوع مقدماً: %.2f EGP%nالمتبقّى على العميل: %.2f EGP",
                    saved.getInvoiceNumber(), saved.getAmountPaid(), due);
                new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
            } else {
                Alert ok = new Alert(Alert.AlertType.INFORMATION, "تم حفظ وإصدار الفاتورة بنجاح: " + saved.getInvoiceNumber());
                ok.showAndWait();
            }
            
            // Print receipts preview immediately
            showThermalReceiptPreview(saved, observableCartList);

            onClearCart();
            refreshLists();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "فشل حفظ الفاتورة: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onCancelInvoice() {
        if (selectedInvoice == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد إلغاء الفاتورة");
        confirm.setHeaderText("هل أنت متأكد من إلغاء الفاتورة رقم: " + selectedInvoice.getInvoiceNumber() + "؟");
        confirm.setContentText("سيتم إرجاع جميع الأصناف للمخزون تلقائياً.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                User u = Session.current();
                AppContext.get().invoiceService.cancelInvoice(selectedInvoice.getId(), u != null ? u.getId() : null);

                // Audit Log
                AppContext.get().auditLogService.log(
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : "system",
                    "INVOICE_CANCEL",
                    "invoices",
                    selectedInvoice.getId(),
                    null,
                    "Cancelled Invoice: " + selectedInvoice.getInvoiceNumber()
                );

                new Alert(Alert.AlertType.INFORMATION, "تم إلغاء الفاتورة وإعادة السلع للمخزون").showAndWait();
                refreshLists();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "فشل إلغاء الفاتورة: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void onReturnItem() {
        if (selectedInvoice == null) return;

        List<InvoiceItem> items = AppContext.get().invoiceService.getItems(selectedInvoice.getId());
        if (items.isEmpty()) return;

        // Custom return dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("إرجاع صنف من الفاتورة");
        dialog.setHeaderText("اختر الصنف والكمية المراد إرجاعها");

        ButtonType btnReturn = new ButtonType("إرجاع", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(btnReturn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<InvoiceItem> itemCombo = new ComboBox<>();
        itemCombo.setItems(FXCollections.observableArrayList(items));
        
        // Custom combo box formatter
        itemCombo.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(InvoiceItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText("");
                } else {
                    setText(item.getPartFullName() + " (المباع: " + item.getQuantity() + ")");
                }
            }
        });
        itemCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InvoiceItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText("");
                } else {
                    setText(item.getPartFullName() + " (المباع: " + item.getQuantity() + ")");
                }
            }
        });

        TextField qtyField = new TextField("1");
        TextField reasonField = new TextField("");

        grid.add(new Label("الصنف:"), 0, 0);
        grid.add(itemCombo, 1, 0);
        grid.add(new Label("الكمية المراد إرجاعها:"), 0, 1);
        grid.add(qtyField, 1, 1);
        grid.add(new Label("السبب:"), 0, 2);
        grid.add(reasonField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnReturn) {
            InvoiceItem selectedItem = itemCombo.getValue();
            if (selectedItem == null) {
                new Alert(Alert.AlertType.ERROR, "يرجى تحديد الصنف المرتجع").showAndWait();
                return;
            }

            try {
                int qty = Integer.parseInt(qtyField.getText());
                String reason = reasonField.getText().trim();

                User u = Session.current();
                AppContext.get().invoiceService.returnItem(
                    selectedInvoice.getId(),
                    selectedItem.getPartId(),
                    qty,
                    reason,
                    u != null ? u.getId() : null
                );

                // Audit Log
                AppContext.get().auditLogService.log(
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : "system",
                    "RETURN",
                    "returns",
                    selectedInvoice.getId(),
                    null,
                    "Returned " + qty + " of part ID: " + selectedItem.getPartId()
                );

                new Alert(Alert.AlertType.INFORMATION, "تم تسجيل المرتجع وتحديث المخزون بنجاح").showAndWait();
                refreshLists();
            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR, "يرجى إدخال كمية صحيحة").showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "خطأ في عملية الإرجاع: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void onPrintInvoice() {
        if (selectedInvoice == null) return;
        List<InvoiceItem> items = AppContext.get().invoiceService.getItems(selectedInvoice.getId());
        showThermalReceiptPreview(selectedInvoice, items);
    }

    /**
     * Renders a mock print dialog displaying thermal receipt layout
     */
    private void showThermalReceiptPreview(Invoice invoice, List<InvoiceItem> items) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("معاينة الفاتورة قبل الطباعة");
        dialog.setHeaderText("معاينة الإيصال الحراري للفاتورة رقم: " + invoice.getInvoiceNumber());

        ButtonType btnSend = new ButtonType("🖨️ إرسال للطابعة", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnPdf = new ButtonType("📄 حفظ كـ PDF", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(btnSend, btnPdf, ButtonType.CANCEL);

        TextArea receiptText = new TextArea();
        receiptText.setEditable(false);
        receiptText.setPrefRowCount(20);
        receiptText.setPrefColumnCount(40);
        receiptText.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12;");

        // Format Text Thermal receipt layout
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("              KIA STORE                 \n");
        sb.append("========================================\n");
        sb.append("رقم الفاتورة: ").append(invoice.getInvoiceNumber()).append("\n");
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm a");
        String dateStr = invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(dtf) : java.time.LocalDate.now().toString();
        sb.append("التاريخ: ").append(dateStr).append("\n");
        sb.append("العميل: ").append(invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank() ? invoice.getCustomerName() : "نقدي").append("\n");
        if (invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()) {
            sb.append("رقم الهاتف: ").append(invoice.getCustomerPhone()).append("\n");
        }
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-22s %-4s %-12s\n", "الصنف", "الكمية", "السعر"));
        sb.append("----------------------------------------\n");

        for (InvoiceItem item : items) {
            String name = item.getPartFullName();
            if (name == null) {
                Part p = partsCache.get(item.getPartId());
                name = p != null ? p.getFullName() : "صنف مجهول";
            }
            if (name.length() > 20) {
                name = name.substring(0, 18) + "..";
            }
            sb.append(String.format("%-22s %-4d %-12.2f\n", name, item.getQuantity(), item.getUnitPrice()));
        }

        sb.append("----------------------------------------\n");
        sb.append(String.format("الإجمالي: %,.2f EGP\n", invoice.getTotalAmount()));
        sb.append(String.format("الخصم: %,.2f EGP\n", invoice.getDiscount()));
        sb.append("----------------------------------------\n");
        sb.append(String.format("المطلوب: %,.2f EGP\n", invoice.getFinalAmount()));
        sb.append("طريقة الدفع: ").append(invoice.getPaymentMethod().name()).append("\n");
        sb.append("========================================\n");
        sb.append("      شكراً لتعاملكم معنا، رافقتكم السلامة!      \n");
        sb.append("========================================\n");

        receiptText.setText(sb.toString());

        VBox content = new VBox(receiptText);
        content.setSpacing(10);
        VBox.setVgrow(receiptText, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> opt = dialog.showAndWait();
        if (opt.isPresent()) {
            if (opt.get() == btnSend) {
                new Alert(Alert.AlertType.INFORMATION, "تم إرسال الأمر للطابعة الحرارية بنجاح.").showAndWait();
            } else if (opt.get() == btnPdf) {
                javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                fileChooser.setTitle("حفظ الفاتورة كـ PDF");
                fileChooser.setInitialFileName(invoice.getInvoiceNumber() + ".pdf");
                fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
                
                javafx.stage.Window window = invoicesTable.getScene().getWindow();
                java.io.File file = fileChooser.showSaveDialog(window);
                if (file != null) {
                    try {
                        com.kiastore.util.InvoicePdfExporter.export(invoice, items, file);
                        new Alert(Alert.AlertType.INFORMATION, "تم حفظ الفاتورة بنجاح كـ PDF").showAndWait();
                    } catch (Exception e) {
                        new Alert(Alert.AlertType.ERROR, "فشل حفظ ملف PDF: " + e.getMessage()).showAndWait();
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @FXML
    private void onExportInvoicesExcel() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("تصدير المبيعات إلى Excel");
        fileChooser.setInitialFileName("sales_" + java.time.LocalDate.now() + ".xlsx");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));

        javafx.stage.Window window = invoicesTable.getScene().getWindow();
        java.io.File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            try {
                com.kiastore.util.ExcelExporter.exportInvoices(observableInvoicesList, file);
                new Alert(Alert.AlertType.INFORMATION, "تم تصدير المبيعات بنجاح إلى ملف Excel").showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "فشل تصدير ملف Excel: " + e.getMessage()).showAndWait();
                e.printStackTrace();
            }
        }
    }
}
