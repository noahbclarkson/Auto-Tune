package com.noahblclarkson.autotune;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.noahblclarkson.autotune.command.CommandManager;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.config.ConfigValidator;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.listener.AutosellListener;
import com.noahblclarkson.autotune.listener.PlayerListener;
import com.noahblclarkson.autotune.listener.SellGuiListener;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.service.EconomicNewsService;
import com.noahblclarkson.autotune.manager.PriceAlertManager;
import com.noahblclarkson.autotune.manager.ScoreboardManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.task.TaskScheduler;
import com.noahblclarkson.autotune.web.WebServer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

public class AutoTune extends JavaPlugin {

    private static AutoTune instance;

    private Injector injector;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private MarketEngine marketEngine;
    private LoanManager loanManager;
    private AutosellManager autosellManager;
    private EconomyMetricsManager economyMetricsManager;
    private PriceAlertManager priceAlertManager;
    private ScoreboardManager scoreboardManager;
    private TreasuryService treasuryService;
    private MarketEventService marketEventService;
    private EconomicNewsService economicNewsService;
    private CommandManager commandManager;
    private TaskScheduler taskScheduler;
    private WebServer webServer;

    private Economy vaultEconomy;
    private Permission vaultPerms;

    @Override
    public void onEnable() {
        instance = this;

        try {
            initialize();
            logStartupSummary();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable Auto-Tune", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdown();
        getLogger().info("Auto-Tune has been disabled.");
    }

    private void initialize() throws Exception {
        // Load configuration first
        configManager = new ConfigManager(this);
        configManager.load();

        // Fail fast if config has invalid values — before any managers or DB are initialized
        var violations = ConfigValidator.validate(configManager.getConfig());
        if (!violations.isEmpty()) {
            throw new IllegalStateException(ConfigValidator.format(violations));
        }

        // Setup Vault economy
        if (!setupEconomy()) {
            throw new IllegalStateException("Vault economy not found! Please install Vault and an economy plugin.");
        }

        // Setup Vault permissions (for guild detection) — non-fatal if absent
        setupPermission();

        // Initialize database
        databaseManager = new DatabaseManager(this, configManager);
        databaseManager.initialize();

        // Create Guice injector with all dependencies
        injector = Guice.createInjector(new AutoTuneModule(this));

        // Initialize managers through Guice
        economyManager = injector.getInstance(EconomyManager.class);
        shopManager = injector.getInstance(ShopManager.class);
        marketEngine = injector.getInstance(MarketEngine.class);
        loanManager = injector.getInstance(LoanManager.class);
        autosellManager = injector.getInstance(AutosellManager.class);
        economyMetricsManager = injector.getInstance(EconomyMetricsManager.class);
        priceAlertManager = injector.getInstance(PriceAlertManager.class);
        priceAlertManager.initialize();
        scoreboardManager = injector.getInstance(ScoreboardManager.class);
        scoreboardManager.start();
        treasuryService = injector.getInstance(TreasuryService.class);
        treasuryService.start();
        marketEventService = injector.getInstance(MarketEventService.class);
        marketEventService.onEnable();
        economicNewsService = injector.getInstance(EconomicNewsService.class);
        economicNewsService.onEnable();
        taskScheduler = injector.getInstance(TaskScheduler.class);

        // Register commands
        commandManager = injector.getInstance(CommandManager.class);
        commandManager.registerCommands();

        // Register event listeners
        getServer().getPluginManager().registerEvents(
                injector.getInstance(PlayerListener.class), this);
        getServer().getPluginManager().registerEvents(
                injector.getInstance(AutosellListener.class), this);
        getServer().getPluginManager().registerEvents(
                injector.getInstance(SellGuiListener.class), this);

        // Start scheduled tasks
        taskScheduler.start();

        // Start web server if enabled
        if (configManager.getConfig().web().enabled()) {
            webServer = injector.getInstance(WebServer.class);
            webServer.start();
        }

        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.noahblclarkson.autotune.util.AutoTunePlaceholders(
                    this, shopManager, marketEngine, economyMetricsManager, configManager
            ).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }
    }

    private void shutdown() {
        if (webServer != null) {
            webServer.stop();
        }

        if (taskScheduler != null) {
            taskScheduler.stop();
        }

        if (treasuryService != null) {
            treasuryService.shutdown();
        }

        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();
        return true;
    }

    public void reload() throws Exception {
        configManager.load();
        marketEngine.reload();
        priceAlertManager.rebuildCache();
        if (webServer != null && configManager.getConfig().web().enabled()) {
            webServer.stop();
            webServer.start();
        }
    }

    @NotNull
    public static AutoTune getInstance() {
        return instance;
    }

    @NotNull
    public Injector getInjector() {
        return injector;
    }

    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @NotNull
    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    public Permission getVaultPerms() {
        return vaultPerms;
    }

    private void setupPermission() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found — guild features disabled.");
            vaultPerms = null;
            return;
        }
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager()
                .getRegistration(Permission.class);
        if (rsp == null) {
            getLogger().warning("No Vault permission provider found — guild features disabled.");
            vaultPerms = null;
            return;
        }
        vaultPerms = rsp.getProvider();
        getLogger().info("Vault permission provider registered (" + vaultPerms.getName() + ").");
    }

    /**
     * Logs a concise startup summary with version, config, and enabled features.
     * Called once after all managers are initialized, before commands/listeners.
     */
    private void logStartupSummary() {
        var meta = getPluginMeta();
        var cfg = configManager.getConfig();

        getLogger().info("========================================");
        getLogger().info("  Auto-Tune v" + meta.getVersion() + " enabled");
        getLogger().info("  Storage: " + cfg.storage().type()
                + " | Web: " + cfg.web().host() + ":" + cfg.web().port()
                + " | Market: " + (cfg.marketFrozen() ? "FROZEN" : "active"));
        getLogger().info("  Economy: " + cfg.economy().currencySymbol()
                + " | Loans: " + (cfg.loans().enabled() ? "on" : "off")
                + " | Events: " + (cfg.marketEvents().enabled() ? "on" : "off")
                + " | Price reporting: " + (cfg.priceReporter().enabled() ? "on" : "off")
                + " | News feed: " + (cfg.news().enabled() ? "on (" + cfg.news().intervalMinutes() + "m)" : "off"));
        getLogger().info("  Commands: /shop, /sell, /autosell, /loan, /auction, /event");
        getLogger().info("  Dashboard: http://" + cfg.web().host() + ":" + cfg.web().port());
        getLogger().info("========================================");
    }

    @NotNull
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    @NotNull
    public ShopManager getShopManager() {
        return shopManager;
    }

    @NotNull
    public MarketEngine getMarketEngine() {
        return marketEngine;
    }

    @NotNull
    public LoanManager getLoanManager() {
        return loanManager;
    }

    @NotNull
    public AutosellManager getAutosellManager() {
        return autosellManager;
    }

    @NotNull
    public TreasuryService getTreasuryService() {
        return treasuryService;
    }

    @NotNull
    public EconomyMetricsManager getEconomyMetricsManager() {
        return economyMetricsManager;
    }

    @NotNull
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    @Nullable
    public WebServer getWebServer() {
        return webServer;
    }
}
