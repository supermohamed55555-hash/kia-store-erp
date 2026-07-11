package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.CustomerLedgerRow;
import com.kiastore.model.CustomerPayment;
import com.kiastore.model.Invoice;
import com.kiastore.model.User;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the CustomerLedger screen.
 * Displays credit-sale summary per customer, allows recording payments,
 * and drilling down to individual credit invoices.
 */
public class CustomerLedgerController {

    @FXML private Label totalBalanceLabel;
    @FXML private TextField searchField;
    @FXML private CheckBox showZeroBalanceCheck;

    // Summary table
    @FXML private TableView<CustomerLedgerRow> ledgerTable;
    @FXML private TableColumn<CustomerLedgerRow, String> colName;
    @FXML private TableColumn<CustomerLedgerRow, String> colPhone;
    @FXML private TableColumn<CustomerLedgerRow, Double> colInvoiced;
    @FXML private TableColumn<CustomerLedgerRow, Double> colPaid;
    @FXML private TableColumn<CustomerLedgerRow, Double> colBalance;

    // Detail table (invoices for selected customer)
    @FXML private TitledPane detailPane;
    @FXML private TableView<Invoice> detailTable;
    @FXML private TableColumn<Invoice, String> colInvNo;
    @FXML private TableColumn<Invoice, String> colInvDate;
    @FXML private TableColumn<Invoice, Double> colInvTotal;
    @FXML private TableColumn<Invoice, Double> colInvPaid;
    @FXML private TableColumn<Invoice, Double> colInvDue;
    @FXML private TableColumn<Invoice, String> colInvNotes;

    private final ObservableList<CustomerLedgerRow> allRows = FXCollections.observableArrayList();
    private FilteredList<CustomerLedgerRow> filteredRows;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        // Set column resize policies programmatically to avoid FXML loading issues
        ledgerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Ledger summary columns
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCustomerName()));
        colPhone.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCustomerPhone()));
        colInvoiced.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getTotalInvoiced()).asObject());
        colPaid.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getTotalPaid()).asObject());
        colBalance.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getBalance()).asObject());

        // Format currency columns
        formatCurrencyColumn(colInvoiced);
        formatCurrencyColumn(colPaid);
        formatCurrencyColumn(colBalance);

        // Color balance column red if positive
        colBalance.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(String.format("%.2f EGP", val));
                setStyle(val > 0.01 ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "-fx-text-fill: #16A34A;");
            }
        });

        // Detail invoice columns
        colInvNo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getInvoiceNumber()));
        colInvDate.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCreatedAt() != null ? cd.getValue().getCreatedAt().format(DATE_FMT) : ""));
        colInvTotal.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getFinalAmount()).asObject());
        colInvPaid.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getAmountPaid()).asObject());
        colInvDue.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getAmountDue()).asObject());
        colInvNotes.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNotes()));
        formatCurrencyColumn(colInvTotal);
        formatCurrencyColumn(colInvPaid);
        formatCurrencyColumn(colInvDue);

        // Wire table selection to detail view
        ledgerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) loadDetailForCustomer(newV.getCustomerPhone());
        });

        // Search filter
        filteredRows = new FilteredList<>(allRows, r -> true);
        ledgerTable.setItems(filteredRows);

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));

        loadLedger();
    }

    @FXML
    private void onRefresh() {
        loadLedger();
    }

    /** Loads the summary ledger from DB and refreshes the table. */
    private void loadLedger() {
        allRows.setAll(AppContext.get().customerPaymentDao.getLedgerSummary());
        applyFilter(searchField.getText());

        double total = allRows.stream().mapToDouble(CustomerLedgerRow::getBalance).sum();
        totalBalanceLabel.setText(String.format("إجمالي الديون: %.2f EGP", total));
    }

    private void applyFilter(String term) {
        boolean showZero = showZeroBalanceCheck.isSelected();
        String lower = term == null ? "" : term.toLowerCase().trim();
        filteredRows.setPredicate(row -> {
            boolean matchText = lower.isEmpty()
                || row.getCustomerName().toLowerCase().contains(lower)
                || (row.getCustomerPhone() != null && row.getCustomerPhone().contains(lower));
            boolean matchBalance = showZero || row.getBalance() > 0.009;
            return matchText && matchBalance;
        });
    }

    private void loadDetailForCustomer(String phone) {
        List<Invoice> invoices = AppContext.get().invoiceDao.findCreditByPhone(phone);
        detailTable.setItems(FXCollections.observableArrayList(invoices));
        detailPane.setExpanded(true);
    }

    /** Opens a dialog to record a payment from the selected customer. */
    @FXML
    private void onRecordPayment() {
        CustomerLedgerRow selected = ledgerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "اختر عميلاً أولاً من الجدول.").showAndWait();
            return;
        }
        if (selected.getBalance() <= 0.009) {
            new Alert(Alert.AlertType.INFORMATION, "هذا العميل ليس عليه أي رصيد متبقٍ.").showAndWait();
            return;
        }

        // Build dialog
        Dialog<CustomerPayment> dialog = new Dialog<>();
        dialog.setTitle("تسجيل دفعة سداد");
        dialog.setHeaderText("العميل: " + selected.getCustomerName()
            + "\nالرصيد المتبقي: " + String.format("%.2f EGP", selected.getBalance()));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label lAmount = new Label("المبلغ المسدَّد (EGP):");
        lAmount.setFont(Font.font("Tajawal", 13));
        TextField fAmount = new TextField(String.format("%.2f", selected.getBalance()));
        fAmount.setPrefWidth(150);

        Label lMethod = new Label("طريقة الدفع:");
        lMethod.setFont(Font.font("Tajawal", 13));
        ComboBox<CustomerPayment.PaymentMethod> cbMethod = new ComboBox<>();
        cbMethod.getItems().setAll(CustomerPayment.PaymentMethod.values());
        cbMethod.setValue(CustomerPayment.PaymentMethod.CASH);

        Label lNotes = new Label("ملاحظات:");
        lNotes.setFont(Font.font("Tajawal", 13));
        TextArea fNotes = new TextArea();
        fNotes.setPrefRowCount(2);

        grid.add(lAmount, 0, 0); grid.add(fAmount, 1, 0);
        grid.add(lMethod, 0, 1); grid.add(cbMethod, 1, 1);
        grid.add(lNotes,  0, 2); grid.add(fNotes,  1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                double amt = 0;
                try { amt = Double.parseDouble(fAmount.getText().trim()); } catch (NumberFormatException ignore) {}
                if (amt <= 0) return null;

                CustomerPayment cp = new CustomerPayment();
                cp.setCustomerName(selected.getCustomerName());
                cp.setCustomerPhone(selected.getCustomerPhone());
                cp.setAmountPaid(amt);
                cp.setPaymentMethod(cbMethod.getValue());
                cp.setNotes(fNotes.getText());

                User u = Session.current();
                if (u != null) cp.setCreatedBy(u.getId());

                return cp;
            }
            return null;
        });

        Optional<CustomerPayment> result = dialog.showAndWait();
        result.ifPresent(cp -> {
            AppContext.get().customerPaymentDao.insert(cp);

            // Audit
            User u = Session.current();
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                "CUSTOMER_PAYMENT",
                "customer_payments",
                cp.getId(),
                null,
                selected.getCustomerPhone() + " paid " + cp.getAmountPaid()
            );

            new Alert(Alert.AlertType.INFORMATION,
                String.format("تم تسجيل دفعة %.2f EGP بنجاح.", cp.getAmountPaid())).showAndWait();
            loadLedger();
        });
    }

    /** Prints a statement for the selected customer. */
    @FXML
    private void onPrintStatement() {
        CustomerLedgerRow selected = ledgerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "اختر عميلاً لطباعة كشف حسابه.").showAndWait();
            return;
        }

        List<Invoice> invoices = AppContext.get().invoiceDao.findCreditByPhone(selected.getCustomerPhone());
        List<CustomerPayment> payments = AppContext.get().customerPaymentDao.findByPhone(selected.getCustomerPhone());

        // Build a text statement (can be extended to PDF later)
        StringBuilder sb = new StringBuilder();
        sb.append("====================================\n");
        sb.append("كشف حساب العميل\n");
        sb.append("====================================\n");
        sb.append("الاسم  : ").append(selected.getCustomerName()).append("\n");
        sb.append("الهاتف : ").append(selected.getCustomerPhone()).append("\n");
        sb.append("------------------------------------\n");
        sb.append("الفواتير الآجلة:\n");
        for (Invoice inv : invoices) {
            sb.append(String.format("  %s | %.2f EGP | سدّد: %.2f | متبقي: %.2f%n",
                inv.getInvoiceNumber(), inv.getFinalAmount(),
                inv.getAmountPaid(), inv.getAmountDue()));
        }
        sb.append("------------------------------------\n");
        sb.append("المدفوعات اللاحقة:\n");
        for (CustomerPayment cp : payments) {
            sb.append(String.format("  %s | %.2f EGP (%s)%n",
                cp.getCreatedAt() != null ? cp.getCreatedAt().format(DATE_FMT) : "—",
                cp.getAmountPaid(), cp.getPaymentMethod().name().toLowerCase()));
        }
        sb.append("====================================\n");
        sb.append(String.format("إجمالي الفواتير: %.2f EGP%n", selected.getTotalInvoiced()));
        sb.append(String.format("إجمالي المسدَّد: %.2f EGP%n", selected.getTotalPaid()));
        sb.append(String.format("الرصيد المتبقي: %.2f EGP%n", selected.getBalance()));
        sb.append("====================================\n");

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setPrefSize(500, 400);
        ta.setStyle("-fx-font-family: monospace; -fx-font-size: 12;");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("كشف حساب — " + selected.getCustomerName());
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setPrefSize(520, 450);
        alert.showAndWait();
    }

    @SuppressWarnings("unchecked")
    private <T> void formatCurrencyColumn(TableColumn<T, Double> col) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? null : String.format("%.2f EGP", val));
            }
        });
    }

    /**
     * Opens a dialog to manually add a new customer and set their initial credit opening balance.
     */
    @FXML
    private void onAddCustomer() {
        Dialog<Invoice> dialog = new Dialog<>();
        dialog.setTitle("إضافة عميل / رصيد آجل جديد");
        dialog.setHeaderText("أدخل بيانات العميل الجديد ورصيده الافتتاحي (إن وجد).");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label lName = new Label("اسم العميل:");
        lName.setFont(Font.font("Tajawal", 13));
        TextField fName = new TextField();
        fName.setPromptText("مثال: أحمد محمد");
        fName.setPrefWidth(200);

        Label lPhone = new Label("رقم الهاتف:");
        lPhone.setFont(Font.font("Tajawal", 13));
        TextField fPhone = new TextField();
        fPhone.setPromptText("مثال: 01xxxxxxxxx");

        Label lBalance = new Label("الرصيد الآجل الافتتاحي (EGP):");
        lBalance.setFont(Font.font("Tajawal", 13));
        TextField fBalance = new TextField("0.00");

        Label lNotes = new Label("ملاحظات:");
        lNotes.setFont(Font.font("Tajawal", 13));
        TextArea fNotes = new TextArea();
        fNotes.setPrefRowCount(2);
        fNotes.setPromptText("رصيد آجل افتتاحي أو ملاحظات أخرى");

        grid.add(lName, 0, 0); grid.add(fName, 1, 0);
        grid.add(lPhone, 0, 1); grid.add(fPhone, 1, 1);
        grid.add(lBalance, 0, 2); grid.add(fBalance, 1, 2);
        grid.add(lNotes, 0, 3); grid.add(fNotes, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = fName.getText().trim();
                String phone = fPhone.getText().trim();
                if (name.isEmpty() || phone.isEmpty()) {
                    return null;
                }

                double bal = 0;
                try { bal = Double.parseDouble(fBalance.getText().trim()); } catch (NumberFormatException ignore) {}

                Invoice inv = new Invoice();
                inv.setCustomerName(name);
                inv.setCustomerPhone(phone);
                inv.setTotalAmount(bal);
                inv.setDiscount(0);
                inv.setFinalAmount(bal);
                inv.setPaymentMethod(Invoice.PaymentMethod.CREDIT);
                inv.setAmountPaid(0);
                inv.setAmountDue(bal);
                inv.setStatus(Invoice.Status.ACTIVE);
                inv.setNotes(fNotes.getText().trim().isEmpty() ? "رصيد افتتاحي" : fNotes.getText().trim());

                User u = Session.current();
                if (u != null) inv.setCreatedBy(u.getId());

                return inv;
            }
            return null;
        });

        Optional<Invoice> result = dialog.showAndWait();
        result.ifPresent(inv -> {
            if (inv.getCustomerName() == null || inv.getCustomerPhone() == null) {
                new Alert(Alert.AlertType.ERROR, "الاسم ورقم الهاتف مطلوبان.").showAndWait();
                return;
            }
            // Generate invoice number
            String timePart = java.time.LocalDate.now().toString().replace("-", "");
            // Find a unique code
            long tempId = System.currentTimeMillis() % 10000;
            inv.setInvoiceNumber("LGR-" + timePart + "-" + String.format("%04d", tempId));

            AppContext.get().invoiceDao.insert(inv);

            // Audit Log
            User u = Session.current();
            AppContext.get().auditLogService.log(
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : "system",
                "CUSTOMER_CREATE",
                "invoices",
                inv.getId(),
                null,
                "Created customer " + inv.getCustomerName() + " with opening balance " + inv.getFinalAmount()
            );

            new Alert(Alert.AlertType.INFORMATION, "تم إضافة العميل بنجاح ورصيده الافتتاحي جاهز.").showAndWait();
            loadLedger();
        });
    }
}
