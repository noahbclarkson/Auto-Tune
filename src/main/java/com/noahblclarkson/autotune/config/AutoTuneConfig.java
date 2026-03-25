package com.noahblclarkson.autotune.config;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AutoTuneConfig(
        @NotNull StorageConfig storage,
        @NotNull WebConfig web,
        @NotNull EconomyConfig economy,
        @NotNull LoanConfig loans,
        @NotNull GuiConfig gui,
        @NotNull PriceReporterConfig priceReporter,
        @NotNull DebugConfig debug,
        @NotNull EnchantmentConfig enchantment
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

    /**
     * Enchantment pricing config — controls price multipliers for enchanted items.
     * Each entry maps an enchantment name to per-level multipliers.
     * E.g. EFFICIENCY → [1.3, 1.7, 2.2, 3.0] means Efficiency I ×1.3, II ×1.7, etc.
     */
    public record EnchantmentConfig(
            boolean enabled,
            @NotNull Map<String, List<Double>> enchantmentMultipliers
    ) {
        public static EnchantmentConfig defaults() {
            Map<String, List<Double>> defaults = new HashMap<>();
            // Sharpness — weapons get meaningful premium
            defaults.put("SHARPNESS", List.of(1.25, 1.60, 2.00, 2.50, 3.00));
            // Efficiency — tools benefit from faster mining
            defaults.put("EFFICIENCY", List.of(1.30, 1.70, 2.20, 3.00, 4.00));
            // Unbreaking — durability saving is valuable
            defaults.put("UNBREAKING", List.of(1.10, 1.25, 1.50));
            // Protection — armor effectiveness
            defaults.put("PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            // Power — bow damage
            defaults.put("POWER", List.of(1.30, 1.70, 2.20, 2.80, 3.50));
            // Flame — embeds value in arrows
            defaults.put("FLAME", List.of(1.15, 1.40));
            // Infinity — infinite arrows
            defaults.put("INFINITY", List.of(2.00));
            // Fortune — significant mining/farming premium
            defaults.put("FORTUNE", List.of(1.50, 2.00, 3.00));
            // Looting — mob loot premium
            defaults.put("LOOTING", List.of(1.30, 1.80, 2.50));
            // Lure — fishing
            defaults.put("LURE", List.of(1.20, 1.50, 2.00));
            // Luck of the Sea — fishing
            defaults.put("LUCK_OF_THE_SEA", List.of(1.20, 1.50, 2.00));
            // Respiration — underwater breathing
            defaults.put("RESPIRATION", List.of(1.10, 1.25, 1.50));
            // Aqua Affinity — mining underwater
            defaults.put("AQUA_AFFINITY", List.of(1.10, 1.25, 1.50));
            // Thorns — armor damage
            defaults.put("THORNS", List.of(1.20, 1.50, 2.00));
            // Fire Protection
            defaults.put("FIRE_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            // Blast Protection
            defaults.put("BLAST_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            // Projectile Protection
            defaults.put("PROJECTILE_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            // Feather Falling
            defaults.put("FEATHER_FALLING", List.of(1.15, 1.35, 1.60, 1.90));
            // Depth Strider
            defaults.put("DEPTH_STRIDER", List.of(1.15, 1.40, 1.70));
            // Frost Walker
            defaults.put("FROST_WALKER", List.of(1.20, 1.50));
            // Riptide
            defaults.put("RIPTIDE", List.of(1.20, 1.60, 2.20));
            // Loyalty
            defaults.put("LOYALTY", List.of(1.10, 1.30, 1.60));
            // Impaling
            defaults.put("IMPALING", List.of(1.20, 1.50, 1.90, 2.40));
            // Channeling
            defaults.put("CHANNELING", List.of(1.10));
            // Multishot
            defaults.put("MULTISHOT", List.of(1.50));
            // Quick Charge
            defaults.put("QUICK_CHARGE", List.of(1.15, 1.35, 1.60));
            // Piercing
            defaults.put("PIERCING", List.of(1.15, 1.40, 1.70));
            // Mending
            defaults.put("MENDING", List.of(1.50));
            // Soul Speed
            defaults.put("SOUL_SPEED", List.of(1.20, 1.50, 2.00));
            // Swift Sneak
            defaults.put("SWIFT_SNEAK", List.of(1.30, 1.70, 2.20));
            // Sweeping Edge
            defaults.put("SWEEPING_EDGE", List.of(1.20, 1.50, 2.00));
            // Knockback
            defaults.put("KNOCKBACK", List.of(1.10, 1.25, 1.50));
            // Fire Aspect
            defaults.put("FIRE_ASPECT", List.of(1.20, 1.50));
            // Punch
            defaults.put("PUNCH", List.of(1.20, 1.50));
            // Bane of Arthropods
            defaults.put("BANE_OF_ARTHROPODS", List.of(1.15, 1.35, 1.60, 1.90));
            // Smite
            defaults.put("SMITE", List.of(1.15, 1.35, 1.60, 1.90));
            // Infinity-like rarity (enchants with no level)
            return new EnchantmentConfig(true, defaults);
        }
    }
}
