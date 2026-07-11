package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.AuditLog;
import com.kiastore.model.Role;
import com.kiastore.model.User;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WelcomeController {

    @FXML private Label welcomeDateTimeLabel;
    @FXML private Label totalPartsCount;
    @FXML private Label lowStockPartsCount;
    @FXML private Label inventoryValue;
    @FXML private Label todayInvoicesCount;
    @FXML private Label todaySalesAmount;

    @FXML private Label totalPartsTrend;
    @FXML private Label lowStockPartsTrend;
    @FXML private Label inventoryValueTrend;
    @FXML private Label todayInvoicesTrend;
    @FXML private Label todaySalesTrend;

    @FXML private BarChart<String, Number> salesComparisonChart;
    @FXML private VBox activityFeedVBox;

    @FXML private Button btnQuickInvoice;
    @FXML private Button btnQuickReceive;

    @FXML
    public void initialize() {
        // Set Arabic current date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", new java.util.Locale("ar"));
        welcomeDateTimeLabel.setText(LocalDateTime.now().format(dtf));

        // Load stats from database
        loadStatistics();

        // Render Today vs Yesterday Sales Chart
        loadMiniChart();

        // Load Audit Logs Feed
        loadActivityFeed();

        // Apply role permissions for quick actions
        applyRoleRestrictions();
    }

    private void loadStatistics() {
        AppContext ctx = AppContext.get();
        
        // 1. Total Parts
        int partsCount = ctx.partService.countAll();
        totalPartsCount.setText(String.valueOf(partsCount));
        totalPartsTrend.setText("✓ أصناف نشطة");
        totalPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        // 2. Low Stock
        int lowStockCount = ctx.partService.lowStock().size();
        lowStockPartsCount.setText(String.valueOf(lowStockCount));
        if (lowStockCount > 0) {
            lowStockPartsTrend.setText("⚠️ " + lowStockCount + " تحت حد الطلب");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-down");
        } else {
            lowStockPartsTrend.setText("✓ المخزون سليم");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-up");
        }

        // 3. Inventory Value
        double val = ctx.partService.getInventoryValue();
        inventoryValue.setText(String.format(java.util.Locale.US, "%,.2f EGP", val));
        inventoryValueTrend.setText("✓ قيمة سعر البيع");
        inventoryValueTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        // 4. Today's Invoices
        int todayInvoices = ctx.invoiceService.countToday();
        int yesterdayInvoices = ctx.invoiceService.countYesterday();
        todayInvoicesCount.setText(String.valueOf(todayInvoices));
        double invDiffPercent = yesterdayInvoices == 0 
            ? (todayInvoices > 0 ? 100.0 : 0.0) 
            : ((double)(todayInvoices - yesterdayInvoices) / yesterdayInvoices) * 100.0;
        setTrendLabel(todayInvoicesTrend, invDiffPercent);

        // 5. Today's Sales
        double todaySales = ctx.invoiceService.totalSalesToday();
        double yesterdaySales = ctx.invoiceService.totalSalesYesterday();
        todaySalesAmount.setText(String.format(java.util.Locale.US, "%,.2f EGP", todaySales));
        double salesDiffPercent = yesterdaySales == 0 
            ? (todaySales > 0 ? 100.0 : 0.0) 
            : ((todaySales - yesterdaySales) / yesterdaySales) * 100.0;
        setTrendLabel(todaySalesTrend, salesDiffPercent);
    }

    private void setTrendLabel(Label label, double pct) {
        if (pct > 0) {
            label.setText(String.format(java.util.Locale.US, "▲ +%.1f%% مقارنة بالأمس", pct));
            label.getStyleClass().setAll("stat-trend", "stat-trend-up");
        } else if (pct < 0) {
            label.setText(String.format(java.util.Locale.US, "▼ %.1f%% مقارنة بالأمس", pct));
            label.getStyleClass().setAll("stat-trend", "stat-trend-down");
        } else {
            label.setText("— لا تغيير عن الأمس");
            label.getStyleClass().setAll("stat-trend", "stat-trend-neutral");
        }
    }

    private void loadMiniChart() {
        AppContext ctx = AppContext.get();
        double today = ctx.invoiceService.totalSalesToday();
        double yesterday = ctx.invoiceService.totalSalesYesterday();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("الأمس (Yesterday)", yesterday));
        series.getData().add(new XYChart.Data<>("اليوم (Today)", today));

        salesComparisonChart.getData().clear();
        salesComparisonChart.getData().add(series);
    }

    private void loadActivityFeed() {
        activityFeedVBox.getChildren().clear();
        AppContext ctx = AppContext.get();
        List<AuditLog> recentLogs = ctx.auditLogService.recent(10);

        if (recentLogs.isEmpty()) {
            Label placeholder = new Label("لا يوجد عمليات مسجلة حالياً");
            placeholder.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 13; -fx-text-fill: #A0A0A0; -fx-padding: 10;");
            activityFeedVBox.getChildren().add(placeholder);
            return;
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a | dd/MM", new java.util.Locale("ar"));

        int index = 0;
        for (AuditLog log : recentLogs) {
            HBox row = new HBox();
            row.setSpacing(15);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            // Alternating background
            if (index % 2 == 0) {
                row.getStyleClass().add("activity-item-even");
            } else {
                row.getStyleClass().add("activity-item-odd");
            }
            index++;
            
            // Colored dot indicator based on action type
            Region dot = new Region();
            dot.getStyleClass().add("activity-dot");
            String action = log.getAction().toUpperCase();
            if (action.contains("CREATE") || action.contains("INSERT")) {
                dot.getStyleClass().add("activity-dot-green");
            } else if (action.contains("UPDATE") || action.contains("EDIT") || action.contains("CANCEL")) {
                dot.getStyleClass().add("activity-dot-blue");
            } else if (action.contains("DELETE")) {
                dot.getStyleClass().add("activity-dot-red");
            } else {
                dot.getStyleClass().add("activity-dot-orange"); // e.g. login
            }
            
            VBox details = new VBox();
            details.setSpacing(3);
            HBox.setHgrow(details, Priority.ALWAYS);
            
            Label desc = new Label(getActivityDescription(log));
            desc.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #363636;");
            
            Label meta = new Label("بواسطة: " + log.getUserName() + " | الجدول: " + log.getTableName());
            meta.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11; -fx-text-fill: #707070;");
            
            details.getChildren().addAll(desc, meta);

            Label timeLabel = new Label(log.getCreatedAt() != null ? log.getCreatedAt().format(timeFormatter) : "-");
            timeLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12; -fx-text-fill: #999999;");
            
            row.getChildren().addAll(dot, details, timeLabel);
            activityFeedVBox.getChildren().add(row);
        }
    }

    private String getActivityDescription(AuditLog log) {
        String action = log.getAction().toUpperCase();
        String table = log.getTableName().toLowerCase();
        
        return switch (action) {
            case "LOGIN" -> "قام بتسجيل الدخول إلى النظام";
            case "CREATE" -> "أضاف صنفاً جديداً في جدول " + getTableArabicName(table) + " (ID: " + log.getRecordId() + ")";
            case "UPDATE" -> "قام بتحديث صنف في " + getTableArabicName(table) + " (ID: " + log.getRecordId() + ")";
            case "DELETE" -> "حذف صنف من " + getTableArabicName(table) + " (ID: " + log.getRecordId() + ")";
            case "INVOICE_CREATE" -> "أصدر فاتورة مبيعات جديدة رقم " + log.getNewData();
            case "INVOICE_CANCEL" -> "ألغى فاتورة مبيعات رقم ID: " + log.getRecordId();
            case "RETURN" -> "سجل عملية إرجاع قطع غيار للفاتورة ID: " + log.getRecordId();
            default -> "عملية " + action + " على " + table;
        };
    }

    private String getTableArabicName(String table) {
        return switch (table) {
            case "parts" -> "قطع الغيار";
            case "users" -> "الموظفين";
            case "suppliers" -> "الموردين";
            case "batches" -> "شحنات المخزن";
            case "invoices" -> "الفواتير";
            case "returns" -> "المرتجع";
            default -> table;
        };
    }

    private void applyRoleRestrictions() {
        User u = Session.current();
        if (u != null) {
            boolean canSell = u.getRole() == Role.ADMIN || u.getRole() == Role.CASHIER;
            boolean canReceive = u.getRole() == Role.ADMIN || u.getRole() == Role.WAREHOUSE;

            btnQuickInvoice.setVisible(canSell);
            btnQuickInvoice.setManaged(canSell);

            btnQuickReceive.setVisible(canReceive);
            btnQuickReceive.setManaged(canReceive);
        }
    }

    @FXML
    private void onQuickInvoice() {
        MainShellController.getInstance().showInvoices();
    }

    @FXML
    private void onQuickReceive() {
        MainShellController.getInstance().showReceiving();
    }

    @FXML
    private void onQuickSearch() {
        MainShellController.getInstance().showParts();
    }
}
