package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.model.Invoice;
import com.kiastore.model.InvoiceItem;
import com.kiastore.model.Part;
import com.kiastore.model.Return;
import com.kiastore.db.ConnectionFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    // Redesigned charts
    @FXML private Canvas salesTrendCanvas;
    @FXML private BarChart<String, Number> topPartsChart;
    @FXML private Canvas donutCanvas;
    @FXML private HBox donutLegendBox;

    // Horizontal statistics bars
    @FXML private Label profitAmountLabel;
    @FXML private Region profitBarFill;
    @FXML private Label returnedAmountLabel;
    @FXML private Region returnedBarFill;
    @FXML private Label cancelledInvoicesLabel;
    @FXML private Region cancelledBarFill;

    private List<Invoice> invoices;
    private List<Return> returns;
    private List<Part> parts;

    // ── 5-minute dashboard cache ──────────────────────────────────────────
    private static List<Invoice> cachedInvoices;
    private static List<Return>  cachedReturns;
    private static List<Part>    cachedParts;
    private static long          cacheTimestamp = 0;
    private static final long    CACHE_TTL_MS   = 5 * 60 * 1_000L; // 5 minutes

    private static int lastInvoiceCount = -1;
    private static int lastMaxInvoiceId = -1;
    private static int lastReturnCount = -1;
    private static int lastMaxReturnId = -1;
    private static int lastPartCount = -1;
    private static int lastMaxPartId = -1;

    private final DoubleProperty donutAnimationScale = new SimpleDoubleProperty(0.0);
    private final Tooltip lineChartTooltip = new Tooltip();

    private static boolean isCacheStale() {
        String sql = "SELECT " +
                     "(SELECT COUNT(*) FROM invoices), (SELECT COALESCE(MAX(id), 0) FROM invoices), " +
                     "(SELECT COUNT(*) FROM returns), (SELECT COALESCE(MAX(id), 0) FROM returns), " +
                     "(SELECT COUNT(*) FROM parts), (SELECT COALESCE(MAX(id), 0) FROM parts)";
        try (Connection c = ConnectionFactory.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int invCount = rs.getInt(1);
                int maxInvId = rs.getInt(2);
                int retCount = rs.getInt(3);
                int maxRetId = rs.getInt(4);
                int partCount = rs.getInt(5);
                int maxPartId = rs.getInt(6);

                if (invCount != lastInvoiceCount || maxInvId != lastMaxInvoiceId ||
                    retCount != lastReturnCount || maxRetId != lastMaxReturnId ||
                    partCount != lastPartCount || maxPartId != lastMaxPartId) {
                    
                    lastInvoiceCount = invCount;
                    lastMaxInvoiceId = maxInvId;
                    lastReturnCount = retCount;
                    lastMaxReturnId = maxRetId;
                    lastPartCount = partCount;
                    lastMaxPartId = maxPartId;
                    return true;
                }
            }
        } catch (Exception e) {
            return true; // default to stale on error
        }
        return false;
    }

    @FXML
    public void initialize() {
        periodComboBox.setItems(FXCollections.observableArrayList(
            "اليوم (Today)", "آخر 7 أيام (Last 7 Days)", "آخر 30 يوم (Last 30 Days)", "كل الأوقات (All Time)"
        ));
        periodComboBox.setValue("كل الأوقات (All Time)");

        // Initialize tooltip styles
        lineChartTooltip.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12px;");

        loadData(false);   // use cache if still fresh
        renderDashboard();
    }

    private void loadData(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        boolean stale = isCacheStale();
        if (!forceRefresh && !stale && cachedInvoices != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            invoices = cachedInvoices;
            returns  = cachedReturns;
            parts    = cachedParts;
            return;
        }
        invoices = AppContext.get().invoiceService.all();
        returns  = AppContext.get().returnDao.findAll();
        parts    = AppContext.get().partService.all();
        cachedInvoices  = invoices;
        cachedReturns   = returns;
        cachedParts     = parts;
        cacheTimestamp  = System.currentTimeMillis();
    }

    public static void invalidateCache() {
        cacheTimestamp = 0;
    }

    private void renderDashboard() {
        String period = periodComboBox.getValue();
        LocalDateTime threshold = getThresholdDate(period);

        // Filter invoices strictly by period
        List<Invoice> filteredInvoices = invoices.stream()
                .filter(i -> i.getCreatedAt().isAfter(threshold))
                .collect(Collectors.toList());

        List<Return> filteredReturns = returns.stream()
                .filter(r -> r.getReturnedAt().isAfter(threshold))
                .collect(Collectors.toList());

        // 1. Static metrics (not period-filtered)
        AppContext ctx = AppContext.get();
        int partsCount = (int) parts.stream().filter(Part::isActive).count();
        totalPartsCount.setText(String.format(java.util.Locale.US, "%,d", partsCount));
        totalPartsTrend.setText("✓ أصناف نشطة");
        totalPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        int lowCount = (int) parts.stream().filter(p -> p.isActive() && p.getCurrentStock() <= p.getMinStock()).count();
        lowStockPartsCount.setText(String.format(java.util.Locale.US, "%,d", lowCount));
        if (lowCount > 0) {
            lowStockPartsTrend.setText("⚠️ " + lowCount + " تحت حد الطلب");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-down");
        } else {
            lowStockPartsTrend.setText("✓ المخزون سليم");
            lowStockPartsTrend.getStyleClass().setAll("stat-trend", "stat-trend-up");
        }

        // Inventory value: SUM of current_stock * sale_price (handle nulls as 0)
        double invVal = parts.stream()
                .filter(Part::isActive)
                .mapToDouble(p -> p.getCurrentStock() * (p.getSalePrice() != null ? p.getSalePrice() : 0.0))
                .sum();
        inventoryValue.setText(String.format(java.util.Locale.US, "%,.2f EGP", invVal));
        inventoryValueTrend.setText("✓ قيمة سعر البيع");
        inventoryValueTrend.getStyleClass().setAll("stat-trend", "stat-trend-neutral");

        // 2. Period-sensitive metrics (Strictly Active Invoices)
        double totalSales = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .mapToDouble(Invoice::getFinalAmount)
                .sum();

        long invoicesCount = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .count();

        boolean isToday = "اليوم (Today)".equals(period);

        // Update card titles for non-today periods
        String invoicesTitle = isToday ? "فواتير اليوم" : "الفواتير - الفترة";
        String salesTitle = isToday ? "مبيعات اليوم" : "مبيعات الفترة";
        if (invoicesTitleLabel != null) invoicesTitleLabel.setText(invoicesTitle);
        if (salesTitleLabel != null) salesTitleLabel.setText(salesTitle);

        todayInvoicesCount.setText(String.format(java.util.Locale.US, "%,d", invoicesCount));
        todaySalesAmount.setText(String.format(java.util.Locale.US, "%,.2f EGP", totalSales));

        // Timezone-aligned comparison logic (Java side JVM timezone)
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        double todaySalesValue = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getCreatedAt().toLocalDate().equals(today))
                .mapToDouble(Invoice::getFinalAmount)
                .sum();

        double yesterdaySalesValue = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getCreatedAt().toLocalDate().equals(yesterday))
                .mapToDouble(Invoice::getFinalAmount)
                .sum();

        long todayInvoicesValue = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getCreatedAt().toLocalDate().equals(today))
                .count();

        long yesterdayInvoicesValue = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getCreatedAt().toLocalDate().equals(yesterday))
                .count();

        if (isToday) {
            double salesDiffPct = yesterdaySalesValue == 0.0 ? (todaySalesValue > 0.0 ? 100.0 : 0.0)
                    : ((todaySalesValue - yesterdaySalesValue) / yesterdaySalesValue) * 100.0;
            setTrendLabel(todaySalesTrend, salesDiffPct);

            double invDiffPct = yesterdayInvoicesValue == 0 ? (todayInvoicesValue > 0 ? 100.0 : 0.0)
                    : ((double)(todayInvoicesValue - yesterdayInvoicesValue) / yesterdayInvoicesValue) * 100.0;
            setTrendLabel(todayInvoicesTrend, invDiffPct);
        } else {
            todayInvoicesTrend.setText("");
            todaySalesTrend.setText("");
        }

        // 3. Returns and profit calculations (Strictly Active Invoices)
        List<Integer> activeInvoiceIds = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .map(Invoice::getId)
                .collect(Collectors.toList());

        java.util.Map<Integer, List<InvoiceItem>> itemsMap =
                AppContext.get().invoiceItemDao.findAllForInvoices(activeInvoiceIds);

        // Net purchase cost = (Total purchase price of sold items)
        double totalPurchaseCost = 0.0;
        for (List<InvoiceItem> items : itemsMap.values()) {
            for (InvoiceItem item : items) {
                totalPurchaseCost += item.getPurchasePrice() * item.getQuantity();
            }
        }

        // Fetch items for returns to avoid N+1 queries
        List<Integer> returnedInvoiceIds = filteredReturns.stream()
                .map(Return::getInvoiceId)
                .distinct()
                .collect(Collectors.toList());
        java.util.Map<Integer, List<InvoiceItem>> returnedItemsMap =
                AppContext.get().invoiceItemDao.findAllForInvoices(returnedInvoiceIds);

        double returnedAmount = 0.0;
        double returnedCost = 0.0;
        for (Return r : filteredReturns) {
            List<InvoiceItem> items = returnedItemsMap.get(r.getInvoiceId());
            if (items != null) {
                double unitPrice = items.stream()
                        .filter(ii -> ii.getPartId() == r.getPartId())
                        .mapToDouble(InvoiceItem::getUnitPrice)
                        .findFirst()
                        .orElse(0.0);
                returnedAmount += unitPrice * r.getQuantity();
            }
            Part p = parts.stream().filter(pt -> pt.getId() == r.getPartId()).findFirst().orElse(null);
            double cost = p != null ? p.getPurchasePrice() : 0.0;
            returnedCost += cost * r.getQuantity();
        }

        double netSales = totalSales - returnedAmount;
        double netCost = Math.max(0.0, totalPurchaseCost - returnedCost);
        double netProfit = Math.max(0.0, netSales - netCost);

        // Cancelled invoices total amount in the period
        double cancelledAmount = filteredInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.CANCELLED)
                .mapToDouble(Invoice::getFinalAmount)
                .sum();

        // Update labels
        profitAmountLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", netProfit));
        returnedAmountLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", returnedAmount));
        cancelledInvoicesLabel.setText(String.format(java.util.Locale.US, "%,.2f EGP", cancelledAmount));

        // Update horizontal progress bars
        double totalSum = netProfit + returnedAmount + cancelledAmount;
        double profitPct = totalSum == 0.0 ? 0.0 : Math.max(0.0, netProfit) / totalSum;
        double returnPct = totalSum == 0.0 ? 0.0 : returnedAmount / totalSum;
        double cancelPct = totalSum == 0.0 ? 0.0 : cancelledAmount / totalSum;

        animateBarWidth(profitBarFill, profitPct);
        animateBarWidth(returnedBarFill, returnPct);
        animateBarWidth(cancelledBarFill, cancelPct);

        // 4. Render Charts
        renderSalesTrendChart(filteredInvoices, period);
        renderTopPartsChart(); // Top 5 sold today
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

    // ─── Chart 1: Top Parts Bar Chart ───
    private void renderTopPartsChart() {
        topPartsChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // Strictly active invoices today
        List<Integer> todayActiveInvoiceIds = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .map(Invoice::getId)
                .collect(Collectors.toList());

        java.util.Map<Integer, List<InvoiceItem>> itemsMap =
                AppContext.get().invoiceItemDao.findAllForInvoices(todayActiveInvoiceIds);

        Map<Integer, Integer> qtyMap = new HashMap<>();
        Map<Integer, Double> revMap = new HashMap<>();
        for (List<InvoiceItem> items : itemsMap.values()) {
            for (InvoiceItem item : items) {
                qtyMap.put(item.getPartId(), qtyMap.getOrDefault(item.getPartId(), 0) + item.getQuantity());
                revMap.put(item.getPartId(), revMap.getOrDefault(item.getPartId(), 0.0) + item.getTotalPrice());
            }
        }

        String[] colors = {"#FAA11F", "#3B82F6", "#22C55E", "#8B5CF6", "#EF4444"};
        List<Map.Entry<Integer, Integer>> sorted = qtyMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        int index = 0;
        for (Map.Entry<Integer, Integer> entry : sorted) {
            Part p = parts.stream().filter(pt -> pt.getId() == entry.getKey()).findFirst().orElse(null);
            String fullName = p != null ? p.getFullName() : ("ID: " + entry.getKey());
            String shortLabel = fullName.length() > 10 ? fullName.substring(0, 10) + "..." : fullName;
            int qty = entry.getValue();
            double rev = revMap.getOrDefault(entry.getKey(), 0.0);

            XYChart.Data<String, Number> data = new XYChart.Data<>(shortLabel, qty);
            series.getData().add(data);

            final String color = colors[index % colors.length];
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-bar-fill: " + color + "; -fx-background-color: " + color + "; -fx-background-radius: 6 6 0 0; -fx-border-radius: 6 6 0 0;");
                    Tooltip t = new Tooltip(fullName + "\nالكمية: " + qty + " قطعة\nالإيرادات: " + String.format(java.util.Locale.US, "%,.2f EGP", rev));
                    t.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12px;");
                    Tooltip.install(newNode, t);
                }
            });
            index++;
        }

        topPartsChart.getData().add(series);

        // Smooth fade-in animation on load
        topPartsChart.setOpacity(0.0);
        FadeTransition(topPartsChart, 0.0, 1.0, 600);
    }

    // ─── Chart 2: Custom Canvas Line Chart (Bezier) ───
    private void renderSalesTrendChart(List<Invoice> filtered, String period) {
        Map<String, Double> salesMap = new java.util.LinkedHashMap<>();
        DateTimeFormatter dtf;

        if ("اليوم (Today)".equals(period)) {
            dtf = DateTimeFormatter.ofPattern("HH:00");
            for (int h = 8; h <= 22; h += 2) {
                salesMap.put(String.format("%02d:00", h), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() == Invoice.Status.ACTIVE) {
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
                if (i.getStatus() == Invoice.Status.ACTIVE) {
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
                if (i.getStatus() == Invoice.Status.ACTIVE) {
                    String key = i.getCreatedAt().format(dtf);
                    if (salesMap.containsKey(key)) {
                        salesMap.put(key, salesMap.get(key) + i.getFinalAmount());
                    }
                }
            }
        } else {
            dtf = DateTimeFormatter.ofPattern("yyyy/MM");
            LocalDate start = LocalDate.now().minusMonths(5);
            for (int m = 0; m < 6; m++) {
                salesMap.put(start.plusMonths(m).format(dtf), 0.0);
            }
            for (Invoice i : filtered) {
                if (i.getStatus() == Invoice.Status.ACTIVE) {
                    String key = i.getCreatedAt().format(dtf);
                    salesMap.put(key, salesMap.getOrDefault(key, 0.0) + i.getFinalAmount());
                }
            }
        }

        double w = salesTrendCanvas.getWidth();
        double h = salesTrendCanvas.getHeight();
        GraphicsContext gc = salesTrendCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double marginLeft = 60;
        double marginRight = 20;
        double marginTop = 20;
        double marginBottom = 40;
        double chartW = w - marginLeft - marginRight;
        double chartH = h - marginTop - marginBottom;

        double maxVal = salesMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (maxVal == 0.0) maxVal = 1000.0;

        // 1. Draw horizontal grid lines (dotted, light)
        gc.setStroke(Color.web("#000000", 0.06));
        gc.setLineWidth(1.0);
        gc.setLineDashes(3.0, 3.0);
        for (int i = 0; i <= 4; i++) {
            double y = marginTop + (chartH * i / 4.0);
            gc.strokeLine(marginLeft, y, w - marginRight, y);

            // Y-axis label
            double labelVal = maxVal - (maxVal * i / 4.0);
            gc.setFill(Color.web("#707070"));
            gc.setFont(javafx.scene.text.Font.font("Inter", 10));
            gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
            gc.fillText(String.format(java.util.Locale.US, "%,.0f", labelVal), marginLeft - 10, y + 4);
        }
        gc.setLineDashes((double[]) null);

        // Draw points coordinates
        int numPoints = salesMap.size();
        double stepX = numPoints > 1 ? chartW / (numPoints - 1) : chartW;
        List<String> keys = new ArrayList<>(salesMap.keySet());
        List<Double> values = new ArrayList<>(salesMap.values());
        List<Point2D> points = new ArrayList<>();

        for (int i = 0; i < numPoints; i++) {
            double x = marginLeft + (i * stepX);
            double y = marginTop + chartH - (values.get(i) / maxVal * chartH);
            points.add(new Point2D(x, y));

            // X-axis Label
            gc.setFill(Color.web("#707070"));
            gc.setFont(javafx.scene.text.Font.font("Tajawal", 9));
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.fillText(keys.get(i), x, h - 15);
        }

        if (numPoints > 0) {
            // 2. Bezier area fill with LinearGradient
            gc.beginPath();
            gc.moveTo(points.get(0).getX(), marginTop + chartH);
            gc.lineTo(points.get(0).getX(), points.get(0).getY());
            for (int i = 0; i < numPoints - 1; i++) {
                Point2D p1 = points.get(i);
                Point2D p2 = points.get(i + 1);
                double xc = (p1.getX() + p2.getX()) / 2.0;
                gc.quadraticCurveTo(p1.getX(), p1.getY(), xc, (p1.getY() + p2.getY()) / 2.0);
            }
            gc.lineTo(points.get(numPoints - 1).getX(), points.get(numPoints - 1).getY());
            gc.lineTo(points.get(numPoints - 1).getX(), marginTop + chartH);
            gc.closePath();

            LinearGradient lg = new LinearGradient(0, marginTop, 0, marginTop + chartH, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#FAA11F", 0.40)),
                new Stop(1, Color.web("#FAA11F", 0.00))
            );
            gc.setFill(lg);
            gc.fill();

            // 3. Draw curved bezier line
            gc.beginPath();
            gc.moveTo(points.get(0).getX(), points.get(0).getY());
            for (int i = 0; i < numPoints - 1; i++) {
                Point2D p1 = points.get(i);
                Point2D p2 = points.get(i + 1);
                double xc = (p1.getX() + p2.getX()) / 2.0;
                gc.quadraticCurveTo(p1.getX(), p1.getY(), xc, (p1.getY() + p2.getY()) / 2.0);
            }
            gc.lineTo(points.get(numPoints - 1).getX(), points.get(numPoints - 1).getY());
            gc.setStroke(Color.web("#FAA11F"));
            gc.setLineWidth(2.5);
            gc.stroke();

            // 4. Draw point markers
            for (Point2D p : points) {
                gc.setFill(Color.WHITE);
                gc.setStroke(Color.web("#FAA11F"));
                gc.setLineWidth(2.0);
                gc.fillOval(p.getX() - 4, p.getY() - 4, 8, 8);
                gc.strokeOval(p.getX() - 4, p.getY() - 4, 8, 8);
            }
        }

        // Setup hover tooltip
        salesTrendCanvas.setOnMouseMoved(e -> {
            double mx = e.getX();
            int closest = -1;
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) {
                double dist = Math.abs(points.get(i).getX() - mx);
                if (dist < minDist && dist < 30) {
                    minDist = dist;
                    closest = i;
                }
            }

            if (closest != -1) {
                lineChartTooltip.setText(keys.get(closest) + "\nالمبيعات: " + String.format(java.util.Locale.US, "%,.2f EGP", values.get(closest)));
                lineChartTooltip.show(salesTrendCanvas, e.getScreenX() + 15, e.getScreenY() + 15);
            } else {
                lineChartTooltip.hide();
            }
        });
        salesTrendCanvas.setOnMouseExited(e -> lineChartTooltip.hide());

        // Fade in animation
        salesTrendCanvas.setOpacity(0.0);
        FadeTransition(salesTrendCanvas, 0.0, 1.0, 600);
    }

    // ─── Chart 3: Custom Donut Chart ───
    private void renderPaymentMethodChart(List<Invoice> filtered) {
        long cashCount = filtered.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.CASH).count();
        long cardCount = filtered.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.CARD).count();
        long creditCount = filtered.stream()
                .filter(i -> i.getStatus() == Invoice.Status.ACTIVE)
                .filter(i -> i.getPaymentMethod() == Invoice.PaymentMethod.CREDIT).count();

        long total = cashCount + cardCount + creditCount;

        donutLegendBox.getChildren().clear();
        if (total > 0) {
            addLegendItem("نقدي (CASH)", cashCount, (double) cashCount / total, "#22C55E");
            addLegendItem("فيزا (CARD)", cardCount, (double) cardCount / total, "#3B82F6");
            addLegendItem("آجل (CREDIT)", creditCount, (double) creditCount / total, "#FAA11F");
        } else {
            Label noData = new Label("لا توجد بيانات للفترة المحددة");
            noData.setStyle("-fx-font-family: 'Tajawal'; -fx-text-fill: #707070;");
            donutLegendBox.getChildren().add(noData);
        }

        // Animate scale
        donutAnimationScale.set(0.0);
        KeyFrame kf = new KeyFrame(
            Duration.millis(600),
            new KeyValue(donutAnimationScale, 1.0, Interpolator.EASE_BOTH)
        );
        Timeline tl = new Timeline(kf);
        donutAnimationScale.addListener((obs, oldV, newV) -> {
            drawDonut(cashCount, cardCount, creditCount, total, newV.doubleValue());
        });
        tl.play();
    }

    private void drawDonut(long cash, long card, long credit, long total, double scale) {
        GraphicsContext gc = donutCanvas.getGraphicsContext2D();
        double w = donutCanvas.getWidth();
        double h = donutCanvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double r = Math.min(w, h) / 2.0 - 15;
        double innerR = r * 0.60;

        gc.clearRect(0, 0, w, h);

        if (total == 0) {
            gc.setFill(Color.web("#E5E7EB"));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            gc.setFill(Color.WHITE);
            gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
            gc.setFill(Color.web("#707070"));
            gc.setFont(javafx.scene.text.Font.font("Tajawal", javafx.scene.text.FontWeight.BOLD, 14));
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.setTextBaseline(javafx.geometry.VPos.CENTER);
            gc.fillText("0", cx, cy);
            return;
        }

        double cashAngle = 360.0 * cash / total;
        double cardAngle = 360.0 * card / total;
        double creditAngle = 360.0 * credit / total;

        double startAngle = 90.0;

        // Cash Arc (green)
        double curAngle = cashAngle * scale;
        if (curAngle > 0) {
            gc.setFill(Color.web("#22C55E"));
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, -curAngle, javafx.scene.shape.ArcType.ROUND);
            startAngle -= curAngle;
        }

        // Card Arc (blue)
        curAngle = cardAngle * scale;
        if (curAngle > 0) {
            gc.setFill(Color.web("#3B82F6"));
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, -curAngle, javafx.scene.shape.ArcType.ROUND);
            startAngle -= curAngle;
        }

        // Credit Arc (orange)
        curAngle = creditAngle * scale;
        if (curAngle > 0) {
            gc.setFill(Color.web("#FAA11F"));
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, -curAngle, javafx.scene.shape.ArcType.ROUND);
        }

        // Inner cut out
        gc.setFill(Color.WHITE);
        gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Center labels
        gc.setFill(Color.web("#1E1E2E"));
        gc.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 22));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(String.format(java.util.Locale.US, "%,d", total), cx, cy - 8);

        gc.setFont(javafx.scene.text.Font.font("Tajawal", javafx.scene.text.FontWeight.NORMAL, 11));
        gc.setFill(Color.web("#707070"));
        gc.fillText("فاتورة", cx, cy + 12);
    }

    private void addLegendItem(String label, long count, double pct, String colorHex) {
        HBox legendItem = new HBox(6);
        legendItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Region indicator = new Region();
        indicator.setPrefSize(10, 10);
        indicator.setMaxSize(10, 10);
        indicator.setStyle("-fx-background-color: " + colorHex + "; -fx-background-radius: 5;");

        Label txt = new Label(String.format(java.util.Locale.US, "%s: %d (%.1f%%)", label, count, pct * 100.0));
        txt.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11px; -fx-text-fill: #363636;");

        legendItem.getChildren().addAll(indicator, txt);
        donutLegendBox.getChildren().add(legendItem);
    }

    // ─── Chart 4: Horizontal progress bars ───
    private void animateBarWidth(Region bar, double percentage) {
        Pane parent = (Pane) bar.getParent();
        if (parent == null) return;

        Runnable action = () -> {
            double targetWidth = parent.getWidth() * percentage;
            KeyFrame kf = new KeyFrame(
                Duration.millis(600),
                new KeyValue(bar.prefWidthProperty(), targetWidth, Interpolator.EASE_OUT)
            );
            Timeline tl = new Timeline(kf);
            tl.play();
        };

        if (parent.getWidth() > 0) {
            action.run();
        } else {
            parent.widthProperty().addListener((obs, oldW, newW) -> action.run());
        }
    }

    private void FadeTransition(javafx.scene.Node node, double from, double to, int durationMs) {
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    @FXML
    private void onPeriodChanged() {
        loadData(true); // force fresh load
        renderDashboard();
    }

    @FXML
    private void onShowEndOfDayReport() {
        javafx.stage.Stage owner = (javafx.stage.Stage) periodComboBox.getScene().getWindow();
        EndOfDayReportDialog dialog = new EndOfDayReportDialog(owner);
        dialog.show();
    }
}
