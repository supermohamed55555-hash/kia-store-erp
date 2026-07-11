package com.kiastore.dao;

import com.kiastore.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reusable generic DAO base class.
 */
public abstract class BaseDao<T> {

    protected abstract String table();
    protected abstract String[] columns();
    protected abstract T extract(ResultSet rs) throws SQLException;
    protected abstract void bindInsert(PreparedStatement ps, T entity) throws SQLException;
    protected abstract void bindUpdate(PreparedStatement ps, T entity) throws SQLException;
    protected abstract int idOf(T entity);
    protected abstract void setId(T entity, int id);

    protected Connection conn() {
        try { return ConnectionFactory.get(); }
        catch (SQLException e) { throw new DaoException("Connection failed", e); }
    }

    public Optional<T> findById(int id) {
        String sql = "SELECT * FROM " + table() + " WHERE id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(extract(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findById " + table() + " failed", e);
        }
    }

    public List<T> findAll() {
        String sql = "SELECT * FROM " + table();
        List<T> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(extract(rs));
        } catch (SQLException e) {
            throw new DaoException("findAll " + table() + " failed", e);
        }
        return out;
    }

    public T insert(T entity) {
        String cols = String.join(",", columns());
        String qs = "?,".repeat(columns().length);
        qs = qs.substring(0, qs.length() - 1);
        String sql = "INSERT INTO " + table() + "(" + cols + ") VALUES (" + qs + ")";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, entity);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) setId(entity, keys.getInt(1));
            }
            return entity;
        } catch (SQLException e) {
            throw new DaoException("insert " + table() + " failed", e);
        }
    }

    public boolean update(T entity) {
        StringBuilder sb = new StringBuilder("UPDATE ").append(table()).append(" SET ");
        for (int i = 0; i < columns().length; i++) {
            sb.append(columns()[i]).append(" = ?");
            if (i < columns().length - 1) sb.append(", ");
        }
        sb.append(" WHERE id = ?");
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            bindUpdate(ps, entity);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("update " + table() + " failed", e);
        }
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM " + table() + " WHERE id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("delete " + table() + " failed", e);
        }
    }
}
