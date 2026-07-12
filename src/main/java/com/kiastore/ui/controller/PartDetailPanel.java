package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import com.kiastore.app.Session;
import com.kiastore.model.*;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Programmatic side panel that slides in from the right on the Parts screen.
 * Shows full part details: image gallery, 5 info tabs, and action buttons.
 */
public class PartDetailPanel {

    private static final double PANEL_WIDTH  = 400.0;
    private static final String FONT_TAJAWAL = "Tajawal";
    private static final String FONT_INTER   = "Inter";
    private static final String ORANGE       = "#FAA11F";
    private static final String RED          = "#EF4444";
    private static final String GREEN        = "#22C55E";
    private static final String BLUE         = "#3B82F6";
    private static final String GRAY         = "#707070";
    private static final String DARK         = "#1E1E2E";
    private static final String WHITE        = "#FFFFFF";
    private static final int    MAX_IMAGES   = 10;

    private final StackPane rootStack;            // The StackPane wrapping the whole Parts screen
    private final Region    overlay;              // semi-transparent click-to-close backdrop
    private final VBox      panel;                // the actual sliding panel
    private final PartsController partsController;
    private boolean         visible = false;
    private Part            currentPart;

    // Image gallery nodes
    private ImageView mainImageView;
    private HBox      thumbsBox;
    private List<String> imageUrls = new ArrayList<>();
    private int       selectedThumbIndex = 0;

    // Tabs
    private Tab tabGeneral, tabSuppliers, tabCars, tabInvoices, tabAudit;

    // ──────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────

    public PartDetailPanel(StackPane rootStack, PartsController controller) {
        this.rootStack = rootStack;
        this.partsController = controller;

        // 1. Dim overlay
        overlay = new Region();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
        overlay.setVisible(false);
        overlay.setOpacity(0);
        overlay.setOnMouseClicked(e -> hide());

        // 2. Panel VBox
        panel = new VBox();
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setFillWidth(true);
        panel.setStyle(
            "-fx-background-color: " + WHITE + ";" +
            "-fx-background-radius: 16 0 0 16;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 30, 0, -4, 0);"
        );
        panel.setTranslateX(PANEL_WIDTH);   // start off-screen to the right
        panel.setVisible(false);

        // Anchor panel to the right side
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);
        StackPane.setAlignment(overlay, Pos.CENTER);

        rootStack.getChildren().addAll(overlay, panel);
    }

    // ──────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────

    public void show(Part part) {
        this.currentPart = part;
        panel.getChildren().clear();
        buildPanel(part);

        overlay.setVisible(true);
        panel.setVisible(true);

        // Fade in overlay
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), overlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Slide in panel
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), panel);
        slideIn.setFromX(PANEL_WIDTH);
        slideIn.setToX(0);
        slideIn.play();

        visible = true;
    }

    public void hide() {
        if (!visible) return;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> overlay.setVisible(false));
        fadeOut.play();

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(280), panel);
        slideOut.setToX(PANEL_WIDTH);
        slideOut.setOnFinished(e -> panel.setVisible(false));
        slideOut.play();

        visible = false;
    }

    public boolean isVisible() { return visible; }

    // ──────────────────────────────────────────────
    //  Build the panel content
    // ──────────────────────────────────────────────

    private void buildPanel(Part p) {
        // Parse image list
        imageUrls = parseImageList(p.getImages());
        selectedThumbIndex = 0;

        VBox content = new VBox(0);
        content.setFillWidth(true);

        // Header
        content.getChildren().add(buildHeader(p));

        // Scrollable body
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox body = new VBox(0);
        body.setFillWidth(true);

        // Image gallery
        body.getChildren().add(buildImageGallery(p));

        // Tabs
        body.getChildren().add(buildTabs(p));

        scroll.setContent(body);
        content.getChildren().add(scroll);

        // Action bar
        content.getChildren().add(buildActionBar(p));

        VBox.setVgrow(content, Priority.ALWAYS);
        panel.getChildren().add(content);
        VBox.setVgrow(panel, Priority.ALWAYS);
    }

    // ──────────────────────────────────────────────
    //  HEADER
    // ──────────────────────────────────────────────

    private HBox buildHeader(Part p) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 12, 16));
        header.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: #E5E7EB; -fx-border-width: 0 0 1 0;");

        // Close button (left, since RTL view)
        Button closeBtn = iconButton("✕", "#EF4444");
        closeBtn.setOnAction(e -> hide());

        // Part name + number
        VBox nameBox = new VBox(2);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        Label nameLabel = new Label(p.getFullName() != null ? p.getFullName() : "—");
        nameLabel.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1E1E2E; -fx-wrap-text: true;");
        nameLabel.setWrapText(true);
        Label partNumLabel = new Label(p.getPartNumber() != null ? p.getPartNumber() : "");
        partNumLabel.setStyle("-fx-font-family: '" + FONT_INTER + "'; -fx-font-size: 11; -fx-text-fill: " + GRAY + ";");
        nameBox.getChildren().addAll(nameLabel, partNumLabel);

        // Star / favorite button
        Button starBtn = iconButton("☆", ORANGE);
        starBtn.setStyle(starBtn.getStyle() + " -fx-font-size: 16;");

        // Three-dots menu
        MenuButton menuBtn = new MenuButton("⋯");
        menuBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + GRAY + ";" +
            "-fx-font-size: 18; -fx-cursor: hand; -fx-padding: 2 6;"
        );
        MenuItem editItem   = new MenuItem("✏️  تعديل الصنف");
        editItem.setOnAction(e -> {
            partsController.focusEditForm();
            hide();
        });

        MenuItem deleteItem = new MenuItem("🗑️  حذف الصنف");
        deleteItem.setOnAction(e -> {
            partsController.onDeletePart();
            hide();
        });

        MenuItem printItem  = new MenuItem("🖨️  طباعة ملصق الباركود");
        printItem.setOnAction(e -> {
            new Alert(Alert.AlertType.INFORMATION, "جاري طباعة ملصق الباركود للصنف:\n" + p.getFullName()).show();
        });

        deleteItem.setStyle("-fx-text-fill: #EF4444;");
        menuBtn.getItems().addAll(editItem, deleteItem, new SeparatorMenuItem(), printItem);

        header.getChildren().addAll(closeBtn, nameBox, starBtn, menuBtn);
        return header;
    }

    // ──────────────────────────────────────────────
    //  IMAGE GALLERY
    // ──────────────────────────────────────────────

    private VBox buildImageGallery(Part p) {
        VBox gallery = new VBox(10);
        gallery.setPadding(new Insets(14, 14, 10, 14));
        gallery.setStyle("-fx-background-color: #F8F9FA;");

        // Main image
        StackPane mainImagePane = new StackPane();
        mainImagePane.setStyle("-fx-background-color: #ECECEC; -fx-background-radius: 10;");
        mainImagePane.setPrefHeight(200);
        mainImagePane.setMinHeight(200);

        mainImageView = new ImageView();
        mainImageView.setFitWidth(PANEL_WIDTH - 28);
        mainImageView.setFitHeight(200);
        mainImageView.setPreserveRatio(true);
        mainImageView.setSmooth(true);

        Label placeholderIcon = new Label("📦");
        placeholderIcon.setStyle("-fx-font-size: 60; -fx-opacity: 0.3;");

        mainImagePane.getChildren().addAll(placeholderIcon, mainImageView);
        loadImage(imageUrls.isEmpty() ? null : imageUrls.get(0), mainImageView, placeholderIcon);

        // Thumbnails row
        thumbsBox = new HBox(8);
        thumbsBox.setAlignment(Pos.CENTER_LEFT);
        refreshThumbnails(p);

        // Upload controls row
        HBox uploadRow = new HBox(8);
        uploadRow.setAlignment(Pos.CENTER_LEFT);

        Button uploadBtn = smallBtn("📁  رفع صورة", "#3B82F6");
        uploadBtn.setOnAction(e -> onUploadImage(p));

        Button urlBtn = smallBtn("🔗  رابط صورة", "#8B5CF6");
        urlBtn.setOnAction(e -> onAddImageUrl(p));

        uploadRow.getChildren().addAll(uploadBtn, urlBtn);

        gallery.getChildren().addAll(mainImagePane, thumbsBox, uploadRow);
        return gallery;
    }

    private void refreshThumbnails(Part p) {
        thumbsBox.getChildren().clear();
        int maxVisible = 5;

        for (int i = 0; i < Math.min(imageUrls.size(), maxVisible); i++) {
            final int idx = i;
            StackPane thumb = buildThumb(imageUrls.get(i), i == selectedThumbIndex, p, idx);
            thumbsBox.getChildren().add(thumb);
        }

        if (imageUrls.size() > maxVisible) {
            Label more = new Label("+" + (imageUrls.size() - maxVisible));
            more.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: white;" +
                "-fx-font-family: '" + FONT_INTER + "'; -fx-font-weight: bold; -fx-font-size: 13;" +
                "-fx-min-width: 56; -fx-min-height: 56; -fx-alignment: center;" +
                "-fx-background-radius: 8; -fx-cursor: hand;"
            );
            thumbsBox.getChildren().add(more);
        }

        // Add-placeholder if no images
        if (imageUrls.isEmpty()) {
            Label empty = new Label("لا توجد صور");
            empty.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-text-fill: " + GRAY + "; -fx-font-size: 11;");
            thumbsBox.getChildren().add(empty);
        }
    }

    private StackPane buildThumb(String url, boolean selected, Part p, int idx) {
        StackPane thumbPane = new StackPane();
        thumbPane.setStyle(
            "-fx-min-width: 56; -fx-min-height: 56; -fx-max-width: 56; -fx-max-height: 56;" +
            "-fx-background-color: #DCDCDC; -fx-background-radius: 8;" +
            (selected ? "-fx-border-color: " + ORANGE + "; -fx-border-width: 2; -fx-border-radius: 8;" : "")
        );
        thumbPane.setCursor(javafx.scene.Cursor.HAND);

        ImageView iv = new ImageView();
        iv.setFitWidth(56);
        iv.setFitHeight(56);
        iv.setPreserveRatio(true);
        loadImageSmall(url, iv);

        // Red X delete button (appears on hover)
        Button delX = new Button("✕");
        delX.setStyle(
            "-fx-background-color: " + RED + "; -fx-text-fill: white;" +
            "-fx-font-size: 9; -fx-cursor: hand; -fx-background-radius: 50%;" +
            "-fx-min-width: 18; -fx-min-height: 18; -fx-max-width: 18; -fx-max-height: 18;" +
            "-fx-padding: 0;"
        );
        StackPane.setAlignment(delX, Pos.TOP_RIGHT);
        delX.setVisible(false);
        delX.setOnAction(e -> { removeImage(idx, p); e.consume(); });

        thumbPane.getChildren().addAll(iv, delX);

        // Hover events
        thumbPane.setOnMouseEntered(e -> { delX.setVisible(true); thumbPane.setOpacity(0.85); });
        thumbPane.setOnMouseExited(e -> { delX.setVisible(false); thumbPane.setOpacity(1.0); });

        // Click → set main image
        thumbPane.setOnMouseClicked(e -> {
            selectedThumbIndex = idx;
            loadImage(url, mainImageView, null);
            refreshThumbnails(p);
        });

        return thumbPane;
    }

    // ──────────────────────────────────────────────
    //  TABS
    // ──────────────────────────────────────────────

    private TabPane buildTabs(Part p) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        tabPane.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-tab-min-height: 36;"
        );

        tabGeneral   = new Tab("معلومات عامة",  buildGeneralTab(p));
        tabSuppliers = new Tab("الموردين",       buildSuppliersTab(p));
        tabCars      = new Tab("السيارات",       buildCarsTab(p));
        tabInvoices  = new Tab("الفواتير",       buildInvoicesTab(p));
        tabAudit     = new Tab("سجل الحركات",   buildAuditTab(p));

        styleTab(tabGeneral);
        styleTab(tabSuppliers);
        styleTab(tabCars);
        styleTab(tabInvoices);
        styleTab(tabAudit);

        tabPane.getTabs().addAll(tabGeneral, tabSuppliers, tabCars, tabInvoices, tabAudit);
        return tabPane;
    }

    // ─── Tab 1: General Info ───

    private ScrollPane buildGeneralTab(Part p) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(14, 14, 14, 14));

        // Status badge
        String statusColor = p.isActive() ? GREEN : RED;
        String statusText  = p.isActive() ? "نشط" : "غير نشط";
        Label statusBadge  = badge(statusText, statusColor);

        // Stock with color
        boolean isLow = p.isLowStock();
        String stockColor = isLow ? RED : GREEN;
        Label stockLbl = infoValue(p.getCurrentStock() + " قطعة", stockColor, true);

        // Last received & sale dates (from batches & invoices async)
        Label lastReceivedLbl = new Label("جارٍ التحميل...");
        Label lastSaleLbl     = new Label("جارٍ التحميل...");
        applyInfoValueStyle(lastReceivedLbl, GRAY, false);
        applyInfoValueStyle(lastSaleLbl, GRAY, false);

        // Primary supplier
        Label supplierLbl = new Label("—");
        applyInfoValueStyle(supplierLbl, BLUE, false);

        loadAsync(() -> {
            List<Batch> batches = AppContext.get().batchDao.findByPart(p.getId());
            String lastReceived = "—";
            String supplierName = "—";
            if (!batches.isEmpty()) {
                Batch b = batches.get(0);
                if (b.getReceivedAt() != null)
                    lastReceived = b.getReceivedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                try {
                    List<Supplier> sups = AppContext.get().supplierService.all();
                    for (Supplier s : sups) {
                        if (s.getId() == b.getSupplierId()) { supplierName = s.getName(); break; }
                    }
                } catch (Exception ignored) {}
            }
            // Last sale from invoice items
            String lastSale = "—";
            try {
                List<InvoiceItem> items = AppContext.get().invoiceItemDao.findByPartId(p.getId());
                if (!items.isEmpty()) {
                    List<Invoice> allInv = AppContext.get().invoiceService.all();
                    int latestInvId = items.stream().mapToInt(InvoiceItem::getInvoiceId).max().orElse(0);
                    for (Invoice inv : allInv) {
                        if (inv.getId() == latestInvId && inv.getCreatedAt() != null) {
                            lastSale = inv.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
            final String lr = lastReceived, ls = lastSale, sn = supplierName;
            javafx.application.Platform.runLater(() -> {
                lastReceivedLbl.setText(lr);
                lastSaleLbl.setText(ls);
                supplierLbl.setText(sn);
            });
        });

        box.getChildren().addAll(
            statusBadge,
            separator(),
            infoRow("رقم القطعة",       infoValue(nvl(p.getPartNumber()), DARK, false)),
            infoRow("الكود الداخلي",    infoValue(nvl(p.getInternalCode()), DARK, false)),
            infoRow("المورد الرئيسي",   supplierLbl),
            infoRow("المخزون الحالي",   stockLbl),
            infoRow("أقل كمية تنبيه",   infoValue(String.valueOf(p.getMinStock()), RED, false)),
            infoRow("سعر البيع",        infoValue(String.format(java.util.Locale.US, "%,.2f EGP", p.getSalePrice()), GREEN, true)),
            infoRow("المكان",           infoValue(nvl(p.getLocation()), DARK, false)),
            infoRow("تاريخ آخر توريد", lastReceivedLbl),
            infoRow("تاريخ آخر بيع",   lastSaleLbl)
        );

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return sp;
    }

    // ─── Tab 2: Suppliers ───

    private ScrollPane buildSuppliersTab(Part p) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(14));

        Label loading = new Label("جارٍ تحميل الموردين...");
        loading.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-text-fill: " + GRAY + ";");
        box.getChildren().add(loading);

        loadAsync(() -> {
            List<Batch> batches = AppContext.get().batchDao.findByPart(p.getId());
            List<Supplier> suppliers = AppContext.get().supplierService.all();
            // Group by supplier
            Map<Integer, List<Batch>> bySup = new LinkedHashMap<>();
            for (Batch b : batches) bySup.computeIfAbsent(b.getSupplierId(), k -> new ArrayList<>()).add(b);

            javafx.application.Platform.runLater(() -> {
                box.getChildren().clear();
                if (bySup.isEmpty()) {
                    box.getChildren().add(emptyState("لا يوجد موردين لهذا الصنف"));
                    return;
                }
                for (Map.Entry<Integer, List<Batch>> entry : bySup.entrySet()) {
                    int supId = entry.getKey();
                    List<Batch> supBatches = entry.getValue();
                    String supName = suppliers.stream()
                        .filter(s -> s.getId() == supId).map(Supplier::getName)
                        .findFirst().orElse("مورد #" + supId);
                    Batch latest = supBatches.get(0);
                    String date = latest.getReceivedAt() != null
                        ? latest.getReceivedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";

                    VBox card = supplierRow(supName, date,
                        String.format(java.util.Locale.US, "%,.2f EGP", latest.getPurchasePrice()));
                    box.getChildren().add(card);
                }
            });
        });

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return sp;
    }

    // ─── Tab 3: Cars ───

    private ScrollPane buildCarsTab(Part p) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(14));

        String carsJson = p.getCompatibleCars();
        List<String> cars = parseStringList(carsJson);

        if (cars.isEmpty()) {
            box.getChildren().add(emptyState("لا توجد سيارات متوافقة مسجلة"));
        } else {
            for (String car : cars) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));
                row.setStyle("-fx-background-color: #F0F4FF; -fx-background-radius: 8;");
                Label carIcon = new Label("🚗");
                carIcon.setStyle("-fx-font-size: 16;");
                Label carLabel = new Label(car);
                carLabel.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 13; -fx-text-fill: " + DARK + ";");
                carLabel.setWrapText(true);
                row.getChildren().addAll(carIcon, carLabel);
                box.getChildren().add(row);
            }
        }

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return sp;
    }

    // ─── Tab 4: Invoices ───

    private ScrollPane buildInvoicesTab(Part p) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        Label loading = new Label("جارٍ تحميل الفواتير...");
        loading.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-text-fill: " + GRAY + ";");
        box.getChildren().add(loading);

        loadAsync(() -> {
            List<Batch> batches = AppContext.get().batchDao.findByPart(p.getId());
            javafx.application.Platform.runLater(() -> {
                box.getChildren().clear();
                if (batches.isEmpty()) {
                    box.getChildren().add(emptyState("لا توجد فواتير شراء لهذا الصنف"));
                    return;
                }
                for (Batch b : batches) {
                    box.getChildren().add(buildBatchCard(b));
                }
            });
        });

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return sp;
    }

    private VBox buildBatchCard(Batch b) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: #F9FAFB; -fx-background-radius: 10;" +
            "-fx-border-color: #E5E7EB; -fx-border-radius: 10; -fx-border-width: 1;"
        );

        // Supplier name (async resolved)
        Label supLabel = new Label("المورد: جارٍ التحميل...");
        supLabel.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: " + DARK + ";");

        loadAsync(() -> {
            try {
                List<Supplier> sups = AppContext.get().supplierService.all();
                String name = sups.stream().filter(s -> s.getId() == b.getSupplierId())
                    .map(Supplier::getName).findFirst().orElse("مورد #" + b.getSupplierId());
                javafx.application.Platform.runLater(() -> supLabel.setText("المورد: " + name));
            } catch (Exception ignored) {}
        });

        String dateStr = b.getReceivedAt() != null
            ? b.getReceivedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
        String invNum = b.getPurchaseInvoiceNumber() != null ? "فاتورة# " + b.getPurchaseInvoiceNumber() : "بدون رقم فاتورة";

        Label dateLabel  = infoSmall("تاريخ التوريد: " + dateStr);
        Label invLabel   = infoSmall("رقم الفاتورة: " + invNum);
        Label priceLabel = infoSmall(String.format(java.util.Locale.US, "سعر الشراء: %,.2f EGP", b.getPurchasePrice()));
        Label qtyLabel   = infoSmall("الكمية: " + b.getQuantity() + " قطعة");

        card.getChildren().addAll(supLabel, dateLabel, invLabel, priceLabel, qtyLabel);
        return card;
    }

    // ─── Tab 5: Audit ───

    private ScrollPane buildAuditTab(Part p) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(14));

        Label loading = new Label("جارٍ تحميل السجل...");
        loading.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-text-fill: " + GRAY + ";");
        box.getChildren().add(loading);

        loadAsync(() -> {
            try {
                List<AuditLog> logs = AppContext.get().auditLogDao.findByRecordId("parts", p.getId(), 30);
                javafx.application.Platform.runLater(() -> {
                    box.getChildren().clear();
                    if (logs.isEmpty()) {
                        box.getChildren().add(emptyState("لا يوجد سجل حركات لهذا الصنف"));
                        return;
                    }
                    for (AuditLog log : logs) {
                        box.getChildren().add(buildAuditRow(log));
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    box.getChildren().clear();
                    box.getChildren().add(emptyState("تعذّر تحميل السجل"));
                });
            }
        });

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return sp;
    }

    private HBox buildAuditRow(AuditLog log) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #FAFAFA; -fx-background-radius: 8; -fx-border-color: #F0F0F0; -fx-border-radius: 8;");

        // Colored dot
        String dotColor = switch (log.getAction() != null ? log.getAction().toUpperCase() : "") {
            case "CREATE" -> GREEN;
            case "UPDATE" -> BLUE;
            case "DELETE" -> RED;
            default       -> GRAY;
        };
        Circle dot = new Circle(5);
        dot.setFill(Color.web(dotColor));

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        String actionAr = switch (log.getAction() != null ? log.getAction().toUpperCase() : "") {
            case "CREATE" -> "إضافة";
            case "UPDATE" -> "تعديل";
            case "DELETE" -> "حذف";
            default       -> log.getAction() != null ? log.getAction() : "—";
        };
        Label actionLabel = new Label(actionAr + " — " + nvl(log.getUserName()));
        actionLabel.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + DARK + ";");
        String dateStr = log.getCreatedAt() != null
            ? log.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
        Label dateLabel = new Label(dateStr);
        dateLabel.setStyle("-fx-font-family: '" + FONT_INTER + "'; -fx-font-size: 10; -fx-text-fill: " + GRAY + ";");
        info.getChildren().addAll(actionLabel, dateLabel);

        row.getChildren().addAll(dot, info);
        return row;
    }

    // ──────────────────────────────────────────────
    //  ACTION BAR
    // ──────────────────────────────────────────────

    private HBox buildActionBar(Part p) {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(12, 14, 16, 14));
        bar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: #E5E7EB; -fx-border-width: 1 0 0 0;");

        Button invoiceBtn = new Button("📄  إنشاء فاتورة بيع");
        invoiceBtn.setStyle(
            "-fx-background-color: " + ORANGE + "; -fx-text-fill: white; -fx-font-family: '" + FONT_TAJAWAL + "';" +
            "-fx-font-weight: bold; -fx-font-size: 13; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 10 16;"
        );
        HBox.setHgrow(invoiceBtn, Priority.ALWAYS);
        invoiceBtn.setMaxWidth(Double.MAX_VALUE);
        invoiceBtn.setOnAction(e -> {
            hide();
            MainShellController.getInstance().showInvoices();
            Object ctrl = MainShellController.getInstance().getActiveController();
            if (ctrl instanceof InvoicesController invCtrl) {
                invCtrl.addPartToCartDirect(p, 1);
            }
        });

        Button receiveBtn = new Button("📦  استلام بضاعة");
        receiveBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + DARK + ";" +
            "-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-weight: bold; -fx-font-size: 13;" +
            "-fx-cursor: hand; -fx-background-radius: 8; -fx-border-color: " + DARK + ";" +
            "-fx-border-radius: 8; -fx-padding: 10 14;"
        );
        receiveBtn.setOnAction(e -> {
            hide();
            MainShellController.getInstance().showReceiving();
            Object ctrl = MainShellController.getInstance().getActiveController();
            if (ctrl instanceof ReceivingController recCtrl) {
                recCtrl.selectPartDirect(p);
            }
        });

        bar.getChildren().addAll(invoiceBtn, receiveBtn);
        return bar;
    }

    // ──────────────────────────────────────────────
    //  IMAGE UPLOAD HANDLERS
    // ──────────────────────────────────────────────

    private void onUploadImage(Part p) {
        if (imageUrls.size() >= MAX_IMAGES) {
            showAlert("الحد الأقصى للصور هو " + MAX_IMAGES + " صور.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("اختر صورة الصنف");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "صور (jpg, jpeg, png, webp)", "*.jpg", "*.jpeg", "*.png", "*.webp"
        ));
        File chosen = fc.showOpenDialog(rootStack.getScene().getWindow());
        if (chosen == null) return;

        try {
            Path destDir = Paths.get(System.getProperty("user.home"), "kia-store", "images", "parts", String.valueOf(p.getId()));
            Files.createDirectories(destDir);
            // Always save as JPEG for consistency / smaller file size
            String baseName = chosen.getName().replaceAll("(?i)\\.[^.]+$", "") + ".jpg";
            Path destFile = destDir.resolve(baseName);
            compressAndSaveImage(chosen, destFile.toFile());
            String uri = destFile.toUri().toString();
            addImageAndSave(uri, p);
        } catch (IOException ex) {
            showAlert("فشل رفع الصورة: " + ex.getMessage());
        }
    }

    /**
     * Resizes the source image to at most 800×800 pixels and compresses it
     * as JPEG with quality reduction until the file is ≤ 300 KB.
     * Original oversized images are never written to disk.
     */
    private void compressAndSaveImage(File src, File dest) throws IOException {
        BufferedImage original = ImageIO.read(src);
        if (original == null) throw new IOException("لا يمكن قراءة ملف الصورة");

        // ── 1. Resize to max 800×800 keeping aspect ratio ──
        int w = original.getWidth();
        int h = original.getHeight();
        int maxDim = 800;
        if (w > maxDim || h > maxDim) {
            double scale = (double) maxDim / Math.max(w, h);
            w = (int) (w * scale);
            h = (int) (h * scale);
        }
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(original, 0, 0, w, h, null);
        g2.dispose();

        // ── 2. JPEG-compress with descending quality until ≤ 300 KB ──
        long maxBytes = 300L * 1024;   // 300 KB
        float quality = 0.85f;
        byte[] jpegBytes = null;
        while (quality >= 0.40f) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(resized, null, null), param);
            writer.dispose();
            jpegBytes = baos.toByteArray();
            if (jpegBytes.length <= maxBytes) break;
            quality -= 0.10f;
        }
        if (jpegBytes == null) throw new IOException("فشل ضغط الصورة");

        // ── 3. Write final bytes to destination ──
        try (FileImageOutputStream fos = new FileImageOutputStream(dest)) {
            fos.write(jpegBytes);
        }
    }

    private void onAddImageUrl(Part p) {
        if (imageUrls.size() >= MAX_IMAGES) {
            showAlert("الحد الأقصى للصور هو " + MAX_IMAGES + " صور.");
            return;
        }
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("إضافة رابط صورة");
        dlg.setHeaderText("ألصق رابط الصورة (URL)");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField urlField = new TextField();
        urlField.setPromptText("https://example.com/image.jpg");
        urlField.setPrefWidth(320);
        VBox content = new VBox(8, new Label("رابط الصورة:"), urlField);
        content.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(content);
        dlg.setResultConverter(btn -> btn == ButtonType.OK ? urlField.getText().trim() : null);

        dlg.showAndWait().ifPresent(url -> {
            if (url.isEmpty()) return;
            if (!isValidImageUrl(url)) {
                showAlert("الرابط لا يبدو رابط صورة صحيح (يجب أن ينتهي بـ .jpg أو .png أو .webp أو .gif)");
                return;
            }
            addImageAndSave(url, p);
        });
    }

    private void addImageAndSave(String uri, Part p) {
        imageUrls.add(uri);
        saveImages(p);
        selectedThumbIndex = imageUrls.size() - 1;
        refreshThumbnails(p);
        loadImage(uri, mainImageView, null);
    }

    private void removeImage(int idx, Part p) {
        if (idx < 0 || idx >= imageUrls.size()) return;
        imageUrls.remove(idx);
        if (selectedThumbIndex >= imageUrls.size()) selectedThumbIndex = Math.max(0, imageUrls.size() - 1);
        saveImages(p);
        refreshThumbnails(p);
        if (!imageUrls.isEmpty()) loadImage(imageUrls.get(selectedThumbIndex), mainImageView, null);
        else mainImageView.setImage(null);
    }

    private void saveImages(Part p) {
        // Build JSON array string
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < imageUrls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(imageUrls.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        String json = sb.toString();
        p.setImages(json);
        loadAsync(() -> AppContext.get().partDao.updateImages(p.getId(), json));
    }

    // ──────────────────────────────────────────────
    //  IMAGE LOADING
    // ──────────────────────────────────────────────

    private void loadImage(String url, ImageView iv, Label placeholder) {
        if (url == null || url.isBlank()) {
            iv.setImage(null);
            if (placeholder != null) placeholder.setVisible(true);
            return;
        }
        if (placeholder != null) placeholder.setVisible(false);
        Task<Image> task = new Task<>() {
            @Override protected Image call() {
                try { return new Image(url, true); } catch (Exception e) { return null; }
            }
        };
        task.setOnSucceeded(e -> {
            Image img = task.getValue();
            if (img != null && !img.isError()) {
                iv.setImage(img);
            } else {
                iv.setImage(null);
                if (placeholder != null) placeholder.setVisible(true);
            }
        });
        task.setOnFailed(e -> { iv.setImage(null); if (placeholder != null) placeholder.setVisible(true); });
        new Thread(task, "image-loader").start();
    }

    private void loadImageSmall(String url, ImageView iv) {
        if (url == null || url.isBlank()) { iv.setImage(null); return; }
        Task<Image> task = new Task<>() {
            @Override protected Image call() {
                try { return new Image(url, 56, 56, true, true, true); } catch (Exception e) { return null; }
            }
        };
        task.setOnSucceeded(e -> { if (task.getValue() != null && !task.getValue().isError()) iv.setImage(task.getValue()); });
        new Thread(task, "thumb-loader").start();
    }

    // ──────────────────────────────────────────────
    //  HELPER BUILDERS
    // ──────────────────────────────────────────────

    private HBox infoRow(String labelText, Label valueNode) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        row.setStyle("-fx-border-color: transparent transparent #F0F0F0 transparent; -fx-border-width: 0 0 1 0;");
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 12; -fx-text-fill: " + GRAY + "; -fx-min-width: 120;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, valueNode);
        return row;
    }

    private Label infoValue(String text, String color, boolean bold) {
        Label l = new Label(text);
        applyInfoValueStyle(l, color, bold);
        return l;
    }

    private void applyInfoValueStyle(Label l, String color, boolean bold) {
        l.setStyle(
            "-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 12;" +
            "-fx-text-fill: " + color + ";" +
            (bold ? "-fx-font-weight: bold;" : "")
        );
    }

    private Label infoSmall(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 11; -fx-text-fill: " + GRAY + ";");
        return l;
    }

    private Label badge(String text, String color) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-background-color: " + color + "20; -fx-text-fill: " + color + ";" +
            "-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-weight: bold; -fx-font-size: 11;" +
            "-fx-padding: 4 10; -fx-background-radius: 12;"
        );
        return l;
    }

    private Label emptyState(String msg) {
        Label l = new Label(msg);
        l.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-text-fill: " + GRAY + "; -fx-font-size: 13; -fx-padding: 20 0;");
        l.setWrapText(true);
        return l;
    }

    private Region separator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: #F0F0F0;");
        VBox.setMargin(sep, new Insets(4, 0, 4, 0));
        return sep;
    }

    private VBox supplierRow(String name, String date, String price) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle("-fx-background-color: #F0F4FF; -fx-background-radius: 8;");
        Label nameL = new Label("🏭  " + name);
        nameL.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: " + DARK + ";");
        Label dateL = new Label("آخر توريد: " + date + "  |  آخر سعر: " + price);
        dateL.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 11; -fx-text-fill: " + GRAY + ";");
        card.getChildren().addAll(nameL, dateL);
        return card;
    }

    private Button iconButton(String icon, String color) {
        Button btn = new Button(icon);
        btn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + color + ";" +
            "-fx-font-size: 14; -fx-cursor: hand; -fx-padding: 4 8;" +
            "-fx-background-radius: 8;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + "-fx-background-color: rgba(0,0,0,0.06);"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-background-color: rgba(0,0,0,0.06);", "")));
        return btn;
    }

    private Button smallBtn(String text, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: " + bgColor + "15; -fx-text-fill: " + bgColor + ";" +
            "-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 11; -fx-font-weight: bold;" +
            "-fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 5 10;" +
            "-fx-border-color: " + bgColor + "40; -fx-border-radius: 6;"
        );
        return btn;
    }

    private void styleTab(Tab tab) {
        // Tab styling is done via CSS classes — just set the label font
        if (tab.getGraphic() == null) {
            Label lbl = new Label(tab.getText());
            lbl.setStyle("-fx-font-family: '" + FONT_TAJAWAL + "'; -fx-font-size: 12;");
            tab.setGraphic(lbl);
            tab.setText("");
        }
    }

    // ──────────────────────────────────────────────
    //  UTILITIES
    // ──────────────────────────────────────────────

    private void loadAsync(Runnable work) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() { work.run(); return null; }
        };
        new Thread(task, "panel-data-loader").start();
    }

    private List<String> parseImageList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        for (String part : json.split(",")) {
            part = part.trim();
            if (part.startsWith("\"")) part = part.substring(1);
            if (part.endsWith("\""))  part = part.substring(0, part.length() - 1);
            if (!part.isBlank()) result.add(part);
        }
        return result;
    }

    private List<String> parseStringList(String json) {
        return parseImageList(json); // same format
    }

    private boolean isValidImageUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http") &&
            (lower.contains(".jpg") || lower.contains(".jpeg") ||
             lower.contains(".png") || lower.contains(".webp") ||
             lower.contains(".gif") || lower.contains("image"));
    }

    private String nvl(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private void showAlert(String msg) {
        javafx.application.Platform.runLater(() ->
            new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait());
    }
}
