package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Market Report command — gives players and admins a weekly economy digest.
 *
 * /market-report          — player's personal weekly trading report
 * /market-report top      — top price movers this week (player-friendly)
 * /market-report admin    — economy-wide weekly digest (admin only)
 */
@Singleton
public class MarketReportCommand {

    private static final Logger log = Logger.getLogger(MarketReportCommand.class.getName());
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d")
            .withZone(ZoneId.systemDefault());
    private static final int TOP_MOVERS = 5;
    private static final int REPORT_WINDOW_DAYS = 7;

    private final AutoTune plugin;
    private final DatabaseManager databaseManager;
    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final LoanRepository loanRepository;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final MarketEventService marketEventService;
    private final EconomyMetricsManager economyMetricsManager;

    @Inject
    public MarketReportCommand(
            AutoTune plugin,
            DatabaseManager databaseManager,
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            LoanRepository loanRepository,
            ShopManager shopManager,
            MarketEngine marketEngine,
            MarketEventService marketEventService,
            EconomyMetricsManager economyMetricsManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.loanRepository = loanRepository;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.marketEventService = marketEventService;
        this.economyMetricsManager = economyMetricsManager;
    }

    // ─── /market-report ───────────────────────────────────────────────────────

    @Command("market-report")
    @Permission("autotune.market-report")
    public void onMarketReport(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        sendPlayerReport(player);
    }

    @Command("market-report top")
    @Permission("autotune.market-report")
    public void onMarketReportTop(CommandContext<CommandSender> ctx) {
        sendTopMoversReport(ctx.sender());
    }

    @Command("market-report admin")
    @Permission("autotune.admin")
    public void onMarketReportAdmin(CommandContext<CommandSender> ctx) {
        sendAdminReport(ctx.sender());
    }

    // ─── Player Report ───────────────────────────────────────────────────────

    private void sendPlayerReport(Player player) {
        Instant weekStart = startOfCurrentWeek();

        databaseManager.supplyAsync(() -> transactionRepository.findByPlayerSince(player.getUniqueId(), weekStart))
                .orTimeout(5, TimeUnit.SECONDS)
                .thenAcceptAsync(transactions -> {
                    Instant finalWeekStart = weekStart;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        renderPlayerReport(player, transactions, finalWeekStart);
                    });
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to load market report for " + player.getName(), ex);
                    player.sendMessage(Component.text("Failed to load market report. Try again.", NamedTextColor.RED));
                    return null;
                });
    }

    private void renderPlayerReport(Player player, List<Transaction> transactions, Instant weekStart) {
        Component header = Component.text("═══ ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("📊 ").color(NamedTextColor.AQUA))
                .append(Component.text("Your Market Report").color(NamedTextColor.WHITE))
                .append(Component.text(" ═══").color(NamedTextColor.GOLD));
        Component period = Component.text("Week of ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(DATE_FORMAT.format(weekStart) + " – " + DATE_FORMAT.format(Instant.now())).color(NamedTextColor.DARK_GRAY));

        player.sendMessage(header);
        player.sendMessage(period);
        player.sendMessage(Component.text(""));

        // ── Top movers (shared view) ─────────────────────────────────────
        Component topMoversHeader = Component.text("─── Top Price Movers (7d) ───").color(NamedTextColor.DARK_GRAY);
        player.sendMessage(topMoversHeader);

        List<ItemChange> changes = computePriceChangesSince(Instant.now().minus(Duration.ofDays(REPORT_WINDOW_DAYS)));
        if (changes.isEmpty()) {
            player.sendMessage(Component.text("Not enough data yet.").color(NamedTextColor.GRAY));
        } else {
            List<ItemChange> topGainers = changes.stream()
                    .filter(c -> c.pct() > 0)
                    .sorted(Comparator.comparingDouble(ItemChange::pct).reversed())
                    .limit(3)
                    .toList();

            for (ItemChange ic : topGainers) {
                player.sendMessage(formatMoverRow(ic, "▲", NamedTextColor.GREEN));
            }

            List<ItemChange> topLosers = changes.stream()
                    .filter(c -> c.pct() < 0)
                    .sorted(Comparator.comparingDouble(ItemChange::pct))
                    .limit(3)
                    .toList();

            for (ItemChange ic : topLosers) {
                player.sendMessage(formatMoverRow(ic, "▼", NamedTextColor.RED));
            }
        }

        if (transactions.isEmpty()) {
            Component noTrades = Component.text("You haven't traded this week yet.")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(" Visit ").color(NamedTextColor.GRAY))
                    .append(Component.text("/shop").color(NamedTextColor.AQUA))
                    .append(Component.text(" to get started!").color(NamedTextColor.GRAY));
            player.sendMessage(noTrades);
            Component footer = Component.text("Run ")
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("/market-report top").color(NamedTextColor.AQUA))
                    .append(Component.text(" for the full movers list.").color(NamedTextColor.DARK_GRAY));
            player.sendMessage(footer);
            return;
        }

        // ── Trading summary ──────────────────────────────────────────────
        int buyCount = 0, sellCount = 0;
        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal totalEarned = BigDecimal.ZERO;
        Map<Integer, BigDecimal> itemNetMap = new HashMap<>();

        for (Transaction tx : transactions) {
            if (tx.type() == Transaction.TransactionType.BUY) {
                buyCount++;
                totalSpent = totalSpent.add(tx.totalPrice());
            } else {
                sellCount++;
                totalEarned = totalEarned.add(tx.totalPrice());
            }
            itemNetMap.merge(tx.itemId(), tx.type() == Transaction.TransactionType.BUY
                    ? tx.totalPrice().negate() : tx.totalPrice(), BigDecimal::add);
        }

        BigDecimal netPosition = totalEarned.subtract(totalSpent);

        player.sendMessage(Component.text(""));
        Component summaryHeader = Component.text("─── Your Trading ───").color(NamedTextColor.DARK_GRAY);
        Component buys = Component.text("  Buys: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(buyCount)).color(NamedTextColor.GREEN))
                .append(Component.text(" trades · Spent: ").color(NamedTextColor.GRAY))
                .append(formatMoney(totalSpent, NamedTextColor.RED));
        Component sells = Component.text("  Sells: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(sellCount)).color(NamedTextColor.GOLD))
                .append(Component.text(" trades · Earned: ").color(NamedTextColor.GRAY))
                .append(formatMoney(totalEarned, NamedTextColor.GREEN));
        Component net = Component.text("  Net: ").color(NamedTextColor.GRAY)
                .append(formatMoney(netPosition.abs(), netPosition.compareTo(BigDecimal.ZERO) >= 0
                        ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(netPosition.compareTo(BigDecimal.ZERO) >= 0
                        ? Component.text(" profit", NamedTextColor.GREEN)
                        : Component.text(" spent", NamedTextColor.RED));

        player.sendMessage(summaryHeader);
        player.sendMessage(buys);
        player.sendMessage(sells);
        player.sendMessage(net);

        // ── Current prices of top traded items ────────────────────────────
        List<Component> priceSnippets = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : itemNetMap.entrySet()) {
            Optional<ShopItem> shopItem = shopManager.getAllItems().stream()
                    .filter(i -> i.id() == entry.getKey())
                    .findFirst();
            if (shopItem.isEmpty()) continue;

            ShopItem item = shopItem.get();
            BigDecimal buyPrice = marketEngine.getBuyPrice(item);
            BigDecimal sellPrice = marketEngine.getSellPrice(item);
            if (buyPrice == null || sellPrice == null) continue;

            BigDecimal mid = buyPrice.add(sellPrice).divide(TWO, 2, RoundingMode.HALF_UP);
            priceSnippets.add(Component.text("  ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(item.getDisplayNameOrMaterial()).color(NamedTextColor.WHITE))
                    .append(Component.text(" buy: ").color(NamedTextColor.GRAY))
                    .append(Component.text("$" + mid.toPlainString()).color(NamedTextColor.GREEN)));
            if (priceSnippets.size() >= 3) break;
        }

        if (!priceSnippets.isEmpty()) {
            Component pricesLabel = Component.text("─── Current Prices ───").color(NamedTextColor.DARK_GRAY);
            player.sendMessage(pricesLabel);
            for (Component snippet : priceSnippets) {
                player.sendMessage(snippet);
            }
        }

        Component footer = Component.text("Run ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("/market-report top").color(NamedTextColor.AQUA))
                .append(Component.text(" for the full movers list.").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(footer);
    }

    // ─── Top Movers ────────────────────────────────────────────────────────

    private void sendTopMoversReport(CommandSender sender) {
        Instant since = Instant.now().minus(Duration.ofDays(REPORT_WINDOW_DAYS));
        List<ItemChange> changes = computePriceChangesSince(since);

        Component header = Component.text("═══ ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("📈 ").color(NamedTextColor.AQUA))
                .append(Component.text("Top Price Movers").color(NamedTextColor.WHITE))
                .append(Component.text(" (7d) ═══").color(NamedTextColor.GOLD));
        sender.sendMessage(header);

        if (changes.isEmpty()) {
            sender.sendMessage(Component.text("Not enough price data yet. Check back tomorrow.")
                    .color(NamedTextColor.GRAY));
            return;
        }

        Component gainersHeader = Component.text("  Gainers").color(NamedTextColor.GREEN);
        sender.sendMessage(gainersHeader);

        List<ItemChange> gainers = changes.stream()
                .filter(c -> c.pct() > 0)
                .sorted(Comparator.comparingDouble(ItemChange::pct).reversed())
                .limit(TOP_MOVERS)
                .toList();

        for (ItemChange ic : gainers) {
            sender.sendMessage(formatMoverRow(ic, "▲", NamedTextColor.GREEN));
        }

        Component losersHeader = Component.text("  Losers").color(NamedTextColor.RED);
        sender.sendMessage(losersHeader);

        List<ItemChange> losers = changes.stream()
                .filter(c -> c.pct() < 0)
                .sorted(Comparator.comparingDouble(ItemChange::pct))
                .limit(TOP_MOVERS)
                .toList();

        for (ItemChange ic : losers) {
            sender.sendMessage(formatMoverRow(ic, "▼", NamedTextColor.RED));
        }

        // Active events
        List<MarketEvent> active = marketEventService.getActiveEvents();
        if (!active.isEmpty()) {
            sender.sendMessage(Component.text(""));
            Component eventsHeader = Component.text("  Active Events").color(NamedTextColor.YELLOW);
            sender.sendMessage(eventsHeader);
            for (MarketEvent evt : active) {
                String icon = eventIcon(evt.type());
                Component evtLine = Component.text("  " + icon + " ").color(NamedTextColor.GRAY)
                        .append(Component.text(evt.name()).color(NamedTextColor.YELLOW));
                sender.sendMessage(evtLine);
            }
        }
    }

    // ─── Admin Report ────────────────────────────────────────────────────────

    private void sendAdminReport(CommandSender sender) {
        Instant since = Instant.now().minus(Duration.ofDays(REPORT_WINDOW_DAYS));

        Optional<EconomySnapshot> snapshot = economyMetricsManager.getLatestSnapshot();

        Component header = Component.text("═══ ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("📊 ").color(NamedTextColor.AQUA))
                .append(Component.text("Economy Report (Admin)").color(NamedTextColor.WHITE))
                .append(Component.text(" (7d) ═══").color(NamedTextColor.GOLD));
        sender.sendMessage(header);
        sender.sendMessage(Component.text("Week of ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(DATE_FORMAT.format(since)).color(NamedTextColor.AQUA))
                .append(Component.text(" – ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(DATE_FORMAT.format(Instant.now())).color(NamedTextColor.AQUA)));
        sender.sendMessage(Component.text(""));

        // Health stats
        Component healthHeader = Component.text("─── Economy Health ───").color(NamedTextColor.DARK_GRAY);
        sender.sendMessage(healthHeader);

        if (snapshot.isPresent()) {
            EconomySnapshot s = snapshot.get();
            BigDecimal gdp = s.gdp();
            BigDecimal debt = s.totalDebt();
            BigDecimal dg = gdp.compareTo(BigDecimal.ZERO) > 0
                    ? debt.divide(gdp, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            NamedTextColor dgColor = dgRatioColor(dg);

            sender.sendMessage(Component.text("  GDP: ").color(NamedTextColor.GRAY)
                    .append(formatMoney(gdp, NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("  Debt: ").color(NamedTextColor.GRAY)
                    .append(formatMoney(debt, NamedTextColor.GOLD)));
            sender.sendMessage(Component.text("  D/G Ratio: ").color(NamedTextColor.GRAY)
                    .append(Component.text(formatDg(dg)).color(dgColor)));
            sender.sendMessage(Component.text("  Circuit Breaker: ").color(NamedTextColor.GRAY)
                    .append(Component.text(circuitBreakerStatus(dg)).color(circuitBreakerColor(dg))));
        } else {
            sender.sendMessage(Component.text("  No economy data available yet.").color(NamedTextColor.GRAY));
        }

        // Loan stats
        sender.sendMessage(Component.text(""));
        Component loansHeader = Component.text("─── Loans ───").color(NamedTextColor.DARK_GRAY);
        sender.sendMessage(loansHeader);

        List<Loan> activeLoans = loanRepository.findAllActive();
        long activeLoanCount = activeLoans.size();
        BigDecimal totalActiveDebt = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (Loan loan : activeLoans) {
            totalActiveDebt = totalActiveDebt.add(loan.currentBalance());
            totalInterest = totalInterest.add(loan.interestRate());
        }
        BigDecimal avgInterest = activeLoanCount > 0
                ? totalInterest.divide(BigDecimal.valueOf(activeLoanCount), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        sender.sendMessage(Component.text("  Active loans: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(activeLoanCount)).color(NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Total active debt: ").color(NamedTextColor.GRAY)
                .append(formatMoney(totalActiveDebt, NamedTextColor.GOLD)));
        if (activeLoanCount > 0) {
            sender.sendMessage(Component.text("  Avg interest rate: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.4f/day", avgInterest.doubleValue()))
                            .color(NamedTextColor.YELLOW)));
        }

        // Top movers
        sender.sendMessage(Component.text(""));
        List<ItemChange> changes = computePriceChangesSince(since);
        Component moversHeader = Component.text("─── Top Gainers ───").color(NamedTextColor.DARK_GRAY);
        sender.sendMessage(moversHeader);

        if (changes.isEmpty()) {
            sender.sendMessage(Component.text("Not enough data.").color(NamedTextColor.GRAY));
        } else {
            changes.stream()
                    .filter(c -> c.pct() > 0)
                    .sorted(Comparator.comparingDouble(ItemChange::pct).reversed())
                    .limit(5)
                    .forEach(ic -> sender.sendMessage(formatMoverRow(ic, "▲", NamedTextColor.GREEN)));
        }

        sender.sendMessage(Component.text("─── Top Losers ───").color(NamedTextColor.DARK_GRAY));
        changes.stream()
                .filter(c -> c.pct() < 0)
                .sorted(Comparator.comparingDouble(ItemChange::pct))
                .limit(5)
                .forEach(ic -> sender.sendMessage(formatMoverRow(ic, "▼", NamedTextColor.RED)));

        // Active events
        List<MarketEvent> active = marketEventService.getActiveEvents();
        if (!active.isEmpty()) {
            sender.sendMessage(Component.text(""));
            Component eventsHeader = Component.text("─── Active Events ───").color(NamedTextColor.DARK_GRAY);
            sender.sendMessage(eventsHeader);
            for (MarketEvent evt : active) {
                sender.sendMessage(Component.text("  " + eventIcon(evt.type()) + " ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(evt.name()).color(NamedTextColor.YELLOW)));
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private List<ItemChange> computePriceChangesSince(Instant since) {
        List<ItemChange> changes = new ArrayList<>();
        for (ShopItem item : shopManager.getAllItems()) {
            BigDecimal buyPrice = marketEngine.getBuyPrice(item);
            BigDecimal sellPrice = marketEngine.getSellPrice(item);
            if (buyPrice == null || sellPrice == null) continue;
            if (buyPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal currentMid = buyPrice.add(sellPrice).divide(TWO, 4, RoundingMode.HALF_UP);
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), since, 5);
            if (history.size() < 2) continue;

            BigDecimal oldest = history.get(history.size() - 1).price();
            if (oldest.compareTo(BigDecimal.ZERO) <= 0) continue;

            double pctChange = currentMid.subtract(oldest)
                    .divide(oldest, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100.0;
            changes.add(new ItemChange(item.getDisplayNameOrMaterial(), oldest, currentMid, pctChange));
        }

        changes.sort(Comparator.<ItemChange>comparingDouble(c -> Math.abs(c.pct())).reversed());
        return changes;
    }

    private Instant startOfCurrentWeek() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        return weekStart.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Component formatMoney(BigDecimal amount, NamedTextColor color) {
        if (amount == null) return Component.text("$—", color);
        String formatted;
        BigDecimal abs = amount.abs();
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            formatted = String.format("$%.1fM", abs.doubleValue() / 1_000_000);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            formatted = String.format("$%.1fK", abs.doubleValue() / 1_000);
        } else {
            formatted = "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            formatted = "-" + formatted;
        }
        return Component.text(formatted, color);
    }

    private Component formatMoverRow(ItemChange ic, String arrow, NamedTextColor arrowColor) {
        String pctStr = String.format("%+.1f%%", ic.pct());
        return Component.text("  " + arrow + " ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(ic.name()).color(NamedTextColor.WHITE))
                .append(Component.text(" $" + ic.oldPrice().toPlainString() + " → $" + ic.newPrice().toPlainString()).color(NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + pctStr).color(arrowColor));
    }

    private String eventIcon(MarketEvent.EventType type) {
        return switch (type) {
            case DEMAND_SURGE -> "📈";
            case SUPPLY_GLUT -> "📉";
            case INFLATION_BOOST -> "💰";
            case DEFLATION_DROP -> "💸";
            case GOLD_RUSH -> "⛏️";
            case CUSTOM -> "⚙️";
        };
    }

    private String formatDg(BigDecimal dg) {
        if (dg == null) return "—";
        return String.format("%.2fx", dg.doubleValue());
    }

    private NamedTextColor dgRatioColor(BigDecimal dg) {
        if (dg == null) return NamedTextColor.GRAY;
        double d = dg.doubleValue();
        if (d < 3.0) return NamedTextColor.GREEN;
        if (d < 8.0) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor circuitBreakerColor(BigDecimal dg) {
        if (dg == null) return NamedTextColor.GRAY;
        double d = dg.doubleValue();
        if (d >= 10.0) return NamedTextColor.RED;
        if (d >= 3.0) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    private String circuitBreakerStatus(BigDecimal dg) {
        if (dg == null) return "—";
        double d = dg.doubleValue();
        if (d >= 15.0) return "TIER3 LOCKED (loans halted)";
        if (d >= 10.0) return "TIER2 ACTIVE (interest capped 50%)";
        if (d >= 3.0) return "TIER1 ACTIVE (interest capped 80%)";
        return "STANDBY";
    }

    /** Price change record. */
    private record ItemChange(String name, BigDecimal oldPrice, BigDecimal newPrice, double pct) {

    }
}
