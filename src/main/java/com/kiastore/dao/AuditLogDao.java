package com.kiastore.dao;

import com.kiastore.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDao extends BaseDao<AuditLog> {

    @Override
    protected String table() {
        return "audit_logs";
    }

    @Override
    protected String[] columns() {
        return new String[]{
            "user_id", "user_name", "action", "table_name", "record_id", "old_data", "new_data", "ip_address"
        };
    }

    @Override
    protected AuditLog extract(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getInt("id"));
        
        int uid = rs.getInt("user_id");
        log.setUserId(rs.wasNull() ? null : uid);
        
        log.setUserName(rs.getString("user_name"));
        log.setAction(rs.getString("action"));
        log.setTableName(rs.getString("table_name"));
        
        int rid = rs.getInt("record_id");
        log.setRecordId(rs.wasNull() ? null : rid);
        
        log.setOldData(rs.getString("old_data"));
        log.setNewData(rs.getString("new_data"));
        log.setIpAddress(rs.getString("ip_address"));
        
        if (rs.getTimestamp("created_at") != null) {
            log.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return log;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, AuditLog log) throws SQLException {
        if (log.getUserId() == null) {
            ps.setNull(1, java.sql.Types.INTEGER);
        } else {
            ps.setInt(1, log.getUserId());
        }
        ps.setString(2, log.getUserName());
        ps.setString(3, log.getAction());
        ps.setString(4, log.getTableName());
        if (log.getRecordId() == null) {
            ps.setNull(5, java.sql.Types.INTEGER);
        } else {
            ps.setInt(5, log.getRecordId());
        }
        ps.setString(6, log.getOldData());
        ps.setString(7, log.getNewData());
        ps.setString(8, log.getIpAddress());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, AuditLog log) throws SQLException {
        bindInsert(ps, log);
        ps.setInt(9, log.getId());
    }

    @Override
    protected int idOf(AuditLog log) {
        return log.getId();
    }

    @Override
    protected void setId(AuditLog log, int id) {
        log.setId(id);
    }

    public List<AuditLog> findRecent(int limit) {
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?";
        List<AuditLog> out = new ArrayList<>();
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

    public List<AuditLog> findByRecordId(String tableName, int recordId, int limit) {
        String sql = "SELECT * FROM audit_logs WHERE table_name = ? AND record_id = ? ORDER BY created_at DESC LIMIT ?";
        List<AuditLog> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setInt(2, recordId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(extract(rs));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("findByRecordId failed", e);
        }
        return out;
    }

    /**
     * Auto-archive: if audit_logs has more than 50,000 rows,
     * move records older than 6 months to audit_logs_archive.
     * Called silently in a background thread at startup.
     */
    public void archiveOldLogs() {
        try (Connection c = conn()) {
            // Check total count first
            int count;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_logs");
                 ResultSet rs = ps.executeQuery()) {
                count = rs.next() ? rs.getInt(1) : 0;
            }
            if (count <= 50_000) return;   // nothing to do

            // Copy old rows to archive
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT IGNORE INTO audit_logs_archive " +
                    "SELECT * FROM audit_logs WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)")) {
                ps.executeUpdate();
            }
            // Delete archived rows from main table
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM audit_logs WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Silent – archive is non-critical; log to stderr only
            System.err.println("[AuditLogDao] archiveOldLogs failed: " + e.getMessage());
        }
    }
}
