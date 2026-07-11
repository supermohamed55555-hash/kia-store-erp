package com.kiastore.dao;

import com.kiastore.model.Role;
import com.kiastore.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDao extends BaseDao<User> {

    @Override protected String table() { return "users"; }

    @Override protected String[] columns() {
        return new String[]{"name", "username", "password_hash", "role", "is_active"};
    }

    @Override protected User extract(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setName(rs.getString("name"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(Role.of(rs.getString("role")));
        u.setActive(rs.getBoolean("is_active"));
        if (rs.getTimestamp("created_at") != null) {
            u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return u;
    }

    @Override protected void bindInsert(PreparedStatement ps, User u) throws SQLException {
        ps.setString(1, u.getName());
        ps.setString(2, u.getUsername());
        ps.setString(3, u.getPasswordHash());
        ps.setString(4, u.getRole().name().toLowerCase()); // Save as lowercase enum
        ps.setBoolean(5, u.isActive());
    }

    @Override protected void bindUpdate(PreparedStatement ps, User u) throws SQLException {
        bindInsert(ps, u);
        ps.setInt(6, u.getId());
    }

    @Override protected int idOf(User u) { return u.getId(); }
    @Override protected void setId(User u, int id) { u.setId(id); }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(extract(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByUsername failed", e);
        }
    }
}
