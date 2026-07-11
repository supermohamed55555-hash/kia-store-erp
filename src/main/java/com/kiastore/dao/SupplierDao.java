package com.kiastore.dao;

import com.kiastore.model.Supplier;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SupplierDao extends BaseDao<Supplier> {

    @Override
    protected String table() {
        return "suppliers";
    }

    @Override
    protected String[] columns() {
        return new String[]{"name", "phone", "address", "notes"};
    }

    @Override
    protected Supplier extract(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        s.setNotes(rs.getString("notes"));
        s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return s;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Supplier s) throws SQLException {
        ps.setString(1, s.getName());
        ps.setString(2, s.getPhone());
        ps.setString(3, s.getAddress());
        ps.setString(4, s.getNotes());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Supplier s) throws SQLException {
        bindInsert(ps, s);
        ps.setInt(5, s.getId());
    }

    @Override
    protected int idOf(Supplier s) {
        return s.getId();
    }

    @Override
    protected void setId(Supplier s, int id) {
        s.setId(id);
    }
}
