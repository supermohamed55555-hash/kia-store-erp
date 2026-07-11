package com.kiastore.dao;

import com.kiastore.model.Invoice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InvoiceDao extends BaseDao<Invoice> {

    @Override
    protected String table() {
        return "invoices";
    }

    @Override
    protected String[] columns() {
        return new String[]{
            "invoice_number", "customer_name", "customer_phone", "total_amount", "discount",
            "final_amount", "payment_method", "amount_paid", "amount_due", "status", "notes", "created_by"
        };
    }

    @Override
    protected Invoice extract(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        inv.setId(rs.getInt("id"));
        inv.setInvoiceNumber(rs.getString("invoice_number"));
        inv.setCustomerName(rs.getString("customer_name"));
        inv.setCustomerPhone(rs.getString("customer_phone"));
        inv.setTotalAmount(rs.getDouble("total_amount"));
        inv.setDiscount(rs.getDouble("discount"));
        inv.setFinalAmount(rs.getDouble("final_amount"));
        inv.setPaymentMethod(Invoice.PaymentMethod.valueOf(rs.getString("payment_method").toUpperCase()));
        inv.setAmountPaid(rs.getDouble("amount_paid"));
        inv.setAmountDue(rs.getDouble("amount_due"));
        inv.setStatus(Invoice.Status.valueOf(rs.getString("status").toUpperCase()));
        inv.setNotes(rs.getString("notes"));
        int cb = rs.getInt("created_by");
        inv.setCreatedBy(rs.wasNull() ? null : cb);
        if (rs.getTimestamp("created_at") != null) {
            inv.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return inv;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Invoice inv) throws SQLException {
        ps.setString(1, inv.getInvoiceNumber());
        ps.setString(2, inv.getCustomerName());
        ps.setString(3, inv.getCustomerPhone());
        ps.setDouble(4, inv.getTotalAmount());
        ps.setDouble(5, inv.getDiscount());
        ps.setDouble(6, inv.getFinalAmount());
        ps.setString(7, inv.getPaymentMethod().name().toLowerCase());
        ps.setDouble(8, inv.getAmountPaid());
        ps.setDouble(9, inv.getAmountDue());
        ps.setString(10, inv.getStatus().name().toLowerCase());
        ps.setString(11, inv.getNotes());
        if (inv.getCreatedBy() == null) {
            ps.setNull(12, java.sql.Types.INTEGER);
        } else {
            ps.setInt(12, inv.getCreatedBy());
        }
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Invoice inv) throws SQLException {
        bindInsert(ps, inv);
        ps.setInt(13, inv.getId());
    }

    @Override
    protected int idOf(Invoice inv) {
        return inv.getId();
    }

    @Override
    protected void setId(Invoice inv, int id) {
        inv.setId(id);
    }

    public Optional<Invoice> findByInvoiceNumber(String number) {
        String sql = "SELECT * FROM invoices WHERE invoice_number = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(extract(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByInvoiceNumber failed", e);
        }
    }

    public List<Invoice> findRecent(int limit) {
        String sql = "SELECT * FROM invoices ORDER BY created_at DESC LIMIT ?";
        List<Invoice> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(extract(rs));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findRecent failed", e);
        }
        return out;
    }

    public int countAll() {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM invoices");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("countAll failed", e);
        }
    }

    public int countToday() {
        String sql = "SELECT COUNT(*) FROM invoices WHERE DATE(created_at) = CURDATE() AND status = 'active'";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("countToday failed", e);
        }
    }

    public int countYesterday() {
        String sql = "SELECT COUNT(*) FROM invoices WHERE DATE(created_at) = SUBDATE(CURDATE(), 1) AND status = 'active'";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("countYesterday failed", e);
        }
    }

    public double totalSalesToday() {
        String sql = "SELECT SUM(final_amount) FROM invoices WHERE DATE(created_at) = CURDATE() AND status = 'active'";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new DaoException("totalSalesToday failed", e);
        }
    }

    public double totalSalesYesterday() {
        String sql = "SELECT SUM(final_amount) FROM invoices WHERE DATE(created_at) = SUBDATE(CURDATE(), 1) AND status = 'active'";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new DaoException("totalSalesYesterday failed", e);
        }
    }

    public int itemsSoldToday() {
        String sql = "SELECT SUM(ii.quantity) FROM invoice_items ii " +
                     "JOIN invoices i ON ii.invoice_id = i.id " +
                     "WHERE DATE(i.created_at) = CURDATE() AND i.status = 'active'";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("itemsSoldToday failed", e);
        }
    }

    public List<Object[]> topSellingPartsToday(int limit) {
        List<Object[]> out = new ArrayList<>();
        String sql = "SELECT p.full_name, SUM(ii.quantity) as total_qty, SUM(ii.total_price) as total_rev " +
                     "FROM invoice_items ii " +
                     "JOIN invoices inv ON ii.invoice_id = inv.id " +
                     "JOIN parts p ON ii.part_id = p.id " +
                     "WHERE DATE(inv.created_at) = CURDATE() AND inv.status = 'active' " +
                     "GROUP BY p.id, p.full_name " +
                     "ORDER BY total_qty DESC LIMIT ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Object[]{
                        rs.getString("full_name"),
                        rs.getInt("total_qty"),
                        rs.getDouble("total_rev")
                    });
                }
            }
        } catch (SQLException e) {
            throw new DaoException("topSellingPartsToday failed", e);
        }
        return out;
    }

    public double totalReturnsToday() {
        String sql = "SELECT SUM(r.quantity * ii.unit_price) FROM returns r " +
                     "JOIN invoice_items ii ON r.invoice_id = ii.invoice_id AND r.part_id = ii.part_id " +
                     "WHERE DATE(r.returned_at) = CURDATE()";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new DaoException("totalReturnsToday failed", e);
        }
    }

    /**
     * Returns all active credit invoices for a specific customer phone.
     */
    public List<Invoice> findCreditByPhone(String phone) {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT * FROM invoices WHERE customer_phone = ? " +
                     "AND payment_method = 'credit' AND status = 'active' ORDER BY created_at DESC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("findCreditByPhone failed", e);
        }
        return list;
    }
}

