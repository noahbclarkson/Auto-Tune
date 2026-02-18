package com.github.noahbclarkson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

import javax.sql.DataSource;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteDataSource;

import com.github.noahbclarkson.database.AutoTuneDatabase;
import com.github.noahbclarkson.database.DatabaseInitializer;
import com.github.noahbclarkson.util.AutoTuneLogger;
import com.github.noahbclarkson.util.EconomyUtil;
import com.github.noahbclarkson.util.Format;

import lombok.Getter;

public class AutoTune extends JavaPlugin {

    private AutoTuneConfig autoTuneConfig;

    @Getter
    private static AutoTune instance;

    private AutoTuneLogger log;

    private AutoTuneDatabase database;

    @Override
    public void onEnable() {
        instance = this;

        if (!loadAutoTuneConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        setupLogger();
        EconomyUtil.setupLocalEconomy(Bukkit.getServer());

        try {
            database = new AutoTuneDatabase(initDataSource());
        } catch (SQLException e) {
            log.severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new Metrics(this, 9687);
    }

    @Override
    public void reloadConfig() {
        if (autoTuneConfig == null) {
            getLogger().severe("Config is not initialized; reload aborted.");
            return;
        }

        try {
            autoTuneConfig.load();
        } catch (IOException | InvalidConfigurationException e) {
            log.severe("Failed to reload config: " + e.getMessage());
        }
    }

    @Override
    public void saveConfig() {
        if (autoTuneConfig == null) {
            getLogger().severe("Config is not initialized; save aborted.");
            return;
        }

        try {
            autoTuneConfig.save();
        } catch (IOException e) {
            log.severe("Failed to save config: " + e.getMessage());
        }
    }

    public AutoTuneConfig getAutoTuneConfig() {
        return autoTuneConfig;
    }

    public static AutoTuneLogger getLog() {
        return instance.log;
    }

    public AutoTuneDatabase getDB() {
        return instance.database;
    }

    private DataSource initDataSource() throws SQLException {
        String databaseUrl = autoTuneConfig.getString("database.url");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new SQLException("database.url is missing from config.yml");
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(databaseUrl);

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }

            DatabaseInitializer initializer = new DatabaseInitializer();
            initializer.initializeDatabase(connection);
        }

        return dataSource;
    }

    private boolean loadAutoTuneConfig() {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        autoTuneConfig = new AutoTuneConfig(configFile);

        if (Files.notExists(configFile)) {
            saveResource("config.yml", false);
        }

        try {
            autoTuneConfig.load();
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
            return false;
        }
    }

    private void setupLogger() {
        String configuredLevel = autoTuneConfig.getString("log-level", "INFO");
        Level parsedLevel;

        try {
            parsedLevel = Level.parse(configuredLevel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Invalid log-level in config.yml: " + configuredLevel
                    + ". Falling back to INFO.");
            parsedLevel = Level.INFO;
        }

        log = Format.loadLogger(parsedLevel);
    }

}
