package com.kiastore.dao;

import com.kiastore.model.InvoiceItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InvoiceItemDao extends BaseDao<InvoiceItem> {

    @Override
    protected String table() {
        return "invoice_items";
    }

    @Override
    protected String[] columns() {
        return new String[]{"invoice_id", "part_id", "quantity", "unit_price", "total_price"};
    }

    @Override
    protected InvoiceItem extract(ResultSet rs) throws SQLException {
        InvoiceItem item = new InvoiceItem();
        item.setId(rs.getInt("id"));
        item.setInvoiceId(rs.getInt("invoice_id"));
        item.setPartId(rs.getInt("part_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        item.setTotalPrice(rs.getDouble("total_price"));
        
        // Try to retrieve joined metadata if available
        try {
            item.setPartFullName(rs.getString("part_full_name"));
            item.setInternalCode(rs.getString("internal_code"));
            item.setPurchasePrice(rs.getDouble("purchase_price"));
        } catch (SQLException ignore) {
            // These columns won't exist in a pure SELECT * FROM invoice_items query
        }
        
        return item;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, InvoiceItem item) throws SQLException {
        ps.setInt(1, item.getInvoiceId());
        ps.setInt(2, item.getPartId());
        ps.setInt(3, item.getQuantity());
        ps.setDouble(4, item.getUnitPrice());
        ps.setDouble(5, item.getTotalPrice());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, InvoiceItem item) throws SQLException {
        bindInsert(ps, item);
        ps.setInt(6, item.getId());
    }

    @Override
    protected int idOf(InvoiceItem item) {
        return item.getId();
    }

    @Override
    protected void setId(InvoiceItem item, int id) {
        item.setId(id);
    }

    public List<InvoiceItem> findByInvoice(int invoiceId) {
        String sql = "SELECT ii.*, p.full_name AS part_full_name, p.internal_code AS internal_code, p.purchase_price AS purchase_price " +
                     "FROM invoice_items ii " +
                     "JOIN parts p ON ii.part_id = p.id " +
                     "WHERE ii.invoice_id = ?";
        List<InvoiceItem> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(extract(rs));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findByInvoice failed", e);
        }
        return out;
    }

    public List<InvoiceItem> findByPartId(int partId) {
        String sql = "SELECT ii.*, p.full_name AS part_full_name, p.internal_code AS internal_code, p.purchase_price AS purchase_price " +
                     "FROM invoice_items ii " +
                     "JOIN parts p ON ii.part_id = p.id " +
                     "WHERE ii.part_id = ? ORDER BY ii.id DESC";
        List<InvoiceItem> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(extract(rs));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findByPartId failed", e);
        }
        return out;
    }

    /**
     * Fetches ALL invoice items for a list of invoice IDs in a single DB query.
     * Returns a Map: invoiceId → List&lt;InvoiceItem&gt;.
     * Use this instead of calling findByInvoice() inside a loop (avoids N+1 problem).
     */
    public java.util.Map<Integer, List<InvoiceItem>> findAllForInvoices(List<Integer> invoiceIds) {
        java.util.Map<Integer, List<InvoiceItem>> result = new java.util.HashMap<>();
        if (invoiceIds == null || invoiceIds.isEmpty()) return result;

        // Build IN (?,?,?) placeholder
        String placeholders = invoiceIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT ii.*, p.full_name AS part_full_name, p.internal_code AS internal_code, p.purchase_price AS purchase_price " +
                     "FROM invoice_items ii " +
                     "JOIN parts p ON ii.part_id = p.id " +
                     "WHERE ii.invoice_id IN (" + placeholders + ")";

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < invoiceIds.size(); i++) {
                ps.setInt(i + 1, invoiceIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceItem item = extract(rs);
                    result.computeIfAbsent(item.getInvoiceId(), k -> new ArrayList<>()).add(item);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findAllForInvoices failed", e);
        }
        return result;
    }
}
