package com.noahblclarkson.autotune.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Analyzes the current economy state and produces specific, evidence-based recommendations
 * for server admins. Answers: "What should I do right now?"
 *
 * Recommendations are grounded in simulation evidence from the Auto-Tune simulation lab
 * and are specific to current economy metrics.
 */
@Singleton
@SuppressWarnings("PMD")
public class EconomyAdvisor {

    private final EconomyMetricsManager metricsManager;
    private final LoanManager loanManager;
    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final EconomySnapshotRepository snapshotRepository;
    private final AutoTuneConfig config;

    @Inject
    public EconomyAdvisor(
            EconomyMetricsManager metricsManager,
            LoanManager loanManager,
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            EconomySnapshotRepository snapshotRepository,
            ConfigManager configManager
    ) {
        this.metricsManager = metricsManager;
        this.loanManager = loanManager;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.snapshotRepository = snapshotRepository;
        this.config = configManager.getConfig();
    }

    /** Result of analyzing the economy. */
    public record AdviceResult(
            HealthLevel health,
            DebtLevel debt,
            ActivityLevel activity,
            VolatilityLevel volatility,
            BalanceLevel balance,
            List<String> recommendations,
            String summary
    ) {}

    public enum HealthLevel {
        HEALTHY(NamedTextColor.GREEN, "Healthy", "Economy is operating normally."),
        STRESSED(NamedTextColor.YELLOW, "Stressed", "Economy is under pressure. Monitor closely."),
        CRITICAL(NamedTextColor.RED, "Critical", "Immediate action recommended.");

        private final TextColor color;
        private final String label;
        private final String description;

        HealthLevel(TextColor color, String label, String description) {
            this.color = color;
            this.label = label;
            this.description = description;
        }

        public TextColor color() { return color; }
        public String label() { return label; }
        public String description() { return description; }
    }

    public enum DebtLevel {
        NONE(NamedTextColor.GRAY, "No debt", "Loan system is available but unused."),
        LOW(NamedTextColor.GREEN, "Healthy", "Debt is well below risk thresholds."),
        ELEVATED(NamedTextColor.YELLOW, "Elevated", "Debt is growing. Watch for further increase."),
        HIGH(NamedTextColor.RED, "High", "D/G is approaching dangerous levels."),
        CRITICAL(NamedTextColor.RED, "Critical", "D/G has crossed into circuit breaker territory.");

        private final TextColor color;
        private final String label;
        private final String description;

        DebtLevel(TextColor color, String label, String description) {
            this.color = color;
            this.label = label;
            this.description = description;
        }

        public TextColor color() { return color; }
        public String label() { return label; }
        public String description() { return description; }
    }

    public enum ActivityLevel {
        LOW(NamedTextColor.YELLOW, "Low", "Trading volume is below normal."),
        NORMAL(NamedTextColor.GREEN, "Normal", "Economy is actively traded."),
        HIGH(NamedTextColor.AQUA, "High", "Trading volume is elevated.");

        private final TextColor color;
        private final String label;
        private final String description;

        ActivityLevel(TextColor color, String label, String description) {
            this.color = color;
            this.label = label;
            this.description = description;
        }

        public TextColor color() { return color; }
        public String label() { return label; }
        public String description() { return description; }
    }

    public enum VolatilityLevel {
        STABLE(NamedTextColor.GREEN, "Stable", "Prices are steady."),
        ACTIVE(NamedTextColor.YELLOW, "Active", "Prices are moving — normal for Auto-Tune economies."),
        VOLATILE(NamedTextColor.RED, "Volatile", "Prices are oscillating. Consider parameter review.");

        private final TextColor color;
        private final String label;
        private final String description;

        VolatilityLevel(TextColor color, String label, String description) {
            this.color = color;
            this.label = label;
            this.description = description;
        }

        public TextColor color() { return color; }
        public String label() { return label; }
        public String description() { return description; }
    }

    public enum BalanceLevel {
        NORMAL(NamedTextColor.GREEN, "Balanced", "Buy/sell pressure is well-balanced."),
        BUY_HEAVY(NamedTextColor.AQUA, "Buy-heavy", "More buy pressure — prices trending up."),
        SELL_HEAVY(NamedTextColor.YELLOW, "Sell-heavy", "More sell pressure. Prices may soften."),
        EXTREME(NamedTextColor.RED, "Extreme", "Strong sell pressure. Consider a market event.");

        private final TextColor color;
        private final String label;
        private final String description;

        BalanceLevel(TextColor color, String label, String description) {
            this.color = color;
            this.label = label;
            this.description = description;
        }

        public TextColor color() { return color; }
        public String label() { return label; }
        public String description() { return description; }
    }

    /** Analyze the current economy and return specific recommendations. */
    public AdviceResult analyze() {
        List<String> recommendations = new ArrayList<>();
        DebtLevel debtLevel = assessDebt();
        ActivityLevel activityLevel = assessActivity();
        VolatilityLevel volatilityLevel = assessVolatility();
        BalanceLevel balanceLevel = assessBalance();

        // Debt-based recommendations
        switch (debtLevel) {
            case NONE -> recommendations.add(
                "Loans are available but unused. Consider highlighting /loans to active players to stimulate the economy.");
            case LOW -> { /* healthy, no action needed */ }
            case ELEVATED -> {
                recommendations.add(
                    "Debt is elevated. Monitor via `/at admin health` and consider enabling floor protection.");
                recommendations.add(
                    "If D/G continues to rise, invoke `/at admin recovery start` to freeze new loan origination.");
            }
            case HIGH -> {
                recommendations.add(
                    "⚠️ High debt detected. Run `/at admin recovery start` to freeze loan origination and prevent cascade.");
                recommendations.add(
                    "Review loan settings: consider lowering `loans.tier1-interest-cap` (currently "
                        + pct(config.loans().tier1InterestCap()) + ") to reduce compounding pressure.");
            }
            case CRITICAL -> {
                recommendations.add(
                    "🚨 Circuit breaker is active. Economy requires immediate intervention.");
                recommendations.add(
                    "Run `/at admin recovery start` to halt new loan origination while the economy deleverages.");
                recommendations.add(
                    "Consider triggering a DEMAND_SURGE event on key items to stimulate buy-side activity.");
            }
        }

        // Volatility-based recommendations
        switch (volatilityLevel) {
            case VOLATILE -> {
                recommendations.add("Price volatility is elevated. Consider:");
                recommendations.add(
                    "  - Increase `market.sell-pressure-multiplier` to 0.9 (from "
                        + config.economy().sellPressureMultiplier() + ") to reduce downward momentum.");
                recommendations.add(
                    "  - Increase `market.trend-dampening` to 0.15 (from "
                        + config.economy().trendDampening() + ") to reduce trend continuation.");
                recommendations.add(
                    "  - Add a MarketMaker archetype player to provide two-sided liquidity.");
            }
            case ACTIVE -> {
                recommendations.add(
                    "Prices are active — this is normal for Auto-Tune. No action needed unless volatility worsens.");
            }
            case STABLE -> { /* no action needed */ }
        }

        // Balance-based recommendations
        switch (balanceLevel) {
            case EXTREME -> {
                recommendations.add(
                    "Extreme sell pressure detected. Trigger a DEMAND_SURGE or INFLATION_BOOST market event:");
                recommendations.add(
                    "  `/at event templates/invoke DEMAND_SURGE --materials DIAMOND,IRON_INGOT --multiplier 1.5 --duration 60`");
            }
            case SELL_HEAVY -> {
                recommendations.add(
                    "Sell pressure is elevated. Consider triggering a GOLD_RUSH event to stimulate demand.");
            }
            case BUY_HEAVY -> {
                recommendations.add(
                    "Buy pressure is dominant — prices may rise. This is generally healthy for sellers.");
            }
            case NORMAL -> { /* no action needed */ }
        }

        // Activity-based recommendations
        if (activityLevel == ActivityLevel.LOW) {
            recommendations.add(
                "Trading volume is low. Consider a market event to stimulate activity:");
            recommendations.add(
                "  `/at event templates/invoke INFLATION_BOOST --multiplier 1.3 --duration 30`");
        }

        // Circuit breaker-specific
        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();
        if (!"NORMAL".equals(cb.tier())) {
            String cbMsg = "Circuit breaker: " + cb.tier()
                + " (D/G " + String.format("%.2fx", cb.debtGdpRatio()) + "). ";
            if (config.loans().counterCyclical()) {
                cbMsg += "Counter-cyclical mode active — interest reducing automatically.";
            } else {
                cbMsg += "Interest capped at " + pct(cb.interestMultiplier()) + ".";
            }
            recommendations.add(cbMsg);
        }

        // Determine overall health
        HealthLevel health = computeHealth(debtLevel, volatilityLevel, balanceLevel, cb.tier());
        String summary = buildSummary(health, debtLevel, volatilityLevel, balanceLevel);

        return new AdviceResult(health, debtLevel, activityLevel, volatilityLevel,
            balanceLevel, recommendations, summary);
    }

    private DebtLevel assessDebt() {
        Optional<EconomySnapshot> snap = metricsManager.getLatestSnapshot();
        if (snap.isEmpty()) return DebtLevel.NONE;

        BigDecimal debt = snap.get().totalDebt();
        BigDecimal gdp = snap.get().gdp();
        if (gdp.compareTo(BigDecimal.ZERO) <= 0) return DebtLevel.NONE;
        if (debt.compareTo(BigDecimal.ZERO) <= 0) return DebtLevel.NONE;

        double ratio = debt.divide(gdp, MathContext.DECIMAL128).doubleValue();

        if (ratio < 1.0) return DebtLevel.LOW;
        if (ratio < config.loans().debtGdpTier1Ratio()) return DebtLevel.ELEVATED;
        if (ratio < config.loans().debtGdpTier2Ratio()) return DebtLevel.HIGH;
        return DebtLevel.CRITICAL;
    }

    private ActivityLevel assessActivity() {
        Optional<EconomySnapshot> snap = metricsManager.getLatestSnapshot();
        if (snap.isEmpty()) return ActivityLevel.NORMAL;
        BigDecimal volume = snap.get().transactionVolume();
        if (volume.compareTo(new BigDecimal("100")) < 0) return ActivityLevel.LOW;
        if (volume.compareTo(new BigDecimal("5000")) > 0) return ActivityLevel.HIGH;
        return ActivityLevel.NORMAL;
    }

    private VolatilityLevel assessVolatility() {
        // Compute aggregate volatility: std dev of items' recent price changes
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<BigDecimal> changes = new ArrayList<>();

        List<ShopItem> items = itemRepository.findAll();
        for (ShopItem item : items) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), cutoff, 10);
            if (history.size() >= 2) {
                BigDecimal newest = history.get(0).price();
                BigDecimal oldest = history.get(history.size() - 1).price();
                if (oldest.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = newest.subtract(oldest)
                            .divide(oldest, 4, RoundingMode.HALF_UP);
                    changes.add(change.abs());
                }
            }
        }

        if (changes.isEmpty()) return VolatilityLevel.ACTIVE;

        // Compute standard deviation of absolute price changes
        BigDecimal mean = changes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(changes.size()), 4, RoundingMode.HALF_UP);
        BigDecimal variance = changes.stream()
                .map(c -> c.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(changes.size()), 4, RoundingMode.HALF_UP);
        double stdDev = Math.sqrt(variance.doubleValue());

        if (stdDev < 0.02) return VolatilityLevel.STABLE;
        if (stdDev < 0.08) return VolatilityLevel.ACTIVE;
        return VolatilityLevel.VOLATILE;
    }

    private BalanceLevel assessBalance() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        // The repository doesn't have a generic findByTimeRange. We can approximate recent overall balance
        // by looking at recent transactions overall, up to the last 1000.
        List<Transaction> transactions = transactionRepository.findRecent(1000);
        if (transactions.isEmpty()) return BalanceLevel.NORMAL;
        
        // Filter down to the last 24h
        List<Transaction> recentTransactions = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.timestamp().isAfter(cutoff)) {
                recentTransactions.add(t);
            }
        }
        if (recentTransactions.isEmpty()) return BalanceLevel.NORMAL;

        long buys = recentTransactions.stream().filter(t -> t.type() == Transaction.TransactionType.BUY).count();
        double buyRatio = (double) buys / recentTransactions.size();

        if (buyRatio > 0.75) return BalanceLevel.BUY_HEAVY;
        if (buyRatio < 0.35) return BalanceLevel.EXTREME;
        if (buyRatio < 0.45) return BalanceLevel.SELL_HEAVY;
        return BalanceLevel.NORMAL;
    }

    private HealthLevel computeHealth(DebtLevel debt, VolatilityLevel vol,
                                      BalanceLevel balance, String tier) {
        if ("TIER2".equals(tier) || "TIER3".equals(tier) || debt == DebtLevel.CRITICAL) {
            return HealthLevel.CRITICAL;
        }
        if (debt == DebtLevel.ELEVATED || vol == VolatilityLevel.VOLATILE
                || balance == BalanceLevel.EXTREME) {
            return HealthLevel.STRESSED;
        }
        return HealthLevel.HEALTHY;
    }

    private String buildSummary(HealthLevel health, DebtLevel debt,
                                VolatilityLevel volatility, BalanceLevel balance) {
        return switch (health) {
            case HEALTHY -> "Economy is healthy. All metrics within normal ranges.";
            case STRESSED -> String.format(
                "Economy is stressed (debt=%s, volatility=%s, balance=%s). Monitor and prepare interventions.",
                debt.label(), volatility.label(), balance.label());
            case CRITICAL -> String.format(
                "Economy requires immediate attention (debt=%s, circuit=TIER2/TIER3). Run `/at admin recovery start`.",
                debt.label());
        };
    }

    /** Build the component message for the advice command output. */
    public Component toComponent(AdviceResult result) {
        List<Component> lines = new ArrayList<>();

        // Header
        lines.add(Component.empty());
        lines.add(Component.text("⚙ ECONOMY ADVISOR", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — Evidence-Based Recommendations", NamedTextColor.GRAY)));
        lines.add(Component.text(
            "Based on simulation-validated thresholds and current market state.", NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());

        // Health badge
        TextColor healthColor = result.health().color();
        lines.add(Component.text("  Overall: ", NamedTextColor.GRAY)
                .append(Component.text(result.health().label(), healthColor, TextDecoration.BOLD))
                .append(Component.text(" — " + result.summary(), NamedTextColor.WHITE)));
        lines.add(Component.empty());

        // Metrics grid
        lines.add(Component.text("  Metrics:", NamedTextColor.GOLD));
        lines.add(metricRow("Debt", result.debt().label(), result.debt().color(),
            result.debt().description()));
        lines.add(metricRow("Activity", result.activity().label(), result.activity().color(),
            result.activity().description()));
        lines.add(metricRow("Volatility", result.volatility().label(), result.volatility().color(),
            result.volatility().description()));
        lines.add(metricRow("Balance", result.balance().label(), result.balance().color(),
            result.balance().description()));
        lines.add(Component.empty());

        // Recommendations
        lines.add(Component.text("  Recommendations:", NamedTextColor.GOLD));
        if (result.recommendations().isEmpty()) {
            lines.add(Component.text(
                "    ✓ No specific actions required. Economy operating within normal parameters.", NamedTextColor.GREEN));
        } else {
            for (String rec : result.recommendations()) {
                lines.add(Component.text("    • ", NamedTextColor.YELLOW)
                        .append(Component.text(rec, NamedTextColor.WHITE)));
            }
        }

        lines.add(Component.empty());
        lines.add(Component.text(
            "  Run `/at admin health` for details, `/at admin history` for trends.", NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());

        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component metricRow(String label, String value, TextColor valueColor,
                                String description) {
        return Component.text("    " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor, TextDecoration.BOLD))
                .append(Component.text(" — " + description, NamedTextColor.DARK_GRAY));
    }

    private static String pct(double d) {
        return String.format("%.0f%%", d * 100);
    }
}
