package com.kiastore.util;

import com.kiastore.model.Invoice;
import com.kiastore.model.InvoiceItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates a professional PDF invoice for the KIA Store ERP.
 * Uses Apache PDFBox 3.x.
 */
public class InvoicePdfExporter {

    private static final String SHOP_NAME      = "KIA Store";
    private static final String SHOP_PHONE     = "Tel: 01234567890";
    private static final String SHOP_ADDRESS   = "Cairo, Egypt";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Generates a PDF invoice and saves it to the specified file path.
     *
     * @param invoice   The invoice to print.
     * @param items     The list of invoice items.
     * @param outputFile The file to save the PDF to.
     * @throws IOException if PDF creation fails.
     */
    public static void export(Invoice invoice, List<InvoiceItem> items, File outputFile) throws IOException {

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin    = 50;
            float pageWidth = page.getMediaBox().getWidth();
            float y         = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ─── Header ───────────────────────────────────────────
                cs.setNonStrokingColor(Color.decode("#1a1a2e"));
                cs.addRect(0, page.getMediaBox().getHeight() - 80, pageWidth, 80);
                cs.fill();

                cs.beginText();
                cs.setFont(fontBold, 18);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(margin, page.getMediaBox().getHeight() - 40);
                cs.showText(SHOP_NAME);
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(margin, page.getMediaBox().getHeight() - 60);
                cs.showText(SHOP_PHONE + "  |  " + SHOP_ADDRESS);
                cs.endText();

                y -= 90;
                cs.setNonStrokingColor(Color.BLACK);

                // ─── Invoice Info ──────────────────────────────────────
                cs.beginText();
                cs.setFont(fontBold, 13);
                cs.newLineAtOffset(margin, y);
                cs.showText("INVOICE");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y - 18);
                cs.showText("Invoice No : " + invoice.getInvoiceNumber());
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y - 32);
                String dateStr = invoice.getCreatedAt() != null
                        ? invoice.getCreatedAt().format(DATE_FMT) : "N/A";
                cs.showText("Date       : " + dateStr);
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y - 46);
                String customer = invoice.getCustomerName() != null ? invoice.getCustomerName() : "Walk-in Customer";
                // Strip non-ASCII for PDF encoding safety
                String asciiCustomer = customer.replaceAll("[^\\x00-\\x7F]", "?");
                if (asciiCustomer.trim().isEmpty() || asciiCustomer.contains("?")) {
                    asciiCustomer = "Customer (Arabic Name)";
                }
                cs.showText("Customer   : " + asciiCustomer);
                cs.endText();

                if (invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()) {
                    cs.beginText();
                    cs.setFont(fontRegular, 10);
                    cs.newLineAtOffset(margin, y - 60);
                    cs.showText("Phone      : " + invoice.getCustomerPhone());
                    cs.endText();
                    y -= 14;
                }

                y -= 80;

                // ─── Table Header ──────────────────────────────────────
                float colItem  = margin;
                float colCode  = margin + 200;
                float colQty   = margin + 320;
                float colPrice = margin + 380;
                float colTotal = margin + 450;

                cs.setNonStrokingColor(Color.decode("#1a1a2e"));
                cs.addRect(margin - 5, y - 5, pageWidth - 2 * margin + 10, 20);
                cs.fill();

                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(colItem,  y + 3);  cs.showText("PART NAME");
                cs.endText();
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(colCode,  y + 3);  cs.showText("CODE");
                cs.endText();
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(colQty,   y + 3);  cs.showText("QTY");
                cs.endText();
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(colPrice, y + 3);  cs.showText("UNIT");
                cs.endText();
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(colTotal, y + 3);  cs.showText("TOTAL");
                cs.endText();

                y -= 22;
                cs.setNonStrokingColor(Color.BLACK);

                // ─── Table Rows ────────────────────────────────────────
                boolean shade = false;
                for (InvoiceItem item : items) {
                    if (shade) {
                        cs.setNonStrokingColor(Color.decode("#f0f0f0"));
                        cs.addRect(margin - 5, y - 4, pageWidth - 2 * margin + 10, 16);
                        cs.fill();
                        cs.setNonStrokingColor(Color.BLACK);
                    }
                    shade = !shade;

                    String partName = item.getPartFullName() != null ? item.getPartFullName() : "Part #" + item.getPartId();
                    // Strip non-ASCII for PDF encoding safety
                    String asciiPartName = partName.replaceAll("[^\\x00-\\x7F]", "?");
                    if (asciiPartName.trim().isEmpty() || asciiPartName.contains("?")) {
                        asciiPartName = item.getInternalCode() != null ? item.getInternalCode() : "Part #" + item.getPartId();
                    }
                    if (asciiPartName.length() > 30) asciiPartName = asciiPartName.substring(0, 28) + "..";

                    String code = item.getInternalCode() != null ? item.getInternalCode() : "-";

                    cs.beginText(); cs.setFont(fontRegular, 9); cs.newLineAtOffset(colItem,  y); cs.showText(asciiPartName); cs.endText();
                    cs.beginText(); cs.setFont(fontRegular, 9); cs.newLineAtOffset(colCode,  y); cs.showText(code); cs.endText();
                    cs.beginText(); cs.setFont(fontRegular, 9); cs.newLineAtOffset(colQty,   y); cs.showText(String.valueOf(item.getQuantity())); cs.endText();
                    cs.beginText(); cs.setFont(fontRegular, 9); cs.newLineAtOffset(colPrice, y); cs.showText(fmt(item.getUnitPrice())); cs.endText();
                    cs.beginText(); cs.setFont(fontRegular, 9); cs.newLineAtOffset(colTotal, y); cs.showText(fmt(item.getTotalPrice())); cs.endText();

                    y -= 18;
                }

                // ─── Separator line ────────────────────────────────────
                y -= 6;
                cs.setStrokingColor(Color.DARK_GRAY);
                cs.moveTo(margin, y); cs.lineTo(pageWidth - margin, y); cs.stroke();
                y -= 18;

                // ─── Totals ────────────────────────────────────────────
                drawTotalLine(cs, fontRegular, fontBold, margin, pageWidth, y,       "Subtotal",       invoice.getTotalAmount(), false);
                drawTotalLine(cs, fontRegular, fontBold, margin, pageWidth, y - 16,  "Discount",       invoice.getDiscount(), false);
                drawTotalLine(cs, fontRegular, fontBold, margin, pageWidth, y - 36,  "TOTAL (EGP)",    invoice.getFinalAmount(), true);

                y -= 70;

                // ─── Payment Method ────────────────────────────────────
                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Payment: " + (invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : "CASH"));
                cs.endText();

                // ─── Footer ────────────────────────────────────────────
                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.setNonStrokingColor(Color.GRAY);
                cs.newLineAtOffset(margin, 40);
                cs.showText("Thank you for your business! — " + SHOP_NAME);
                cs.endText();
            }

            doc.save(outputFile);
        }
    }

    private static void drawTotalLine(PDPageContentStream cs,
                                      PDType1Font regular, PDType1Font bold,
                                      float margin, float pageWidth,
                                      float y, String label, double amount, boolean highlight) throws IOException {
        if (highlight) {
            cs.setNonStrokingColor(Color.decode("#1a1a2e"));
            cs.addRect(pageWidth - margin - 160, y - 4, 165, 16);
            cs.fill();
            cs.setNonStrokingColor(Color.WHITE);
        } else {
            cs.setNonStrokingColor(Color.BLACK);
        }

        cs.beginText();
        cs.setFont(highlight ? bold : regular, highlight ? 11 : 10);
        cs.newLineAtOffset(pageWidth - margin - 155, y);
        cs.showText(label + ":  " + fmt(amount) + " EGP");
        cs.endText();

        cs.setNonStrokingColor(Color.BLACK);
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}
