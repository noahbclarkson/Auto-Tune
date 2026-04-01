package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.DatabaseCleanupManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.ExchangeRateService;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.manager.PriceReporter;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.ExchangeRate;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.PriceOverride;
import com.noahblclarkson.autotune.model.ItemTier;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("PMD")
@Singleton
public class AdminCommand {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final EconomyMetricsManager metricsManager;
    private final PriceOverrideRepository overrideRepo;
    private final LoanManager loanManager;
    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final ExchangeRateService exchangeRateService;
    private final PriceReporter priceReporter;
    private final DatabaseCleanupManager cleanupManager;
    private final MarketEventService marketEventService;

    @Inject
    public AdminCommand(
            AutoTune plugin,
            ConfigManager configManager,
            ShopManager shopManager,
            MarketEngine marketEngine,
            EconomyMetricsManager metricsManager,
            PriceOverrideRepository overrideRepo,
            LoanManager loanManager,
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            ExchangeRateService exchangeRateService,
            DatabaseCleanupManager cleanupManager,
            MarketEventService marketEventService,
            PriceReporter priceReporter
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.metricsManager = metricsManager;
        this.overrideRepo = overrideRepo;
        this.loanManager = loanManager;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.exchangeRateService = exchangeRateService;
        this.cleanupManager = cleanupManager;
        this.marketEventService = marketEventService;
        this.priceReporter = priceReporter;
    }

    @Command("autotune admin")
    @Permission("autotune.admin")
    public void adminHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune Admin", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/at admin info", NamedTextColor.YELLOW)
                .append(Component.text(" — Economy overview and health", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin health", NamedTextColor.YELLOW)
                .append(Component.text(" — Full economy diagnostic report", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin audit", NamedTextColor.YELLOW)
                .append(Component.text(" — System health and consistency check", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin trend [days]", NamedTextColor.YELLOW)
                .append(Component.text(" — Economy trajectory over N days (default: 7)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin stats", NamedTextColor.YELLOW)
                .append(Component.text(" — Detailed market statistics", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin market freeze", NamedTextColor.YELLOW)
                .append(Component.text(" — Freeze/unfreeze price engine", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price set <item> <price> [hours]", NamedTextColor.YELLOW)
                .append(Component.text(" — Override item price", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price remove <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Remove price override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price list", NamedTextColor.YELLOW)
                .append(Component.text(" — List all active overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item spread <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Per-item base spread override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item maxchange <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Per-item max price change override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item floor <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Price floor (min price), -1 to clear", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item ceiling <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Price ceiling (max price), -1 to clear", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item info <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Show item config & overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item reset <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Clear all per-item overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item freeze <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Freeze price updates for one item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item unfreeze <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Unfreeze price updates for one item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config and caches", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin transactions [player]", NamedTextColor.YELLOW)
                .append(Component.text(" — View recent transaction history", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin exchange", NamedTextColor.YELLOW)
                .append(Component.text(" — Show cross-server exchange rates", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin transaction-min", NamedTextColor.YELLOW)
                .append(Component.text(" — Show minimum transaction size settings", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin info")
    @Permission("autotune.admin")
    public void adminInfo(CommandSender sender) {
        boolean frozen = marketEngine.isFrozen();
        Map<Integer, PriceOverride> overrides = marketEngine.getActiveOverrides();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune Market Status", NamedTextColor.GOLD, TextDecoration.BOLD));

        Component frozenStatus = frozen
                ? Component.text("FROZEN", NamedTextColor.RED)
                : Component.text("Active", NamedTextColor.GREEN);
        sender.sendMessage(Component.text("  Market: ").color(NamedTextColor.GRAY)
                .append(frozenStatus));

        Component overrideStatus = overrides.isEmpty()
                ? Component.text("None", NamedTextColor.GRAY)
                : Component.text(overrides.size() + " active", NamedTextColor.YELLOW);
        sender.sendMessage(Component.text("  Overrides: ").color(NamedTextColor.GRAY)
                .append(overrideStatus));

        // GDP and debt from latest snapshot
        metricsManager.getLatestSnapshot().ifPresent(snap -> {
            sender.sendMessage(Component.text("  GDP (24h): ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.gdp()), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("  Total Debt: ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.totalDebt()),
                            snap.totalDebt().compareTo(BigDecimal.ZERO) > 0 ? NamedTextColor.RED : NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("  Active Loans: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(snap.activeLoans()), NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("  Online Players: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(snap.playerCount()), NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("  24h Volume: ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.transactionVolume()), NamedTextColor.AQUA)));
        });

        if (!metricsManager.getLatestSnapshot().isPresent()) {
            sender.sendMessage(Component.text("  No economy snapshot available yet.", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin audit")
    @Permission("autotune.admin")
    public void adminAudit(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune System Audit", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — " + DATE_FORMAT.format(Instant.now()), NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.empty());

        int issues = 0;

        // ── 1. Vault check ────────────────────────────────────────────────
        sender.sendMessage(Component.text("Vault & Dependencies", NamedTextColor.YELLOW, TextDecoration.BOLD));
        boolean vaultOk = checkVault(sender);
        if (!vaultOk) issues++;
        sender.sendMessage(Component.empty());

        // ── 2. Config sanity ───────────────────────────────────────────────
        sender.sendMessage(Component.text("Configuration", NamedTextColor.YELLOW, TextDecoration.BOLD));
        issues += checkConfig(sender);
        sender.sendMessage(Component.empty());

        // ── 3. Economy state ───────────────────────────────────────────────
        sender.sendMessage(Component.text("Economy State", NamedTextColor.YELLOW, TextDecoration.BOLD));
        issues += checkEconomyState(sender);
        sender.sendMessage(Component.empty());

        // ── 4. Database health ─────────────────────────────────────────────
        sender.sendMessage(Component.text("Database", NamedTextColor.YELLOW, TextDecoration.BOLD));
        issues += checkDatabase(sender);
        sender.sendMessage(Component.empty());

        // ── 5. Circuit breaker ─────────────────────────────────────────────
        sender.sendMessage(Component.text("Loan Circuit Breaker", NamedTextColor.YELLOW, TextDecoration.BOLD));
        issues += checkCircuitBreaker(sender);
        sender.sendMessage(Component.empty());

        // ── Summary ────────────────────────────────────────────────────────
        sender.sendMessage(Component.empty());
        if (issues == 0) {
            sender.sendMessage(Component.text("✅ No issues found — economy looks healthy.",
                    NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        } else {
            sender.sendMessage(Component.text("⚠️  " + issues + " issue(s) found — review above for details.",
                    NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text("  Run /at admin health for a full market diagnostic.",
                    NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.empty());
    }

    private boolean checkVault(CommandSender sender) {
        try {
            var reg = plugin.getServer().getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (reg == null) {
                sender.sendMessage(Component.text("  ❌ Vault Economy provider not found.", NamedTextColor.RED)
                        .append(Component.text(" Players cannot buy or sell.", NamedTextColor.GRAY)));
                return false;
            }
            net.milkbowl.vault.economy.Economy eco = reg.getProvider();
            // Test with the console's UUID (Minecraft console UUID: UUID for "Console" or use first online player)
            if (eco != null) {
                sender.sendMessage(Component.text("  ✅ Vault Economy connected.", NamedTextColor.GREEN));
                return true;
            } else {
                sender.sendMessage(Component.text("  ❌ Vault Economy provider is null.", NamedTextColor.RED));
                return false;
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("  ❌ Vault Economy error: " + e.getMessage(), NamedTextColor.RED));
            return false;
        }
    }

    private int checkConfig(CommandSender sender) {
        int issues = 0;
        var config = configManager.getConfig();

        // Market frozen
        if (marketEngine.isFrozen()) {
            sender.sendMessage(Component.text("  ⚠️  Market is frozen — prices are not updating.", NamedTextColor.YELLOW));
            issues++;
        }

        // Spread sanity
        double baseSpread = config.economy().spread().baseSpread();
        if (baseSpread <= 0) {
            sender.sendMessage(Component.text("  ❌ baseSpread is " + baseSpread + " — must be > 0. Prices cannot update.",
                    NamedTextColor.RED));
            issues++;
        } else if (baseSpread > 1.0) {
            sender.sendMessage(Component.text("  ❌ baseSpread is " + baseSpread + " — must be ≤ 1.0 (100%).",
                    NamedTextColor.RED));
            issues++;
        } else if (baseSpread > 0.5) {
            sender.sendMessage(Component.text("  ⚠️  baseSpread is " + (baseSpread * 100) + "% — spreads will be very wide.",
                    NamedTextColor.YELLOW));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ baseSpread: " + (baseSpread * 100) + "%", NamedTextColor.GREEN));
        }

        // Max price change
        double maxChange = config.economy().maxPriceChangePercent();
        if (maxChange <= 0 || maxChange > 50) {
            sender.sendMessage(Component.text("  ❌ maxPriceChangePercent is " + maxChange
                    + "% — should be between 0.1 and 50. Prices may behave unpredictably.", NamedTextColor.RED));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ maxPriceChangePercent: " + maxChange + "%", NamedTextColor.GREEN));
        }

        // Market tick interval
        long tickInterval = config.economy().updateInterval();
        long tickMs = tickInterval * 50;
        if (tickInterval <= 0) {
            sender.sendMessage(Component.text("  ❌ Market tick interval is " + tickInterval + " — must be > 0.",
                    NamedTextColor.RED));
            issues++;
        } else if (tickMs < 60000) {
            sender.sendMessage(Component.text("  ⚠️  Market tick is every " + (tickMs / 1000) + "s — very fast! "
                    + "Consider ≥ 6000 (5 min) for typical servers.", NamedTextColor.YELLOW));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ Market tick: every " + (tickMs / 1000) + "s", NamedTextColor.GREEN));
        }

        // Loan enabled but no interest rate
        if (config.loans().enabled()) {
            double rate = config.loans().baseInterestRate();
            if (rate <= 0) {
                sender.sendMessage(Component.text("  ❌ Loans enabled but interest rate is " + rate
                        + " — loans will never generate revenue.", NamedTextColor.RED));
                issues++;
            } else if (rate > 0.5) {
                sender.sendMessage(Component.text("  ⚠️  Interest rate is " + (rate * 100)
                        + "% — may be too high for players.", NamedTextColor.YELLOW));
                issues++;
            } else {
                sender.sendMessage(Component.text("  ✅ Loan interest rate: " + (rate * 100) + "%", NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text("  ℹ️  Loans disabled.", NamedTextColor.GRAY));
        }

        // Exchange rate fetch issues
        if (exchangeRateService.isEnabled()) {
            if (exchangeRateService.lastFetchFailed()) {
                sender.sendMessage(Component.text("  ⚠️  Exchange rate fetch failed on last attempt — "
                        + "check API server is reachable and API key is valid.", NamedTextColor.YELLOW));
                issues++;
            } else if (exchangeRateService.lastFetchedAt() == null) {
                sender.sendMessage(Component.text("  ⚠️  Exchange rates enabled but never fetched successfully.",
                        NamedTextColor.YELLOW));
                issues++;
            } else {
                Duration ago = Duration.between(exchangeRateService.lastFetchedAt(), Instant.now());
                sender.sendMessage(Component.text("  ✅ Exchange rates — last fetched "
                        + formatDuration(ago) + " ago", NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text("  ℹ️  Exchange rates disabled.", NamedTextColor.GRAY));
        }

        // Database cleanup
        int cleanupHours = config.cleanup().cleanupIntervalHours();
        if (cleanupHours <= 0) {
            sender.sendMessage(Component.text("  ⚠️  Database cleanup disabled — data.db may grow unbounded.",
                    NamedTextColor.YELLOW));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ Database cleanup every " + cleanupHours + "h", NamedTextColor.GREEN));
        }

        return issues;
    }

    private int checkEconomyState(CommandSender sender) {
        int issues = 0;

        Optional<EconomySnapshot> snapOpt = metricsManager.getLatestSnapshot();
        if (snapOpt.isEmpty()) {
            sender.sendMessage(Component.text("  ⚠️  No economy snapshot yet — "
                    + "economy hasn't been running long enough.", NamedTextColor.YELLOW));
            issues++;
            return issues;
        }

        EconomySnapshot snap = snapOpt.get();

        // GDP sanity
        if (snap.gdp().compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("  ❌ GDP is " + configManager.formatCurrency(snap.gdp())
                    + " — no economic activity detected.", NamedTextColor.RED));
            issues++;
        } else if (snap.gdp().compareTo(new BigDecimal("100")) < 0) {
            sender.sendMessage(Component.text("  ⚠️  GDP is very low: " + configManager.formatCurrency(snap.gdp())
                    + " — may indicate a new or inactive economy.", NamedTextColor.YELLOW));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ GDP: " + configManager.formatCurrency(snap.gdp()),
                    NamedTextColor.GREEN));
        }

        // Debt/GDP ratio
        if (snap.gdp().compareTo(BigDecimal.ZERO) > 0) {
            double ratio = snap.totalDebt().divide(snap.gdp(), 4, RoundingMode.HALF_UP).doubleValue();
            if (ratio > 10) {
                sender.sendMessage(Component.text("  ❌ Debt/GDP is " + String.format("%.1fx", ratio)
                        + " — critical. Circuit breaker should be pausing interest.",
                        NamedTextColor.RED));
                issues++;
            } else if (ratio > 3) {
                sender.sendMessage(Component.text("  ⚠️  Debt/GDP is " + String.format("%.1fx", ratio)
                        + " — elevated. Monitor with /at admin health.", NamedTextColor.YELLOW));
                issues++;
            } else {
                sender.sendMessage(Component.text("  ✅ Debt/GDP: " + String.format("%.1fx", ratio),
                        NamedTextColor.GREEN));
            }
        }

        // Volume activity
        if (snap.transactionVolume().compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("  ⚠️  No transaction volume in the snapshot period — "
                    + "players may not be trading.", NamedTextColor.YELLOW));
            issues++;
        }

        // Stale snapshot
        Duration age = Duration.between(snap.timestamp(), Instant.now());
        if (age.toMinutes() > 15) {
            sender.sendMessage(Component.text("  ❌ Latest snapshot is " + formatDuration(age)
                    + " old — snapshot system may be broken.", NamedTextColor.RED));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ Snapshot age: " + formatDuration(age) + " ago",
                    NamedTextColor.GREEN));
        }

        // Active expired price overrides
        Map<Integer, PriceOverride> overrides = overrideRepo.getAllOverrides();
        long expiredCount = overrides.values().stream().filter(PriceOverride::isExpired).count();
        if (expiredCount > 0) {
            sender.sendMessage(Component.text("  ⚠️  " + expiredCount + " expired price override(s) not cleaned up — "
                    + "run /at admin reload to clear or remove manually with /at admin price remove.",
                    NamedTextColor.YELLOW));
            issues++;
        }

        // Items with stale prices
        List<ShopItem> allItems = shopManager.getAllItems();
        Instant oneWeekAgo = Instant.now().minus(Duration.ofDays(7));
        int staleItems = 0;
        for (ShopItem item : allItems) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), oneWeekAgo, 1);
            if (history.isEmpty()) {
                staleItems++;
            }
        }
        if (staleItems > 0 && allItems.size() > 0) {
            double pct = (staleItems * 100.0) / allItems.size();
            sender.sendMessage(Component.text("  ⚠️  " + staleItems + "/" + allItems.size()
                    + " items (" + String.format("%.0f%%", pct) + ") have no price history in 7 days — "
                    + "these items may not be trading.", NamedTextColor.YELLOW));
            issues++;
        }

        return issues;
    }

    private int checkDatabase(CommandSender sender) {
        int issues = 0;

        try {
            DatabaseCleanupManager.CleanupStats stats = cleanupManager.getStats();

            sender.sendMessage(Component.text("  Table sizes:", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("    Transactions: " + formatNumber(stats.transactionCount()), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("    Market history: " + formatNumber(stats.marketHistoryCount()), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("    Economy snapshots: " + formatNumber(stats.snapshotCount()), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("    Auction orders: " + formatNumber(stats.auctionOrderCount()), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("    Auction fills: " + formatNumber(stats.auctionFillCount()), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("    Market events: " + formatNumber(stats.marketEventCount()), NamedTextColor.AQUA));

            // Warn about large tables
            if (stats.transactionCount() > 1_000_000) {
                sender.sendMessage(Component.text("  ⚠️  Transactions table has "
                        + formatNumber(stats.transactionCount()) + " rows — "
                        + "consider reducing cleanup.retention-days if > 14 days.",
                        NamedTextColor.YELLOW));
                issues++;
            }
            if (stats.marketHistoryCount() > 5_000_000) {
                sender.sendMessage(Component.text("  ⚠️  Market history has "
                        + formatNumber(stats.marketHistoryCount()) + " rows — "
                        + "consider reducing cleanup.market-history.retention-days if > 7 days.",
                        NamedTextColor.YELLOW));
                issues++;
            }

            // Market events stuck in unusual state
            List<MarketEvent> allEvents = marketEventService.listEvents();
            long activeCount = allEvents.stream()
                    .filter(e -> e.status() == com.noahblclarkson.autotune.model.MarketEvent.Status.ACTIVE)
                    .count();
            if (activeCount > 5) {
                sender.sendMessage(Component.text("  ⚠️  " + activeCount + " active market events — "
                        + "too many simultaneous events may distort prices.",
                        NamedTextColor.YELLOW));
                issues++;
            } else {
                sender.sendMessage(Component.text("  ✅ Active market events: " + activeCount, NamedTextColor.GREEN));
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("  ❌ Failed to read database stats: " + e.getMessage(),
                    NamedTextColor.RED));
            issues++;
        }

        return issues;
    }

    private int checkCircuitBreaker(CommandSender sender) {
        int issues = 0;
        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();

        sender.sendMessage(Component.text("  Tier: ", NamedTextColor.GRAY)
                .append(switch (cb.tier()) {
                    case "TIER3" -> Component.text("EMERGENCY", NamedTextColor.RED);
                    case "TIER2" -> Component.text("DANGER", NamedTextColor.RED);
                    case "TIER1" -> Component.text("WARNING", NamedTextColor.YELLOW);
                    default -> Component.text("Normal", NamedTextColor.GREEN);
                }));

        if (cb.debtGdpRatio() >= 0) {
            sender.sendMessage(Component.text("  Debt/GDP: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2fx", cb.debtGdpRatio()),
                            cb.debtGdpRatio() > 10 ? NamedTextColor.RED
                                    : cb.debtGdpRatio() > 3 ? NamedTextColor.YELLOW
                                    : NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("  Debt/GDP: N/A (GDP = 0)", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("  Interest rate: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f%% of normal",
                        cb.interestMultiplier() * 100),
                        cb.interestMultiplier() < 1.0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));

        if (!"NORMAL".equals(cb.tier())) {
            sender.sendMessage(Component.text("  ⚠️  Circuit breaker is active — "
                    + "interest is being capped or paused.", NamedTextColor.YELLOW)
                    .append(Component.text(" Run /at admin health to see full circuit breaker state.",
                            NamedTextColor.GRAY)));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ Circuit breaker is idle — economy is in a healthy state.",
                    NamedTextColor.GREEN));
        }

        // Check for overdue loans
        var overdueLoans = loanManager.getOverdueLoans();
        if (!overdueLoans.isEmpty()) {
            sender.sendMessage(Component.text("  ⚠️  " + overdueLoans.size()
                    + " overdue loan(s) — consider using /loan forgive or adjusting terms.",
                    NamedTextColor.YELLOW));
            issues++;
        } else {
            sender.sendMessage(Component.text("  ✅ No overdue loans.", NamedTextColor.GREEN));
        }

        return issues;
    }

    @Command("autotune admin health")
    @Permission("autotune.admin")
    public void adminHealth(CommandSender sender) {
        Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
        List<ShopItem> allItems = shopManager.getAllItems();

        // ── Market status ────────────────────────────────────────────────────
        boolean frozen = marketEngine.isFrozen();
        Component marketStatus = frozen
                ? Component.text("FROZEN", NamedTextColor.RED)
                : Component.text("Active", NamedTextColor.GREEN);

        // ── GDP + Debt + Circuit breaker ────────────────────────────────────
        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();
        BigDecimal gdp = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        int activeLoans = 0;

        if (metricsManager.getLatestSnapshot().isPresent()) {
            EconomySnapshot snap = metricsManager.getLatestSnapshot().get();
            gdp = snap.gdp();
            totalDebt = snap.totalDebt();
            activeLoans = snap.activeLoans();
        }

        // Debt/GDP ratio + tier color
        Component debtGdpLabel;
        NamedTextColor debtGdpColor;
        if (cb.debtGdpRatio() < 0) {
            debtGdpLabel = Component.text("N/A (no GDP)", NamedTextColor.GRAY);
            debtGdpColor = NamedTextColor.GRAY;
        } else if (cb.debtGdpRatio() < 3.0) {
            debtGdpLabel = Component.text(String.format("%.2fx", cb.debtGdpRatio()), NamedTextColor.GREEN);
            debtGdpColor = NamedTextColor.GREEN;
        } else if (cb.debtGdpRatio() < 10.0) {
            debtGdpLabel = Component.text(String.format("%.2fx ⚠", cb.debtGdpRatio()), NamedTextColor.YELLOW);
            debtGdpColor = NamedTextColor.YELLOW;
        } else {
            debtGdpLabel = Component.text(String.format("%.2fx ❌", cb.debtGdpRatio()), NamedTextColor.RED);
            debtGdpColor = NamedTextColor.RED;
        }

        // Circuit breaker tier badge
        Component tierBadge;
        NamedTextColor tierColor;
        switch (cb.tier()) {
            case "TIER3" -> { tierBadge = Component.text("TIER3 — EMERGENCY", NamedTextColor.RED); tierColor = NamedTextColor.RED; }
            case "TIER2" -> { tierBadge = Component.text("TIER2 — DANGER", NamedTextColor.RED); tierColor = NamedTextColor.RED; }
            case "TIER1" -> { tierBadge = Component.text("TIER1 — WARNING", NamedTextColor.YELLOW); tierColor = NamedTextColor.YELLOW; }
            default -> { tierBadge = Component.text("Normal", NamedTextColor.GREEN); tierColor = NamedTextColor.GREEN; }
        }

        // ── Buy ratio ───────────────────────────────────────────────────────
        BigDecimal buyVol = transactionRepository.getGlobalBuyVolume(oneDayAgo);
        BigDecimal totalVol = transactionRepository.getGlobalVolume(oneDayAgo);
        double buyPct = 0.0;
        if (totalVol.compareTo(BigDecimal.ZERO) > 0) {
            buyPct = buyVol.divide(totalVol, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        }
        NamedTextColor buyColor = (buyPct >= 45 && buyPct <= 55)
                ? NamedTextColor.GREEN
                : (buyPct >= 40 && buyPct <= 60)
                        ? NamedTextColor.YELLOW
                        : NamedTextColor.RED;
        Component buyLabel = Component.text(String.format("%.1f%% buy / %.1f%% sell",
                buyPct, 100.0 - buyPct), buyColor);

        // ── Spread ──────────────────────────────────────────────────────────
        double totalBpd = 0, totalSpd = 0;
        int spreadCount = 0;
        for (ShopItem item : allItems) {
            MarketEngine.SpreadResult sp = marketEngine.getSpread(item.id());
            totalBpd += sp.bpd().doubleValue();
            totalSpd += sp.spd().doubleValue();
            spreadCount++;
        }
        double avgBpd = spreadCount > 0 ? (totalBpd / spreadCount) * 100 : 0;
        double avgSpd = spreadCount > 0 ? (totalSpd / spreadCount) * 100 : 0;
        NamedTextColor spreadColor = (avgBpd < 5) ? NamedTextColor.GREEN : (avgBpd < 10) ? NamedTextColor.YELLOW : NamedTextColor.RED;
        Component spreadLabel = Component.text(
                String.format("BPD %.2f%% / SPD %.2f%%", avgBpd, avgSpd), spreadColor);

        // ── Volume activity ─────────────────────────────────────────────────
        double globalMult = marketEngine.getGlobalVolumeMultiplier();
        Component volLabel;
        NamedTextColor volColor;
        if (globalMult > 1.5) {
            volLabel = Component.text(String.format("High (%.2fx)", globalMult), NamedTextColor.YELLOW);
            volColor = NamedTextColor.YELLOW;
        } else if (globalMult < 0.5) {
            volLabel = Component.text(String.format("Low (%.2fx)", globalMult), NamedTextColor.RED);
            volColor = NamedTextColor.RED;
        } else {
            volLabel = Component.text(String.format("Normal (%.2fx)", globalMult), NamedTextColor.GREEN);
            volColor = NamedTextColor.GREEN;
        }

        // ── Inflation ───────────────────────────────────────────────────────
        Component inflationLabel = Component.text(metricsManager.getInflationLabel(),
                metricsManager.getInflationLabel().equals("Stable") ? NamedTextColor.GREEN
                        : NamedTextColor.YELLOW);

        // ── Top volatile + undersold items ─────────────────────────────────
        List<ItemVolatility> volatilities = new ArrayList<>();
        List<ItemVolatility> undersells = new ArrayList<>();
        for (ShopItem item : allItems) {
            List<PriceHistory> history = itemRepository
                    .getPriceHistorySince(item.id(), oneDayAgo, 10);
            if (history.size() < 2) {
                continue;
            }
            BigDecimal newest = history.get(0).price();
            BigDecimal oldest = history.get(history.size() - 1).price();
            if (oldest.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            double pctChange = newest.subtract(oldest)
                    .divide(oldest, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100.0;
            volatilities.add(new ItemVolatility(item, pctChange));

            // Displacement from current price vs oldest historical price (proxy for base)
            double displacement = newest.subtract(oldest)
                    .divide(oldest, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100.0;
            undersells.add(new ItemVolatility(item, displacement));
        }

        // Sort: most volatile = highest absolute % change
        volatilities.sort(Comparator.comparingDouble((ItemVolatility v) -> Math.abs(v.pctChange())).reversed());
        undersells.sort(Comparator.comparingDouble(v -> v.pctChange())); // most negative = most undersold

        // ── Aggregate economy volatility ───────────────────────────────────
        // Standard deviation of all items' 24h price changes — the single most important
        // economy health indicator (confirmed across 23 simulation runs)
        double avgVolatility = 0.0;
        if (!volatilities.isEmpty()) {
            double sum = volatilities.stream().mapToDouble(ItemVolatility::pctChange).sum();
            double mean = sum / volatilities.size();
            double variance = volatilities.stream()
                    .mapToDouble(v -> {
                        double d = v.pctChange() / 100.0 - mean; // normalize to decimal
                        return d * d;
                    })
                    .sum() / volatilities.size();
            avgVolatility = Math.sqrt(variance);
        }

        // ── Render ─────────────────────────────────────────────────────────
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune Economy Health Report", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  Market: ").color(NamedTextColor.GRAY).append(marketStatus));

        sender.sendMessage(Component.text("  GDP: ").color(NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(gdp), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Debt: ").color(NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(totalDebt),
                        totalDebt.compareTo(BigDecimal.ZERO) > 0 ? NamedTextColor.RED : NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Debt/GDP: ").color(NamedTextColor.GRAY).append(debtGdpLabel));
        sender.sendMessage(Component.text("  Circuit Breaker: ").color(NamedTextColor.GRAY).append(tierBadge));
        if (!"NORMAL".equals(cb.tier()) && cb.debtGdpRatio() >= 0) {
            sender.sendMessage(Component.text("  Interest Rate: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f%% of normal", cb.interestMultiplier() * 100),
                            NamedTextColor.YELLOW)));
        }
        sender.sendMessage(Component.text("  Active Loans: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(activeLoans), NamedTextColor.AQUA)));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  24h Trade Mix: ").color(NamedTextColor.GRAY).append(buyLabel));
        sender.sendMessage(Component.text("  Avg Spread: ").color(NamedTextColor.GRAY).append(spreadLabel));
        sender.sendMessage(Component.text("  Volume Activity: ").color(NamedTextColor.GRAY).append(volLabel));
        sender.sendMessage(Component.text("  Inflation: ").color(NamedTextColor.GRAY).append(inflationLabel));

        // Aggregate economy volatility (std dev of all items' 24h price changes)
        NamedTextColor econVolColor;
        String econVolLabel;
        if (avgVolatility < 0.05) {
            econVolColor = NamedTextColor.GREEN;
            econVolLabel = "Stable";
        } else if (avgVolatility < 0.15) {
            econVolColor = NamedTextColor.YELLOW;
            econVolLabel = "Moderate";
        } else {
            econVolColor = NamedTextColor.RED;
            econVolLabel = "Unstable";
        }
        Component volBadge = Component.text(" [" + econVolLabel + "]", econVolColor);
        sender.sendMessage(Component.text("  Econ Volatility: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.4f", avgVolatility), econVolColor))
                .append(volBadge));

        // ── Volatile items ───────────────────────────────────────────────────
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Most Volatile Items (24h)", NamedTextColor.GOLD, TextDecoration.BOLD));
        renderVolatilityList(sender, volatilities, 5, false);

        // ── Most undersold items ────────────────────────────────────────────
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Most Undersold Items (below fair value)", NamedTextColor.GOLD, TextDecoration.BOLD));
        renderVolatilityList(sender, undersells, 5, true);

        sender.sendMessage(Component.empty());
    }

    private record ItemVolatility(ShopItem item, double pctChange) {}

    private void renderVolatilityList(CommandSender sender, List<ItemVolatility> items,
                                      int limit, boolean undersold) {
        if (items.isEmpty()) {
            sender.sendMessage(Component.text("  No data available yet.", NamedTextColor.GRAY));
            return;
        }
        for (int i = 0; i < Math.min(limit, items.size()); i++) {
            ItemVolatility v = items.get(i);
            NamedTextColor color;
            String arrow;
            if (undersold) {
                // Most undersold = most negative change
                if (v.pctChange() <= -20) { color = NamedTextColor.RED; arrow = "▼"; }
                else if (v.pctChange() <= -5) { color = NamedTextColor.YELLOW; arrow = "▼"; }
                else { color = NamedTextColor.GRAY; arrow = "—"; }
            } else {
                // Most volatile = largest absolute move either direction
                if (Math.abs(v.pctChange()) >= 20) { color = NamedTextColor.RED; arrow = (v.pctChange() > 0) ? "▲▲" : "▼▼"; }
                else if (Math.abs(v.pctChange()) >= 5) { color = NamedTextColor.YELLOW; arrow = (v.pctChange() > 0) ? "▲" : "▼"; }
                else { color = NamedTextColor.GRAY; arrow = "—"; }
            }
            String label = v.item().getDisplayNameOrMaterial();
            sender.sendMessage(Component.text("  " + arrow + " " + label + ": "
                    + String.format("%+.1f%%", v.pctChange()), color));
        }
    }

    // ─── Economy trend analysis ─────────────────────────────────────────────────
    @Command("autotune admin trend [days]")
    @Permission("autotune.admin")
    public void adminTrend(CommandSender sender, @Argument(value = "days") Optional<Integer> daysArg) {
        int days = daysArg.orElse(7);
        if (days < 1 || days > 90) {
            sender.sendMessage(Component.text("Days must be between 1 and 90.", NamedTextColor.RED));
            return;
        }

        Instant windowStart = Instant.now().minus(Duration.ofDays(days));
        List<EconomySnapshot> snapshots = metricsManager.getSnapshotsInWindow(windowStart, 80);

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Economy Trend — Last " + daysArg + " Day(s)", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(" (" + snapshots.size() + " snapshots)", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.empty());

        if (snapshots.size() < 2) {
            sender.sendMessage(Component.text("  Not enough snapshot data yet. Check back in a few hours.",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.empty());
            return;
        }

        EconomySnapshot first = snapshots.get(0);
        EconomySnapshot last = snapshots.get(snapshots.size() - 1);

        // Helper: compute percent change, handle zero base
        java.util.function.BiFunction<BigDecimal, BigDecimal, Double> pctChange = (start, end) -> {
            if (start.compareTo(BigDecimal.ZERO) == 0) return end.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : 100.0;
            return end.subtract(start).divide(start, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        };

        // GDP trend
        BigDecimal gdpStart = first.gdp();
        BigDecimal gdpEnd = last.gdp();
        double gdpPct = pctChange.apply(gdpStart, gdpEnd);
        NamedTextColor gdpColor = gdpPct > 5 ? NamedTextColor.GREEN : gdpPct < -5 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        String gdpArrow = gdpPct > 1 ? "↑" : gdpPct < -1 ? "↓" : "—";
        sender.sendMessage(Component.text("  GDP", NamedTextColor.GRAY)
                .append(Component.text("  " + gdpArrow + " " + configManager.formatCurrency(gdpStart)
                        + " → " + configManager.formatCurrency(gdpEnd), gdpColor))
                .append(Component.text("  (" + String.format("%+.1f%%", gdpPct) + ")", NamedTextColor.DARK_GRAY)));

        // Debt trend
        BigDecimal debtStart = first.totalDebt();
        BigDecimal debtEnd = last.totalDebt();
        double debtPct = pctChange.apply(debtStart, debtEnd);
        NamedTextColor debtColor = debtPct < -5 ? NamedTextColor.GREEN : debtPct > 5 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        String debtArrow = debtPct > 1 ? "↑" : debtPct < -1 ? "↓" : "—";
        sender.sendMessage(Component.text("  Debt", NamedTextColor.GRAY)
                .append(Component.text("  " + debtArrow + " " + configManager.formatCurrency(debtStart)
                        + " → " + configManager.formatCurrency(debtEnd), debtColor))
                .append(Component.text("  (" + String.format("%+.1f%%", debtPct) + ")", NamedTextColor.DARK_GRAY)));

        // D/G ratio trend
        double dgStart = gdpStart.compareTo(BigDecimal.ZERO) > 0
                ? debtStart.divide(gdpStart, 4, RoundingMode.HALF_UP).doubleValue() : 0;
        double dgEnd = gdpEnd.compareTo(BigDecimal.ZERO) > 0
                ? debtEnd.divide(gdpEnd, 4, RoundingMode.HALF_UP).doubleValue() : 0;
        double dgDelta = dgEnd - dgStart;
        NamedTextColor dgColor = dgEnd < 3 ? NamedTextColor.GREEN : dgEnd < 10 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        String dgArrow = dgDelta > 0.1 ? "↑" : dgDelta < -0.1 ? "↓" : "—";
        sender.sendMessage(Component.text("  D/G Ratio", NamedTextColor.GRAY)
                .append(Component.text("  " + dgArrow + " " + String.format("%.2fx", dgStart)
                        + " → " + String.format("%.2fx", dgEnd), dgColor))
                .append(Component.text("  (" + String.format("%+.2f", dgDelta) + ")", NamedTextColor.DARK_GRAY)));

        // Volume trend
        BigDecimal volStart = first.transactionVolume();
        BigDecimal volEnd = last.transactionVolume();
        double volPct = pctChange.apply(volStart, volEnd);
        NamedTextColor volColor = volPct > 5 ? NamedTextColor.GREEN : volPct < -5 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        String volArrow = volPct > 1 ? "↑" : volPct < -1 ? "↓" : "—";
        sender.sendMessage(Component.text("  Volume", NamedTextColor.GRAY)
                .append(Component.text("  " + volArrow + " " + formatCompact(volStart)
                        + " → " + formatCompact(volEnd), volColor))
                .append(Component.text("  (" + String.format("%+.1f%%", volPct) + ")", NamedTextColor.DARK_GRAY)));

        // Avg price change (inflation) trend
        BigDecimal avgChgStart = first.averagePriceChange();
        BigDecimal avgChgEnd = last.averagePriceChange();
        NamedTextColor inflColor = avgChgEnd.compareTo(BigDecimal.valueOf(1)) > 0 ? NamedTextColor.RED
                : avgChgEnd.compareTo(BigDecimal.valueOf(-1)) < 0 ? NamedTextColor.AQUA : NamedTextColor.GREEN;
        String inflArrow = avgChgEnd.compareTo(avgChgStart) > 0 ? "↑" : avgChgEnd.compareTo(avgChgStart) < 0 ? "↓" : "—";
        sender.sendMessage(Component.text("  Avg Price Chg", NamedTextColor.GRAY)
                .append(Component.text("  " + inflArrow + " " + String.format("%+.2f%%", avgChgStart.doubleValue())
                        + "/tick → " + String.format("%+.2f%%", avgChgEnd.doubleValue()) + "/tick", inflColor)));

        // Active loans trend
        int loansStart = first.activeLoans();
        int loansEnd = last.activeLoans();
        NamedTextColor loansColor = loansEnd > loansStart ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        sender.sendMessage(Component.text("  Active Loans", NamedTextColor.GRAY)
                .append(Component.text("  " + loansStart + " → " + loansEnd, loansColor)));

        // Player count trend
        int playersStart = first.playerCount();
        int playersEnd = last.playerCount();
        NamedTextColor playersColor = playersEnd > playersStart ? NamedTextColor.GREEN
                : playersEnd < playersStart ? NamedTextColor.RED : NamedTextColor.GRAY;
        sender.sendMessage(Component.text("  Online Players", NamedTextColor.GRAY)
                .append(Component.text("  " + playersStart + " → " + playersEnd, playersColor)));

        // Mini sparkline for GDP across window
        sender.sendMessage(Component.empty());
        StringBuilder bar = new StringBuilder();
        bar.append("  ").append(Component.text("GDP trajectory: ", NamedTextColor.DARK_GRAY));
        for (EconomySnapshot snap : snapshots) {
            BigDecimal snapDg = snap.gdp();
            NamedTextColor barColor;
            if (gdpEnd.compareTo(gdpStart) >= 0) {
                // Growing: green at end
                barColor = snap.equals(last) ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
            } else {
                // Shrinking: red at end
                barColor = snap.equals(last) ? NamedTextColor.RED : NamedTextColor.DARK_GRAY;
            }
            bar.append(Component.text("▬", barColor));
        }
        sender.sendMessage(Component.text(bar.toString()));

        // Circuit breaker proximity warning
        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();
        sender.sendMessage(Component.empty());
        if (cb.debtGdpRatio() < 0) {
            sender.sendMessage(Component.text("  Circuit Breaker: " + cb.tier() + " (no GDP data)", NamedTextColor.GRAY));
        } else {
            double breakerLimit = configManager.getConfig().loans().debtGdpTier3Ratio();
            double proximity = cb.debtGdpRatio() / breakerLimit;
            NamedTextColor cbColor = proximity < 0.3 ? NamedTextColor.GREEN
                    : proximity < 0.7 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            sender.sendMessage(Component.text("  Circuit Breaker: " + cb.tier()
                    + "  [" + String.format("%.2fx", cb.debtGdpRatio()) + " / "
                    + String.format("%.1fx", breakerLimit) + " limit]",
                    cbColor));
            if (proximity >= 0.7) {
                sender.sendMessage(Component.text("  ⚠️  Debt/GDP is >70% of breaker limit — monitor closely.",
                        NamedTextColor.RED));
            }
        }

        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin stats")
    @Permission("autotune.admin")
    public void adminStats(CommandSender sender) {
        List<ShopItem> allItems = shopManager.getAllItems();
        BigDecimal totalMarketCap = BigDecimal.ZERO;
        int updatedItems = 0;
        double avgSpread = 0;
        int spreadCount = 0;

        for (ShopItem item : allItems) {
            BigDecimal price = marketEngine.getCurrentPrice(item.id());
            MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());
            totalMarketCap = totalMarketCap.add(price);
            updatedItems++;
            avgSpread += spread.bpd().doubleValue() + spread.spd().doubleValue();
            spreadCount += 2;
        }

        double globalMult = marketEngine.getGlobalVolumeMultiplier();
        String volLabel = globalMult > 1.05 ? "High" : (globalMult < 0.95 ? "Low" : "Normal");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Market Statistics", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  Items tracked: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(updatedItems), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Total market cap: ").color(NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(totalMarketCap), NamedTextColor.GREEN)));
        if (spreadCount > 0) {
            sender.sendMessage(Component.text("  Avg spread (BPD+SPD): ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2f%%", (avgSpread / spreadCount) * 100), NamedTextColor.YELLOW)));
        }
        sender.sendMessage(Component.text("  Volume activity: ").color(NamedTextColor.GRAY)
                .append(Component.text(volLabel + " (" + String.format("%.2fx", globalMult) + ")",
                        globalMult > 1.05 ? NamedTextColor.RED : (globalMult < 0.95 ? NamedTextColor.YELLOW : NamedTextColor.GREEN))));
        sender.sendMessage(Component.text("  Inflation: ").color(NamedTextColor.GRAY)
                .append(Component.text(metricsManager.getInflationLabel(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin reload")
    @Permission("autotune.admin")
    public void adminReload(CommandSender sender) {
        try {
            plugin.reload();
            sender.sendMessage(Component.text("Auto-Tune config and caches reloaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    @Command("autotune admin transaction-min")
    @Permission("autotune.admin")
    public void transactionMin(CommandSender sender) {
        AutoTuneConfig.EconomyConfig ec = configManager.getConfig().economy();
        sender.sendMessage(Component.text("═══ Minimum Transaction Settings ═══", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Min buy quantity:  ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(ec.minBuyQuantity()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Min sell quantity: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(ec.minSellQuantity()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Min buy value:     ", NamedTextColor.GRAY)
                .append(Component.text(formatMinValue(ec.minBuyValue()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Min sell value:   ", NamedTextColor.GRAY)
                .append(Component.text(formatMinValue(ec.minSellValue()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Edit economy.min-*-quantity and economy.min-*-value in config.yml, "
                + "then run /at admin reload.", NamedTextColor.DARK_GRAY));
    }

    private String formatMinValue(double val) {
        return val > 0 ? configManager.formatCurrency(val) : "disabled";
    }

    @Command("autotune admin transactions [player]")
    @Permission("autotune.admin")
    public void adminTransactions(CommandSender sender, @Argument(value = "player", suggestions = "minecraft-player") Optional<String> playerNameArg) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be used as a player.", NamedTextColor.RED));
            return;
        }

        UUID filterUuid = null;

        if (!playerNameArg.isEmpty() && !playerNameArg.get().isBlank()) {
            String playerName = playerNameArg.get();
            // Look up the player's UUID from their name
            org.bukkit.OfflinePlayer offlineTarget = org.bukkit.Bukkit.getOfflinePlayerIfCached(playerName);
            if (offlineTarget == null) {
                sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
                return;
            }
            filterUuid = offlineTarget.getUniqueId();
            sender.sendMessage(Component.text("Opening transaction history for " + offlineTarget.getName() + "...", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Opening full transaction history...", NamedTextColor.GRAY));
        }

        // Open ADMIN-mode transaction history GUI, optionally filtered to one player
        new com.noahblclarkson.autotune.ui.TransactionHistoryGui(
                plugin, player,
                com.noahblclarkson.autotune.ui.TransactionHistoryGui.Mode.ADMIN,
                filterUuid
        ).open();
    }

    // ─── Market freeze subcommand ──────────────────────────────────────────────

    @Command("autotune admin market")
    @Permission("autotune.admin")
    public void marketHelp(CommandSender sender) {
        boolean frozen = marketEngine.isFrozen();
        Component status = frozen
                ? Component.text("FROZEN", NamedTextColor.RED)
                .append(Component.text(" — prices will not update", NamedTextColor.GRAY))
                : Component.text("Active", NamedTextColor.GREEN)
                .append(Component.text(" — prices update normally", NamedTextColor.GRAY));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Market Engine: ", NamedTextColor.GOLD).append(status));
        sender.sendMessage(Component.text("Use /at admin market freeze to toggle.", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin market freeze")
    @Permission("autotune.admin")
    public void marketFreeze(CommandSender sender) {
        boolean nowFrozen = !marketEngine.isFrozen();
        marketEngine.setFrozen(nowFrozen);

        if (nowFrozen) {
            sender.sendMessage(Component.text("Market frozen. Prices will not update until unfrozen.",
                    NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Market unfrozen. Prices will resume updating.",
                    NamedTextColor.GREEN));
        }
    }

    // ─── Price override subcommands ───────────────────────────────────────────

    @Suggestions("price-override-material")
    public List<String> suggestMaterials(CommandContext<?> ctx, String input) {
        return shopManager.getAllItems().stream()
                .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.contains(input.toLowerCase(Locale.ROOT)))
                .limit(20)
                .toList();
    }

    @Command("autotune admin price set <material> <price> [hours]")
    @Permission("autotune.admin")
    public void priceSet(
            CommandSender sender,
            @Argument("material") String materialName,
            @Argument("price") BigDecimal price,
            @Argument("hours") Optional<Integer> hoursArg
    ) {
        Integer hours = hoursArg.orElse(null);
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("Price must be greater than 0.", NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        ShopItem item;
        if (shopItem.isEmpty()) {
            item = shopManager.addItem(mat, price, "admin");
        } else {
            item = shopItem.get();
        }

        Instant expiresAt = null;
        if (hours != null && hours > 0) {
            expiresAt = Instant.now().plusSeconds(hours * 3600L);
        }

        UUID setBy = (sender instanceof Player p) ? p.getUniqueId() : null;
        overrideRepo.setOverride(item.id(), price, expiresAt, setBy);
        marketEngine.refreshOverrideCache();

        String expiryStr = (hours != null && hours > 0) ? " for " + hours + "h" : " (permanent)";
        sender.sendMessage(Component.text("Override set for " + item.getDisplayNameOrMaterial()
                + ": " + configManager.formatCurrency(price) + expiryStr, NamedTextColor.GREEN));
    }

    @Command("autotune admin price remove <material>")
    @Permission("autotune.admin")
    public void priceRemove(CommandSender sender, @Argument("material") String materialName) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<PriceOverride> existing = marketEngine.getOverride(shopItem.get().id());
        if (existing.isEmpty()) {
            sender.sendMessage(Component.text("No active override for " + mat.name() + ".", NamedTextColor.YELLOW));
            return;
        }

        overrideRepo.removeOverride(shopItem.get().id());
        marketEngine.refreshOverrideCache();
        sender.sendMessage(Component.text("Override removed for " + mat.name() + ".", NamedTextColor.GREEN));
    }

    @Command("autotune admin price list")
    @Permission("autotune.admin")
    public void priceList(CommandSender sender) {
        Map<Integer, PriceOverride> all = overrideRepo.getAllOverrides();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No price overrides active.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Price Overrides", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (Map.Entry<Integer, PriceOverride> entry : all.entrySet()) {
            int itemId = entry.getKey();
            PriceOverride over = entry.getValue();

            Optional<ShopItem> item = shopManager.getItemById(itemId);
            String itemName = item.map(ShopItem::getDisplayNameOrMaterial).orElse("#" + itemId);
            String priceStr = configManager.formatCurrency(over.price());
            String expiryStr = over.formatExpiry();
            String expiredLabel = over.isExpired() ? " [EXPIRED]" : "";

            Component line = Component.text("  " + itemName + ": " + priceStr
                    + " (expires: " + expiryStr + ")" + expiredLabel,
                    over.isExpired() ? NamedTextColor.GRAY : NamedTextColor.AQUA);
            sender.sendMessage(line);
        }

        sender.sendMessage(Component.empty());
    }

    // ─── Bulk price CSV export / import ───────────────────────────────────────

    private static final String CSV_HEADER =
            "material,display_name,section,price,price_floor,price_ceiling,spread_override,max_change_override,price_frozen";

    @Command("autotune admin prices export")
    @Permission("autotune.admin")
    public void pricesExport(CommandSender sender) {
        String filename = "autotune-prices-" + LocalDate.now() + ".csv";
        exportPrices(sender, filename);
    }

    @Command("autotune admin prices export <filename>")
    @Permission("autotune.admin")
    public void pricesExportFile(CommandSender sender, @Argument("filename") String filename) {
        exportPrices(sender, filename);
    }

    private void exportPrices(CommandSender sender, String filename) {
        List<ShopItem> items = shopManager.getAllItems();
        if (items.isEmpty()) {
            sender.sendMessage(Component.text("No items in shop to export.", NamedTextColor.YELLOW));
            return;
        }

        Path path = Paths.get(filename);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(CSV_HEADER);
            writer.newLine();

            for (ShopItem item : items) {
                String line = String.join(",",
                        escape(item.material().name()),
                        escape(item.displayName()),
                        escape(item.section()),
                        item.price().toPlainString(),
                        nullOrEmpty(item.priceFloorOverride()),
                        nullOrEmpty(item.priceCeilingOverride()),
                        nullOrEmpty(item.baseSpreadOverride()),
                        nullOrEmpty(item.maxPriceChangeOverride()),
                        String.valueOf(item.priceFrozen())
                );
                writer.write(line);
                writer.newLine();
            }

            sender.sendMessage(Component.text("✅ Exported " + items.size() + " items to " + filename,
                    NamedTextColor.GREEN));
            sender.sendMessage(Component.text("  Edit in a spreadsheet, then import with /at admin prices import <filename>",
                    NamedTextColor.GRAY));
        } catch (IOException e) {
            sender.sendMessage(Component.text("❌ Export failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    @Command("autotune admin prices import <filename>")
    @Permission("autotune.admin")
    public void pricesImport(CommandSender sender, @Argument("filename") String filename) {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) {
            sender.sendMessage(Component.text("File not found: " + filename, NamedTextColor.RED));
            sender.sendMessage(Component.text("  Put the CSV in the server root directory (where you run the JAR).",
                    NamedTextColor.GRAY));
            return;
        }

        // Build material → ShopItem map for fast lookup
        Map<String, ShopItem> byMaterial = shopManager.getAllItems().stream()
                .collect(Collectors.toMap(
                        it -> it.material().name().toLowerCase(Locale.ROOT),
                        it -> it,
                        (a, b) -> a
                ));

        int updated = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String header = reader.readLine();
            if (header == null) {
                sender.sendMessage(Component.text("Empty CSV file.", NamedTextColor.RED));
                return;
            }

            String line;
            int rowNum = 1; // already read header
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank() || line.startsWith("#")) continue;

                String[] cols = parseCsvLine(line);
                if (cols.length < 4) {
                    errors++;
                    errorLines.add("row " + rowNum + ": too few columns (need at least material + price)");
                    continue;
                }

                String materialName = cols[0].trim().toUpperCase(Locale.ROOT);
                ShopItem item = byMaterial.get(materialName.toLowerCase(Locale.ROOT));

                if (item == null) {
                    skipped++;
                    continue; // material not in shop — skip silently
                }

                boolean changed = false;

                // Column 3 = price (index 3)
                if (cols.length > 3 && !cols[3].isBlank()) {
                    try {
                        BigDecimal newPrice = new BigDecimal(cols[3].trim());
                        if (newPrice.compareTo(BigDecimal.ZERO) > 0) {
                            shopManager.setPrice(item.id(), newPrice);
                            changed = true;
                        }
                    } catch (NumberFormatException ignored) {
                        // skip invalid price
                    }
                }

                // Column 4 = price_floor (index 4)
                if (cols.length > 4 && !cols[4].isBlank()) {
                    try {
                        BigDecimal floor = new BigDecimal(cols[4].trim());
                        shopManager.setPriceFloorOverride(item.id(), floor);
                        changed = true;
                    } catch (NumberFormatException ignored) {
                        // skip invalid floor
                    }
                } else if (cols.length > 4 && cols[4].isBlank()) {
                    shopManager.setPriceFloorOverride(item.id(), null); // clear
                }

                // Column 5 = price_ceiling (index 5)
                if (cols.length > 5 && !cols[5].isBlank()) {
                    try {
                        BigDecimal ceiling = new BigDecimal(cols[5].trim());
                        shopManager.setPriceCeilingOverride(item.id(), ceiling);
                        changed = true;
                    } catch (NumberFormatException ignored) {
                        // skip invalid ceiling
                    }
                } else if (cols.length > 5 && cols[5].isBlank()) {
                    shopManager.setPriceCeilingOverride(item.id(), null); // clear
                }

                // Column 6 = spread_override (index 6)
                if (cols.length > 6 && !cols[6].isBlank()) {
                    try {
                        Double spread = Double.parseDouble(cols[6].trim());
                        shopManager.setBaseSpreadOverride(item.id(), spread);
                        changed = true;
                    } catch (NumberFormatException ignored) {
                        // skip invalid spread
                    }
                } else if (cols.length > 6 && cols[6].isBlank()) {
                    shopManager.setBaseSpreadOverride(item.id(), null);
                }

                // Column 7 = max_change_override (index 7)
                if (cols.length > 7 && !cols[7].isBlank()) {
                    try {
                        Double maxChange = Double.parseDouble(cols[7].trim());
                        shopManager.setMaxPriceChangeOverride(item.id(), maxChange);
                        changed = true;
                    } catch (NumberFormatException ignored) {
                        // skip invalid max change
                    }
                } else if (cols.length > 7 && cols[7].isBlank()) {
                    shopManager.setMaxPriceChangeOverride(item.id(), null);
                }

                // Column 8 = price_frozen (index 8)
                if (cols.length > 8 && !cols[8].isBlank()) {
                    boolean frozen = cols[8].trim().equalsIgnoreCase("true")
                            || cols[8].trim().equalsIgnoreCase("1")
                            || cols[8].trim().equalsIgnoreCase("yes");
                    shopManager.setPriceFrozen(item.id(), frozen);
                    changed = true;
                }

                if (changed) updated++;
            }

            // Refresh market engine cache so new prices/spreads take effect immediately
            marketEngine.refreshOverrideCache();

            Component summary = Component.text("Import complete: ", NamedTextColor.GREEN)
                    .append(Component.text(updated + " updated", NamedTextColor.AQUA))
                    .append(Component.text(", " + skipped + " not-in-shop (skipped)", NamedTextColor.GRAY))
                    .append(Component.text(", " + errors + " parse errors", NamedTextColor.YELLOW));
            sender.sendMessage(Component.empty());
            sender.sendMessage(summary);

            if (!errorLines.isEmpty()) {
                sender.sendMessage(Component.text("  Errors: " + String.join("; ", errorLines.subList(0, Math.min(3, errorLines.size()))),
                        NamedTextColor.YELLOW));
                if (errorLines.size() > 3) {
                    sender.sendMessage(Component.text("  ...and " + (errorLines.size() - 3) + " more.",
                            NamedTextColor.YELLOW));
                }
            }

            sender.sendMessage(Component.text("Run /at admin reload to repopulate shop cache.", NamedTextColor.GRAY));

        } catch (IOException e) {
            sender.sendMessage(Component.text("❌ Import failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    // ─── Per-item config override subcommands ──────────────────────────────────

    @Command("autotune admin item spread <material> <value>")
    @Permission("autotune.admin")
    public void itemSpread(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        if (value <= 0 || value > 1.0) {
            sender.sendMessage(Component.text("Base spread must be between 0.01 and 1.0 (e.g. 0.20 = 20%).",
                    NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setBaseSpreadOverride(item.id(), value);
        sender.sendMessage(Component.text("Base spread override for " + item.getDisplayNameOrMaterial()
                + " set to " + String.format("%.2f%%", value * 100), NamedTextColor.GREEN));
    }

    @Command("autotune admin item maxchange <material> <value>")
    @Permission("autotune.admin")
    public void itemMaxChange(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        if (value <= 0 || value > 50.0) {
            sender.sendMessage(Component.text("Max price change must be between 0.01 and 50.0 (percent).",
                    NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setMaxPriceChangeOverride(item.id(), value);
        sender.sendMessage(Component.text("Max price change override for " + item.getDisplayNameOrMaterial()
                + " set to " + String.format("%.2f%%", value), NamedTextColor.GREEN));
    }

    @Command("autotune admin item floor <material> <value>")
    @Permission("autotune.admin")
    public void itemFloor(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        if (value < 0) {
            // -1 sentinel = clear floor
            shopManager.setPriceFloorOverride(item.id(), null);
            sender.sendMessage(Component.text("Price floor cleared for " + item.getDisplayNameOrMaterial()
                    + ". Using free market pricing.", NamedTextColor.GREEN));
            return;
        }

        if (value < 0.01) {
            sender.sendMessage(Component.text("Price floor must be at least $0.01. Use -1 to clear.",
                    NamedTextColor.RED));
            return;
        }

        BigDecimal floor = BigDecimal.valueOf(value);
        shopManager.setPriceFloorOverride(item.id(), floor);
        sender.sendMessage(Component.text("Price floor for " + item.getDisplayNameOrMaterial()
                + " set to " + configManager.formatCurrency(floor)
                + " — buy/sell prices will not go below this.", NamedTextColor.GREEN));
    }

    @Command("autotune admin item ceiling <material> <value>")
    @Permission("autotune.admin")
    public void itemCeiling(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        if (value < 0) {
            // -1 sentinel = clear ceiling
            shopManager.setPriceCeilingOverride(item.id(), null);
            sender.sendMessage(Component.text("Price ceiling cleared for " + item.getDisplayNameOrMaterial()
                    + ". Using free market pricing.", NamedTextColor.GREEN));
            return;
        }

        if (value < 0.01) {
            sender.sendMessage(Component.text("Price ceiling must be at least $0.01. Use -1 to clear.",
                    NamedTextColor.RED));
            return;
        }

        BigDecimal ceiling = BigDecimal.valueOf(value);
        shopManager.setPriceCeilingOverride(item.id(), ceiling);
        sender.sendMessage(Component.text("Price ceiling for " + item.getDisplayNameOrMaterial()
                + " set to " + configManager.formatCurrency(ceiling)
                + " — buy/sell prices will not exceed this.", NamedTextColor.GREEN));
    }

    @Command("autotune admin item info <material>")
    @Permission("autotune.admin")
    public void itemInfo(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        BigDecimal currentPrice = marketEngine.getCurrentPrice(item.id());
        MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());
        double globalMaxChange = configManager.getConfig().economy().maxPriceChangePercent();
        double globalBaseSpread = configManager.getConfig().economy().spread().baseSpread();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(item.getDisplayNameOrMaterial(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" (" + item.section() + ")", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("  Price: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(currentPrice), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  Spread: ", NamedTextColor.GRAY)
                .append(Component.text("BPD " + String.format("%.2f%%", spread.bpd().doubleValue() * 100)
                        + " / SPD " + String.format("%.2f%%", spread.spd().doubleValue() * 100), NamedTextColor.AQUA)));

        // Base spread override
        String spreadStr = item.baseSpreadOverride() != null
                ? String.format("%.2f%%", item.baseSpreadOverride() * 100) + " (override)"
                : String.format("%.2f%%", globalBaseSpread * 100) + " (global)";
        sender.sendMessage(Component.text("  Base Spread: ", NamedTextColor.GRAY)
                .append(Component.text(spreadStr,
                        item.baseSpreadOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Max price change override
        String maxChangeStr = item.maxPriceChangeOverride() != null
                ? String.format("%.2f%%", item.maxPriceChangeOverride()) + " (override)"
                : String.format("%.2f%%", globalMaxChange) + " (global)";
        sender.sendMessage(Component.text("  Max Change: ", NamedTextColor.GRAY)
                .append(Component.text(maxChangeStr,
                        item.maxPriceChangeOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Price floor override
        String floorStr = item.priceFloorOverride() != null
                ? configManager.formatCurrency(item.priceFloorOverride()) + " (override)"
                : "None";
        sender.sendMessage(Component.text("  Price Floor: ", NamedTextColor.GRAY)
                .append(Component.text(floorStr,
                        item.priceFloorOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Price ceiling override
        String ceilingStr = item.priceCeilingOverride() != null
                ? configManager.formatCurrency(item.priceCeilingOverride()) + " (override)"
                : "None";
        sender.sendMessage(Component.text("  Price Ceiling: ", NamedTextColor.GRAY)
                .append(Component.text(ceilingStr,
                        item.priceCeilingOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Price freeze status
        sender.sendMessage(Component.text("  Price Freeze: ", NamedTextColor.GRAY)
                .append(Component.text(item.priceFrozen() ? "FROZEN (no price updates)" : "Normal",
                        item.priceFrozen() ? NamedTextColor.RED : NamedTextColor.GREEN)));

        // Item tier
        ItemTier effectiveTier = item.effectiveTier();
        NamedTextColor tierColor = switch (effectiveTier) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
        String tierLabel = item.tier() != null
                ? effectiveTier.name() + " (override)"
                : effectiveTier.name() + " (default — " + item.material().name() + ")";
        sender.sendMessage(Component.text("  Tier: ", NamedTextColor.GRAY)
                .append(Component.text(tierLabel, tierColor)));

        // Price override
        Optional<PriceOverride> priceOverride = marketEngine.getOverride(item.id());
        if (priceOverride.isPresent()) {
            PriceOverride over = priceOverride.get();
            sender.sendMessage(Component.text("  Price Override: ", NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(over.price())
                            + " (expires: " + over.formatExpiry() + ")", NamedTextColor.YELLOW)));
        }

        sender.sendMessage(Component.text("  Buyable: ", NamedTextColor.GRAY)
                .append(Component.text(shopManager.isBuyable(item) ? "Yes" : "No",
                        shopManager.isBuyable(item) ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin item reset <material>")
    @Permission("autotune.admin")
    public void itemReset(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setBaseSpreadOverride(item.id(), null);
        shopManager.setMaxPriceChangeOverride(item.id(), null);
        shopManager.setPriceFloorOverride(item.id(), null);
        shopManager.setPriceCeilingOverride(item.id(), null);
        shopManager.setPriceFrozen(item.id(), false);
        shopManager.setTier(item.id(), null);
        sender.sendMessage(Component.text("All per-item overrides cleared for "
                + item.getDisplayNameOrMaterial() + ". Using global config values.", NamedTextColor.GREEN));
    }

    @Command("autotune admin item freeze <material>")
    @Permission("autotune.admin")
    public void itemFreeze(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setPriceFrozen(item.id(), true);

        // Warn about spread blowout risk for high-tier items (simulation-proven)
        ItemTier tier = item.effectiveTier();
        boolean highValueWarning = (tier == ItemTier.LEGENDARY || tier == ItemTier.EPIC);

        Component msg = Component.text("Price updates frozen for " + item.getDisplayNameOrMaterial()
                + ". The price will stay at " + configManager.formatCurrency(marketEngine.getCurrentPrice(item.id()))
                + " until you unfreeze it. Spreads continue to update — item remains tradeable.", NamedTextColor.YELLOW);

        sender.sendMessage(msg);

        if (highValueWarning) {
            sender.sendMessage(Component.text("⚠ Warning: " + tier.name() + "-tier items like "
                    + item.getDisplayNameOrMaterial() + " can see spreads blow out 3-4× when frozen "
                    + "(price can't move → engine compensates with wider spreads). "
                    + "Consider using InsiderTrader instead for event pricing stability.", NamedTextColor.RED));
        }
    }

    @Command("autotune admin item unfreeze <material>")
    @Permission("autotune.admin")
    public void itemUnfreeze(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setPriceFrozen(item.id(), false);
        sender.sendMessage(Component.text("Price updates unfrozen for " + item.getDisplayNameOrMaterial()
                + ". Normal price discovery resumes on the next market tick.", NamedTextColor.GREEN));
    }

    @Command("autotune admin item tier <material> <tier>")
    @Permission("tier.admin")
    public void itemTier(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument(value = "tier", suggestions = "tier-name") String tierName
    ) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();

        if (tierName.equalsIgnoreCase("clear") || tierName.equalsIgnoreCase("none")) {
            shopManager.setTier(item.id(), null);
            ItemTier defaultTier = item.effectiveTier();
            sender.sendMessage(Component.text("Tier cleared for " + item.getDisplayNameOrMaterial()
                    + ". Reverting to default tier: " + defaultTier.name(), NamedTextColor.YELLOW));
            return;
        }

        ItemTier tier;
        try {
            tier = ItemTier.valueOf(tierName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown tier: " + tierName + ". Valid tiers: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, or 'clear' to remove override.", NamedTextColor.RED));
            return;
        }

        shopManager.setTier(item.id(), tier);

        NamedTextColor tierColor = switch (tier) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };

        sender.sendMessage(Component.text("Tier set to ", NamedTextColor.GRAY)
                .append(Component.text(tier.name(), tierColor))
                .append(Component.text(" for " + item.getDisplayNameOrMaterial()
                        + ". Spread: ×" + tier.spreadMultiplier
                        + ", MaxPriceChange: ×" + tier.maxPriceChangeMultiplier
                        + ". Reload to apply.", NamedTextColor.GRAY)));
    }

    @Command("autotune admin exchange")
    @Permission("autotune.admin")
    public void adminExchange(CommandSender sender) {
        if (!exchangeRateService.isEnabled()) {
            sender.sendMessage(Component.text("Exchange rates are disabled. "
                    + "Enable exchange-rate in config.yml and ensure price-reporter is configured.", NamedTextColor.RED));
            return;
        }

        var rates = exchangeRateService.getExchangeRates();
        var localRate = exchangeRateService.getLocalExchangeRate();
        var lastFetched = exchangeRateService.lastFetchedAt();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Cross-Server Exchange Rates", NamedTextColor.GOLD, TextDecoration.BOLD));

        if (lastFetched == null) {
            sender.sendMessage(Component.text("  No data fetched yet.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("  Last updated: ").color(NamedTextColor.GRAY)
                    .append(Component.text(DATE_FORMAT.format(lastFetched), NamedTextColor.WHITE)));
        }

        // Local server's own rate
        if (localRate != null) {
            double pct = (localRate.rate() - 1.0) * 100;
            NamedTextColor localColor = Math.abs(pct) <= 5 ? NamedTextColor.GREEN
                    : pct > 0 ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
            sender.sendMessage(Component.text("  Your server: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format(Locale.ROOT, "%.2fx", localRate.rate()), localColor))
                    .append(Component.text("  (" + localRate.label() + ")", NamedTextColor.WHITE)));
        } else {
            sender.sendMessage(Component.text("  Your server: ").color(NamedTextColor.GRAY)
                    .append(Component.text("No submission data — ensure price-reporter is configured and has submitted.", NamedTextColor.GRAY)));
        }

        if (rates.isEmpty()) {
            sender.sendMessage(Component.text("  No other servers have submitted data yet.", NamedTextColor.GRAY));
            sender.sendMessage(Component.empty());
            return;
        }

        sender.sendMessage(Component.text("  Other servers:", NamedTextColor.YELLOW));
        int shown = 0;
        for (ExchangeRate rate : rates) {
            if (shown >= 10) {
                sender.sendMessage(Component.text("  ... and " + (rates.size() - 10) + " more servers.", NamedTextColor.GRAY));
                break;
            }
            double pct = (rate.rate() - 1.0) * 100;
            NamedTextColor color = Math.abs(pct) <= 5 ? NamedTextColor.GREEN
                    : pct > 0 ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
            sender.sendMessage(
                    Component.text("  " + rate.name(), NamedTextColor.WHITE)
                            .append(Component.text("  " + String.format(Locale.ROOT, "%.2fx", rate.rate()), color))
                            .append(Component.text("  (" + rate.playerCount() + " players)", NamedTextColor.GRAY))
            );
            shown++;
        }
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Rate > 1.0 = more expensive than global average; < 1.0 = cheaper.", NamedTextColor.DARK_GRAY));
    }

    @Command("autotune admin reseed-prices")
    @Permission("autotune.admin")
    public void adminReseedPrices(CommandSender sender) {
        if (!configManager.getConfig().economy().seedFromSharedPrices()) {
            sender.sendMessage(Component.text("economy.seed-from-shared-prices is not enabled in config.yml.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Enable it and run /at admin reload, then try again.", NamedTextColor.GRAY));
            return;
        }
        var cfg = configManager.getConfig().priceReporter();
        if (cfg.apiKey().isBlank() || cfg.serverId().isBlank()) {
            sender.sendMessage(Component.text("price-reporter api-key and server-id must be set in config.yml.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Fetching shared true prices…", NamedTextColor.YELLOW));
        priceReporter.seedPricesFromApi();
        sender.sendMessage(Component.text("Shared prices fetch started — check server log for results.", NamedTextColor.GREEN));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String formatDuration(Duration d) {
        if (d.toMinutes() < 1) return "<1 min";
        if (d.toMinutes() < 60) return d.toMinutes() + " min";
        if (d.toHours() < 24) return d.toHours() + " h";
        return d.toDays() + " d";
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private org.bukkit.Material matchMaterial(String name) {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(name);
        }
        return mat;
    }

    /** Formats a large number compactly: 1,234,567 → "1.23M", 123,456 → "123K" */
    private String formatCompact(BigDecimal amount) {
        if (amount == null) return "0";
        double v = amount.doubleValue();
        if (v >= 1_000_000_000) return String.format("%.1fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000);
        return configManager.formatCurrency(amount);
    }

    // ─── CSV helpers ───────────────────────────────────────────────────────────

    /** Escape a string for CSV: quotes around it if it contains comma/quote/newline. */
    private String escape(String s) {
        if (s == null) return "";
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n");
        return needsQuotes ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** Format a nullable BigDecimal as plain string, or empty string if null. */
    private String nullOrEmpty(BigDecimal val) {
        return val == null ? "" : val.toPlainString();
    }

    /** Format a nullable Double as plain string, or empty string if null. */
    private String nullOrEmpty(Double val) {
        return val == null ? "" : String.valueOf(val);
    }

    /** Parse a CSV line, respecting double-quote wrapping and comma/quote escaping. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field = new StringBuilder();
                } else {
                    field.append(c);
                }
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
}
