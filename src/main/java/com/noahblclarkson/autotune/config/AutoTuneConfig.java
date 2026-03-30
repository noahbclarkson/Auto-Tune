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
        @NotNull AutosellConfig autosell,
        @NotNull DebugConfig debug,
        @NotNull EnchantmentConfig enchantment,
        @NotNull CleanupConfig cleanup,
        @NotNull TaxConfig tax,
        @NotNull ScoreboardConfig scoreboard,
        @NotNull ExchangeRateConfig exchangeRate,
        @NotNull AuctionConfig auction,
        @NotNull MarketEventConfig marketEvents,
        @NotNull EconomicNewsConfig news,
        boolean marketFrozen
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
            int minBuyQuantity,
            int minSellQuantity,
            double minBuyValue,
            double minSellValue,
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
                    1,
                    1,
                    0.0,
                    0.0,
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
            /// Tiered debt/GDP circuit breaker.
            /// Above tier1Ratio → interest capped at tier1Cap (50%).
            /// Above tier2Ratio → interest capped at tier2Cap (25%).
            /// Above tier3Ratio → interest fully paused.
            double debtGdpTier1Ratio,
            double debtGdpTier2Ratio,
            double debtGdpTier3Ratio,
            double tier1InterestCap,
            double tier2InterestCap,
            /// Hours a player must wait after a defaulted loan before they can take a new loan.
            int postDefaultCooldownHours,
            /// Maximum size of a single loan as a multiple of economy GDP.
            /// A value of 1.0 means no single loan can exceed total GDP.
            double singleLoanGdpCap
    ) {
        public static LoanConfig defaults() {
            return new LoanConfig(
                    true, 0.05, true, 2.0, 200,
                    7, 3, 30, 0.002, 24, 1, 24, 1.5, 0.5, 50,
                    3.0, 5.0, 10.0, 0.5, 0.25,
                    168,    // postDefaultCooldownHours: 7 days
                    1.0     // singleLoanGdpCap: single loan capped at 1× GDP
            );
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
            @NotNull String marketHistory,
            @NotNull String transactionHistory,
            @NotNull String adminTransactionHistory
    ) {
        public static TitlesConfig defaults() {
            return new TitlesConfig(
                    "Auto-Tune Shop",
                    "Sell Items",
                    "Autosell",
                    "Market Trends",
                    "Price History",
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

    /**
     * Autosell configuration — controls automatic selling behavior when
     * players pick up items or close their inventory.
     *
     * minimumPrice: items are not autosold if their current sell price is below
     * this threshold. Set to 0 to sell everything. Prevents spamming cheap
     * items (e.g., cobblestone at $0.01) from filling up action bars.
     *
     * soundOnPickup: Minecraft sound played when an item is autosold on pickup.
     * Use "NONE" to disable. Recommended: ENTITY_ITEM_PICKUP.
     *
     * soundOnInventorySell: Minecraft sound played after selling inventory via
     * the autosell GUI button or inventory-close trigger.
     * Use "NONE" to disable. Recommended: UI_LOOT_YOUR_FILLED_CONTAINER.
     */
    public record AutosellConfig(
            double minimumPrice,
            @NotNull String soundOnPickup,
            @NotNull String soundOnInventorySell
    ) {
        public static AutosellConfig defaults() {
            return new AutosellConfig(0.01, "ENTITY_ITEM_PICKUP", "UI_LOOT_YOUR_FILLED_CONTAINER");
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
    /**
     * Database cleanup config — controls retention policies to prevent unbounded growth.
     * Each table is pruned independently based on age thresholds.
     */
    public record CleanupConfig(
            @NotNull RetentionConfig transactions,
            @NotNull RetentionConfig marketHistory,
            @NotNull RetentionConfig economySnapshots,
            @NotNull RetentionConfig auctionOrders,
            @NotNull RetentionConfig auctionFills,
            @NotNull RetentionConfig marketEvents,
            int cleanupIntervalHours
    ) {
        public record RetentionConfig(boolean enabled, int retentionDays) {
            public static RetentionConfig defaults(boolean enabled, int days) {
                return new RetentionConfig(enabled, days);
            }
        }

        public static CleanupConfig defaults() {
            return new CleanupConfig(
                    RetentionConfig.defaults(true, 14),
                    RetentionConfig.defaults(true, 7),
                    RetentionConfig.defaults(true, 30),
                    RetentionConfig.defaults(true, 30),   // auctionOrders
                    RetentionConfig.defaults(true, 60),   // auctionFills
                    RetentionConfig.defaults(true, 7),    // marketEvents (keep ended/cancelled for 7 days)
                    24
            );
        }
    }

    /**
     * Transaction tax config — a percentage of every trade is collected as tax
     * and accumulated in the server treasury.
     *
     * Tax is deducted from the player's cost/proceeds before the transaction.
     * The treasury accumulates these amounts and can be withdrawn by admins.
     *
     * Default: disabled (all rates 0.0).
     * Enable by setting at least one tax rate > 0 in config.yml.
     */
    public record TaxConfig(
            boolean enabled,
            double buyTaxPercent,
            double sellTaxPercent,
            double auctionTaxPercent,
            double loanInterestTaxPercent
    ) {
        public static TaxConfig defaults() {
            return new TaxConfig(false, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Economy scoreboard config — controls the per-player sidebar scoreboard
     * shown to all players, displaying live economy statistics.
     *
     * enabled: whether to show scoreboards at all
     * title: scoreboard header text (max 32 chars, Minecraft limitation)
     * updateIntervalSeconds: how often to refresh stats (min 10s recommended)
     *
     * Displayed entries:
     *   GDP       — 24h trade volume (dollar amount)
     *   Debt      — total outstanding loan principal
     *   Loans     — active loan count
     *   Activity  — trade volume indicator (High/Norm/Low)
     *   Inflation — price change direction + label
     */
    public record ScoreboardConfig(
            boolean enabled,
            @NotNull String title,
            int updateIntervalSeconds
    ) {
        public static ScoreboardConfig defaults() {
            return new ScoreboardConfig(false, "Auto-Tune Economy", 30);
        }
    }

    public record EnchantmentConfig(
            boolean enabled,
            @NotNull Map<String, List<Double>> enchantmentMultipliers
    ) {
        public static EnchantmentConfig defaults() {
            Map<String, List<Double>> defaults = new HashMap<>();
            defaults.put("SHARPNESS", List.of(1.25, 1.60, 2.00, 2.50, 3.00));
            defaults.put("EFFICIENCY", List.of(1.30, 1.70, 2.20, 3.00, 4.00));
            defaults.put("UNBREAKING", List.of(1.10, 1.25, 1.50));
            defaults.put("PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            defaults.put("POWER", List.of(1.30, 1.70, 2.20, 2.80, 3.50));
            defaults.put("FLAME", List.of(1.15, 1.40));
            defaults.put("INFINITY", List.of(2.00));
            defaults.put("FORTUNE", List.of(1.50, 2.00, 3.00));
            defaults.put("LOOTING", List.of(1.30, 1.80, 2.50));
            defaults.put("LURE", List.of(1.20, 1.50, 2.00));
            defaults.put("LUCK_OF_THE_SEA", List.of(1.20, 1.50, 2.00));
            defaults.put("RESPIRATION", List.of(1.10, 1.25, 1.50));
            defaults.put("AQUA_AFFINITY", List.of(1.10, 1.25, 1.50));
            defaults.put("THORNS", List.of(1.20, 1.50, 2.00));
            defaults.put("FIRE_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            defaults.put("BLAST_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            defaults.put("PROJECTILE_PROTECTION", List.of(1.20, 1.50, 1.90, 2.40));
            defaults.put("FEATHER_FALLING", List.of(1.15, 1.35, 1.60, 1.90));
            defaults.put("DEPTH_STRIDER", List.of(1.15, 1.40, 1.70));
            defaults.put("FROST_WALKER", List.of(1.20, 1.50));
            defaults.put("RIPTIDE", List.of(1.20, 1.60, 2.20));
            defaults.put("LOYALTY", List.of(1.10, 1.30, 1.60));
            defaults.put("IMPALING", List.of(1.20, 1.50, 1.90, 2.40));
            defaults.put("CHANNELING", List.of(1.10));
            defaults.put("MULTISHOT", List.of(1.50));
            defaults.put("QUICK_CHARGE", List.of(1.15, 1.35, 1.60));
            defaults.put("PIERCING", List.of(1.15, 1.40, 1.70));
            defaults.put("MENDING", List.of(1.50));
            defaults.put("SOUL_SPEED", List.of(1.20, 1.50, 2.00));
            defaults.put("SWIFT_SNEAK", List.of(1.30, 1.70, 2.20));
            defaults.put("SWEEPING_EDGE", List.of(1.20, 1.50, 2.00));
            defaults.put("KNOCKBACK", List.of(1.10, 1.25, 1.50));
            defaults.put("FIRE_ASPECT", List.of(1.20, 1.50));
            defaults.put("PUNCH", List.of(1.20, 1.50));
            defaults.put("BANE_OF_ARTHROPODS", List.of(1.15, 1.35, 1.60, 1.90));
            defaults.put("SMITE", List.of(1.15, 1.35, 1.60, 1.90));
            return new EnchantmentConfig(true, defaults);
        }
    }

    /**
     * Exchange rate config — controls fetching of cross-server exchange rates
     * from the shared API server.
     *
     * Exchange rates are relative to the global true-price baseline:
     *   &gt; 1.0: your economy is more expensive than the global average
     *   &lt; 1.0: your economy is cheaper than the global average
     *   = 1.0: aligned with global average
     *
     * Requires price-reporter to be enabled (server must be registered with the API).
     */
    public record ExchangeRateConfig(
            boolean enabled,
            long fetchIntervalMinutes
    ) {
        public static ExchangeRateConfig defaults() {
            return new ExchangeRateConfig(true, 15);
        }
    }

    /**
     * Auction house configuration.
     * @param defaultDurationHours   How long orders remain active before expiring (hours).
     * @param expirationCheckIntervalMinutes How often to process expired orders (minutes).
     */
    public record AuctionConfig(
            int defaultDurationHours,
            int expirationCheckIntervalMinutes
    ) {
        public static AuctionConfig defaults() {
            return new AuctionConfig(72, 15);
        }
    }

    /**
     * Market events configuration — controls scheduled or triggered market events
     * that modify price behavior for matching items.
     *
     * @param enabled            Whether market events are active at all
     * @param defaultEvents      Pre-defined event templates loaded from config
     * @param checkIntervalMinutes How often to check for event lifecycle (minutes)
     */
    public record MarketEventConfig(
            boolean enabled,
            @NotNull List<MarketEventConfigEntry> defaultEvents,
            int checkIntervalMinutes
    ) {
        public static MarketEventConfig defaults() {
            return new MarketEventConfig(true, List.of(), 5);
        }
    }

    /**
     * A single market event template from config.
     */
    public record MarketEventConfigEntry(
            @NotNull String name,
            @NotNull String type,
            @NotNull List<String> materials,
            double multiplier,
            int durationMinutes,
            String startMessage,
            String endMessage
    ) {
        public static MarketEventConfigEntry defaults() {
            return new MarketEventConfigEntry("", "CUSTOM", List.of(), 2.0, 60, "", "");
        }
    }

    /**
     * Economic news feed configuration — controls the in-game market narration
     * that broadcasts significant market events to all players.
     *
     * The news feed periodically scans recent price history for:
     * - Large price surges or crashes (> threshold within window)
     * - Unusual volume spikes (> multiplier × normal volume)
     * - Circuit breaker activation (tier 1, 2, or 3)
     * - Market freeze/unfreeze events
     *
     * Messages are delivered via action bar to avoid spamming chat.
     * A per-item cooldown prevents the same item from dominating the feed.
     *
     * @param enabled            Whether the news feed is active
     * @param intervalMinutes    How often to scan and broadcast a news item (minutes)
     * @param priceChangeThresholdPercent  Minimum % price change to trigger a news item
     * @param volumeSpikeMultiplier       Volume must exceed normal × this to trigger
     * @param historyWindowMinutes        How far back to look for price changes (minutes)
     * @param maxItemsPerCycle  Maximum news items to broadcast per cycle (randomized subset)
     * @param itemCooldownMinutes         Don't re-announce the same item within this window
     */
    public record EconomicNewsConfig(
            boolean enabled,
            int intervalMinutes,
            double priceChangeThresholdPercent,
            double volumeSpikeMultiplier,
            int historyWindowMinutes,
            int maxItemsPerCycle,
            int itemCooldownMinutes
    ) {
        public static EconomicNewsConfig defaults() {
            return new EconomicNewsConfig(true, 5, 5.0, 3.0, 60, 3, 30);
        }
    }
}
