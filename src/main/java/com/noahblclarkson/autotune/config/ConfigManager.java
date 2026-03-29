package com.noahblclarkson.autotune.config;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final AutoTune plugin;
    private final MiniMessage miniMessage;

    private AutoTuneConfig config;
    private FileConfiguration messagesConfig;
    private String messagePrefix;
    private volatile boolean marketFrozen;

    public ConfigManager(AutoTune plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void load() throws IOException {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("shops.yml");

        FileConfiguration cfg = plugin.getConfig();
        this.marketFrozen = cfg.getBoolean("market-frozen", false);
        this.config = parseConfig(cfg);

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        this.messagePrefix = messagesConfig.getString("prefix", "<gray>[AutoTune]</gray> ");
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private AutoTuneConfig parseConfig(FileConfiguration cfg) {
        return new AutoTuneConfig(
                parseStorageConfig(cfg.getConfigurationSection("storage")),
                parseWebConfig(cfg.getConfigurationSection("web")),
                parseEconomyConfig(cfg.getConfigurationSection("economy")),
                parseLoanConfig(cfg.getConfigurationSection("loans")),
                parseGuiConfig(cfg.getConfigurationSection("gui")),
                parsePriceReporterConfig(cfg.getConfigurationSection("price-reporter")),
                parseAutosellConfig(cfg.getConfigurationSection("autosell")),
                parseDebugConfig(cfg.getConfigurationSection("debug")),
                parseEnchantmentConfig(cfg.getConfigurationSection("enchantment")),
                parseCleanupConfig(cfg.getConfigurationSection("cleanup")),
                parseTaxConfig(cfg.getConfigurationSection("tax")),
                parseScoreboardConfig(cfg.getConfigurationSection("scoreboard")),
                parseExchangeRateConfig(cfg.getConfigurationSection("exchange-rate")),
                parseAuctionConfig(cfg.getConfigurationSection("auction")),
                parseMarketEventConfig(cfg.getConfigurationSection("market-events")),
                this.marketFrozen
        );
    }

    private StorageConfig parseStorageConfig(ConfigurationSection section) {
        if (section == null) {
            return new StorageConfig(
                    StorageConfig.StorageType.SQLITE,
                    "localhost", 3306, "autotune", "root", "",
                    StorageConfig.PoolConfig.defaults()
            );
        }

        StorageConfig.StorageType type;
        try {
            type = StorageConfig.StorageType.valueOf(
                    section.getString("type", "SQLITE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = StorageConfig.StorageType.SQLITE;
        }

        ConfigurationSection poolSection = section.getConfigurationSection("pool");
        StorageConfig.PoolConfig pool = poolSection != null
                ? new StorageConfig.PoolConfig(
                poolSection.getInt("maximum-size", 10),
                poolSection.getInt("minimum-idle", 2),
                poolSection.getLong("connection-timeout", 30000),
                poolSection.getLong("idle-timeout", 600000),
                poolSection.getLong("max-lifetime", 1800000)
        )
                : StorageConfig.PoolConfig.defaults();

        return new StorageConfig(
                type,
                section.getString("host", "localhost"),
                section.getInt("port", 3306),
                section.getString("database", "autotune"),
                section.getString("username", "root"),
                section.getString("password", ""),
                pool
        );
    }

    private WebConfig parseWebConfig(ConfigurationSection section) {
        if (section == null) {
            return WebConfig.defaults();
        }
        return new WebConfig(
                section.getBoolean("enabled", true),
                section.getInt("port", 8989),
                section.getString("host", "0.0.0.0"),
                section.getBoolean("websocket-enabled", true)
        );
    }

    private EconomyConfig parseEconomyConfig(ConfigurationSection section) {
        if (section == null) {
            return EconomyConfig.defaults();
        }

        ConfigurationSection spreadSection = section.getConfigurationSection("spread");
        SpreadConfig spreadConfig = spreadSection != null
                ? new SpreadConfig(
                spreadSection.getDouble("base-spread", 0.20),
                spreadSection.getDouble("volume-impact", 0.8),
                spreadSection.getDouble("player-impact", 0.6),
                spreadSection.getDouble("liquidity-coeff", 0.01),
                spreadSection.getInt("liquidity-full-effect-traders", 10)
        )
                : SpreadConfig.defaults();

        ConfigurationSection playerScalingSection = section.getConfigurationSection("player-scaling");
        PlayerScalingConfig playerScalingConfig = playerScalingSection != null
                ? new PlayerScalingConfig(
                playerScalingSection.getInt("full-effect-players", 10)
        )
                : PlayerScalingConfig.defaults();

        return new EconomyConfig(
                section.getString("currency-symbol", "$"),
                section.getLong("update-interval", 6000),
                section.getDouble("max-price-change-percent", 1.5),
                section.getInt("trade-window-days", 7),
                section.getBoolean("require-first-sell", true),
                section.getDouble("slippage-coeff", 0.01),
                section.getDouble("sell-pressure-multiplier", 1.0),
                section.getDouble("sector-correlation", 0.05),
                section.getDouble("player-rate-limit-multiplier", 3.0),
                section.getDouble("trend-dampening", 0.05),
                section.getDouble("trend-streak-threshold-percent", 0.1),
                section.getDouble("trend-dampening-floor", 0.25),
                section.getBoolean("adaptive-window", true),
                section.getInt("min-window-days", 2),
                section.getInt("max-window-days", 7),
                section.getInt("max-sector-correlation-group-size", 20),
                spreadConfig,
                playerScalingConfig
        );
    }

    private LoanConfig parseLoanConfig(ConfigurationSection section) {
        if (section == null) {
            return LoanConfig.defaults();
        }
        return new LoanConfig(
                section.getBoolean("enabled", true),
                section.getDouble("base-interest-rate", 0.05),
                section.getBoolean("credit-score-modifier", true),
                section.getDouble("max-loan-multiplier", 2.0),
                section.getInt("min-credit-score", 200),
                section.getInt("default-duration-days", 7),
                section.getInt("min-term-days", 3),
                section.getInt("max-term-days", 30),
                section.getDouble("term-premium-per-day", 0.002),
                section.getInt("compound-interval-hours", 24),
                section.getInt("overdue-check-interval-hours", 1),
                section.getInt("warning-before-due-hours", 24),
                section.getDouble("early-repayment-bonus-multiplier", 1.5),
                section.getDouble("inflation-rate-impact", 0.5),
                section.getInt("default-penalty", 50),
                section.getDouble("debt-gdp-tier1-ratio", 3.0),
                section.getDouble("debt-gdp-tier2-ratio", 5.0),
                section.getDouble("debt-gdp-tier3-ratio", 10.0),
                section.getDouble("tier1-interest-cap", 0.5),
                section.getDouble("tier2-interest-cap", 0.25)
        );
    }

    private GuiConfig parseGuiConfig(ConfigurationSection section) {
        if (section == null) {
            return GuiConfig.defaults();
        }

        ConfigurationSection titlesSection = section.getConfigurationSection("titles");
        TitlesConfig titles = titlesSection != null
                ? new TitlesConfig(
                titlesSection.getString("shop", "Auto-Tune Shop"),
                titlesSection.getString("sell", "Sell Items"),
                titlesSection.getString("autosell", "Autosell"),
                titlesSection.getString("trends", "Market Trends"),
                titlesSection.getString("market-history", "Price History"),
                titlesSection.getString("transaction-history", "Your Transactions"),
                titlesSection.getString("admin-transaction-history", "All Transactions")
        )
                : TitlesConfig.defaults();

        ConfigurationSection colorsSection = section.getConfigurationSection("colors");
        ColorsConfig colors = colorsSection != null
                ? new ColorsConfig(
                colorsSection.getString("buy-price", "<green>"),
                colorsSection.getString("sell-price", "<gold>"),
                colorsSection.getString("spread", "<aqua>"),
                colorsSection.getString("section-name", "<gold>"),
                colorsSection.getString("item-name", "<white>"),
                colorsSection.getString("trend-up", "<green>"),
                colorsSection.getString("trend-down", "<red>"),
                colorsSection.getString("trend-stable", "<gray>"),
                colorsSection.getString("positive", "<green>"),
                colorsSection.getString("negative", "<red>"),
                colorsSection.getString("accent", "<aqua>"),
                colorsSection.getString("muted", "<dark_gray>")
        )
                : ColorsConfig.defaults();

        ConfigurationSection materialsSection = section.getConfigurationSection("materials");
        MaterialsConfig materials = materialsSection != null
                ? new MaterialsConfig(
                materialsSection.getString("border", "BLACK_STAINED_GLASS_PANE"),
                materialsSection.getString("buy-button", "LIME_STAINED_GLASS_PANE"),
                materialsSection.getString("sell-button", "ORANGE_STAINED_GLASS_PANE"),
                materialsSection.getString("not-available", "BARRIER"),
                materialsSection.getString("previous-page", "ARROW"),
                materialsSection.getString("next-page", "ARROW"),
                materialsSection.getString("back", "DARK_OAK_DOOR"),
                materialsSection.getString("page-indicator", "PAPER"),
                materialsSection.getString("search", "NAME_TAG"),
                materialsSection.getString("trends", "SPYGLASS"),
                materialsSection.getString("close", "BARRIER"),
                materialsSection.getString("economy-stats", "GOLD_BLOCK"),
                materialsSection.getString("enable-all", "LIME_DYE"),
                materialsSection.getString("disable-all", "RED_DYE"),
                materialsSection.getString("sell-inventory", "HOPPER")
        )
                : MaterialsConfig.defaults();

        List<Integer> buyQuantities = section.getIntegerList("buy-quantities");
        if (buyQuantities.isEmpty()) {
            buyQuantities = List.of(1, 2, 4, 8, 16, 32, 64);
        } else {
            buyQuantities = buyQuantities.stream()
                    .filter(q -> q >= 1 && q <= 64)
                    .distinct()
                    .sorted()
                    .toList();
            if (buyQuantities.isEmpty()) {
                buyQuantities = List.of(1, 2, 4, 8, 16, 32, 64);
            }
        }

        return new GuiConfig(
                section.getInt("items-per-page", 45),
                section.getBoolean("search-enabled", true),
                section.getInt("search-timeout", 600),
                section.getBoolean("show-economy-stats", true),
                section.getBoolean("show-24h-change", true),
                titles,
                colors,
                materials,
                buyQuantities
        );
    }

    private PriceReporterConfig parsePriceReporterConfig(ConfigurationSection section) {
        if (section == null) {
            return PriceReporterConfig.defaults();
        }
        return new PriceReporterConfig(
                section.getBoolean("enabled", true),
                section.getString("api-url", "https://prices.auto-tune.io"),
                section.getString("api-key", "your-server-api-key"),
                section.getString("server-id", "your-server-uuid"),
                section.getLong("report-interval-minutes", 5)
        );
    }

    private AutosellConfig parseAutosellConfig(ConfigurationSection section) {
        if (section == null) {
            return AutosellConfig.defaults();
        }
        return new AutosellConfig(
                section.getDouble("minimum-price", 0.01),
                section.getString("sound-on-pickup", "ENTITY_ITEM_PICKUP"),
                section.getString("sound-on-inventory-sell", "UI_LOOT_YOUR_FILLED_CONTAINER")
        );
    }

    private DebugConfig parseDebugConfig(ConfigurationSection section) {
        if (section == null) {
            return DebugConfig.defaults();
        }
        return new DebugConfig(
                section.getBoolean("enabled", false),
                section.getBoolean("log-prices", false)
        );
    }

    public YamlConfiguration loadShopsConfig() {
        File shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        return YamlConfiguration.loadConfiguration(shopsFile);
    }

    @NotNull
    public AutoTuneConfig getConfig() {
        return config;
    }

    @NotNull
    public Component getMessage(String path, Map<String, String> placeholders) {
        String template = messagesConfig.getString(path, "<red>Missing message: " + path);

        TagResolver.Builder resolvers = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }

        return miniMessage.deserialize(messagePrefix + template, resolvers.build());
    }

    @NotNull
    public Component getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }

    @NotNull
    public Component getMessageRaw(String path, Map<String, String> placeholders) {
        String template = messagesConfig.getString(path, "<red>Missing message: " + path);

        TagResolver.Builder resolvers = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }

        return miniMessage.deserialize(template, resolvers.build());
    }

    @NotNull
    public TextColor resolveColor(String miniMessageColor) {
        if (miniMessageColor == null || miniMessageColor.isEmpty()) {
            return NamedTextColor.WHITE;
        }
        try {
            Component parsed = miniMessage.deserialize(miniMessageColor + ".");
            TextColor color = parsed.color();
            return color != null ? color : NamedTextColor.WHITE;
        } catch (Exception e) {
            return NamedTextColor.WHITE;
        }
    }

    @NotNull
    public Material resolveMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
    }

    public String formatCurrency(double amount) {
        return config.economy().currencySymbol() + String.format("%.2f", amount);
    }

    public String formatCurrency(java.math.BigDecimal amount) {
        return formatCurrency(amount.doubleValue());
    }

    /**
     * Whether the market engine is frozen (prices will not update).
     * Persisted to config.yml so it survives restarts.
     */
    public boolean isMarketFrozen() {
        return marketFrozen;
    }

    /**
     * Freeze or unfreeze the market. Freezing stops price updates;
     * the engine still records trades and updates spreads normally.
     */
    public void setMarketFrozen(boolean frozen) {
        this.marketFrozen = frozen;
        plugin.getConfig().set("market-frozen", frozen);
        plugin.saveConfig();
        // Refresh the config object so getConfig() returns the updated record
        this.config = parseConfig(plugin.getConfig());
    }

    private EnchantmentConfig parseEnchantmentConfig(ConfigurationSection section) {
        if (section == null) {
            return EnchantmentConfig.defaults();
        }

        boolean enabled = section.getBoolean("enabled", true);
        Map<String, List<Double>> multipliers = new HashMap<>();

        // Start with defaults, then overlay any config overrides
        Map<String, List<Double>> defaults = EnchantmentConfig.defaults().enchantmentMultipliers();
        for (Map.Entry<String, List<Double>> entry : defaults.entrySet()) {
            String key = entry.getKey();
            if (section.contains("multipliers." + key)) {
                List<Double> override = section.getDoubleList("multipliers." + key);
                if (!override.isEmpty()) {
                    multipliers.put(key, override);
                }
            } else {
                multipliers.put(key, entry.getValue());
            }
        }

        // Allow new enchantments to be added via config
        if (section.contains("multipliers")) {
            ConfigurationSection multSection = section.getConfigurationSection("multipliers");
            if (multSection != null) {
                for (String enchant : multSection.getKeys(false)) {
                    if (!multipliers.containsKey(enchant)) {
                        List<Double> vals = section.getDoubleList("multipliers." + enchant);
                        if (!vals.isEmpty()) {
                            multipliers.put(enchant, vals);
                        }
                    }
                }
            }
        }

        return new EnchantmentConfig(enabled, multipliers);
    }

    private CleanupConfig parseCleanupConfig(ConfigurationSection section) {
        if (section == null) {
            return CleanupConfig.defaults();
        }

        ConfigurationSection txSection = section.getConfigurationSection("transactions");
        AutoTuneConfig.CleanupConfig.RetentionConfig txConfig = txSection != null
                ? new AutoTuneConfig.CleanupConfig.RetentionConfig(
                txSection.getBoolean("enabled", true),
                txSection.getInt("retention-days", 14))
                : AutoTuneConfig.CleanupConfig.RetentionConfig.defaults(true, 14);

        ConfigurationSection histSection = section.getConfigurationSection("market-history");
        AutoTuneConfig.CleanupConfig.RetentionConfig histConfig = histSection != null
                ? new AutoTuneConfig.CleanupConfig.RetentionConfig(
                histSection.getBoolean("enabled", true),
                histSection.getInt("retention-days", 7))
                : AutoTuneConfig.CleanupConfig.RetentionConfig.defaults(true, 7);

        ConfigurationSection snapSection = section.getConfigurationSection("economy-snapshots");
        AutoTuneConfig.CleanupConfig.RetentionConfig snapConfig = snapSection != null
                ? new AutoTuneConfig.CleanupConfig.RetentionConfig(
                snapSection.getBoolean("enabled", true),
                snapSection.getInt("retention-days", 30))
                : AutoTuneConfig.CleanupConfig.RetentionConfig.defaults(true, 30);

        return new CleanupConfig(txConfig, histConfig, snapConfig,
                section.getInt("cleanup-interval-hours", 24));
    }

    private TaxConfig parseTaxConfig(ConfigurationSection section) {
        if (section == null) {
            return TaxConfig.defaults();
        }
        return new TaxConfig(
                section.getBoolean("enabled", false),
                section.getDouble("buy-tax-percent", 0.0),
                section.getDouble("sell-tax-percent", 0.0),
                section.getDouble("auction-tax-percent", 0.0),
                section.getDouble("loan-interest-tax-percent", 0.0)
        );
    }

    private ScoreboardConfig parseScoreboardConfig(ConfigurationSection section) {
        if (section == null) {
            return ScoreboardConfig.defaults();
        }
        return new ScoreboardConfig(
                section.getBoolean("enabled", false),
                section.getString("title", "Auto-Tune Economy"),
                Math.max(10, section.getInt("update-interval-seconds", 30))
        );
    }

    private ExchangeRateConfig parseExchangeRateConfig(ConfigurationSection section) {
        if (section == null) {
            return ExchangeRateConfig.defaults();
        }
        return new ExchangeRateConfig(
                section.getBoolean("enabled", true),
                Math.max(5, section.getLong("fetch-interval-minutes", 15))
        );
    }

    private AuctionConfig parseAuctionConfig(ConfigurationSection section) {
        if (section == null) {
            return AuctionConfig.defaults();
        }
        return new AuctionConfig(
                Math.max(1, section.getInt("default-duration-hours", 72)),
                Math.max(1, section.getInt("expiration-check-interval-minutes", 15))
        );
    }

    private MarketEventConfig parseMarketEventConfig(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return MarketEventConfig.defaults();
        }

        List<AutoTuneConfig.MarketEventConfigEntry> entries = new ArrayList<>();
        List<?> eventList = section.getList("events");
        if (eventList != null) {
            for (Object item : eventList) {
                if (item instanceof org.bukkit.configuration.ConfigurationSection eventSection) {
                    entries.add(new AutoTuneConfig.MarketEventConfigEntry(
                            eventSection.getString("name", "Unnamed Event"),
                            eventSection.getString("type", "CUSTOM"),
                            eventSection.getStringList("materials"),
                            eventSection.getDouble("multiplier", 2.0),
                            eventSection.getInt("duration-minutes", 60),
                            eventSection.getString("start-message", ""),
                            eventSection.getString("end-message", "")
                    ));
                }
            }
        }

        return new MarketEventConfig(
                section.getBoolean("enabled", true),
                entries,
                Math.max(1, section.getInt("check-interval-minutes", 5))
        );
    }
}
