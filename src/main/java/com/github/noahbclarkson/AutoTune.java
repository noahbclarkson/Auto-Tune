package com.github.noahbclarkson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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

    @Getter
    private AutoTuneConfig config;

    @Getter
    private static AutoTune instance;

    private AutoTuneLogger log;

    private AutoTuneDatabase database;

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        setupLogger();
        EconomyUtil.setupLocalEconomy(Bukkit.getServer());
        database = new AutoTuneDatabase(initDataSource());
        new Metrics(this, 9687);
    }

    public void reloadConfig() {
        try {
            config.load();
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AutoTuneLogger getLog() {
        return instance.log;
    }

    public AutoTuneDatabase getDB() {
        return instance.database;
    }

    private DataSource initDataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(config.getString("database.url"));
        try (Connection connection = dataSource.getConnection()) {
            DatabaseInitializer initializer = new DatabaseInitializer();
            testDataSource(dataSource);
            initializer.initializeDatabase(connection);
            return dataSource;
        } catch (SQLException e) {
            log.severe("Failed to initialize database: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return null;
        }
    }

    private void testDataSource(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }
        }
    }

    private void loadConfig() {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        config = new AutoTuneConfig(configFile);

        if (Files.notExists(configFile)) {
            saveResource("config.yml", false);
        }

        try {
            config.load();
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void setupLogger() {
        String logLevel = config.getString("log-level", "INFO");
        log = Format.loadLogger(Level.parse(logLevel));
    }

}
