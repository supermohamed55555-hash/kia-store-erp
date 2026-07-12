package com.kiastore.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Single point of access to database connections, backed by a HikariCP pool.
 */
public final class ConnectionFactory {

    private static HikariDataSource pool;
    private static String jdbcUrl;
    private static String jdbcUrlNoDb;
    private static String dbName;
    private static String user;
    private static String password;

    static {
        Properties p = loadProps();
        String host = sysOrProp(p, "db.host");
        String port = sysOrProp(p, "db.port");
        dbName      = sysOrProp(p, "db.name");
        user        = sysOrProp(p, "db.user");
        password    = sysOrProp(p, "db.password");
        boolean tls = Boolean.parseBoolean(sysOrProp(p, "db.tls"));

        // ── Tuned JDBC suffix ──────────────────────────────────────────────
        // cachePrepStmts / prepStmtCacheSize: larger cache = fewer parse cycles
        // useServerPrepStmts: let MySQL server cache execution plans
        // rewriteBatchedStatements: bulk INSERT/UPDATE in one round-trip
        // elideSetAutoCommits: skip redundant autocommit toggle packets
        // maintainTimeStats: disable internal time tracking (saves CPU)
        String suffix = "?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8"
                + "&cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=4096"
                + "&useServerPrepStmts=true&rewriteBatchedStatements=true"
                + "&elideSetAutoCommits=true&maintainTimeStats=false"
                + (tls
                    ? "&sslMode=REQUIRED&enabledTLSProtocols=TLSv1.2,TLSv1.3"
                    : "&useSSL=false&allowPublicKeyRetrieval=true");

        String base = "jdbc:mysql://" + host + ":" + port + "/";
        jdbcUrl     = base + dbName + suffix;
        jdbcUrlNoDb = base + suffix;
    }


    /** Connects WITHOUT a target database — used once at bootstrap to CREATE DATABASE. */
    public static Connection serverOnly() throws SQLException {
        return DriverManager.getConnection(jdbcUrlNoDb, user, password);
    }

    public static String dbName() { return dbName; }
    public static String dbPath() { return jdbcUrl; }

    private ConnectionFactory() {}

    /** Initialise the pool lazily; safe to call from multiple threads. */
    private static synchronized HikariDataSource pool() {
        if (pool == null) {
            // ── Pool size: CPU cores + 1, capped at 8 (sweet spot for desktop ERP) ──
            int cores   = Runtime.getRuntime().availableProcessors();
            int maxPool = Math.min(cores + 1, 8);
            int minIdle = Math.max(2, maxPool / 4);

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(user);
            cfg.setPassword(password);
            cfg.setPoolName("KiaStorePool");

            // ── Pool sizing ──────────────────────────────────────────────
            cfg.setMaximumPoolSize(maxPool);
            cfg.setMinimumIdle(minIdle);

            // ── Timeouts & lifecycle ─────────────────────────────────────
            cfg.setConnectionTimeout(8_000);      // fail fast if DB is dead
            cfg.setValidationTimeout(3_000);      // how long to test a connection
            cfg.setIdleTimeout(60_000);           // release idle conns after 1 min
            cfg.setMaxLifetime(25 * 60_000L);     // recycle before MySQL kills idle conns
            cfg.setKeepaliveTime(2 * 60_000L);    // ping every 2 min so pool stays warm
            cfg.setLeakDetectionThreshold(10_000);// warn if conn held > 10 s (dev aid)

            // ── General ──────────────────────────────────────────────────
            cfg.setAutoCommit(true);              // transactional callers flip this off

            pool = new HikariDataSource(cfg);
        }
        return pool;
    }


    /**
     * Returns a pooled connection. Caller MUST close it (try-with-resources) so the
     * pool can reclaim it. Thread-safe.
     */
    public static Connection get() throws SQLException {
        return pool().getConnection();
    }

    public static Connection borrow() throws SQLException {
        return pool().getConnection();
    }

    /** Closes the pool. Called at app shutdown so the JVM doesn't hang on Hikari threads. */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    // ---------------- helpers ----------------

    private static Properties loadProps() {
        Properties p = new Properties();
        try (InputStream in = ConnectionFactory.class.getResourceAsStream("/application.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            System.err.println("[ConnectionFactory] Could not read application.properties: " + e.getMessage());
        }
        return p;
    }

    private static String sysOrProp(Properties p, String key) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) return v;
        v = p.getProperty(key);
        return v == null ? "" : v;
    }
}
