package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.StorageConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

@SuppressWarnings("PMD")
public class DatabaseManager {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private Jdbi jdbi;
    private ExecutorService asyncExecutor;

    public DatabaseManager(AutoTune plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() throws SQLException {
        StorageConfig config = configManager.getConfig().storage();

        // Create async executor for database operations
        int poolSize = config.type() == StorageConfig.StorageType.SQLITE ? 1 : config.pool().maximumSize();
        asyncExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "AutoTune-DB-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("AutoTune-Pool");

        if (config.type() == StorageConfig.StorageType.SQLITE) {
            File dbFile = new File(plugin.getDataFolder(), "autotune.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
        } else {
            String jdbcUrl = String.format(
                    "jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    config.host(),
                    config.port(),
                    config.database()
            );
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
            hikariConfig.setUsername(config.username());
            hikariConfig.setPassword(config.password());

            StorageConfig.PoolConfig pool = config.pool();
            hikariConfig.setMaximumPoolSize(pool.maximumSize());
            hikariConfig.setMinimumIdle(pool.minimumIdle());
            hikariConfig.setConnectionTimeout(pool.connectionTimeout());
            hikariConfig.setIdleTimeout(pool.idleTimeout());
            hikariConfig.setMaxLifetime(pool.maxLifetime());
        }

        dataSource = new HikariDataSource(hikariConfig);
        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());

        runMigrations();

        plugin.getLogger().info("Database connection established successfully.");
    }

    private void runMigrations() throws SQLException {
        ensureSchemaVersionTable();

        int currentVersion = getSchemaVersion();
        int highestVersion = currentVersion;

        // V1: Initial consolidated schema
        if (currentVersion < 1) {
            plugin.getLogger().info("Applying database migration V1 (Initial Schema)...");
            runMigration("db/V1__Initial_Schema.sql");
            highestVersion = 1;
        }

        // V2: Player onboarding milestone tracking
        if (currentVersion < 2) {
            plugin.getLogger().info("Applying database migration V2 (Player Onboarding)...");
            runMigration("db/V2__Player_Onboarding.sql");
            highestVersion = 2;
        }

        // V3: Player trading streaks
        if (currentVersion < 3) {
            plugin.getLogger().info("Applying database migration V3 (Player Streaks)...");
            runMigration("db/V3__Player_Streaks.sql");
            highestVersion = 3;
        }

        // V4: Admin economy audit log
        // Repair note: older rewrite-2 builds skipped V4 while advancing to V5.
        // V4 is idempotent, so run it for schema version 5 as a one-time repair before V6.
        if (currentVersion < 4 || currentVersion == 5) {
            plugin.getLogger().info("Applying database migration V4 (Admin Audit Log)...");
            runMigration("db/V4__Admin_Audit_Log.sql");
            highestVersion = Math.max(highestVersion, 4);
        }

        // V5: Player type column for archetype-aware loan enforcement
        if (currentVersion < 5) {
            plugin.getLogger().info("Applying database migration V5 (Player Types)...");
            runMigration("db/V5__Player_Types.sql");
            highestVersion = 5;
        }
        if (currentVersion < 6) {
            plugin.getLogger().info("Applying database migration V5b (Watched Auctions)...");
            runMigration("db/V5b__Watched_Auctions.sql");
            highestVersion = 6;
        }

        if (currentVersion < 7) {
            plugin.getLogger().info("Applying database migration V7 (Circuit Events)...");
            runMigration("db/V7__Circuit_Events.sql");
            highestVersion = 7;
        }

        if (currentVersion < 8) {
            plugin.getLogger().info("Applying database migration V8 (Auction Fill Status)...");
            runMigration("db/V8__Auction_Fill_Status.sql");
            highestVersion = 8;
        }

        if (currentVersion < 9) {
            plugin.getLogger().info("Applying database migration V9 (Auction Pending Returns)...");
            runMigration("db/V9__Auction_Pending_Returns.sql");
            highestVersion = 9;
        }

        if (highestVersion > currentVersion) {
            setSchemaVersion(highestVersion);
        }

        plugin.getLogger().info("Database schema initialized (version " + highestVersion + ").");
    }

    private void runMigration(String resourcePath) throws SQLException {
        boolean mysql = configManager.getConfig().storage().type() == StorageConfig.StorageType.MYSQL;

        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) {
                throw new SQLException("Could not find migration script: " + resourcePath);
            }

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String statement : splitSqlStatements(sql)) {
                    stmt.execute(mysql ? translateForMysql(statement) : statement);
                }
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read migration script: " + resourcePath, e);
        }
    }

    private static String translateForMysql(String statement) {
        return statement
                .replace("AUTOINCREMENT", "AUTO_INCREMENT")
                .replace("INSERT OR IGNORE", "INSERT IGNORE");
    }

    /**
     * Splits a SQL script into individual executable statements. Strips {@code --}
     * line comments (whole-line and inline) and treats semicolons inside single-quoted
     * string literals as data rather than statement terminators. Migrations do not use
     * block comments or trigger bodies, so those are intentionally not handled.
     */
    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int length = sql.length();

        for (int i = 0; i < length; i++) {
            char c = sql.charAt(i);

            if (inString) {
                current.append(c);
                if (c == '\'') {
                    if (i + 1 < length && sql.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                }
            } else if (c == '\'') {
                inString = true;
                current.append(c);
            } else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                while (i < length && sql.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
            } else if (c == ';') {
                addStatement(statements, current);
            } else {
                current.append(c);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
        current.setLength(0);
    }

    private void ensureSchemaVersionTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS at_schema_version (
                        version INTEGER NOT NULL
                    )""");
        }
    }

    public int getSchemaVersion() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COALESCE(MAX(version), 0) FROM at_schema_version")
                        .mapTo(Integer.class)
                        .one());
    }

    private void setSchemaVersion(int version) {
        jdbi.useHandle(handle -> {
            handle.createUpdate("DELETE FROM at_schema_version").execute();
            handle.createUpdate("INSERT INTO at_schema_version (version) VALUES (:version)")
                    .bind("version", version)
                    .execute();
        });
    }

    public void shutdown() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public boolean isSqlite() {
        return configManager.getConfig().storage().type() == StorageConfig.StorageType.SQLITE;
    }

    @NotNull
    public Jdbi getJdbi() {
        return jdbi;
    }

    @NotNull
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    @NotNull
    public ExecutorService getExecutor() {
        return asyncExecutor;
    }

    /**
     * Execute a database operation asynchronously.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Async database operation failed", ex);
                    return null;
                });
    }

    /**
     * Execute a database operation asynchronously (no return value).
     */
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, asyncExecutor)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Async database operation failed", ex);
                    return null;
                });
    }

    /**
     * Run a task on the main server thread.
     */
    public void runOnMain(Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
    }
}
