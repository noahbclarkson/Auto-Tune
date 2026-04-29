package com.noahblclarkson.autotune;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.guild.GuildService;
import com.noahblclarkson.autotune.database.AdminAuditRepository;
import com.noahblclarkson.autotune.database.AutosellRepository;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.BadgeRepository;
import com.noahblclarkson.autotune.database.WatchedAuctionRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.MarketEventRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.service.AdminAuditService;
import com.noahblclarkson.autotune.service.AdminWebhookService;
import com.noahblclarkson.autotune.service.EconomicNewsService;
import com.noahblclarkson.autotune.service.BadgeService;
import com.noahblclarkson.autotune.service.MarketDigestService;
import com.noahblclarkson.autotune.service.PriceMilestoneService;
import com.noahblclarkson.autotune.service.PlayerOnboardingService;
import com.noahblclarkson.autotune.service.PlayerImpactService;
import com.noahblclarkson.autotune.service.PlayerStreakService;
import com.noahblclarkson.autotune.service.EconomyWhatMovedService;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.PriceAlertRepository;
import com.noahblclarkson.autotune.database.ShopFavoriteRepository;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.DatabaseCleanupManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.DefaultPluginAdapter;
import com.noahblclarkson.autotune.manager.ExchangeRateService;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
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
        bind(Server.class).toInstance(plugin.getServer());
        bind(PluginAdapter.class).toInstance(new DefaultPluginAdapter(plugin));
    }

    @Provides
    @Singleton
    public ConfigManager provideConfigManager() {
        return plugin.getConfigManager();
    }

    @Provides
    @Singleton
    public AutoTuneConfig provideAutoTuneConfig() {
        return plugin.getConfigManager().getConfig();
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
    public WatchedAuctionRepository provideWatchedAuctionRepository(DatabaseManager databaseManager) {
        return new WatchedAuctionRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PriceAlertRepository providePriceAlertRepository(DatabaseManager databaseManager) {
        return new PriceAlertRepository(databaseManager);
    }

    @Provides
    @Singleton
    public ShopFavoriteRepository provideShopFavoriteRepository(DatabaseManager databaseManager) {
        return new ShopFavoriteRepository(databaseManager);
    }

    @Provides
    @Singleton
    public BadgeRepository provideBadgeRepository(DatabaseManager databaseManager) {
        return new BadgeRepository(databaseManager);
    }

    @Provides
    @Singleton
    public PriceAlertManager providePriceAlertManager(
            PriceAlertRepository priceAlertRepository,
            MarketEngine marketEngine,
            ShopManager shopManager,
            ConfigManager configManager,
            BadgeService badgeService,
            com.noahblclarkson.autotune.database.PendingNotificationRepository pendingNotificationRepository
    ) {
        return new PriceAlertManager(
                plugin,
                priceAlertRepository,
                marketEngine,
                shopManager,
                configManager,
                badgeService,
                pendingNotificationRepository
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

    @Provides
    @Singleton
    public MarketEventRepository provideMarketEventRepository(DatabaseManager databaseManager) {
        return new MarketEventRepository(databaseManager);
    }

    @Provides
    @Singleton
    public MarketEventService provideMarketEventService(
            MarketEventRepository marketEventRepository,
            PluginAdapter pluginAdapter,
            ConfigManager configManager
    ) {
        return new MarketEventService(plugin, marketEventRepository, pluginAdapter, configManager);
    }

    @Provides
    @Singleton
    public EconomicNewsService provideEconomicNewsService(
            ItemRepository itemRepository,
            ShopManager shopManager,
            LoanManager loanManager,
            ConfigManager configManager,
            PluginAdapter pluginAdapter,
            AdminWebhookService adminWebhookService
    ) {
        return new EconomicNewsService(
                plugin, itemRepository, shopManager, loanManager, configManager, pluginAdapter,
                adminWebhookService);
    }

    @Provides
    @Singleton
    public BadgeService provideBadgeService(DatabaseManager databaseManager) {
        return new BadgeService(databaseManager, plugin);
    }

    @Provides
    @Singleton
    public DatabaseCleanupManager provideDatabaseCleanupManager(
            ConfigManager configManager,
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            EconomySnapshotRepository snapshotRepository,
            AuctionRepository auctionRepository,
            MarketEventRepository marketEventRepository,
            DatabaseManager databaseManager
    ) {
        return new DatabaseCleanupManager(
                plugin, configManager, transactionRepository, itemRepository,
                snapshotRepository, auctionRepository, marketEventRepository, databaseManager);
    }

    @Provides
    @Singleton
    public MarketDigestService provideMarketDigestService(
            AutoTune plugin,
            ConfigManager configManager,
            MarketEventService marketEventService,
            EconomyMetricsManager economyMetricsManager,
            LoanManager loanManager,
            MarketEngine marketEngine,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository,
            ShopManager shopManager
    ) {
        return new MarketDigestService(
                plugin, configManager, marketEventService, economyMetricsManager,
                loanManager, marketEngine, itemRepository, transactionRepository, shopManager);
    }

    @Provides
    @Singleton
    public PriceMilestoneService providePriceMilestoneService(
            AutoTune plugin,
            MarketEngine marketEngine,
            ShopManager shopManager,
            ConfigManager configManager
    ) {
        return new PriceMilestoneService(plugin, marketEngine, shopManager, configManager);
    }

    @Provides
    @Singleton
    public PlayerOnboardingService providePlayerOnboardingService(
            AutoTune plugin,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository,
            com.noahblclarkson.autotune.database.PendingNotificationRepository pendingNotificationRepository,
            ConfigManager configManager
    ) {
        return new PlayerOnboardingService(
                plugin, databaseManager, playerRepository,
                pendingNotificationRepository, configManager);
    }

    @Provides
    @Singleton
    public PlayerImpactService providePlayerImpactService(
            PlayerRepository playerRepository,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository,
            MarketEngine marketEngine
    ) {
        return new PlayerImpactService(playerRepository, itemRepository, transactionRepository, marketEngine);
    }

    @Provides
    @Singleton
    public PlayerStreakService providePlayerStreakService(
            AutoTune plugin,
            DatabaseManager databaseManager,
            BadgeService badgeService
    ) {
        return new PlayerStreakService(plugin, databaseManager, badgeService);
    }

    @Provides
    @Singleton
    public EconomyWhatMovedService provideEconomyWhatMovedService(
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            ShopManager shopManager
    ) {
        return new EconomyWhatMovedService(transactionRepository, itemRepository, shopManager);
    }

    @Provides
    @Singleton
    public AdminAuditRepository provideAdminAuditRepository(DatabaseManager databaseManager) {
        return new AdminAuditRepository(databaseManager);
    }

    @Provides
    @Singleton
    public AdminAuditService provideAdminAuditService(AdminAuditRepository repository) {
        return new AdminAuditService(repository);
    }
}
