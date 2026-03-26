package com.noahblclarkson.autotune.manager;

import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketEngine price and spread calculations.
 * Tests the core market math in isolation from the database and server.
 */
class MarketEngineTest {

    /** Minimal fake adapter — only provides what MarketEngine needs. */
    private PluginAdapter makeFakeAdapter() {
        Server fakeServer = mock(Server.class);
        when(fakeServer.getOnlinePlayers()).thenReturn(Collections.emptyList());
        return new PluginAdapter() {
            private final Logger logger = Logger.getLogger(getClass().getName());
            @Override public Logger getLogger() { return logger; }
            @Override public Server getServer() { return fakeServer; }
        };
    }

    private PluginAdapter adapter;
    private ConfigManager configManager;
    private ItemRepository itemRepository;
    private TransactionRepository transactionRepository;
    private PriceOverrideRepository priceOverrideRepository;
    private AutoTuneConfig config;
    private MarketEngine engine;

    @BeforeEach
    void setUp() {
        // Create mocks for repositories and config
        configManager = mock(ConfigManager.class);
        itemRepository = mock(ItemRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        priceOverrideRepository = mock(PriceOverrideRepository.class);

        // Use real config with defaults
        config = new AutoTuneConfig(
                new AutoTuneConfig.StorageConfig(
                        AutoTuneConfig.StorageConfig.StorageType.SQLITE, "localhost", 3306,
                        "test.db", "user", "pass",
                        AutoTuneConfig.StorageConfig.PoolConfig.defaults()
                ),
                AutoTuneConfig.WebConfig.defaults(),
                AutoTuneConfig.EconomyConfig.defaults(),
                AutoTuneConfig.LoanConfig.defaults(),
                AutoTuneConfig.GuiConfig.defaults(),
                AutoTuneConfig.PriceReporterConfig.defaults(),
                AutoTuneConfig.AutosellConfig.defaults(),
                AutoTuneConfig.DebugConfig.defaults(),
                AutoTuneConfig.EnchantmentConfig.defaults(),
                AutoTuneConfig.CleanupConfig.defaults(),
                AutoTuneConfig.TaxConfig.defaults(),
                false
        );

        adapter = makeFakeAdapter();

        when(configManager.getConfig()).thenReturn(config);
        when(configManager.isMarketFrozen()).thenReturn(false);
        when(priceOverrideRepository.getActiveOverrides()).thenReturn(Collections.emptyMap());

        engine = new MarketEngine(
                adapter, configManager, itemRepository,
                transactionRepository, priceOverrideRepository
        );
    }

    // -------------------------------------------------------------------------
    // calculatePlayerScaling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("calculatePlayerScaling")
    class CalculatePlayerScaling {

        @Test
        @DisplayName("returns 0 when no players online")
        void zeroPlayers() {
            double result = engine.calculatePlayerScaling(0, config.economy());
            assertEquals(0.0, result, 1e-9);
        }

        @Test
        @DisplayName("returns ~0.868 at half of fullEffectPlayers (5 of 10)")
        void halfPlayers() {
            double result = engine.calculatePlayerScaling(5, config.economy());
            // tanh(5 * ATANH_099 / 10) = tanh(1.323) ≈ 0.868
            assertEquals(0.868, result, 0.01);
        }

        @Test
        @DisplayName("returns ~0.99 at fullEffectPlayers (10)")
        void fullPlayers() {
            double result = engine.calculatePlayerScaling(10, config.economy());
            // tanh(ATANH_099) ≈ 0.99
            assertEquals(0.99, result, 0.01);
        }

        @Test
        @DisplayName("approaches but never reaches 1.0 at 2x fullEffectPlayers")
        void doublePlayers() {
            double result = engine.calculatePlayerScaling(20, config.economy());
            assertTrue(result > 0.999, "Should approach 1.0");
            assertTrue(result < 1.0, "Should never reach 1.0");
        }

        @Test
        @DisplayName("is roughly linear in the low-count range")
        void roughlyLinearInLowRange() {
            double s1 = engine.calculatePlayerScaling(1, config.economy());
            double s2 = engine.calculatePlayerScaling(2, config.economy());
            double s3 = engine.calculatePlayerScaling(3, config.economy());
            double inc1 = s2 - s1;
            double inc2 = s3 - s2;
            assertTrue(inc1 > 0 && inc2 > 0, "Each increment should increase scaling");
            // tanh is not perfectly linear, but both increments are positive and in the same ballpark
            assertEquals(inc1, inc2, 0.10, "Increments should be roughly similar (tanh is nearly linear in low range)");
        }
    }

    // -------------------------------------------------------------------------
    // calculateNewPrice
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("calculateNewPrice")
    class CalculateNewPrice {

        @Test
        @DisplayName("returns current price when there is no trading activity")
        void noActivity() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(0, 0, 0, 0, 0);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 5, config.economy());
            assertEquals(new BigDecimal("100.00"), result);
        }

        @Test
        @DisplayName("price rises with net buying pressure")
        void netBuying() {
            ShopItem item = makeItem(1, "100.00");
            // 100 buys, 0 sells → tradeRatio = 1.0
            var metrics = tradeMetrics(100, 0, 100, 0, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            assertTrue(result.compareTo(new BigDecimal("100.00")) > 0,
                    "Price should rise with net buying, got " + result);
        }

        @Test
        @DisplayName("price falls with net selling pressure")
        void netSelling() {
            ShopItem item = makeItem(1, "100.00");
            // 0 buys, 100 sells → tradeRatio = -1.0
            var metrics = tradeMetrics(0, 100, 0, 100, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            assertTrue(result.compareTo(new BigDecimal("100.00")) < 0,
                    "Price should fall with net selling, got " + result);
        }

        @Test
        @DisplayName("price is stable when buys and sells are balanced")
        void balanced() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 50, 50, 50, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            assertEquals(0, new BigDecimal("100.00").compareTo(result),
                    "Price should be unchanged when buys == sells, got " + result);
        }

        @Test
        @DisplayName("price change is bounded by maxPriceChangePercent")
        void boundedByMaxChange() {
            ShopItem item = makeItem(1, "100.00");
            // Extreme buy pressure
            var metrics = tradeMetrics(10000, 1, 10000, 1, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            // maxPriceChangePercent = 1.5%, so price shouldn't rise more than ~1.5%
            BigDecimal maxAllowed = new BigDecimal("101.50");
            assertTrue(result.compareTo(maxAllowed) <= 0,
                    "Price should not exceed max change %, got " + result);
        }

        @Test
        @DisplayName("selling pressure is amplified by sellPressureMultiplier")
        void sellPressureAmplified() {
            // Custom config with sellPressureMultiplier = 1.5 (amplify downward moves)
            AutoTuneConfig.EconomyConfig amplified = new AutoTuneConfig.EconomyConfig(
                    "$", 6000, 1.5, 7, true, 0.01,
                    1.5,  // amplified sell pressure
                    0.05, 3.0, 0.05, 0.1, 0.25,
                    true, 2, 7, 20,
                    AutoTuneConfig.SpreadConfig.defaults(),
                    AutoTuneConfig.PlayerScalingConfig.defaults()
            );

            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(0, 100, 0, 100, 1);

            BigDecimal defaultResult = engine.calculateNewPrice(item, metrics, 10, config.economy());
            BigDecimal amplifiedResult = engine.calculateNewPrice(item, metrics, 10, amplified);

            assertTrue(amplifiedResult.compareTo(defaultResult) < 0,
                    "Higher sellPressureMultiplier should cause larger price drop");
        }

        @Test
        @DisplayName("price floor is applied — result is >= $0.01")
        void priceFloor() {
            // Note: the floor is applied in the tick loop. Here we test that
            // heavy sell pressure doesn't push the price arbitrarily negative.
            ShopItem item = makeItem(1, "1.00"); // Start higher so floor is observable
            var metrics = tradeMetrics(0, 10000, 0, 10000, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            // Even with massive sell pressure, the engine math keeps it positive
            assertTrue(result.compareTo(BigDecimal.ZERO) > 0,
                    "Price should not go negative, got " + result);
        }

        @Test
        @DisplayName("per-item maxPriceChangeOverride takes precedence over global")
        void perItemOverride() {
            // Item with 5% max change override
            ShopItem item = makeItemWithMaxChange(1, "100.00", 5.0);
            var metrics = tradeMetrics(100, 0, 100, 0, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            // Should be able to rise up to 5% (not just 1.5%)
            assertTrue(result.compareTo(new BigDecimal("103.00")) > 0,
                    "5%% override should allow larger move, got " + result);
            assertTrue(result.compareTo(new BigDecimal("106.00")) < 0,
                    "Should still be bounded by override, got " + result);
        }

        @Test
        @DisplayName("price returns a positive non-null value")
        void priceReturnsValidValue() {
            ShopItem item = makeItem(1, "33.33");
            var metrics = tradeMetrics(1, 0, 1, 0, 1);

            BigDecimal result = engine.calculateNewPrice(item, metrics, 10, config.economy());
            assertNotNull(result);
            assertTrue(result.compareTo(BigDecimal.ZERO) > 0, "Price should be positive");
        }

        @Test
        @DisplayName("price change amplified by player scaling (more players = larger moves)")
        void playerScalingAmplifiesChange() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 0, 50, 0, 1); // moderate buy pressure

            double lowPlayers = engine.calculatePlayerScaling(1, config.economy());
            double highPlayers = engine.calculatePlayerScaling(10, config.economy());

            // With higher player scaling, the same trade ratio produces larger price change
            BigDecimal resultLow = engine.calculateNewPrice(item, metrics, 1, config.economy());
            BigDecimal resultHigh = engine.calculateNewPrice(item, metrics, 10, config.economy());

            assertTrue(resultHigh.compareTo(resultLow) > 0,
                    "More players should amplify price change, low=" + resultLow + " high=" + resultHigh);
        }
    }

    // -------------------------------------------------------------------------
    // calculateSpread
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("calculateSpread")
    class CalculateSpread {

        @Test
        @DisplayName("returns positive spreads")
        void positiveSpreads() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 50, 50, 50, 5);

            MarketEngine.SpreadResult result = engine.calculateSpread(item, metrics, 10, 1.0, config.economy());

            assertTrue(result.bpd().doubleValue() > 0, "BPD should be positive");
            assertTrue(result.spd().doubleValue() > 0, "SPD should be positive");
        }

        @Test
        @DisplayName("buy imbalance increases BPD relative to SPD")
        void buyImbalance() {
            ShopItem item = makeItem(1, "100.00");
            var metricsBuy = tradeMetrics(100, 0, 100, 0, 1);
            var metricsSell = tradeMetrics(0, 100, 0, 100, 1);

            MarketEngine.SpreadResult buyResult = engine.calculateSpread(item, metricsBuy, 10, 1.0, config.economy());
            MarketEngine.SpreadResult sellResult = engine.calculateSpread(item, metricsSell, 10, 1.0, config.economy());

            // Buy imbalance: BPD should be wider than the balanced case
            // Sell imbalance: SPD should be wider than the balanced case
            assertTrue(buyResult.bpd().doubleValue() > sellResult.bpd().doubleValue(),
                    "Buy imbalance should widen BPD vs sell imbalance, got buyBPD=" + buyResult.bpd() + " sellBPD=" + sellResult.bpd());
            assertTrue(sellResult.spd().doubleValue() > buyResult.spd().doubleValue(),
                    "Sell imbalance should widen SPD vs buy imbalance, got sellSPD=" + sellResult.spd() + " buySPD=" + buyResult.spd());
        }

        @Test
        @DisplayName("spreads widen as global volume increases (multiplier > 1)")
        void highGlobalVolume() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 50, 50, 50, 5);

            MarketEngine.SpreadResult normal = engine.calculateSpread(item, metrics, 10, 1.0, config.economy());
            MarketEngine.SpreadResult highVol = engine.calculateSpread(item, metrics, 10, 1.3, config.economy());

            assertTrue(highVol.bpd().doubleValue() > normal.bpd().doubleValue(),
                    "High volume should widen BPD");
            assertTrue(highVol.spd().doubleValue() > normal.spd().doubleValue(),
                    "High volume should widen SPD");
        }

        @Test
        @DisplayName("spreads tighten as global volume decreases (multiplier < 1)")
        void lowGlobalVolume() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 50, 50, 50, 5);

            MarketEngine.SpreadResult normal = engine.calculateSpread(item, metrics, 10, 1.0, config.economy());
            MarketEngine.SpreadResult lowVol = engine.calculateSpread(item, metrics, 10, 0.8, config.economy());

            assertTrue(lowVol.bpd().doubleValue() < normal.bpd().doubleValue(),
                    "Low volume should tighten BPD");
        }

        @Test
        @DisplayName("spreads widen as player count decreases")
        void playerCountEffect() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(50, 50, 50, 50, 5);

            MarketEngine.SpreadResult many = engine.calculateSpread(item, metrics, 10, 1.0, config.economy());
            MarketEngine.SpreadResult few = engine.calculateSpread(item, metrics, 2, 1.0, config.economy());

            assertTrue(few.bpd().doubleValue() > many.bpd().doubleValue(),
                    "Fewer players should widen BPD");
        }

        @Test
        @DisplayName("liquidity reduces spread")
        void liquidityEffect() {
            ShopItem item = makeItem(1, "100.00");
            var metricsHighLiq = tradeMetrics(50, 50, 50, 50, 10);
            var metricsLowLiq = tradeMetrics(50, 50, 50, 50, 1);

            MarketEngine.SpreadResult highLiq = engine.calculateSpread(item, metricsHighLiq, 10, 1.0, config.economy());
            MarketEngine.SpreadResult lowLiq = engine.calculateSpread(item, metricsLowLiq, 10, 1.0, config.economy());

            assertTrue(highLiq.bpd().doubleValue() < lowLiq.bpd().doubleValue(),
                    "More distinct traders should reduce BPD");
        }

        @Test
        @DisplayName("spread values are scaled to 5 decimal places")
        void spreadPrecision() {
            ShopItem item = makeItem(1, "100.00");
            var metrics = tradeMetrics(100, 100, 100, 100, 5);

            MarketEngine.SpreadResult result = engine.calculateSpread(item, metrics, 10, 1.0, config.economy());
            assertEquals(5, result.bpd().scale());
            assertEquals(5, result.spd().scale());
        }
    }

    // -------------------------------------------------------------------------
    // getBuyPrice / getSellPrice
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getBuyPrice / getSellPrice")
    class PlayerPrices {

        @Test
        @DisplayName("buy price is above mid (mid = item price)")
        void buyPriceAboveMid() {
            ShopItem item = makeItem(1, "100.00");
            BigDecimal buyPrice = engine.getBuyPrice(item);
            assertTrue(buyPrice.compareTo(new BigDecimal("100.00")) > 0,
                    "Buy price should be above mid, got " + buyPrice);
        }

        @Test
        @DisplayName("sell price is below mid")
        void sellPriceBelowMid() {
            ShopItem item = makeItem(1, "100.00");
            BigDecimal sellPrice = engine.getSellPrice(item);
            assertTrue(sellPrice.compareTo(new BigDecimal("100.00")) < 0,
                    "Sell price should be below mid, got " + sellPrice);
        }

        @Test
        @DisplayName("buy price for larger quantities includes slippage")
        void buySlippage() {
            ShopItem item = makeItem(1, "100.00");
            BigDecimal oneItem = engine.getBuyPrice(item, 1);
            BigDecimal bulk = engine.getBuyPrice(item, 100);
            assertTrue(bulk.compareTo(oneItem) > 0,
                    "Bulk buy should cost more due to slippage");
        }

        @Test
        @DisplayName("sell price for larger quantities is reduced by slippage")
        void sellSlippage() {
            ShopItem item = makeItem(1, "100.00");
            BigDecimal oneItem = engine.getSellPrice(item, 1);
            BigDecimal bulk = engine.getSellPrice(item, 100);
            assertTrue(bulk.compareTo(oneItem) < 0,
                    "Bulk sell should yield less due to slippage");
        }

        @Test
        @DisplayName("prices have 2 decimal places")
        void priceScale() {
            ShopItem item = makeItem(1, "100.00");
            assertEquals(2, engine.getBuyPrice(item).scale());
            assertEquals(2, engine.getSellPrice(item).scale());
        }
    }

    // -------------------------------------------------------------------------
    // updateTrendStreak
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateTrendStreak")
    class TrendStreak {

        @Test
        @DisplayName("first move above threshold starts a streak of 1")
        void firstMove() {
            engine.updateTrendStreak(1, new BigDecimal("102.00"), new BigDecimal("100.00"));
            assertEquals(1, engine.getTrendStreak(1));
            assertEquals(MarketEngine.PriceTrend.Direction.UP, engine.getTrendDirection(1));
        }

        @Test
        @DisplayName("continuing in same direction increments streak")
        void continuingDirection() {
            engine.updateTrendStreak(1, new BigDecimal("102.00"), new BigDecimal("100.00"));
            engine.updateTrendStreak(1, new BigDecimal("104.00"), new BigDecimal("102.00"));
            assertEquals(2, engine.getTrendStreak(1));
        }

        @Test
        @DisplayName("reversing direction resets streak to 1")
        void reversal() {
            engine.updateTrendStreak(1, new BigDecimal("102.00"), new BigDecimal("100.00"));
            engine.updateTrendStreak(1, new BigDecimal("100.00"), new BigDecimal("102.00"));
            assertEquals(1, engine.getTrendStreak(1));
            assertEquals(MarketEngine.PriceTrend.Direction.DOWN, engine.getTrendDirection(1));
        }

        @Test
        @DisplayName("stable price (below threshold) resets streak to 0")
        void stableResetsStreak() {
            // Default threshold is 0.1%: 100.00 → 100.05 is only 0.05%, below threshold → STABLE
            engine.updateTrendStreak(1, new BigDecimal("100.05"), new BigDecimal("100.00"));
            assertEquals(0, engine.getTrendStreak(1));
            assertEquals(MarketEngine.PriceTrend.Direction.STABLE, engine.getTrendDirection(1));
        }

        @Test
        @DisplayName("zero old price is treated as stable")
        void zeroPriceIsStable() {
            engine.updateTrendStreak(1, new BigDecimal("100.00"), BigDecimal.ZERO);
            assertEquals(0, engine.getTrendStreak(1));
            assertEquals(MarketEngine.PriceTrend.Direction.STABLE, engine.getTrendDirection(1));
        }
    }

    // -------------------------------------------------------------------------
    // Volume tracking
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("recordBuy / recordSell")
    class VolumeTracking {

        @Test
        @DisplayName("recordBuy does not throw")
        void recordBuy() {
            assertDoesNotThrow(() -> {
                engine.recordBuy(1, 10);
                engine.recordBuy(1, 5);
                engine.recordBuy(2, 3);
            });
        }

        @Test
        @DisplayName("recordSell does not throw")
        void recordSell() {
            assertDoesNotThrow(() -> {
                engine.recordSell(1, 20);
                engine.recordSell(1, 5);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Price override
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Price override")
    class PriceOverrideTests {

        @Test
        @DisplayName("getOverride returns empty when no override exists")
        void noOverride() {
            assertTrue(engine.getOverride(999).isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // SpreadResult
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SpreadResult")
    class SpreadResultTests {

        @Test
        @DisplayName("has sensible default spread")
        void defaultSpread() {
            MarketEngine.SpreadResult def = MarketEngine.SpreadResult.DEFAULT;
            assertEquals(new BigDecimal("0.15000"), def.bpd());
            assertEquals(new BigDecimal("0.15000"), def.spd());
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private MarketEngine.TradeMetrics tradeMetrics(
            double weightedBuys,
            double weightedSells,
            int buyCount,
            int sellCount,
            int distinctTraders
    ) {
        return new MarketEngine.TradeMetrics(weightedBuys, weightedSells, buyCount, sellCount, distinctTraders);
    }

    private ShopItem makeItem(int id, String price) {
        return new ShopItem(
                id,
                Material.DIAMOND,
                "diamond",
                "Diamond",
                new BigDecimal(price),
                "misc",
                true,
                null,
                null,
                null, null,
                Instant.now(),
                Instant.now()
        );
    }

    private ShopItem makeItemWithMaxChange(int id, String price, double maxChange) {
        return new ShopItem(
                id,
                Material.DIAMOND,
                "diamond",
                "Diamond",
                new BigDecimal(price),
                "misc",
                true,
                null,
                null,
                maxChange, null,
                Instant.now(),
                Instant.now()
        );
    }
}
