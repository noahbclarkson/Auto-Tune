package com.github.noahbclarkson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoTuneConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReadsYamlValues() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, "log-level: \"WARNING\"\ndatabase:\n  url: \"jdbc:sqlite:test.db\"\n");

        AutoTuneConfig config = new AutoTuneConfig(configPath);
        config.load();

        assertEquals("WARNING", config.getString("log-level"));
        assertEquals("jdbc:sqlite:test.db", config.getString("database.url"));
    }

    @Test
    void saveWritesUpdatedValues() throws Exception {
        Path configPath = tempDir.resolve("nested").resolve("config.yml");

        AutoTuneConfig config = new AutoTuneConfig(configPath);
        config.set("log-level", "FINE");
        config.set("database.url", "jdbc:sqlite:plugins/Auto-Tune/data.db");
        config.save();

        AutoTuneConfig reloadedConfig = new AutoTuneConfig(configPath);
        reloadedConfig.load();

        assertEquals("FINE", reloadedConfig.getString("log-level"));
        assertEquals("jdbc:sqlite:plugins/Auto-Tune/data.db", reloadedConfig.getString("database.url"));
    }

    @Test
    void loadThrowsIfConfigMissing() {
        Path missingFile = tempDir.resolve("does-not-exist.yml");
        AutoTuneConfig config = new AutoTuneConfig(missingFile);

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, config::load);
    }

    @Test
    void loadSupportsValidYaml() throws Exception {
        Path configPath = tempDir.resolve("valid.yml");
        Files.writeString(configPath, "shops:\n  apple:\n    price: 2.0\n");

        AutoTuneConfig config = new AutoTuneConfig(configPath);
        config.load();

        assertEquals(2.0, config.getDouble("shops.apple.price"));
    }

    @Test
    void loadThrowsForInvalidYaml() throws Exception {
        Path configPath = tempDir.resolve("invalid.yml");
        Files.writeString(configPath, "shops:\n  apple: [1,2\n");

        AutoTuneConfig config = new AutoTuneConfig(configPath);
        org.junit.jupiter.api.Assertions.assertThrows(InvalidConfigurationException.class, config::load);
    }

}
