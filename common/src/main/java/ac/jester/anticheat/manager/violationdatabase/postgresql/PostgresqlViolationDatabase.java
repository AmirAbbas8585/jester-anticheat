package ac.jester.anticheat.manager.violationdatabase.postgresql;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.manager.violationdatabase.*;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class PostgresqlViolationDatabase implements ViolationDatabase {
    private static String quoteVerboseColumn() {
        return "\"" + DatabaseConstants.VIOLATIONS_VERBOSE_COLUMN + "\"";
    }

    private HikariDataSource dataSource;
    private final DatabaseDialect dialect;

    public PostgresqlViolationDatabase(String url, String database, String username, String password) {
        this.dialect = new PostgresqlDialect();
        setupDataSource(url, database, username, password);
    }

    private void setupDataSource(String url, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + url + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setAutoCommit(true);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public void connect() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String pkSyntax = dialect.getAutoIncrementPrimaryKeySyntax();
            String uuidType = dialect.getUuidColumnType();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.SERVERS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.SERVERS_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.SERVERS_TABLE + "_name ON " + DatabaseConstants.SERVERS_TABLE + "(" + DatabaseConstants.SERVERS_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.CHECK_NAMES_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.CHECK_NAMES_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.CHECK_NAMES_TABLE + "_string ON " + DatabaseConstants.CHECK_NAMES_TABLE + "(" + DatabaseConstants.CHECK_NAMES_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.GRIM_VERSIONS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.GRIM_VERSIONS_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.GRIM_VERSIONS_TABLE + "_string ON " + DatabaseConstants.GRIM_VERSIONS_TABLE + "(" + DatabaseConstants.GRIM_VERSIONS_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.CLIENT_BRANDS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.CLIENT_BRANDS_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.CLIENT_BRANDS_TABLE + "_string ON " + DatabaseConstants.CLIENT_BRANDS_TABLE + "(" + DatabaseConstants.CLIENT_BRANDS_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.CLIENT_VERSIONS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.CLIENT_VERSIONS_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.CLIENT_VERSIONS_TABLE + "_string ON " + DatabaseConstants.CLIENT_VERSIONS_TABLE + "(" + DatabaseConstants.CLIENT_VERSIONS_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.SERVER_VERSIONS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.SERVER_VERSIONS_STRING_COLUMN + " VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.SERVER_VERSIONS_TABLE + "_string ON " + DatabaseConstants.SERVER_VERSIONS_TABLE + "(" + DatabaseConstants.SERVER_VERSIONS_STRING_COLUMN + ");"
            ).execute();

            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + DatabaseConstants.VIOLATIONS_TABLE + "(" +
                            "id " + pkSyntax + ", " +
                            DatabaseConstants.VIOLATIONS_SERVER_ID_COLUMN + " BIGINT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_UUID_COLUMN + " " + uuidType + " NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_CHECK_NAME_ID_COLUMN + " BIGINT NOT NULL, " +
                            quoteVerboseColumn() + " TEXT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_VL_COLUMN + " INT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_CREATED_AT_COLUMN + " BIGINT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_GRIM_VERSION_ID_COLUMN + " BIGINT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_CLIENT_BRAND_ID_COLUMN + " BIGINT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_CLIENT_VERSION_ID_COLUMN + " BIGINT NOT NULL, " +
                            DatabaseConstants.VIOLATIONS_SERVER_VERSION_ID_COLUMN + " BIGINT NOT NULL, " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_SERVER_ID_COLUMN + ") REFERENCES " + DatabaseConstants.SERVERS_TABLE + "(id), " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_CHECK_NAME_ID_COLUMN + ") REFERENCES " + DatabaseConstants.CHECK_NAMES_TABLE + "(id), " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_GRIM_VERSION_ID_COLUMN + ") REFERENCES " + DatabaseConstants.GRIM_VERSIONS_TABLE + "(id), " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_CLIENT_BRAND_ID_COLUMN + ") REFERENCES " + DatabaseConstants.CLIENT_BRANDS_TABLE + "(id), " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_CLIENT_VERSION_ID_COLUMN + ") REFERENCES " + DatabaseConstants.CLIENT_VERSIONS_TABLE + "(id), " +
                            "FOREIGN KEY (" + DatabaseConstants.VIOLATIONS_SERVER_VERSION_ID_COLUMN + ") REFERENCES " + DatabaseConstants.SERVER_VERSIONS_TABLE + "(id)" +
                            ")"
            ).execute();

            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_uuid ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_UUID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_created_at ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_CREATED_AT_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_server_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_SERVER_ID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_check_name_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_CHECK_NAME_ID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_jester_version_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_GRIM_VERSION_ID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_client_brand_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_CLIENT_BRAND_ID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_client_version_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_CLIENT_VERSION_ID_COLUMN + ");"
            ).execute();
            connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_" + DatabaseConstants.VIOLATIONS_TABLE + "_server_version_id ON " + DatabaseConstants.VIOLATIONS_TABLE + "(" + DatabaseConstants.VIOLATIONS_SERVER_VERSION_ID_COLUMN + ");"
            ).execute();
        } catch (SQLException ex) {
            LogUtil.error("Failed to generate violations database:", ex);
            throw ex;
        }
    }

    @Override
    public synchronized void logAlert(GrimPlayer player, String JesterVersion, String verbose, String checkName, int vls) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insertAlert = connection.prepareStatement(
                     "INSERT INTO " + DatabaseConstants.VIOLATIONS_TABLE + " (" +
                             DatabaseConstants.VIOLATIONS_SERVER_ID_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_UUID_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_CHECK_NAME_ID_COLUMN + ", " +
                             quoteVerboseColumn() + ", " +
                             DatabaseConstants.VIOLATIONS_VL_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_CREATED_AT_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_GRIM_VERSION_ID_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_CLIENT_BRAND_ID_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_CLIENT_VERSION_ID_COLUMN + ", " +
                             DatabaseConstants.VIOLATIONS_SERVER_VERSION_ID_COLUMN +
                             ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )
        ) {
            String serverName = GrimAPI.INSTANCE.getConfigManager().getConfig().getStringElse("history.server-name", "Prison");
            long serverId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.SERVERS_TABLE, DatabaseConstants.SERVERS_STRING_COLUMN, serverName);
            long checkNameId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.CHECK_NAMES_TABLE, DatabaseConstants.CHECK_NAMES_STRING_COLUMN, checkName);
            long grimVersionId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.GRIM_VERSIONS_TABLE, DatabaseConstants.GRIM_VERSIONS_STRING_COLUMN, JesterVersion);
            long clientBrandId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.CLIENT_BRANDS_TABLE, DatabaseConstants.CLIENT_BRANDS_STRING_COLUMN, player.getBrand());
            long clientVersionId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.CLIENT_VERSIONS_TABLE, DatabaseConstants.CLIENT_VERSIONS_STRING_COLUMN, player.getClientVersion().getReleaseName());
            long serverVersionId = DatabaseUtils.getOrCreateId(connection, dialect, DatabaseConstants.SERVER_VERSIONS_TABLE, DatabaseConstants.SERVER_VERSIONS_STRING_COLUMN, PacketEvents.getAPI().getServerManager().getVersion().toString());

            insertAlert.setLong(1, serverId);
            insertAlert.setObject(2, player.getUniqueId());
            insertAlert.setLong(3, checkNameId);
            insertAlert.setString(4, verbose);
            insertAlert.setInt(5, vls);
            insertAlert.setLong(6, System.currentTimeMillis());
            insertAlert.setLong(7, grimVersionId);
            insertAlert.setLong(8, clientBrandId);
            insertAlert.setLong(9, clientVersionId);
            insertAlert.setLong(10, serverVersionId);

            insertAlert.execute();
        } catch (SQLException ex) {
            LogUtil.error("Failed to log alert", ex);
        }
    }

    @Override
    public synchronized int getLogCount(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement countLogs = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + DatabaseConstants.VIOLATIONS_TABLE + " WHERE " + DatabaseConstants.VIOLATIONS_UUID_COLUMN + " = ?"
             )
        ) {
            countLogs.setObject(1, uuid);
            ResultSet result = countLogs.executeQuery();
            if (result.next()) {
                return result.getInt(1);
            }
        } catch (SQLException ex) {
            LogUtil.error("Failed to count logs", ex);
        }
        return 0;
    }

    @Override
    public synchronized List<Violation> getViolations(UUID uuid, int page, int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement fetchLogs = connection.prepareStatement(
                     "SELECT " +
                             "v." + DatabaseConstants.VIOLATIONS_ID_COLUMN + ", " +
                             "s." + DatabaseConstants.SERVERS_STRING_COLUMN + ", " +
                             "v." + DatabaseConstants.VIOLATIONS_UUID_COLUMN + ", " +
                             "cn." + DatabaseConstants.CHECK_NAMES_STRING_COLUMN + ", " +
                             "v." + quoteVerboseColumn() + ", " +
                             "v." + DatabaseConstants.VIOLATIONS_VL_COLUMN + ", " +
                             "v." + DatabaseConstants.VIOLATIONS_CREATED_AT_COLUMN + ", " +
                             "gv." + DatabaseConstants.GRIM_VERSIONS_STRING_COLUMN + ", " +
                             "cb." + DatabaseConstants.CLIENT_BRANDS_STRING_COLUMN + ", " +
                             "clv." + DatabaseConstants.CLIENT_VERSIONS_STRING_COLUMN + ", " +
                             "srv." + DatabaseConstants.SERVER_VERSIONS_STRING_COLUMN + " " +
                             "FROM " + DatabaseConstants.VIOLATIONS_TABLE + " v " +
                             "JOIN " + DatabaseConstants.SERVERS_TABLE + " s ON v." + DatabaseConstants.VIOLATIONS_SERVER_ID_COLUMN + " = s.id " +
                             "JOIN " + DatabaseConstants.CHECK_NAMES_TABLE + " cn ON v." + DatabaseConstants.VIOLATIONS_CHECK_NAME_ID_COLUMN + " = cn.id " +
                             "JOIN " + DatabaseConstants.GRIM_VERSIONS_TABLE + " gv ON v." + DatabaseConstants.VIOLATIONS_GRIM_VERSION_ID_COLUMN + " = gv.id " +
                             "JOIN " + DatabaseConstants.CLIENT_BRANDS_TABLE + " cb ON v." + DatabaseConstants.VIOLATIONS_CLIENT_BRAND_ID_COLUMN + " = cb.id " +
                             "JOIN " + DatabaseConstants.CLIENT_VERSIONS_TABLE + " clv ON v." + DatabaseConstants.VIOLATIONS_CLIENT_VERSION_ID_COLUMN + " = clv.id " +
                             "JOIN " + DatabaseConstants.SERVER_VERSIONS_TABLE + " srv ON v." + DatabaseConstants.VIOLATIONS_SERVER_VERSION_ID_COLUMN + " = srv.id " +
                             "WHERE v." + DatabaseConstants.VIOLATIONS_UUID_COLUMN + " = ? ORDER BY v." + DatabaseConstants.VIOLATIONS_CREATED_AT_COLUMN + " DESC LIMIT ? OFFSET ?"
             )
        ) {
            fetchLogs.setObject(1, uuid);
            fetchLogs.setInt(2, limit);
            fetchLogs.setInt(3, Math.max(0, (page - 1)) * limit);
            return Violation.fromResultSet(fetchLogs.executeQuery());
        } catch (SQLException ex) {
            LogUtil.error("Failed to fetch logs", ex);
            return null;
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean sameConfig(String host, String db, String user, String pwd) {
        String wantUrl = "jdbc:postgresql://" + host + "/" + db;
        return wantUrl.equalsIgnoreCase(dataSource.getJdbcUrl())
                && user.equals(dataSource.getUsername())
                && pwd.equals(dataSource.getPassword());
    }
}
