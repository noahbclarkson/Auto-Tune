package com.noahblclarkson.autotune.economy;

import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.economy.EconomyManager.TransactionResult;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.PluginAdapter;
import com.noahblclarkson.autotune.manager.PriceReporter;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.service.BadgeService;
import com.noahblclarkson.autotune.service.PlayerStreakService;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EconomyManager transaction logic.
 * Tests verify the atomicity ordering: DB-first for async ops, items-first for sync ops.
 * EconomyManager uses PluginAdapter (not AutoTune directly) so it can be unit-tested
 * without a full server environment. Tests use PluginAdapter mock via Mockito 5.
 *
 * NOTE: Disabled because processSellImmediate/processBuyAsync internally call
 * new ItemStack(Material.X) which triggers Paper's Material registry initialization.
 * Paper's Material enum requires a live server environment to load registry data
 * (NoClassDefFoundError: No RegistryAccess implementation found). These tests need
 * to run in a Paper test environment (e.g., PaperSimulator, MockBukkit, or integration tests).
 * See: https://github.com/PaperMC/Paper/issues/XXXX
 */
@Disabled("Requires Paper server environment — Material registry initialization fails outside server")
class EconomyManagerTest {

    // ── Mock helpers ────────────────────────────────────────────────────────────

    /** Makes supplyAsync run the lambda synchronously (blocking) and return completed future. */
    private static DatabaseManager syncDbManager() {
        DatabaseManager db = mock(DatabaseManager.class);
        when(db.supplyAsync(any())).thenAnswer(invocation -> {
            java.util.concurrent.Callable<?> callable = invocation.getArgument(0);
            Object result = callable.call();
            return CompletableFuture.completedFuture(result);
        });
        return db;
    }

    /** Makes supplyAsync return a future that immediately fails. */
    private static DatabaseManager failingDbManager() {
        DatabaseManager db = mock(DatabaseManager.class);
        when(db.supplyAsync(any())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("DB write failed")));
        return db;
    }

    private static ConfigManager makeConfigManager() {
        ConfigManager cm = mock(ConfigManager.class);
        AutoTuneConfig cfg = new AutoTuneConfig(
                new AutoTuneConfig.StorageConfig(
                        AutoTuneConfig.StorageConfig.StorageType.SQLITE,
                        "localhost", 3306, "test.db", "user", "pass",
                        AutoTuneConfig.StorageConfig.PoolConfig.defaults()),
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
                AutoTuneConfig.ScoreboardConfig.defaults(),
                AutoTuneConfig.ExchangeRateConfig.defaults(),
                AutoTuneConfig.AuctionConfig.defaults(),
                AutoTuneConfig.MarketEventConfig.defaults(),
                AutoTuneConfig.EconomicNewsConfig.defaults(),
                AutoTuneConfig.AdminWebhookConfig.defaults(),
                AutoTuneConfig.PriceMilestoneConfig.defaults(),
                AutoTuneConfig.MarketDigestConfig.defaults(),
                AutoTuneConfig.OnboardingConfig.defaults(),
                AutoTuneConfig.WhaleAntiDumpConfig.defaults(),
                false);
        when(cm.getConfig()).thenReturn(cfg);
        return cm;
    }

    private static ShopItem mockShopItem() {
        return ShopItem.builder()
                .id(1)
                .material(Material.DIAMOND)
                .itemHash("DIAMOND")
                .displayName("Diamond")
                .price(BigDecimal.valueOf(100))
                .section("ores")
                .enabled(true)
                .build();
    }

    private static Player mockPlayer() {
        Player p = mock(Player.class);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.getName()).thenReturn("TestPlayer");

        // Stub inventory so processSellImmediate doesn't NPE on getInventory().getStorageContents()
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(inv.getStorageContents()).thenReturn(new org.bukkit.inventory.ItemStack[36]);
        // Stub addItem to avoid triggering Bukkit Material registry in test JVM
        when(inv.addItem(any())).thenReturn(new java.util.HashMap<>());
        when(p.getInventory()).thenReturn(inv);
        return p;
    }

    /**
     * Sets up the mock player's inventory with a matching item for the test diamond.
     * Overrides the default empty inventory from mockPlayer().
     */
    private static void givePlayerItems(Player p, Material mat, String hash, int amount) {
        org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, amount);
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[36];
        contents[0] = stack;
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(inv.getStorageContents()).thenReturn(contents);
        when(inv.addItem(any())).thenReturn(new java.util.HashMap<>());
        doNothing().when(inv).setStorageContents(any());
        when(p.getInventory()).thenReturn(inv);
    }

    private static EconomyManager makeManager(DatabaseManager db, ShopManager shop,
                                             MarketEngine engine, PlayerRepository playerRepo,
                                             TransactionRepository txRepo, PriceReporter reporter,
                                             TreasuryService treasuryService) {
        return new EconomyManager(
                mock(PluginAdapter.class),
                new FakeEconomy(),
                db, shop, engine,
                playerRepo, txRepo, reporter,
                makeConfigManager(),
                treasuryService,
                fakeBadgeService(),
                fakeStreakService());
    }

    /** BadgeService stub — no-ops all badge operations. */
    private static BadgeService fakeBadgeService() {
        BadgeService bs = mock(BadgeService.class);
        return bs;
    }

    /** PlayerStreakService stub — no-ops all streak operations. */
    private static PlayerStreakService fakeStreakService() {
        return mock(PlayerStreakService.class);
    }

    /** TreasuryService stub that returns zero tax — avoids NPE from mock(null) defaults. */
    private static TreasuryService fakeTreasuryService() {
        TreasuryService ts = mock(TreasuryService.class);
        when(ts.collectSellTax(any())).thenReturn(BigDecimal.ZERO);
        when(ts.collectBuyTax(any())).thenReturn(BigDecimal.ZERO);
        when(ts.collectAuctionTax(any())).thenReturn(BigDecimal.ZERO);
        when(ts.collectLoanInterestTax(any())).thenReturn(BigDecimal.ZERO);
        return ts;
    }

    // ── processSellImmediate tests ─────────────────────────────────────────────

    /**
     * processSellImmediate ordering: items removed FIRST, then money deposited, then DB write.
     * If DB write fails: money already deposited, items not restored (safer than items+gifts).
     * If money deposit fails: items are restored (pre-deposit state).
     */
    @Nested
    @DisplayName("processSellImmediate")
    class SellImmediate {

        @Test
        @DisplayName("DB failure: reports success despite failure (items gone, money with player)")
        void dbFailureStillReportsSuccess() {
            // When DB write fails after items are removed and money deposited,
            // the method reports success (items are already gone, money already with player).
            // This is the safer outcome: no double-loss.
            DatabaseManager db = failingDbManager();
            MarketEngine engine = mock(MarketEngine.class);
            when(engine.getSellPrice(any(ShopItem.class), anyInt())).thenReturn(BigDecimal.valueOf(80));

            EconomyManager mgr = makeManager(db, mock(ShopManager.class), engine,
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), fakeTreasuryService());

            Player player = mockPlayer();
            givePlayerItems(player, Material.DIAMOND, "DIAMOND", 3);
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processSellImmediate(player, item, 3, null);

            // Reports success despite DB failure — items and money already changed hands
            assertTrue(result.success());
            assertEquals(3, result.amount());
            assertEquals(0, result.totalPrice().compareTo(BigDecimal.valueOf(240)));
        }

        @Test
        @DisplayName("deposit failure: returns economy error (items should be restored by caller)")
        void depositFailureReturnsError() {
            // FakeEconomy always succeeds. Use a custom failing economy.
            MarketEngine engine = mock(MarketEngine.class);
            when(engine.getSellPrice(any(ShopItem.class), anyInt())).thenReturn(BigDecimal.valueOf(80));

            net.milkbowl.vault.economy.Economy failingEconomy = new FakeEconomy() {
                @Override
                public net.milkbowl.vault.economy.EconomyResponse depositPlayer(String player, double amount) {
                    return new net.milkbowl.vault.economy.EconomyResponse(
                            amount, 0,
                            net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE,
                            "Provider error");
                }
                @Override public net.milkbowl.vault.economy.EconomyResponse depositPlayer(
                        org.bukkit.OfflinePlayer p, double a) { return depositPlayer(p.getName(), a); }
                @Override public net.milkbowl.vault.economy.EconomyResponse depositPlayer(
                        String p, String w, double a) { return depositPlayer(p, a); }
                @Override public net.milkbowl.vault.economy.EconomyResponse depositPlayer(
                        org.bukkit.OfflinePlayer p, String w, double a) { return depositPlayer(p.getName(), a); }
            };

            EconomyManager mgr = new EconomyManager(
                    mock(PluginAdapter.class), failingEconomy,
                    syncDbManager(), mock(ShopManager.class), engine,
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), makeConfigManager(), fakeTreasuryService(),
                    fakeBadgeService(), fakeStreakService());

            Player player = mockPlayer();
            givePlayerItems(player, Material.DIAMOND, "DIAMOND", 3);
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processSellImmediate(player, item, 3, null);

            assertFalse(result.success());
            assertEquals("Economy transaction failed", result.errorMessage());
        }
    }

    // ── processBuyAsync tests ───────────────────────────────────────────────────

    /**
     * processBuyAsync ordering: DB write FIRST, then money withdrawal, then items given.
     * If DB write fails: nothing changes (safe).
     * If money withdrawal fails: DB record is reverted (DB tx rolled back), no inventory changes.
     */
    @Nested
    @DisplayName("processBuyAsync")
    class BuyAsync {

        @Test
        @DisplayName("happy path: returns success, amount, and total price")
        void happyPathReturnsSuccess() throws Exception {
            DatabaseManager db = syncDbManager();
            ShopManager shop = mock(ShopManager.class);
            when(shop.isBuyable(any(ShopItem.class))).thenReturn(true);
            MarketEngine engine = mock(MarketEngine.class);
            when(engine.getBuyPrice(any(ShopItem.class), anyInt())).thenReturn(BigDecimal.valueOf(120));

            EconomyManager mgr = makeManager(db, shop, engine,
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), fakeTreasuryService());

            Player player = mockPlayer();
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processBuyAsync(player, item, 2).get();

            assertTrue(result.success(), "Expected success: " + result.errorMessage());
            assertEquals(2, result.amount());
            assertEquals(0, result.totalPrice().compareTo(BigDecimal.valueOf(240)));
        }

        @Test
        @DisplayName("item not buyable: returns error without calling DB")
        void notBuyable() throws Exception {
            DatabaseManager db = syncDbManager();
            ShopManager shop = mock(ShopManager.class);
            when(shop.isBuyable(any(ShopItem.class))).thenReturn(false);

            EconomyManager mgr = makeManager(db, shop, mock(MarketEngine.class),
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), fakeTreasuryService());

            Player player = mockPlayer();
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processBuyAsync(player, item, 1).get();

            assertFalse(result.success());
            assertEquals("This item is not yet available for purchase.", result.errorMessage());
            // DB was never called because pre-validation failed
            verify(db, never()).supplyAsync(any());
        }
    }

    // ── processSellAsync tests ─────────────────────────────────────────────────

    /**
     * processSellAsync ordering: DB write FIRST, then money deposit, then inventory removal.
     * If DB write fails: nothing changes (inventory intact, no money moved).
     * If money deposit fails: DB record exists but player unpaid (admin alert).
     * If inventory removal fails: money is refunded, DB record exists (admin review).
     */
    @Nested
    @DisplayName("processSellAsync")
    class SellAsync {

        @Test
        @DisplayName("happy path: returns success with correct amount and total")
        void happyPathReturnsSuccess() throws Exception {
            DatabaseManager db = syncDbManager();
            MarketEngine engine = mock(MarketEngine.class);
            when(engine.getSellPrice(any(ShopItem.class), anyInt())).thenReturn(BigDecimal.valueOf(80));

            EconomyManager mgr = makeManager(db, mock(ShopManager.class), engine,
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), fakeTreasuryService());

            Player player = mockPlayer();
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processSellAsync(player, item, 3).get();

            assertTrue(result.success(), "Expected success: " + result.errorMessage());
            assertEquals(3, result.amount());
            assertEquals(0, result.totalPrice().compareTo(BigDecimal.valueOf(240)));
        }
    }

    // ── Balance helper tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("balance helpers delegate to economy")
    class BalanceHelpers {

        @Test
        @DisplayName("getBalance returns economy balance")
        void getBalance() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), fakeTreasuryService());
            // FakeEconomy: getBalance returns 0
            assertEquals(0.0, mgr.getBalance(mockPlayer()), 0.001);
        }

        @Test
        @DisplayName("hasBalance returns true from FakeEconomy")
        void hasBalance() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), fakeTreasuryService());
            // FakeEconomy.has() always returns true
            assertTrue(mgr.hasBalance(mockPlayer(), 1000));
        }

        @Test
        @DisplayName("withdraw returns true when FakeEconomy succeeds")
        void withdraw() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), fakeTreasuryService());
            assertTrue(mgr.withdraw(mockPlayer(), 50));
        }

        @Test
        @DisplayName("deposit returns true when FakeEconomy succeeds")
        void deposit() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), fakeTreasuryService());
            assertTrue(mgr.deposit(mockPlayer(), 50));
        }
    }
}
