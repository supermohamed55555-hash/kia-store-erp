package com.kiastore.service;

import com.kiastore.dao.AuditLogDao;
import com.kiastore.model.AuditLog;

import java.util.List;

public class AuditLogService {

    private final AuditLogDao auditLogDao;

    public AuditLogService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public List<AuditLog> all() {
        return auditLogDao.findAll();
    }

    public List<AuditLog> recent(int limit) {
        return auditLogDao.findRecent(limit);
    }

    /**
     * Logs an administrative or transactional action into the database.
     * The oldData and newData parameters are automatically converted to valid JSON
     * so they can be stored in MySQL JSON columns.
     */
    public void log(Integer userId, String username, String action, String tableName,
                    Integer recordId, String oldData, String newData) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUserName(username);
        log.setAction(action);
        log.setTableName(tableName);
        log.setRecordId(recordId);
        // Auto-convert to valid JSON so MySQL JSON column never rejects the value
        log.setOldData(asJson(oldData));
        log.setNewData(asJson(newData));

        // Retrieve local host IP for logging
        try {
            log.setIpAddress(java.net.InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            log.setIpAddress("127.0.0.1");
        }

        try {
            auditLogDao.insert(log);
        } catch (Exception e) {
            System.err.println("[AuditLogService] Failed to write audit log: " + e.getMessage());
        }
    }

    /**
     * Ensures the given string is a valid JSON value suitable for storage in a MySQL
     * JSON column.
     *
     * <ul>
     *   <li>null  → null  (stored as SQL NULL)</li>
     *   <li>Already looks like a JSON object/array/quoted-string → returned as-is</li>
     *   <li>Any other plain text → wrapped in double-quotes with special chars escaped</li>
     * </ul>
     */
    private static String asJson(String data) {
        if (data == null) return null;
        String trimmed = data.trim();
        // Already a JSON object, array, boolean, number, or quoted string – pass through
        if (trimmed.startsWith("{") || trimmed.startsWith("[")
                || trimmed.startsWith("\"")
                || trimmed.equals("true") || trimmed.equals("false")
                || trimmed.equals("null")) {
            return trimmed;
        }
        // Try to detect a bare number
        try {
            Double.parseDouble(trimmed);
            return trimmed; // valid JSON number
        } catch (NumberFormatException ignored) {
            // Not a number — fall through to string wrapping
        }
        // Wrap as a JSON string literal with proper escaping
        return "\"" + trimmed
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}

