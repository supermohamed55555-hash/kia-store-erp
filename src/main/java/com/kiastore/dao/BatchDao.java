package com.kiastore.dao;

import com.kiastore.model.Batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BatchDao extends BaseDao<Batch> {

    @Override
    protected String table() {
        return "batches";
    }

    @Override
    protected String[] columns() {
        return new String[]{
            "part_id", "supplier_id", "quantity", "purchase_price", "purchase_invoice_number", "received_by", "notes"
        };
    }

    @Override
    protected Batch extract(ResultSet rs) throws SQLException {
        Batch b = new Batch();
        b.setId(rs.getInt("id"));
        b.setPartId(rs.getInt("part_id"));
        b.setSupplierId(rs.getInt("supplier_id"));
        b.setQuantity(rs.getInt("quantity"));
        b.setPurchasePrice(rs.getDouble("purchase_price"));
        b.setPurchaseInvoiceNumber(rs.getString("purchase_invoice_number"));
        if (rs.getTimestamp("received_at") != null) {
            b.setReceivedAt(rs.getTimestamp("received_at").toLocalDateTime());
        }
        int by = rs.getInt("received_by");
        b.setReceivedBy(rs.wasNull() ? null : by);
        b.setNotes(rs.getString("notes"));
        return b;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Batch b) throws SQLException {
        ps.setInt(1, b.getPartId());
        ps.setInt(2, b.getSupplierId());
        ps.setInt(3, b.getQuantity());
        ps.setDouble(4, b.getPurchasePrice());
        ps.setString(5, b.getPurchaseInvoiceNumber());
        if (b.getReceivedBy() == null) {
            ps.setNull(6, java.sql.Types.INTEGER);
        } else {
            ps.setInt(6, b.getReceivedBy());
        }
        ps.setString(7, b.getNotes());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Batch b) throws SQLException {
        bindInsert(ps, b);
        ps.setInt(8, b.getId());
    }

    @Override
    protected int idOf(Batch b) {
        return b.getId();
    }

    @Override
    protected void setId(Batch b, int id) {
        b.setId(id);
    }

    public List<Batch> findByPart(int partId) {
        String sql = "SELECT * FROM batches WHERE part_id = ? ORDER BY received_at DESC";
        List<Batch> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(extract(rs));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findByPart failed", e);
        }
        return out;
    }
}
