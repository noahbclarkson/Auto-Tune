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

        // Add future migrations here:
        // if (currentVersion < 8) { ... }

        if (highestVersion > currentVersion) {
            setSchemaVersion(highestVersion);
        }

        plugin.getLogger().info("Database schema initialized (version " + highestVersion + ").");
    }

    private void runMigration(String resourcePath) throws SQLException {
        StorageConfig config = configManager.getConfig().storage();

        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) {
                throw new SQLException("Could not find migration script: " + resourcePath);
            }

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));

            if (config.type() == StorageConfig.StorageType.MYSQL) {
                sql = sql.replace("AUTOINCREMENT", "AUTO_INCREMENT");
                sql = sql.replace("INSERT OR IGNORE", "INSERT IGNORE");
            }

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";", -1)) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read migration script: " + resourcePath, e);
        }
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

    private int getSchemaVersion() {
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
