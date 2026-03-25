package com.noahblclarkson.autotune.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AutoTuneConfig(
        @NotNull StorageConfig storage,
        @NotNull WebConfig web,
        @NotNull EconomyConfig economy,
        @NotNull LoanConfig loans,
        @NotNull GuiConfig gui,
        @NotNull PriceReporterConfig priceReporter,
        @NotNull DebugConfig debug
) {

    public record StorageConfig(
            @NotNull StorageType type,
            @NotNull String host,
            int port,
            @NotNull String database,
            @NotNull String username,
            @NotNull String password,
            @NotNull PoolConfig pool
    ) {
        public enum StorageType {
            SQLITE,
            MYSQL
        }

        public record PoolConfig(
                int maximumSize,
                int minimumIdle,
                long connectionTimeout,
                long idleTimeout,
                long maxLifetime
        ) {
            public static PoolConfig defaults() {
                return new PoolConfig(10, 2, 30000, 600000, 1800000);
            }
        }
    }

    public record WebConfig(
            boolean enabled,
            int port,
            @NotNull String host,
            boolean websocketEnabled
    ) {
        public static WebConfig defaults() {
            return new WebConfig(true, 8989, "0.0.0.0", true);
        }
    }

    public record EconomyConfig(
            @NotNull String currencySymbol,
            long updateInterval,
            double maxPriceChangePercent,
            int tradeWindowDays,
            boolean requireFirstSell,
            double slippageCoeff,
            double sellPressureMultiplier,
            double sectorCorrelation,
            double playerRateLimitMultiplier,
            double trendDampening,
            double trendStreakThresholdPercent,
            double trendDampeningFloor,
            boolean adaptiveWindow,
            int minWindowDays,
            int maxWindowDays,
            int maxSectorCorrelationGroupSize,
            @NotNull SpreadConfig spread,
            @NotNull PlayerScalingConfig playerScaling
    ) {
        public static EconomyConfig defaults() {
            return new EconomyConfig(
                    "$",
                    6000,
                    1.5,
                    7,
                    true,
                    0.01,
                    1.0,
                    0.05,
                    3.0,
                    0.05,
                    0.1,
                    0.25,
                    true,
                    2,
                    7,
                    20,
                    SpreadConfig.defaults(),
                    PlayerScalingConfig.defaults()
            );
        }
    }

    public record SpreadConfig(
            double baseSpread,
            double volumeImpact,
            double playerImpact,
            double liquidityCoeff,
            int liquidityFullEffectTraders
    ) {
        public static SpreadConfig defaults() {
            return new SpreadConfig(0.20, 0.8, 0.6, 0.01, 10);
        }
    }

    public record PlayerScalingConfig(
            int fullEffectPlayers
    ) {
        public static PlayerScalingConfig defaults() {
            return new PlayerScalingConfig(10);
        }
    }

    public record LoanConfig(
            boolean enabled,
            double baseInterestRate,
            boolean creditScoreModifier,
            double maxLoanMultiplier,
            int minCreditScore,
            int defaultDurationDays,
            int minTermDays,
            int maxTermDays,
            double termPremiumPerDay,
            int compoundIntervalHours,
            int overdueCheckIntervalHours,
            int warningBeforeDueHours,
            double earlyRepaymentBonusMultiplier,
            double inflationRateImpact,
            int defaultPenalty,
            double debtGdpCircuitBreakerRatio
    ) {
        public static LoanConfig defaults() {
            return new LoanConfig(true, 0.05, true, 2.0, 200, 7, 3, 30, 0.002, 24, 1, 24, 1.5, 0.5, 50, 10.0);
        }
    }

    public record GuiConfig(
            int itemsPerPage,
            boolean searchEnabled,
            int searchTimeoutTicks,
            boolean showEconomyStats,
            boolean show24hChange,
            @NotNull TitlesConfig titles,
            @NotNull ColorsConfig colors,
            @NotNull MaterialsConfig materials,
            @NotNull List<Integer> buyQuantities
    ) {
        public static GuiConfig defaults() {
            return new GuiConfig(
                    45, true, 600, true, true,
                    TitlesConfig.defaults(),
                    ColorsConfig.defaults(),
                    MaterialsConfig.defaults(),
                    List.of(1, 2, 4, 8, 16, 32, 64)
            );
        }
    }

    public record TitlesConfig(
            @NotNull String shop,
            @NotNull String sell,
            @NotNull String autosell,
            @NotNull String trends,
            @NotNull String transactionHistory,
            @NotNull String adminTransactionHistory
    ) {
        public static TitlesConfig defaults() {
            return new TitlesConfig(
                    "Auto-Tune Shop",
                    "Sell Items",
                    "Autosell",
                    "Market Trends",
                    "Your Transactions",
                    "All Transactions"
            );
        }
    }

    public record ColorsConfig(
            @NotNull String buyPrice,
            @NotNull String sellPrice,
            @NotNull String spread,
            @NotNull String sectionName,
            @NotNull String itemName,
            @NotNull String trendUp,
            @NotNull String trendDown,
            @NotNull String trendStable,
            @NotNull String positive,
            @NotNull String negative,
            @NotNull String accent,
            @NotNull String muted
    ) {
        public static ColorsConfig defaults() {
            return new ColorsConfig(
                    "<green>", "<gold>", "<aqua>", "<gold>",
                    "<white>", "<green>", "<red>", "<gray>",
                    "<green>", "<red>", "<aqua>", "<dark_gray>"
            );
        }
    }

    public record MaterialsConfig(
            @NotNull String border,
            @NotNull String buyButton,
            @NotNull String sellButton,
            @NotNull String notAvailable,
            @NotNull String previousPage,
            @NotNull String nextPage,
            @NotNull String back,
            @NotNull String pageIndicator,
            @NotNull String search,
            @NotNull String trends,
            @NotNull String close,
            @NotNull String economyStats,
            @NotNull String enableAll,
            @NotNull String disableAll,
            @NotNull String sellInventory
    ) {
        public static MaterialsConfig defaults() {
            return new MaterialsConfig(
                    "BLACK_STAINED_GLASS_PANE", "LIME_STAINED_GLASS_PANE",
                    "ORANGE_STAINED_GLASS_PANE", "BARRIER",
                    "ARROW", "ARROW", "DARK_OAK_DOOR",
                    "PAPER", "NAME_TAG", "SPYGLASS",
                    "BARRIER", "GOLD_BLOCK",
                    "LIME_DYE", "RED_DYE", "HOPPER"
            );
        }
    }

    public record PriceReporterConfig(
            boolean enabled,
            @NotNull String apiUrl,
            @NotNull String apiKey,
            @NotNull String serverId,
            long reportIntervalMinutes
    ) {
        public static PriceReporterConfig defaults() {
            return new PriceReporterConfig(true, "https://prices.auto-tune.io", "your-server-api-key", "your-server-uuid", 5);
        }
    }

    public record DebugConfig(
            boolean enabled,
            boolean logPrices
    ) {
        public static DebugConfig defaults() {
            return new DebugConfig(false, false);
        }
    }
}
