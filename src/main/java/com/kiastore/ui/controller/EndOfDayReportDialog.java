package com.kiastore.ui.controller;

import com.kiastore.app.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Dialog to calculate, display, print, and export the Daily End-of-Day Report.
 */
public class EndOfDayReportDialog {

    private final Stage stage;
    private final VBox printArea;

    // Report stats
    private double totalSales;
    private int invoicesCount;
    private int itemsSold;
    private double totalReturns;
    private double netProfit;
    private List<Object[]> topParts;
    private final String reportTime;

    public EndOfDayReportDialog(Stage owner) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("تقرير نهاية اليوم - KIA Store");

        reportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Load data from Services
        loadReportData();

        // Build UI Layout
        printArea = new VBox(15);
        printArea.setPadding(new Insets(25));
        printArea.setStyle("-fx-background-color: #FFFFFF;");
        printArea.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        // Header Section
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);
        Label shopNameLabel = new Label("KIA Store");
        shopNameLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 28; -fx-font-weight: 800; -fx-text-fill: #FAA11F;");
        
        Label titleLabel = new Label("تقرير نهاية اليوم — Daily End of Day");
        titleLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #363636;");
        
        Label dateLabel = new Label("تاريخ التقرير: " + reportTime);
        dateLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11; -fx-text-fill: #707070;");
        
        header.getChildren().addAll(shopNameLabel, titleLabel, dateLabel);
        printArea.getChildren().add(header);

        // Separator
        Separator sep1 = new Separator();
        printArea.getChildren().add(sep1);

        // Stats Grid
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        addStatRow(grid, "إجمالي مبيعات اليوم (Sales):", fmtPrice(totalSales), 0);
        addStatRow(grid, "عدد الفواتير الصادرة (Invoices):", String.valueOf(invoicesCount), 1);
        addStatRow(grid, "عدد قطع الغيار المباعة (Items):", String.valueOf(itemsSold), 2);
        addStatRow(grid, "إجمالي المرتجعات اليوم (Returns):", fmtPrice(totalReturns), 3);
        
        // Net Profit highlighted in Green
        Label profitLabel = new Label("صافي أرباح اليوم (Net Profit):");
        profitLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 13; -fx-font-weight: bold;");
        Label profitVal = new Label(fmtPrice(netProfit));
        profitVal.setStyle("-fx-font-family: 'Inter', 'Tajawal'; -fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #22C55E;");
        grid.add(profitLabel, 0, 4);
        grid.add(profitVal, 1, 4);

        printArea.getChildren().add(grid);

        // Separator
        Separator sep2 = new Separator();
        printArea.getChildren().add(sep2);

        // Top 5 Best-Selling Parts
        VBox topPartsBox = new VBox(8);
        topPartsBox.setAlignment(Pos.TOP_RIGHT);
        Label topTitle = new Label("الأصناف الأكثر مبيعاً اليوم (Top 5 Parts Today):");
        topTitle.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #363636;");
        topPartsBox.getChildren().add(topTitle);

        if (topParts.isEmpty()) {
            Label emptyLabel = new Label("لا يوجد عمليات بيع اليوم.");
            emptyLabel.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12; -fx-text-fill: #707070;");
            topPartsBox.getChildren().add(emptyLabel);
        } else {
            int rank = 1;
            for (Object[] partData : topParts) {
                String name = (String) partData[0];
                int qty = (int) partData[1];
                double rev = (double) partData[2];

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label rankLbl = new Label(rank + ". ");
                rankLbl.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-text-fill: #FAA11F;");

                Label nameLbl = new Label(name);
                nameLbl.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 12;");
                HBox.setHgrow(nameLbl, Priority.ALWAYS);

                Label detailsLbl = new Label("الكمية: " + qty + " | الإيراد: " + fmtPrice(rev));
                detailsLbl.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 11; -fx-text-fill: #505050;");

                row.getChildren().addAll(rankLbl, nameLbl, detailsLbl);
                topPartsBox.getChildren().add(row);
                rank++;
            }
        }

        printArea.getChildren().add(topPartsBox);

        // Separator
        Separator sep3 = new Separator();
        printArea.getChildren().add(sep3);

        // Footer Thank you / Note
        Label footerNote = new Label("تم توليد هذا التقرير تلقائياً بواسطة نظام كيا إيرب");
        footerNote.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 10; -fx-text-fill: #A0A0A0;");
        VBox footerContainer = new VBox(footerNote);
        footerContainer.setAlignment(Pos.CENTER);
        printArea.getChildren().add(footerContainer);

        // Action Buttons Layout
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(15, 25, 20, 25));
        actions.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: #E5E7EB; -fx-border-width: 1 0 0 0;");

        Button btnPrint = new Button("🖨️  طباعة التقرير");
        btnPrint.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: #FFFFFF; -fx-font-family: 'Tajawal'; -fx-font-weight: bold; -fx-cursor: hand;");
        btnPrint.setOnAction(e -> printReport());

        Button btnPdf = new Button("📄  تصدير PDF");
        btnPdf.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-family: 'Tajawal'; -fx-font-weight: bold; -fx-cursor: hand;");
        btnPdf.setOnAction(e -> exportToPdf());

        Button btnClose = new Button("إغلاق");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #363636; -fx-border-color: #E5E7EB; -fx-font-family: 'Tajawal'; -fx-cursor: hand;");
        btnClose.setOnAction(e -> stage.close());

        actions.getChildren().addAll(btnPrint, btnPdf, btnClose);

        // Outer Root VBox
        VBox root = new VBox();
        root.getChildren().addAll(printArea, actions);
        VBox.setVgrow(printArea, Priority.ALWAYS);

        Scene scene = new Scene(root, 480, 680);
        stage.setScene(scene);
    }

    public void show() {
        stage.showAndWait();
    }

    private void loadReportData() {
        AppContext ctx = AppContext.get();
        totalSales = ctx.invoiceService.totalSalesToday();
        invoicesCount = ctx.invoiceService.countToday();
        itemsSold = ctx.invoiceService.itemsSoldToday();
        totalReturns = ctx.invoiceService.totalReturnsToday();
        netProfit = totalSales - totalReturns;
        topParts = ctx.invoiceService.topSellingPartsToday(5);
    }

    private void addStatRow(GridPane grid, String label, String value, int rowIndex) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-family: 'Tajawal'; -fx-font-size: 13; -fx-text-fill: #505050;");
        Label val = new Label(value);
        val.setStyle("-fx-font-family: 'Inter', 'Tajawal'; -fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #363636;");
        grid.add(lbl, 0, rowIndex);
        grid.add(val, 1, rowIndex);
    }

    private void printReport() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(stage)) {
            // Temporarily hide background color or borders if needed, then print
            boolean success = job.printPage(printArea);
            if (success) {
                job.endJob();
            }
        }
    }

    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("تصدير تقرير نهاية اليوم PDF");
        fileChooser.setInitialFileName("EndOfDayReport_" + java.time.LocalDate.now() + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                generatePdf(file);
                Alert ok = new Alert(Alert.AlertType.INFORMATION, "تم تصدير تقرير نهاية اليوم بنجاح كـ PDF");
                ok.showAndWait();
            } catch (IOException e) {
                Alert err = new Alert(Alert.AlertType.ERROR, "فشل تصدير ملف PDF: " + e.getMessage());
                err.showAndWait();
                e.printStackTrace();
            }
        }
    }

    private void generatePdf(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontReg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 50;
            float pageWidth = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header Banner
                cs.setNonStrokingColor(java.awt.Color.decode("#1a1a2e"));
                cs.addRect(0, page.getMediaBox().getHeight() - 80, pageWidth, 80);
                cs.fill();

                cs.beginText();
                cs.setFont(fontBold, 22);
                cs.setNonStrokingColor(java.awt.Color.WHITE);
                cs.newLineAtOffset(margin, page.getMediaBox().getHeight() - 40);
                cs.showText("KIA STORE");
                cs.endText();

                cs.beginText();
                cs.setFont(fontReg, 11);
                cs.setNonStrokingColor(java.awt.Color.WHITE);
                cs.newLineAtOffset(margin, page.getMediaBox().getHeight() - 60);
                cs.showText("Daily End of Day Report");
                cs.endText();

                y -= 90;
                cs.setNonStrokingColor(java.awt.Color.BLACK);

                // Meta
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("REPORT METRICS");
                cs.endText();

                cs.beginText();
                cs.setFont(fontReg, 10);
                cs.newLineAtOffset(margin, y - 18);
                cs.showText("Generated At : " + reportTime);
                cs.endText();

                y -= 50;

                // Stats Section Table layout
                float colLabel = margin;
                float colValue = margin + 200;

                drawPdfRow(cs, fontReg, fontBold, colLabel, colValue, y, "Total Today's Sales:", fmtPriceEnglish(totalSales));
                drawPdfRow(cs, fontReg, fontBold, colLabel, colValue, y - 18, "Invoices Created:", String.valueOf(invoicesCount));
                drawPdfRow(cs, fontReg, fontBold, colLabel, colValue, y - 36, "Items Sold:", String.valueOf(itemsSold));
                drawPdfRow(cs, fontReg, fontBold, colLabel, colValue, y - 54, "Total Returns:", fmtPriceEnglish(totalReturns));
                drawPdfRow(cs, fontBold, fontBold, colLabel, colValue, y - 72, "Net Profit:", fmtPriceEnglish(netProfit));

                y -= 110;

                // Top Selling Parts today
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("TOP 5 BEST SELLING PARTS TODAY");
                cs.endText();
                y -= 15;

                if (topParts.isEmpty()) {
                    cs.beginText();
                    cs.setFont(fontReg, 10);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("No sales transactions today.");
                    cs.endText();
                } else {
                    int rank = 1;
                    for (Object[] partData : topParts) {
                        String name = (String) partData[0];
                        int qty = (int) partData[1];
                        double rev = (double) partData[2];

                        // Strip non-ASCII for PDFBox standard font safety
                        String asciiName = name.replaceAll("[^\\x00-\\x7F]", "?");
                        if (asciiName.trim().isEmpty() || asciiName.contains("?")) {
                            asciiName = "Part Description (Arabic content hidden in PDF)";
                        }
                        if (asciiName.length() > 40) asciiName = asciiName.substring(0, 38) + "..";

                        cs.beginText();
                        cs.setFont(fontReg, 9);
                        cs.newLineAtOffset(margin, y);
                        cs.showText(rank + ". " + asciiName + "  -  Qty: " + qty + "  -  Revenue: " + fmtPriceEnglish(rev));
                        cs.endText();

                        y -= 16;
                        rank++;
                    }
                }

                // Footer
                cs.beginText();
                cs.setFont(fontReg, 8);
                cs.setNonStrokingColor(java.awt.Color.GRAY);
                cs.newLineAtOffset(margin, 40);
                cs.showText("KIA Store ERP system — Automated Daily Report Summary.");
                cs.endText();
            }

            doc.save(file);
        }
    }

    private void drawPdfRow(PDPageContentStream cs, PDType1Font fontLabel, PDType1Font fontVal, float colLabel, float colValue, float y, String label, String value) throws IOException {
        cs.beginText();
        cs.setFont(fontLabel, 10);
        cs.newLineAtOffset(colLabel, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(fontVal, 10);
        cs.newLineAtOffset(colValue, y);
        cs.showText(value);
        cs.endText();
    }

    private String fmtPrice(double value) {
        return String.format(Locale.US, "%,.2f EGP", value);
    }

    private String fmtPriceEnglish(double value) {
        return String.format(Locale.US, "%,.2f EGP", value);
    }
}
