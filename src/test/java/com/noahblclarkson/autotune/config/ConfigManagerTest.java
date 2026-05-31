package com.noahblclarkson.autotune.config;

import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigManagerTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("Explicit disabled feature sections stay disabled after parsing")
    void disabledFeatureTogglesStayDisabled() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("market-events.enabled", false);
        yaml.set("news.enabled", false);
        yaml.set("price-milestone.enabled", false);
        yaml.set("onboarding.enabled", false);

        AutoTuneConfig config = new ConfigManager(mock(AutoTune.class)).parseConfig(yaml);

        assertFalse(config.marketEvents().enabled(), "market-events.enabled=false should remain disabled");
        assertFalse(config.news().enabled(), "news.enabled=false should remain disabled");
        assertFalse(config.priceMilestones().enabled(), "price-milestone.enabled=false should remain disabled");
        assertFalse(config.onboarding().enabled(), "onboarding.enabled=false should remain disabled");
    }

    @Test
    @DisplayName("Generated price-reporter server id is persisted under the read path")
    void generatedPriceReporterServerIdUsesKebabCasePath() {
        AutoTune plugin = mock(AutoTune.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigManagerTest"));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("price-reporter.server-id", "your-server-uuid");

        new ConfigManager(plugin).parseConfig(yaml);

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tempDir.resolve("config.yml").toFile());
        assertNotNull(saved.getString("price-reporter.server-id"));
        assertNotEquals("your-server-uuid", saved.getString("price-reporter.server-id"));
        assertFalse(saved.contains("priceReporter.server-id"),
                "legacy camelCase priceReporter.server-id path should not be written");
    }

    @SuppressWarnings("PMD.UseProperClassLoader")
    @Test
    @DisplayName("Shipped config.yml parses and validates")
    void shippedConfigParsesAndValidates() {
        AutoTune plugin = mock(AutoTune.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigManagerTest"));

        YamlConfiguration yaml;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(is, "config.yml should be packaged as a test resource");
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new AssertionError("Failed to read config.yml", e);
        }

        AutoTuneConfig config = new ConfigManager(plugin).parseConfig(yaml);

        assertTrue(ConfigValidator.validate(config).isEmpty(), "shipped config.yml should validate cleanly");
        assertFalse(config.priceReporter().enabled(), "price reporter should be opt-in by default");
        assertTrue(config.enchantment().enabled(), "enchantment config should be visible and enabled by default");
    }
}
