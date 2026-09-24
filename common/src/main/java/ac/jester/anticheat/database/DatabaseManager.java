package ac.jester.anticheat.database;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {
    private static HikariDataSource dataSource;
    private static final ExecutorService asyncExecutor =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "JesterAC-DB");
                t.setDaemon(true);
                return t;
            });

    private static boolean enabled = false;
    private static long retentionMs;
    private static String connectionSignature = null;
    private static boolean purgeTaskScheduled = false;

    public static void init(ConfigManager config) {
        boolean wasEnabled = enabled;
        enabled = config.getBooleanElse("database.enabled", false);

        logThrottleMs = config.getLongElse("database.log-throttle-ms", 2000L);
        long retentionHours = config.getLongElse("database.violation-retention-hours", 6L);
        retentionMs = retentionHours * 3_600_000L;

        if (!enabled) {
            if (wasEnabled && dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            dataSource = null;
            connectionSignature = null;
            return;
        }

        String type = config.getStringElse("database.type", "sqlite");
        String host = config.getStringElse("database.host", "localhost");
        int port = config.getIntElse("database.port", 3306);
        String db = config.getStringElse("database.database", "jester");
        String user = config.getStringElse("database.username", "root");
        String pass = config.getStringElse("database.password", "");
        int poolSize = config.getIntElse("database.pool-size", 5);

        String newSignature = type + "|" + host + "|" + port + "|" + db + "|" + user + "|" + pass + "|" + poolSize;
        if (dataSource != null && !dataSource.isClosed() && newSignature.equals(connectionSignature)) {
            maybeSchedulePurgeTask();
            return;
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setMaximumPoolSize(poolSize);
        hikari.setConnectionTimeout(10_000);
        hikari.setIdleTimeout(600_000);
        hikari.setMaxLifetime(1_800_000);
        hikari.setPoolName("JesterAC-Pool");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");

        if (type.equalsIgnoreCase("mysql")) {
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8");
            hikari.setUsername(user);
            hikari.setPassword(pass);
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            String path = "plugins/JesterAntiCheat/jester.db";
            hikari.setJdbcUrl("jdbc:sqlite:" + path);
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
        }

        HikariDataSource old = dataSource;
        try {
            dataSource = new HikariDataSource(hikari);
            createTables();
            connectionSignature = newSignature;
            LogUtil.info((old == null ? "Database connected (" : "Database reconnected (") + type + ")");
        } catch (Exception e) {
            LogUtil.error("Failed to connect to database. Disabling DB logging.", e);
            enabled = false;
            dataSource = old;
            return;
        }
        if (old != null && !old.isClosed()) {
            old.close();
        }

        maybeSchedulePurgeTask();
    }

    private static void maybeSchedulePurgeTask() {
        if (purgeTaskScheduled || !enabled) return;
        try {
            GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(
                    GrimAPI.INSTANCE.getGrimPlugin(), DatabaseManager::purgeOldViolations, 0L, 1L, TimeUnit.HOURS);
            purgeTaskScheduled = true;
        } catch (Exception e) {
        }
    }

    private static void purgeOldViolations() {
        if (!enabled || dataSource == null || retentionMs <= 0) return;
        long cutoff = System.currentTimeMillis() - retentionMs;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM jester_violations WHERE flagged_at < ?")) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LogUtil.info("Purged " + deleted + " violation log row(s) older than "
                        + (retentionMs / 3_600_000L) + "h");
            }
        } catch (SQLException e) {
            LogUtil.error("DB error purging old violations", e);
        }
    }

    private static void createTables() throws SQLException {
        boolean mysql = isMySQL();
        String ai = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS jester_violations (" +
                "    id         INTEGER PRIMARY KEY " + ai + "," +
                "    uuid       VARCHAR(36) NOT NULL," +
                "    username   VARCHAR(16) NOT NULL," +
                "    check_name VARCHAR(64) NOT NULL," +
                "    vl         DOUBLE NOT NULL," +
                "    verbose    TEXT," +
                "    ping       INTEGER NOT NULL DEFAULT 0," +
                "    tps        DOUBLE NOT NULL DEFAULT 20," +
                "    flagged_at BIGINT NOT NULL" +
                ")");
            addColumnQuietly(conn, "ALTER TABLE jester_violations ADD COLUMN ping INTEGER NOT NULL DEFAULT 0");
            addColumnQuietly(conn, "ALTER TABLE jester_violations ADD COLUMN tps DOUBLE NOT NULL DEFAULT 20");
            createIndexQuietly(conn, "CREATE INDEX idx_viol_uuid ON jester_violations(uuid)");
            createIndexQuietly(conn, "CREATE INDEX idx_viol_check ON jester_violations(check_name)");
            createIndexQuietly(conn, "CREATE INDEX idx_viol_time ON jester_violations(flagged_at)");
            createIndexQuietly(conn, "CREATE INDEX idx_pun_uuid ON jester_punishments(uuid)");
            createIndexQuietly(conn, "CREATE INDEX idx_pun_time ON jester_punishments(punished_at)");

            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS jester_punishments (" +
                "    id          INTEGER PRIMARY KEY " + ai + "," +
                "    uuid        VARCHAR(36) NOT NULL," +
                "    username    VARCHAR(16) NOT NULL," +
                "    check_name  VARCHAR(64) NOT NULL," +
                "    command     TEXT NOT NULL," +
                "    punished_at BIGINT NOT NULL" +
                ")");

            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS jester_profiles (" +
                "    uuid           VARCHAR(36) PRIMARY KEY," +
                "    username       VARCHAR(16) NOT NULL," +
                "    client_version VARCHAR(16)," +
                "    client_brand   VARCHAR(64)," +
                "    last_seen      BIGINT NOT NULL," +
                "    total_vl       DOUBLE NOT NULL DEFAULT 0" +
                ")");
        }
    }

    private static void addColumnQuietly(Connection conn, String sql) {
        try {
            conn.createStatement().executeUpdate(sql);
        } catch (SQLException ignored) {
        }
    }

    private static void createIndexQuietly(Connection conn, String sql) {
        try {
            if (!isMySQL()) sql = sql.replace("CREATE INDEX", "CREATE INDEX IF NOT EXISTS");
            conn.createStatement().executeUpdate(sql);
        } catch (SQLException ignored) {
        }
    }

    private static boolean isMySQL() {
        if (dataSource == null) return false;
        return dataSource.getJdbcUrl().startsWith("jdbc:mysql");
    }

    private static long logThrottleMs = 2000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> lastLogged =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void onPlayerQuit(UUID uuid) {
        if (uuid == null) return;
        String prefix = uuid + "|";
        lastLogged.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void logViolation(UUID uuid, String username, String checkName, double vl, String verbose,
                                    int ping, double tps) {
        if (!enabled || dataSource == null) return;

        String throttleKey = uuid + "|" + checkName;
        long now = System.currentTimeMillis();
        Long prev = lastLogged.get(throttleKey);
        if (prev != null && now - prev < logThrottleMs) return;
        lastLogged.put(throttleKey, now);
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO jester_violations (uuid, username, check_name, vl, verbose, ping, tps, flagged_at) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, checkName);
                ps.setDouble(4, vl);
                ps.setString(5, verbose);
                ps.setInt(6, ping);
                ps.setDouble(7, tps);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                LogUtil.error("DB error logging violation", e);
            }
        }, asyncExecutor);
    }

    public static void logPunishment(UUID uuid, String username, String checkName, String command) {
        if (!enabled || dataSource == null) return;
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO jester_punishments (uuid, username, check_name, command, punished_at) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, checkName);
                ps.setString(4, command);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                LogUtil.error("DB error logging punishment", e);
            }
        }, asyncExecutor);
    }

    public static void updateProfile(UUID uuid, String username, String clientVersion, String clientBrand) {
        if (!enabled || dataSource == null) return;
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = isMySQL()
                        ? "INSERT INTO jester_profiles (uuid, username, client_version, client_brand, last_seen) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE username=?, client_version=?, client_brand=?, last_seen=?"
                        : "INSERT OR REPLACE INTO jester_profiles (uuid, username, client_version, client_brand, last_seen) VALUES (?,?,?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    long now = System.currentTimeMillis();
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setString(3, clientVersion);
                    ps.setString(4, clientBrand);
                    ps.setLong(5, now);
                    if (isMySQL()) {
                        ps.setString(6, username);
                        ps.setString(7, clientVersion);
                        ps.setString(8, clientBrand);
                        ps.setLong(9, now);
                    }
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                LogUtil.error("DB error updating profile", e);
            }
        }, asyncExecutor);
    }

    public static final class ViolationEntry {
        public final String username;
        public final String checkName;
        public final double vl;
        public final String verbose;
        public final int ping;
        public final double tps;
        public final long flaggedAt;

        public ViolationEntry(String username, String checkName, double vl, String verbose,
                              int ping, double tps, long flaggedAt) {
            this.username = username;
            this.checkName = checkName;
            this.vl = vl;
            this.verbose = verbose;
            this.ping = ping;
            this.tps = tps;
            this.flaggedAt = flaggedAt;
        }
    }

    public static CompletableFuture<List<ViolationEntry>> fetchViolations(String username, int limit, int offset) {
        if (!enabled || dataSource == null) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> {
            List<ViolationEntry> entries = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT username, check_name, vl, verbose, ping, tps, flagged_at FROM jester_violations " +
                         "WHERE username = ? ORDER BY flagged_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new ViolationEntry(
                                rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getString(4),
                                rs.getInt(5), rs.getDouble(6), rs.getLong(7)));
                    }
                }
            } catch (SQLException e) {
                LogUtil.error("DB error fetching violations", e);
            }
            return entries;
        }, asyncExecutor);
    }

    public static void shutdown() {
        asyncExecutor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static boolean isEnabled() { return enabled; }
}
