package com.kiastore.util;

import com.kiastore.model.Invoice;
import com.kiastore.model.Part;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility class to export system data (Parts, Invoices) to Microsoft Excel (.xlsx) format.
 * Uses Apache POI.
 */
public class ExcelExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Exports a list of parts to an Excel file.
     */
    public static void exportParts(List<Part> parts, File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("المخزون (Inventory)");

            // Create headers
            String[] headers = {
                "كود الصنف (ID)", "الكود الداخلي", "الباركود", "الاسم بالكامل", 
                "نوع الصنف", "السيارة", "الشركة المصنعة", "مكان التخزين", 
                "الكمية الحالية", "سعر البيع"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create styles for data cells
            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle priceStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("#,##0.00"));
            priceStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle warningStyle = workbook.createCellStyle();
            warningStyle.setFillForegroundColor(IndexedColors.RED1.getIndex());
            warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            warningStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowNum = 1;
            for (Part part : parts) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(part.getId());
                row.createCell(1).setCellValue(part.getInternalCode() != null ? part.getInternalCode() : "");
                row.createCell(2).setCellValue(part.getBarcode() != null ? part.getBarcode() : "");
                row.createCell(3).setCellValue(part.getFullName() != null ? part.getFullName() : "");
                row.createCell(4).setCellValue(part.getPartType() != null ? part.getPartType() : "");
                row.createCell(5).setCellValue((part.getCarName() != null ? part.getCarName() : "") + " " + (part.getCarModel() != null ? part.getCarModel() : ""));
                row.createCell(6).setCellValue(part.getManufacturer() != null ? part.getManufacturer() : "");
                row.createCell(7).setCellValue(part.getLocation() != null ? part.getLocation() : "");

                Cell stockCell = row.createCell(8);
                stockCell.setCellValue(part.getCurrentStock());
                if (part.isLowStock()) {
                    // Highlight low stock
                    CellStyle lowStockStyle = workbook.createCellStyle();
                    lowStockStyle.cloneStyleFrom(normalStyle);
                    Font font = workbook.createFont();
                    font.setColor(IndexedColors.RED.getIndex());
                    font.setBold(true);
                    lowStockStyle.setFont(font);
                    stockCell.setCellStyle(lowStockStyle);
                } else {
                    stockCell.setCellStyle(normalStyle);
                }

                Cell priceCell = row.createCell(9);
                priceCell.setCellValue(part.getSalePrice());
                priceCell.setCellStyle(priceStyle);

                // Apply alignment style to text cells
                for (int i = 0; i <= 7; i++) {
                    row.getCell(i).setCellStyle(normalStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
        }
    }

    /**
     * Exports a list of invoices to an Excel file.
     */
    public static void exportInvoices(List<Invoice> invoices, File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("المبيعات (Sales)");

            // Create headers
            String[] headers = {
                "رقم الفاتورة", "التاريخ", "العميل", "الهاتف", 
                "الإجمالي", "الخصم", "الصافي", "طريقة الدفع", "الحالة", "ملاحظات"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create cell styles
            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle amountStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            amountStyle.setDataFormat(format.getFormat("#,##0.00"));
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);

            int rowNum = 1;
            for (Invoice inv : invoices) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(inv.getInvoiceNumber());
                row.createCell(1).setCellValue(inv.getCreatedAt() != null ? inv.getCreatedAt().format(DATE_FMT) : "");
                row.createCell(2).setCellValue(inv.getCustomerName() != null && !inv.getCustomerName().isBlank() ? inv.getCustomerName() : "نقدي");
                row.createCell(3).setCellValue(inv.getCustomerPhone() != null ? inv.getCustomerPhone() : "");

                Cell totalCell = row.createCell(4);
                totalCell.setCellValue(inv.getTotalAmount());
                totalCell.setCellStyle(amountStyle);

                Cell discountCell = row.createCell(5);
                discountCell.setCellValue(inv.getDiscount());
                discountCell.setCellStyle(amountStyle);

                Cell finalCell = row.createCell(6);
                finalCell.setCellValue(inv.getFinalAmount());
                finalCell.setCellStyle(amountStyle);

                row.createCell(7).setCellValue(inv.getPaymentMethod() != null ? inv.getPaymentMethod().name() : "CASH");
                
                String statusAr = switch (inv.getStatus()) {
                    case ACTIVE -> "نشطة";
                    case CANCELLED -> "ملغاة";
                    case RETURNED -> "مرتجعة بالكامل";
                };
                row.createCell(8).setCellValue(statusAr);
                row.createCell(9).setCellValue(inv.getNotes() != null ? inv.getNotes() : "");

                // Align non-numeric cells
                for (int i = 0; i < headers.length; i++) {
                    if (i != 4 && i != 5 && i != 6) {
                        row.getCell(i).setCellStyle(normalStyle);
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);

        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);

        return style;
    }
}
