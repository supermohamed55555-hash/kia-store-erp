package com.kiastore.dao;

import com.kiastore.model.Return;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReturnDao extends BaseDao<Return> {

    @Override
    protected String table() {
        return "returns";
    }

    @Override
    protected String[] columns() {
        return new String[]{"invoice_id", "part_id", "quantity", "reason", "returned_by"};
    }

    @Override
    protected Return extract(ResultSet rs) throws SQLException {
        Return r = new Return();
        r.setId(rs.getInt("id"));
        r.setInvoiceId(rs.getInt("invoice_id"));
        r.setPartId(rs.getInt("part_id"));
        r.setQuantity(rs.getInt("quantity"));
        r.setReason(rs.getString("reason"));
        int rb = rs.getInt("returned_by");
        r.setReturnedBy(rs.wasNull() ? null : rb);
        if (rs.getTimestamp("returned_at") != null) {
            r.setReturnedAt(rs.getTimestamp("returned_at").toLocalDateTime());
        }
        return r;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Return r) throws SQLException {
        ps.setInt(1, r.getInvoiceId());
        ps.setInt(2, r.getPartId());
        ps.setInt(3, r.getQuantity());
        ps.setString(4, r.getReason());
        if (r.getReturnedBy() == null) {
            ps.setNull(5, java.sql.Types.INTEGER);
        } else {
            ps.setInt(5, r.getReturnedBy());
        }
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Return r) throws SQLException {
        bindInsert(ps, r);
        ps.setInt(6, r.getId());
    }

    @Override
    protected int idOf(Return r) {
        return r.getId();
    }

    @Override
    protected void setId(Return r, int id) {
        r.setId(id);
    }

    public List<Return> findByInvoice(int invoiceId) {
        String sql = "SELECT * FROM returns WHERE invoice_id = ? ORDER BY returned_at DESC";
        List<Return> out = new ArrayList<>();
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
}
