package com.kiastore.db;

import at.favre.lib.crypto.bcrypt.BCrypt;

import com.kiastore.dao.AuditLogDao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs once at application startup to check the DB, apply migrations, and seed default users.
 */
public final class DatabaseBootstrap {

    private static final Path SCHEMA = Path.of("database", "schema.sql");
    private static final Path SEED   = Path.of("database", "seed.sql");

    private DatabaseBootstrap() {}

    public static void run() throws SQLException, IOException {
        ensureDatabase();
        try (Connection c = ConnectionFactory.get()) {
            applySql(c, SCHEMA);
            ensureDefaultUsers(c);
            if ("on".equalsIgnoreCase(System.getProperty("matraknhash.seed", "on"))) {
                applySql(c, SEED);
            }
        }
        // Background: archive old audit logs silently (non-blocking)
        Thread archiveThread = new Thread(() -> new AuditLogDao().archiveOldLogs(), "audit-archive");
        archiveThread.setDaemon(true);
        archiveThread.start();
    }


    private static void ensureDatabase() throws SQLException {
        try (Connection c = ConnectionFactory.serverOnly();
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + ConnectionFactory.dbName()
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void applySql(Connection c, Path file) throws SQLException, IOException {
        if (!Files.exists(file)) return;
        String sql = stripLineComments(Files.readString(file, StandardCharsets.UTF_8));
        try (Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    st.execute(trimmed);
                } catch (SQLException e) {
                    if (isAlreadyExists(e, trimmed)) continue;
                    throw e;
                }
            }
        }
    }

    private static boolean isAlreadyExists(SQLException e, String stmt) {
        String m = String.valueOf(e.getMessage()).toLowerCase();
        String s = stmt.toLowerCase();

        if (s.startsWith("create index") || s.startsWith("create unique index")) {
            return m.contains("duplicate key name") || m.contains("already exists");
        }
        if (s.startsWith("alter table")) {
            if (m.contains("duplicate column")) return true;
            if (m.contains("duplicate foreign key") || m.contains("already exists")
                    || m.contains("duplicate key on write or update")) return true;
            if (m.contains("check constraint") && m.contains("does not exist")) return true;
            if (m.contains("doesn't exist") || m.contains("doesn t exist")
                    || m.contains("check") && m.contains("not found")) return true;
        }
        return false;
    }

    private static String stripLineComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\\R")) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }

    private static void ensureDefaultUsers(Connection c) throws SQLException {
        ensureUser(c, 1, "admin",     "admin123",     "System Administrator", "admin");
        ensureUser(c, 2, "cashier",   "cashier123",   "Default Cashier",      "cashier");
        ensureUser(c, 3, "warehouse", "warehouse123", "Default Warehouse",    "warehouse");
    }

    private static void ensureUser(Connection c, int id, String user, String pwd, String name, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE id = ? OR username = ?")) {
            ps.setInt(1, id);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String hash = BCrypt.withDefaults().hashToString(10, pwd.toCharArray());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(id,username,password_hash,name,role) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setString(2, user);
            ps.setString(3, hash);
            ps.setString(4, name);
            ps.setString(5, role);
            ps.executeUpdate();
        }
    }
}
