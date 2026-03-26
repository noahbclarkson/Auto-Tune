package com.noahblclarkson.autotune.task;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.PriceAlertManager;
import com.noahblclarkson.autotune.manager.PriceReporter;
import com.noahblclarkson.autotune.web.WebServer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;

@Singleton
public class TaskScheduler {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final MarketEngine marketEngine;
    private final LoanManager loanManager;
    private final EconomyMetricsManager economyMetricsManager;
    private final Provider<WebServer> webServerProvider;
    private final PriceReporter priceReporter;
    private final PriceAlertManager priceAlertManager;

    private ScheduledTask marketTask;
    private ScheduledTask loanInterestTask;
    private ScheduledTask loanOverdueTask;
    private ScheduledTask loanWarningTask;
    private ScheduledTask economySnapshotTask;
    private ScheduledTask priceReporterTask;
    private ScheduledTask alertCheckTask;

    @Inject
    public TaskScheduler(
            AutoTune plugin,
            ConfigManager configManager,
            MarketEngine marketEngine,
            LoanManager loanManager,
            EconomyMetricsManager economyMetricsManager,
            Provider<WebServer> webServerProvider,
            PriceReporter priceReporter,
            PriceAlertManager priceAlertManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketEngine = marketEngine;
        this.loanManager = loanManager;
        this.economyMetricsManager = economyMetricsManager;
        this.webServerProvider = webServerProvider;
        this.priceReporter = priceReporter;
        this.priceAlertManager = priceAlertManager;
    }

    public void start() {
        startMarketTask();
        startLoanTasks();
        startEconomySnapshotTask();
        startPriceReporterTask();
        startAlertCheckTask();
        plugin.getLogger().info("Scheduled tasks started.");
    }

    public void stop() {
        if (marketTask != null) {
            marketTask.cancel();
        }
        if (loanInterestTask != null) {
            loanInterestTask.cancel();
        }
        if (loanOverdueTask != null) {
            loanOverdueTask.cancel();
        }
        if (loanWarningTask != null) {
            loanWarningTask.cancel();
        }
        if (economySnapshotTask != null) {
            economySnapshotTask.cancel();
        }
        if (priceReporterTask != null) {
            priceReporterTask.cancel();
        }
        if (alertCheckTask != null) {
            alertCheckTask.cancel();
        }
        plugin.getLogger().info("Scheduled tasks stopped.");
    }

    private void startMarketTask() {
        long intervalTicks = configManager.getConfig().economy().updateInterval();
        long intervalMs = intervalTicks * 50;

        marketTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        marketEngine.tick();
                        if (configManager.getConfig().web().websocketEnabled()) {
                            webServerProvider.get().broadcastPriceUpdate(marketEngine.getPriceCache());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in market tick: " + e.getMessage());
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    private void startLoanTasks() {
        AutoTuneConfig.LoanConfig loanConfig = configManager.getConfig().loans();
        if (!loanConfig.enabled()) {
            return;
        }

        int compoundHours = loanConfig.compoundIntervalHours();
        loanInterestTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        loanManager.processInterest();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing loan interest: " + e.getMessage());
                    }
                },
                1,
                compoundHours,
                TimeUnit.HOURS
        );

        int overdueHours = loanConfig.overdueCheckIntervalHours();
        loanOverdueTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        loanManager.processOverdueLoans();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing overdue loans: " + e.getMessage());
                    }
                },
                1,
                overdueHours,
                TimeUnit.HOURS
        );

        if (loanConfig.warningBeforeDueHours() > 0) {
            loanWarningTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    task -> {
                        try {
                            loanManager.processWarnings();
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error processing loan warnings: " + e.getMessage());
                        }
                    },
                    30,
                    30,
                    TimeUnit.MINUTES
            );
        }
    }

    private void startEconomySnapshotTask() {
        economySnapshotTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        economyMetricsManager.captureSnapshot();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error capturing economy snapshot: " + e.getMessage());
                    }
                },
                5,
                5,
                TimeUnit.MINUTES
        );
    }
    private void startPriceReporterTask() {
        if (!priceReporter.isEnabled()) {
            return;
        }

        long intervalMinutes = priceReporter.getIntervalMinutes();
        priceReporterTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        priceReporter.submitSnapshot();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in price reporter task: " + e.getMessage());
                    }
                },
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
    }

    private void startAlertCheckTask() {
        alertCheckTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    try {
                        priceAlertManager.checkAlerts();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error checking price alerts: " + e.getMessage());
                    }
                },
                1,
                1,
                TimeUnit.MINUTES
        );
    }

}
