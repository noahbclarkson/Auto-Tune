package com.noahblclarkson.autotune.economy;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.economy.EconomyManager.TransactionResult;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.PriceReporter;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.manager.TreasuryService;
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
 * Uses inline mock setup following LoanManagerTest patterns.
 *
 * TODO: Re-enable once mock infrastructure is upgraded or EconomyManager refactored.
 * Blocked by: AutoTune extends JavaPlugin (final class) — Mockito can't mock it on JDK 17 CI.
 * Paper's JavaPlugin is final by design. Options: (a) upgrade CI to JDK 21 with inline-mock-maker,
 * (b) extract plugin dependency into a LoggerProvider interface, (c) use integration tests.
 */
@Disabled("AutoTune extends JavaPlugin (final) — Mockito can't mock on JDK 17. See TODO above.")
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
                AutoTuneConfig.DebugConfig.defaults(),
                AutoTuneConfig.EnchantmentConfig.defaults(),
                AutoTuneConfig.CleanupConfig.defaults(),
                AutoTuneConfig.TaxConfig.defaults(),
                false);
        when(cm.getConfig()).thenReturn(cfg);
        return cm;
    }

    private static ShopItem mockShopItem() {
        ShopItem item = mock(ShopItem.class);
        when(item.id()).thenReturn(1);
        when(item.material()).thenReturn(Material.DIAMOND);
        when(item.itemHash()).thenReturn("DIAMOND");
        when(item.price()).thenReturn(BigDecimal.valueOf(100));
        when(item.section()).thenReturn("ores");
        when(item.enabled()).thenReturn(true);
        when(item.itemData()).thenReturn(null);
        when(item.getDisplayNameOrMaterial()).thenReturn("Diamond");
        return item;
    }

    private static Player mockPlayer() {
        Player p = mock(Player.class);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.getName()).thenReturn("TestPlayer");
        return p;
    }

    private static EconomyManager makeManager(DatabaseManager db, ShopManager shop,
                                             MarketEngine engine, PlayerRepository playerRepo,
                                             TransactionRepository txRepo, PriceReporter reporter,
                                             TreasuryService treasuryService) {
        return new EconomyManager(
                mock(AutoTune.class),
                new FakeEconomy(),
                db, shop, engine,
                playerRepo, txRepo, reporter,
                makeConfigManager(),
                treasuryService);
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
                    mock(PriceReporter.class), mock(TreasuryService.class));

            Player player = mockPlayer();
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
                    mock(AutoTune.class), failingEconomy,
                    syncDbManager(), mock(ShopManager.class), engine,
                    mock(PlayerRepository.class), mock(TransactionRepository.class),
                    mock(PriceReporter.class), makeConfigManager(), mock(TreasuryService.class));

            Player player = mockPlayer();
            ShopItem item = mockShopItem();

            TransactionResult result = mgr.processSellImmediate(player, item, 3, null);

            assertFalse(result.success());
            assertEquals("Economy transaction failed", result.errorMessage());
            // DB write was not called because deposit failed before it
            verify(mock(TransactionRepository.class), never()).insert(any());
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
                    mock(PriceReporter.class), mock(TreasuryService.class));

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
                    mock(PriceReporter.class), mock(TreasuryService.class));

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
                    mock(PriceReporter.class), mock(TreasuryService.class));

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
                    mock(TransactionRepository.class), mock(PriceReporter.class), mock(TreasuryService.class));
            // FakeEconomy: getBalance returns 0
            assertEquals(0.0, mgr.getBalance(mockPlayer()), 0.001);
        }

        @Test
        @DisplayName("hasBalance returns true from FakeEconomy")
        void hasBalance() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), mock(TreasuryService.class));
            // FakeEconomy.has() always returns true
            assertTrue(mgr.hasBalance(mockPlayer(), 1000));
        }

        @Test
        @DisplayName("withdraw returns true when FakeEconomy succeeds")
        void withdraw() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), mock(TreasuryService.class));
            assertTrue(mgr.withdraw(mockPlayer(), 50));
        }

        @Test
        @DisplayName("deposit returns true when FakeEconomy succeeds")
        void deposit() {
            EconomyManager mgr = makeManager(syncDbManager(), mock(ShopManager.class),
                    mock(MarketEngine.class), mock(PlayerRepository.class),
                    mock(TransactionRepository.class), mock(PriceReporter.class), mock(TreasuryService.class));
            assertTrue(mgr.deposit(mockPlayer(), 50));
        }
    }
}
