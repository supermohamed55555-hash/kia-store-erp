package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.model.Invoice;
import com.kiastore.model.InvoiceItem;
import com.kiastore.model.Part;
import com.kiastore.model.Return;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private ComboBox<String> periodComboBox;
    @FXML private Label invoicesTitleLabel;
    @FXML private Label salesTitleLabel;

    // 5 stat cards
    @FXML private Label totalPartsCount;
    @FXML private Label lowStockPartsCount;
    @FXML private Label inventoryValue;
    @FXML private Label todayInvoicesCount;
    @FXML private Label todaySalesAmount;

    // Trend labels
    @FXML private Label totalPartsTrend;
    @FXML private Label lowStockPartsTrend;
    @FXML private Label inventoryValueTrend;
    @FXML private Label todayInvoicesTrend;
    @FXML private Label todaySalesTrend;

    @FXML private LineChart<String, Number> salesTrendChart;
    @FXML private BarChart<String, Number> topPartsChart;
    @FXML private PieChart paymentMethodChart;

    // Legacy secondary labels (may or may not be in FXML, null-safe)
    @FXML private Label activeInvoicesLabel;
    @FXML private Label cancelledInvoicesLabel;
    @FXML private Label returnedAmountLabel;
    @FXML private Label profitAmountLabel;

    private List<Invoice> invoices;
    private List<Return> returns;
    private List<Part> parts;

    // ── 5-minute dashboard cache ──────────────────────────────────────────
    private static List<Invoice> cachedInvoices;
    private static List<Return>  cachedReturns;
    private static List<Part>    cachedParts;
    private static long          cacheTimestamp = 0;
    private static final long    CACHE_TTL_MS   = 5 * 60 * 1_000L; // 5 minutes

    @FXML
    public void initialize() {
        periodComboBox.setItems(FXCollections.observableArrayList(
            "اليوم (Today)", "آخر 7 أيام (Last 7 Days)", "آخر 30 يوم (Last 30 Days)", "كل الأوقات (All Time)"
        ));
        periodComboBox.setValue("كل الأوقات (All Time)");

        loadData(false);   // use cache if still fresh
        renderDashboard();
    }

    /**
     * Loads (or reuses cached) dashboard data.
     * @param forceRefresh  true → always hit the DB; false → use cache if < 5 min old
     */
    private void loadData(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedInvoices != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            // Cache hit — reuse stored data
            invoices = cachedInvoices;
            returns  = cachedReturns;
            parts    = cachedParts;
            return;
        }
        // Cache miss or forced refresh — query DB
        invoices = AppContext.get().invoiceService.all();
        returns  = AppContext.get().returnDao.findAll();
        parts    = AppContext.get().partService.all();
        // Store in cache
        cachedInvoices  = invoices;
        cachedReturns   = returns;
        cachedParts     = parts;
        cacheTimestamp  = System.currentTimeMillis();
    }

    /** Clears the static cache so next open forces a fresh DB load. */
    public static void invalidateCache() {
        cacheTimestamp = 0;
    }


    private void renderDashboard() {
        // Filter invoices based on selected period
        String period = periodComboBox.getValue();
        LocalDateTime threshold = getThresholdDate(period);

        List<Invoice> filteredInvoices = invoices.stream()
                .filter(i -> i.getCreatedAt().isAfter(threshold))
                .collect(Collectors.toList());

        List<Return> filteredReturns = returns.stream()
                .filter(r -> r.getReturnedAt().isAfter(threshold))
                .collect(Collectors.toList());

        // 1. Static metrics (not period-filtered)
        AppContext ctx = AppContext.get();
        int partsCount = ctx.partService.countAll();
        totalPartsCount.setText(String.valueOf(partsCount));
        totalPartsTrend.setText("✓ أصناف نشطة");
        totalPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        int lowCount = ctx.partService.lowStock().size();
        lowStockPartsCount.setText(String.valueOf(lowCount));
        if (lowCount > 0) {
            lowStockPartsTrend.setText("⚠️ " + lowCount + " تحت حد الطلب");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-down");
        } else {
            lowStockPartsTrend.setText("✓ المخزون سليم");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-up");
        }

        double invVal = ctx.partService.getInventoryValue();
        inventoryValue.setText(String.format(java.util.Locale.US, "%,.2f EGP", invVal));
        inventoryValueTrend.setText("✓ قيمة سعر البيع");
        inventoryValueTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        // 2. Period-sensitive metrics
        double totalRevenue = filteredInvoices.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .mapToDouble(Invoice::getFinalAmount)
                .sum();

        long invoicesCount = filteredInvoices.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .count();

        boolean isToday = "اليوم (Today)".equals(period);

        // Update card titles for non-today periods
        String invoicesTitle = isToday ? "فواتير اليوم" : "الفواتير - الفترة";
        String salesTitle = isToday ? "مبيعات اليوم" : "مبيعات الفترة";
        if (invoicesTitleLabel != null) invoicesTitleLabel.setText(invoicesTitle);
        if (salesTitleLabel != null) salesTitleLabel.setText(salesTitle);

        todayInvoicesCount.setText(String.valueOf(invoicesCount));
        todaySalesAmount.setText(String.format(java.util.Locale.US, "%,.2f EGP", totalRevenue));

        if (isToday) {
            // Compare with yesterday
            int todayInv = ctx.invoiceService.countToday();
            int yestInv = ctx.invoiceService.countYesterday();
            double salesDiff = yesterdaySales(ctx);
            double invDiffPct = yestInv == 0 ? (todayInv > 0 ? 100.0 : 0.0)
                    : ((double)(todayInv - yestInv) / yestInv) * 100.0;
            setTrendLabel(todayInvoicesTrend, invDiffPct);

            double todaySales = ctx.invoiceService.totalSalesToday();
            double yestSales = ctx.invoiceService.totalSalesYesterday();
            double salesDiffPct = yestSales == 0 ? (todaySales > 0 ? 100.0 : 0.0)
                    : ((todaySales - yestSales) / yestSales) * 100.0;
            setTrendLabel(todaySalesTrend, salesDiffPct);
        } else {
            todayInvoicesTrend.setText("");
            todaySalesTrend.setText("");
        }

        // Calculate and set secondary stats & profits
        long activeCount = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE || i.getStatus() == Invoice.Status.RETURNED)
                .count();

        long cancelledCount = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.CANCELLED)
                .count();

        double returnedAmount = 0.0;
        for (Return r : filteredReturns) {
            List<InvoiceItem> items = AppContext.get().invoiceService.getItems(r.getInvoiceId());
            double unitPrice = items.stream()
                    .filter(ii -> ii.getPartId() == r.getPartId())
                    .mapToDouble(InvoiceItem::getUnitPrice)
                    .findFirst()
                    .orElse(0.0);
            returnedAmount += unitPrice * r.getQuantity();
        }

        List<Integer> activeInvoiceIds = filteredInvoices.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .map(Invoice::getId)
                .collect(Collectors.toList());

        java.util.Map<Integer, List<InvoiceItem>> itemsMap =
                AppContext.get().invoiceItemDao.findAllForInvoices(activeInvoiceIds);

        double totalCost = 0.0;
        for (List<InvoiceItem> items : itemsMap.values()) {
            for (InvoiceItem item : items) {
                totalCost += item.getPurchasePrice() * item.getQuantity();
            }
        }

        double returnedCost = 0.0;
        for (Return r : filteredReturns) {
            Part p = parts.stream().filter(pt -> pt.getId() == r.getPartId()).findFirst().orElse(null);
            double cost = p != null ? p.getPurchasePrice() : 0.0;
            returnedCost += cost * r.getQuantity();
        }

        double netRevenue = totalRevenue - returnedAmount;
        double netCost = Math.max(0.0, totalCost - returnedCost);
        double totalProfit = Math.max(0.0, netRevenue - netCost);

        if (activeInvoicesLabel != null) activeInvoicesLabel.setText(String.valueOf(activeCount));
        if (cancelledInvoicesLabel != null) cancelledInvoicesLabel.setText(String.valueOf(cancelledCount));
        if (returnedAmountLabel != null) returnedAmountLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", returnedAmount));
        if (profitAmountLabel != null) profitAmountLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", totalProfit));

        // 3. Render Charts
        renderSalesTrendChart(filteredInvoices, period);
        renderTopPartsChart(filteredInvoices);
        renderPaymentMethodChart(filteredInvoices);
    }

    private LocalDateTime getThresholdDate(String period) {
        if (period == null) return LocalDateTime.MIN;
        return switch (period) {
            case "اليوم (Today)" -> LocalDate.now().atStartOfDay();
            case "آخر 7 أيام (Last 7 Days)" -> LocalDate.now().minusDays(7).atStartOfDay();
            case "آخر 30 يوم (Last 30 Days)" -> LocalDate.now().minusDays(30).atStartOfDay();
            default -> LocalDateTime.MIN;
        };
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

    private double yesterdaySales(AppContext ctx) {
        return ctx.invoiceService.totalSalesYesterday();
    }

    private void renderSalesTrendChart(List<Invoice> filtered, String period) {
        salesTrendChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        Map<String, Double> salesMap = new java.util.LinkedHashMap<>();
        DateTimeFormatter dtf;

        if ("اليوم (Today)".equals(period)) {
            dtf = DateTimeFormatter.ofPattern("HH:00");
            // Populate even hours from 08:00 to 22:00
            for (int h = 8; h <= 22; h += 2) {
                salesMap.put(String.format("%02d:00", h), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() != Invoice.Status.CANCELLED) {
                    int hour = i.getCreatedAt().getHour();
                    int bucketHour = (hour / 2) * 2;
                    String key = String.format("%02d:00", bucketHour);
                    if (salesMap.containsKey(key)) {
                        salesMap.put(key, salesMap.get(key) + i.getFinalAmount());
                    }
                }
            }
        } else if ("آخر 7 أيام (Last 7 Days)".equals(period)) {
            dtf = DateTimeFormatter.ofPattern("MM/dd");
            LocalDate today = LocalDate.now();
            for (int d = 6; d >= 0; d--) {
                salesMap.put(today.minusDays(d).format(dtf), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() != Invoice.Status.CANCELLED) {
                    String key = i.getCreatedAt().format(dtf);
                    if (salesMap.containsKey(key)) {
                        salesMap.put(key, salesMap.get(key) + i.getFinalAmount());
                    }
                }
            }
        } else if ("آخر 30 يوم (Last 30 Days)".equals(period)) {
            dtf = DateTimeFormatter.ofPattern("MM/dd");
            LocalDate today = LocalDate.now();
            for (int d = 29; d >= 0; d--) {
                salesMap.put(today.minusDays(d).format(dtf), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() != Invoice.Status.CANCELLED) {
                    String key = i.getCreatedAt().format(dtf);
                    if (salesMap.containsKey(key)) {
                        salesMap.put(key, salesMap.get(key) + i.getFinalAmount());
                    }
                }
            }
        } else {
            // All time: group by month-year for the last 6 months
            dtf = DateTimeFormatter.ofPattern("yyyy/MM");
            LocalDate start = LocalDate.now().minusMonths(5);
            for (int m = 0; m < 6; m++) {
                salesMap.put(start.plusMonths(m).format(dtf), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() != Invoice.Status.CANCELLED) {
                    String key = i.getCreatedAt().format(dtf);
                    salesMap.put(key, salesMap.getOrDefault(key, 0.0) + i.getFinalAmount());
                }
            }
        }

        salesMap.forEach((key, val) -> {
            series.getData().add(new XYChart.Data<>(key, val));
        });

        salesTrendChart.getData().add(series);
    }

    private void renderTopPartsChart(List<Invoice> filtered) {
        topPartsChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // Collect all invoice IDs in one shot, then fetch all items in a SINGLE query (fixes N+1)
        List<Integer> invoiceIds = filtered.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .map(Invoice::getId)
                .collect(Collectors.toList());

        java.util.Map<Integer, List<InvoiceItem>> itemsMap =
                AppContext.get().invoiceItemDao.findAllForInvoices(invoiceIds);

        // Map: Part ID → Quantity Sold
        Map<Integer, Integer> qtyMap = new HashMap<>();
        for (List<InvoiceItem> items : itemsMap.values()) {
            for (InvoiceItem item : items) {
                qtyMap.put(item.getPartId(), qtyMap.getOrDefault(item.getPartId(), 0) + item.getQuantity());
            }
        }

        // Load names & build top 5
        qtyMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .forEach(entry -> {
                    Part p = parts.stream().filter(pt -> pt.getId() == entry.getKey()).findFirst().orElse(null);
                    String label = p != null ? p.getPartType() + " " + p.getCarModel() : "ID: " + entry.getKey();
                    if (label.length() > 14) label = label.substring(0, 12) + "..";
                    series.getData().add(new XYChart.Data<>(label, entry.getValue()));
                });

        topPartsChart.getData().add(series);
    }

    private void renderPaymentMethodChart(List<Invoice> filtered) {
        paymentMethodChart.getData().clear();

        long cashCount = filtered.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.CASH).count();
        long cardCount = filtered.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.CARD).count();
        long otherCount = filtered.stream()
                .filter(i -> i.getStatus() != Invoice.Status.CANCELLED)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.OTHER).count();

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        if (cashCount > 0) pieData.add(new PieChart.Data("نقدي (CASH) - " + cashCount, cashCount));
        if (cardCount > 0) pieData.add(new PieChart.Data("فيزا (CARD) - " + cardCount, cardCount));
        if (otherCount > 0) pieData.add(new PieChart.Data("آخر (OTHER) - " + otherCount, otherCount));

        paymentMethodChart.setData(pieData);
    }

    @FXML
    private void onPeriodChanged() {
        // Period changed by user → force fresh DB load, bypass cache
        loadData(true);
        renderDashboard();
    }

    @FXML
    private void onShowEndOfDayReport() {
        javafx.stage.Stage owner = (javafx.stage.Stage) periodComboBox.getScene().getWindow();
        EndOfDayReportDialog dialog = new EndOfDayReportDialog(owner);
        dialog.show();
    }
}
