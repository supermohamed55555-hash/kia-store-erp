package com.kiastore.service;

import com.kiastore.dao.PartDao;
import com.kiastore.dao.InvoiceDao;
import com.kiastore.dao.InvoiceItemDao;
import com.kiastore.dao.ReturnDao;
import com.kiastore.model.Invoice;
import com.kiastore.model.InvoiceItem;
import com.kiastore.model.Return;
import com.kiastore.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class InvoiceService {

    private final InvoiceDao invoiceDao;
    private final InvoiceItemDao invoiceItemDao;
    private final PartDao partDao;
    private final ReturnDao returnDao;

    public InvoiceService(InvoiceDao invoiceDao, InvoiceItemDao invoiceItemDao, PartDao partDao, ReturnDao returnDao) {
        this.invoiceDao = invoiceDao;
        this.invoiceItemDao = invoiceItemDao;
        this.partDao = partDao;
        this.returnDao = returnDao;
    }

    public List<Invoice> all() {
        return invoiceDao.findAll();
    }

    public List<Invoice> recent(int limit) {
        return invoiceDao.findRecent(limit);
    }

    public Optional<Invoice> findById(int id) {
        return invoiceDao.findById(id);
    }

    public List<InvoiceItem> getItems(int invoiceId) {
        return invoiceItemDao.findByInvoice(invoiceId);
    }

    /**
     * Creates an invoice with items in a secure database transaction.
     */
    public Invoice createInvoice(Invoice inv, List<InvoiceItem> items) throws SQLException {
        Connection c = ConnectionFactory.borrow();
        try {
            c.setAutoCommit(false);
            
            // 1. Set temporary placeholder — will be replaced after we get the DB-generated ID
            inv.setInvoiceNumber("TEMP");
            
            // Calculate final amounts
            double total = 0;
            for (InvoiceItem item : items) {
                item.setTotalPrice(item.getUnitPrice() * item.getQuantity());
                total += item.getTotalPrice();
            }
            inv.setTotalAmount(total);
            inv.setFinalAmount(Math.max(0.0, total - inv.getDiscount()));

            // 2. Insert Invoice (gets real DB-generated ID)
            Invoice savedInv = invoiceDao.insert(inv);
            int invoiceId = savedInv.getId();

            // 3. Now update invoice_number using the real ID: INV-20260711-0001
            String timePart = LocalDate.now().toString().replace("-", "");
            String finalNumber = "INV-" + timePart + "-" + String.format("%04d", invoiceId);
            savedInv.setInvoiceNumber(finalNumber);
            invoiceDao.update(savedInv);

            // 3. Insert Items & Decrement Stock
            for (InvoiceItem item : items) {
                item.setInvoiceId(invoiceId);
                invoiceItemDao.insert(item);
                
                // Decrement stock
                boolean ok = partDao.decrementStock(item.getPartId(), item.getQuantity());
                if (!ok) {
                    throw new SQLException("الكمية المطلوبة غير متوفرة في المخزن للصنف ID: " + item.getPartId());
                }
            }

            c.commit();
            return savedInv;
        } catch (Exception e) {
            c.rollback();
            throw new SQLException("فشل إنشاء الفاتورة: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
            c.close();
        }
    }

    /**
     * Cancels an active invoice, returning all items back to stock.
     */
    public boolean cancelInvoice(int invoiceId, Integer cancelledByUserId) throws SQLException {
        Optional<Invoice> opt = invoiceDao.findById(invoiceId);
        if (opt.isEmpty()) return false;
        
        Invoice inv = opt.get();
        if (inv.getStatus() != Invoice.Status.ACTIVE) {
            throw new SQLException("لا يمكن إلغاء فاتورة غير نشطة");
        }

        Connection c = ConnectionFactory.borrow();
        try {
            c.setAutoCommit(false);

            // 1. Set Invoice status to cancelled
            inv.setStatus(Invoice.Status.CANCELLED);
            invoiceDao.update(inv);

            // 2. Return items to stock
            List<InvoiceItem> items = invoiceItemDao.findByInvoice(invoiceId);
            for (InvoiceItem item : items) {
                partDao.incrementStock(item.getPartId(), item.getQuantity());
            }

            c.commit();
            return true;
        } catch (Exception e) {
            c.rollback();
            throw new SQLException("فشل إلغاء الفاتورة: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
            c.close();
        }
    }

    /**
     * Returns a specific quantity of a part from an invoice, restocking the part.
     */
    public void returnItem(int invoiceId, int partId, int qty, String reason, Integer operatorUserId) throws SQLException {
        Optional<Invoice> opt = invoiceDao.findById(invoiceId);
        if (opt.isEmpty()) throw new SQLException("الفاتورة غير موجودة");

        Invoice inv = opt.get();
        if (inv.getStatus() != Invoice.Status.ACTIVE) {
            throw new SQLException("لا يمكن إرجاع أصناف من فاتورة ملغاة أو مرتجعة بالكامل");
        }

        List<InvoiceItem> items = invoiceItemDao.findByInvoice(invoiceId);
        InvoiceItem targetItem = items.stream()
                .filter(ii -> ii.getPartId() == partId)
                .findFirst()
                .orElseThrow(() -> new SQLException("الصنف المحدد غير موجود في الفاتورة"));

        // Check if already returned quantity allows this return
        List<Return> pastReturns = returnDao.findByInvoice(invoiceId);
        int alreadyReturned = pastReturns.stream()
                .filter(r -> r.getPartId() == partId)
                .mapToInt(Return::getQuantity)
                .sum();

        if (alreadyReturned + qty > targetItem.getQuantity()) {
            throw new SQLException("الكمية المراد إرجاعها تتجاوز الكمية المباعة المتبقية");
        }

        Connection c = ConnectionFactory.borrow();
        try {
            c.setAutoCommit(false);

            // 1. Insert Return log
            Return ret = new Return();
            ret.setInvoiceId(invoiceId);
            ret.setPartId(partId);
            ret.setQuantity(qty);
            ret.setReason(reason);
            ret.setReturnedBy(operatorUserId);
            returnDao.insert(ret);

            // 2. Increment Stock
            partDao.incrementStock(partId, qty);

            // 3. Check if all items in invoice are fully returned
            boolean allReturned = true;
            for (InvoiceItem item : items) {
                int totalReturnedForPart = returnDao.findByInvoice(invoiceId).stream()
                        .filter(r -> r.getPartId() == item.getPartId())
                        .mapToInt(Return::getQuantity)
                        .sum();
                // Add the current return quantity if not committed/visible yet
                if (item.getPartId() == partId) {
                    totalReturnedForPart += qty;
                }
                if (totalReturnedForPart < item.getQuantity()) {
                    allReturned = false;
                    break;
                }
            }

            if (allReturned) {
                inv.setStatus(Invoice.Status.RETURNED);
                invoiceDao.update(inv);
            }

            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw new SQLException("فشل إرجاع الصنف: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
            c.close();
        }
    }

    public double totalSalesToday() {
        return invoiceDao.totalSalesToday();
    }

    public double totalSalesYesterday() {
        return invoiceDao.totalSalesYesterday();
    }

    public int countToday() {
        return invoiceDao.countToday();
    }

    public int countYesterday() {
        return invoiceDao.countYesterday();
    }

    /**
     * Stats of daily sales total amounts for the last 7 days.
     */
    public Map<String, Double> last7DaysSales() {
        Map<String, Double> map = new LinkedHashMap<>();
        // Pre-fill last 7 days with 0.0
        for (int i = 6; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).toString();
            map.put(date, 0.0);
        }

        String sql = "SELECT DATE(created_at), SUM(final_amount) FROM invoices " +
                     "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) AND status = 'active' " +
                     "GROUP BY DATE(created_at) ORDER BY DATE(created_at) ASC";
        try (Connection c = ConnectionFactory.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getDouble(2));
            }
        } catch (SQLException ignore) {}
        return map;
    }

    /**
     * Returns top selling parts this week.
     */
    public List<Object[]> topSellingPartsThisWeek(int limit) {
        List<Object[]> out = new ArrayList<>();
        String sql = "SELECT p.id, p.full_name, p.internal_code, SUM(ii.quantity) as total_sold " +
                     "FROM invoice_items ii " +
                     "JOIN invoices inv ON ii.invoice_id = inv.id " +
                     "JOIN parts p ON ii.part_id = p.id " +
                     "WHERE inv.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) AND inv.status = 'active' " +
                     "GROUP BY p.id, p.full_name, p.internal_code " +
                     "ORDER BY total_sold DESC LIMIT ?";
        try (Connection c = ConnectionFactory.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Object[]{
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        rs.getString("internal_code"),
                        rs.getInt("total_sold")
                    });
                }
            }
        } catch (SQLException ignore) {}
        return out;
    }

    public int itemsSoldToday() {
        return invoiceDao.itemsSoldToday();
    }

    public List<Object[]> topSellingPartsToday(int limit) {
        return invoiceDao.topSellingPartsToday(limit);
    }

    public double totalReturnsToday() {
        return invoiceDao.totalReturnsToday();
    }
}
