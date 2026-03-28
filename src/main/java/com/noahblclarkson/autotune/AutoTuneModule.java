package com.noahblclarkson.autotune;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.guild.GuildService;
import com.noahblclarkson.autotune.database.AutosellRepository;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.PriceAlertRepository;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.DefaultPluginAdapter;
import com.noahblclarkson.autotune.manager.ExchangeRateService;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.PluginAdapter;
import com.noahblclarkson.autotune.manager.PriceAlertManager;
import com.noahblclarkson.autotune.manager.ScoreboardManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoTuneModule extends AbstractModule {

    private final AutoTune plugin;

    public AutoTuneModule(AutoTune plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(AutoTune.class).toInstance(plugin);
        bind(JavaPlugin.class).toInstance(plugin);
        bind(PluginAdapter.class).toInstance(new DefaultPluginAdapter(plugin));
    }

    @Provides
    @Singleton
    public ConfigManager provideConfigManager() {
        return plugin.getConfigManager();
    }

    @Provides
    @Singleton
    public DatabaseManager provideDatabaseManager() {
        return plugin.getDatabaseManager();
    }

    @Provides
    @Singleton
    public Economy provideEconomy() {
        return plugin.getVaultEconomy();
    }

    @Provides
    @Singleton
    public Permission providePermission() {
        return plugin.getVaultPerms();
    }

    @Provides
    @Singleton
    public GuildService provideGuildService(
            PlayerRepository playerRepository,
            LoanRepository loanRepository,
            Server server
    ) {
        return new GuildService(playerRepository, loanRepository, server);
    }

    @Provides
    @Singleton
    public ItemRepository provideItemRepository(DatabaseManager databaseManager) {
        return new ItemRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PlayerRepository providePlayerRepository(DatabaseManager databaseManager) {
        return new PlayerRepository(databaseManager);
    }

    @Provides
    @Singleton
    public LoanRepository provideLoanRepository(DatabaseManager databaseManager) {
        return new LoanRepository(databaseManager);
    }

    @Provides
    @Singleton
    public TransactionRepository provideTransactionRepository(DatabaseManager databaseManager) {
        return new TransactionRepository(databaseManager);
    }

    @Provides
    @Singleton
    public AutosellRepository provideAutosellRepository(DatabaseManager databaseManager) {
        return new AutosellRepository(databaseManager);
    }

    @Provides
    @Singleton
    public EconomySnapshotRepository provideEconomySnapshotRepository(DatabaseManager databaseManager) {
        return new EconomySnapshotRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PriceOverrideRepository providePriceOverrideRepository(DatabaseManager databaseManager) {
        return new PriceOverrideRepository(databaseManager);
    }

    @Provides
    @Singleton
    public AuctionRepository provideAuctionRepository(DatabaseManager databaseManager) {
        return new AuctionRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PriceAlertRepository providePriceAlertRepository(DatabaseManager databaseManager) {
        return new PriceAlertRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PriceAlertManager providePriceAlertManager(
            PriceAlertRepository priceAlertRepository,
            MarketEngine marketEngine,
            ShopManager shopManager,
            ConfigManager configManager
    ) {
        return new PriceAlertManager(
                plugin,
                priceAlertRepository,
                marketEngine,
                shopManager,
                configManager
        );
    }

    @Provides
    @Singleton
    public ScoreboardManager provideScoreboardManager(
            EconomySnapshotRepository economySnapshotRepository,
            AutoTuneConfig autoTuneConfig
    ) {
        return new ScoreboardManager(plugin, economySnapshotRepository, autoTuneConfig);
    }

    @Provides
    @Singleton
    public ExchangeRateService provideExchangeRateService(
            ConfigManager configManager
    ) {
        return new ExchangeRateService(plugin, configManager);
    }
}
