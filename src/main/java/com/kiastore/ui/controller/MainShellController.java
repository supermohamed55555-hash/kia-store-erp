package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Main;
import com.kiastore.app.Session;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

public class MainShellController {

    private static MainShellController instance;

    public static MainShellController getInstance() {
        return instance;
    }

    @FXML private StackPane contentArea;
    @FXML private Label pageTitle;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label topUserLabel;
    @FXML private Label topRoleLabel;
    @FXML private VBox sidebar;
    @FXML private Label sidebarBrandLabel;
    @FXML private Button btnCollapse;
    @FXML private TextField globalSearchField;

    @FXML private Button navWelcome;
    @FXML private Button navDashboard;
    @FXML private Button navParts;
    @FXML private Button navInvoices;
    @FXML private Button navCustomerLedger;
    @FXML private Button navSuppliers;
    @FXML private Button navReceiving;
    @FXML private Button navAuditLog;
    @FXML private Button navUsers;
    @FXML private Label lowStockBadge;
    @FXML private Button navSettings;

    private final java.util.concurrent.ScheduledExecutorService lowStockScheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LowStockChecker");
            t.setDaemon(true);
            return t;
        });

    private Button activeButton;
    private boolean isSidebarCollapsed = false;
    private Object activeController;

    // Barcode scanner detection
    private long lastKeyTime = 0;
    private int barcodeCharCount = 0;
    private boolean likelyBarcodeScan = false;

    @FXML
    public void initialize() {
        instance = this;
        User u = Session.current();
        if (u != null) {
            userLabel.setText(u.getName());
            roleLabel.setText(getRoleDisplayName(u.getRole()));
            if (topUserLabel != null) topUserLabel.setText(u.getName());
            if (topRoleLabel != null) topRoleLabel.setText(getRoleDisplayName(u.getRole()));
            applyRoleVisibility(u.getRole());
        }
        
        // Show Welcome / Home screen by default
        showWelcome();

        // Start low stock background checker (runs immediately, then every 30 mins)
        startLowStockChecking();
    }

    private String getRoleDisplayName(Role role) {
        return switch (role) {
            case ADMIN -> "المدير العام";
            case CASHIER -> "مسؤول المبيعات / كاشير";
            case WAREHOUSE -> "أمين المخزن";
        };
    }

    /**
     * Applies role-based visibility to sidebar links.
     */
    private void applyRoleVisibility(Role role) {
        boolean isAdmin = role == Role.ADMIN;
        boolean isCashier = role == Role.CASHIER;
        boolean isWarehouse = role == Role.WAREHOUSE;

        toggle(navWelcome, true); // Welcome screen is visible to all
        toggle(navDashboard, isAdmin);
        toggle(navParts, isAdmin || isWarehouse || isCashier);
        toggle(navInvoices, isAdmin || isCashier);
        toggle(navCustomerLedger, isAdmin || isCashier);
        toggle(navSuppliers, isAdmin || isWarehouse);
        toggle(navReceiving, isAdmin || isWarehouse);
        toggle(navAuditLog, isAdmin);
        toggle(navUsers, isAdmin);
        toggle(navSettings, isAdmin);
    }

    private void toggle(Button b, boolean show) {
        if (b == null) return;
        b.setVisible(show);
        b.setManaged(show);
    }

    @FXML
    public void showWelcome() {
        swap("Welcome.fxml", "الرئيسية", navWelcome);
    }

    @FXML
    public void showDashboard() {
        swap("Dashboard.fxml", "لوحة التحكم", navDashboard);
    }

    @FXML
    public void showParts() {
        swap("Parts.fxml", "إدارة قطع الغيار", navParts);
    }

    @FXML
    public void showInvoices() {
        swap("Invoices.fxml", "الفواتير والمبيعات", navInvoices);
    }

    @FXML
    public void showCustomerLedger() {
        swap("CustomerLedger.fxml", "حسابات العملاء والآجل", navCustomerLedger);
    }

    @FXML
    public void showSuppliers() {
        swap("Suppliers.fxml", "إدارة الموردين", navSuppliers);
    }

    @FXML
    public void showReceiving() {
        swap("Receiving.fxml", "استلام الشحنات والمخزون", navReceiving);
    }

    @FXML
    public void showAuditLog() {
        swap("AuditLog.fxml", "سجل العمليات والرقابة", navAuditLog);
    }

    @FXML
    public void showUsers() {
        swap("Users.fxml", "إدارة حسابات الموظفين", navUsers);
    }

    @FXML
    public void showSettings() {
        swap("Settings.fxml", "الإعدادات والنسخ الاحتياطي", navSettings);
    }

    @FXML
    private void onToggleSidebar() {
        isSidebarCollapsed = !isSidebarCollapsed;
        if (isSidebarCollapsed) {
            sidebar.setPrefWidth(60);
            sidebar.setMaxWidth(60);
            sidebarBrandLabel.setVisible(false);
            sidebarBrandLabel.setManaged(false);
            
            // Set collapsed icons-only text
            navWelcome.setText("🏠");
            navDashboard.setText("📊");
            navParts.setText("⚙️");
            navInvoices.setText("📄");
            if (navCustomerLedger != null) navCustomerLedger.setText("📒");
            navSuppliers.setText("🤝");
            navReceiving.setText("📥");
            navAuditLog.setText("📋");
            navUsers.setText("👥");
            if (navSettings != null) navSettings.setText("⚙️");
            if (lowStockBadge != null) lowStockBadge.setTranslateX(40);
        } else {
            sidebar.setPrefWidth(240);
            sidebar.setMaxWidth(240);
            sidebarBrandLabel.setVisible(true);
            sidebarBrandLabel.setManaged(true);
            
            // Set full Arabic labels
            navWelcome.setText("🏠  الرئيسية");
            navDashboard.setText("📊  لوحة التحكم");
            navParts.setText("⚙️  إدارة الأصناف");
            navInvoices.setText("📄  الفواتير والمبيعات");
            if (navCustomerLedger != null) navCustomerLedger.setText("📒  حسابات العملاء والآجل");
            navSuppliers.setText("🤝  إدارة الموردين");
            navReceiving.setText("📥  استلام المخزون");
            navAuditLog.setText("📋  سجل العمليات");
            navUsers.setText("👥  إدارة المستخدمين");
            if (navSettings != null) navSettings.setText("⚙️  الإعدادات والنسخ الاحتياطي");
            if (lowStockBadge != null) lowStockBadge.setTranslateX(200);
        }
    }

    @FXML
    private void onToggleTheme() {
        Node rootNode = Main.primaryStage().getScene().getRoot();
        if (rootNode.getStyleClass().contains("dark-theme")) {
            rootNode.getStyleClass().remove("dark-theme");
        } else {
            rootNode.getStyleClass().add("dark-theme");
        }
    }

    @FXML private Label searchSuggestionLabel;

    @FXML
    private void onGlobalSearchKeyReleased(javafx.scene.input.KeyEvent event) {
        String term = globalSearchField.getText();
        long now = System.currentTimeMillis();

        // Barcode scanner detection: track inter-key timing
        if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
            if (likelyBarcodeScan && term != null && !term.isBlank()) {
                handleBarcodeScan(term);
                globalSearchField.clear();
                barcodeCharCount = 0;
                likelyBarcodeScan = false;
                return;
            }
            barcodeCharCount = 0;
            likelyBarcodeScan = false;
        } else {
            long gap = now - lastKeyTime;
            if (lastKeyTime == 0 || gap > 500) {
                // New burst started — reset counters
                barcodeCharCount = 1;
                likelyBarcodeScan = true;
            } else if (gap < 100) {
                barcodeCharCount++;
                // Must have at least 4 chars all under 100ms to count as scanner
                likelyBarcodeScan = barcodeCharCount >= 4;
            } else {
                // Human typing speed — not a scanner
                barcodeCharCount = 0;
                likelyBarcodeScan = false;
            }
            lastKeyTime = now;
        }

        // Handle TAB or RIGHT arrow to autocomplete
        if ((event.getCode() == javafx.scene.input.KeyCode.TAB || event.getCode() == javafx.scene.input.KeyCode.RIGHT)
                && searchSuggestionLabel.getText() != null && !searchSuggestionLabel.getText().isBlank()) {
            globalSearchField.setText(searchSuggestionLabel.getText());
            globalSearchField.positionCaret(globalSearchField.getText().length());
            searchSuggestionLabel.setText("");
            term = globalSearchField.getText();
            event.consume();
        }

        // Propagate search to active view
        if (activeController instanceof Searchable searchable) {
            searchable.search(term);
        } else if (term != null && !term.isBlank() && !(activeController instanceof PartsController)) {
            showParts();
            if (activeController instanceof Searchable searchable) {
                searchable.search(term);
            }
        }

        // Update autocomplete suggestion
        if (term == null || term.isBlank()) {
            searchSuggestionLabel.setText("");
        } else {
            String suggestion = AppContext.get().partService.getAutocompleteSuggestion(term);
            if (suggestion != null && suggestion.toLowerCase().startsWith(term.toLowerCase())) {
                searchSuggestionLabel.setText(suggestion);
            } else {
                searchSuggestionLabel.setText("");
            }
        }
    }

    /**
     * Called when barcode scanner input is detected (fast burst ending with Enter).
     * Searches for exact match and opens Part Detail Panel.
     */
    private void handleBarcodeScan(String rawBarcode) {
        com.kiastore.model.Part found = AppContext.get().partService.findByBarcode(rawBarcode);
        if (found != null) {
            // Navigate to Parts screen and open detail panel for matched part
            showParts();
            javafx.application.Platform.runLater(() -> {
                if (activeController instanceof PartsController partsCtrl) {
                    partsCtrl.openPartDetail(found);
                }
            });
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("نتيجة مسح الباركود");
            alert.setHeaderText("لم يتم العثور على القطعة");
            alert.setContentText("لا توجد قطعة مسجلة بالباركود: " + rawBarcode);
            alert.show();
        }
    }

    @FXML
    private void onLogout() {
        Session.clear();
        try {
            Main.setRoot("Login.fxml", "KIA Store ERP - Login");
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "فشل تسجيل الخروج: " + e.getMessage()).showAndWait();
        }
    }

    private void swap(String fxml, String title, Button active) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxml));
            Node node = loader.load();
            activeController = loader.getController();
            
            // Smooth fade-in animation
            FadeTransition ft = new FadeTransition(Duration.millis(250), node);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();

            contentArea.getChildren().setAll(node);
            pageTitle.setText(title);
            setActive(active);
            
            // Sync current search field text if swapped to a searchable screen
            if (activeController instanceof Searchable searchable) {
                searchable.search(globalSearchField.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "فشل تحميل الواجهة " + fxml + ":\n" + e.getMessage()).showAndWait();
        }
    }

    public Object getActiveController() {
        return activeController;
    }

    private void setActive(Button b) {
        if (activeButton != null) activeButton.getStyleClass().remove("active");
        if (b != null && !b.getStyleClass().contains("active")) b.getStyleClass().add("active");
        activeButton = b;
    }

    /**
     * Starts background low-stock checking on app start and every 30 minutes.
     * Never blocks the UI thread.
     */
    private void startLowStockChecking() {
        lowStockScheduler.scheduleAtFixedRate(() -> {
            try {
                java.util.List<com.kiastore.model.Part> lowStockParts =
                    AppContext.get().partService.lowStock();
                javafx.application.Platform.runLater(() -> updateLowStockUI(lowStockParts));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 30, java.util.concurrent.TimeUnit.MINUTES);
    }

    private void updateLowStockUI(java.util.List<com.kiastore.model.Part> lowStockParts) {
        int count = lowStockParts.size();
        if (lowStockBadge != null) {
            if (count > 0) {
                lowStockBadge.setText(String.valueOf(count));
                lowStockBadge.setVisible(true);
                // Show toast notification popup
                new NotificationPopup(lowStockParts, this::showPartsFiltered);
            } else {
                lowStockBadge.setVisible(false);
            }
        }
    }

    /**
     * Navigate to Parts screen and apply low-stock filter.
     */
    public void showPartsFiltered() {
        showParts();
        javafx.application.Platform.runLater(() -> {
            if (activeController instanceof PartsController partsCtrl) {
                partsCtrl.filterLowStockOnly();
            }
        });
    }

    /**
     * Interface for sub-views that support live top-bar searching.
     */
    public interface Searchable {
        void search(String query);
    }
}
