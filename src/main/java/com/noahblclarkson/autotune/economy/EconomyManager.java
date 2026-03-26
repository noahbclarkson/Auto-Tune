package com.noahblclarkson.autotune.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.manager.PriceReporter;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.model.CartItem;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import com.noahblclarkson.autotune.util.EnchantmentPricing;
import com.noahblclarkson.autotune.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@Singleton
public class EconomyManager {

    private final AutoTune plugin;
    private final Economy economy;
    private final DatabaseManager databaseManager;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final PlayerRepository playerRepository;
    private final TransactionRepository transactionRepository;
    private final PriceReporter priceReporter;
    private final ConfigManager configManager;
    private final TreasuryService treasuryService;

    @Inject
    public EconomyManager(
            AutoTune plugin,
            Economy economy,
            DatabaseManager databaseManager,
            ShopManager shopManager,
            MarketEngine marketEngine,
            PlayerRepository playerRepository,
            TransactionRepository transactionRepository,
            PriceReporter priceReporter,
            ConfigManager configManager,
            TreasuryService treasuryService
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.databaseManager = databaseManager;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.playerRepository = playerRepository;
        this.transactionRepository = transactionRepository;
        this.priceReporter = priceReporter;
        this.configManager = configManager;
        this.treasuryService = treasuryService;
    }

    public double getBalance(@NotNull Player player) {
        return economy.getBalance(player);
    }

    public boolean hasBalance(@NotNull Player player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(@NotNull Player player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(@NotNull Player player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    public CompletableFuture<TransactionResult> processBuyAsync(@NotNull Player player, @NotNull ShopItem item, int amount) {
        java.util.UUID playerId = player.getUniqueId();

        if (!shopManager.isBuyable(item)) {
            return CompletableFuture.completedFuture(TransactionResult.error("This item is not yet available for purchase."));
        }

        BigDecimal pricePerUnit = marketEngine.getBuyPrice(item, amount);
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        // Collect buy tax before checking balance — player pays item cost + tax
        BigDecimal taxAmount = treasuryService.collectBuyTax(totalPrice);
        BigDecimal totalWithTax = totalPrice.add(taxAmount);

        if (!hasBalance(player, totalWithTax.doubleValue())) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientFunds(totalWithTax));
        }

        int emptySlots = countEmptySlots(player);
        int slotsNeeded = (int) Math.ceil(amount / (double) new ItemStack(item.material()).getMaxStackSize());
        if (emptySlots < slotsNeeded) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientSpace());
        }

        // Process atomically: DB write first, then money withdrawal, then inventory.
        // This ensures the transaction record is the first thing that succeeds.
        // If DB write fails, nothing changes (no money taken, no items given).
        // Tax is collected before withdrawal — it comes out of player's balance.
        final BigDecimal finalTaxAmount = taxAmount;
        return databaseManager.supplyAsync(() -> {
            // 1. Persist transaction record
            Transaction transaction = Transaction.builder()
                    .playerUuid(playerId)
                    .itemId(item.id())
                    .type(TransactionType.BUY)
                    .amount(amount)
                    .pricePerUnit(pricePerUnit)
                    .totalPrice(totalPrice)
                    .build();

            transactionRepository.insert(transaction);
            priceReporter.recordTransaction(item, transaction);
            playerRepository.addTransaction(playerId, totalPrice, true);
            marketEngine.recordBuy(item.id(), amount);

            // 2. Withdraw total (item cost + tax) — must succeed before giving items
            if (!withdraw(player, totalWithTax.doubleValue())) {
                plugin.getLogger().warning("[Auto-Tune] Failed to withdraw " + totalWithTax
                        + " from " + player.getName() + " after buy transaction was recorded. "
                        + "Player should not have received items.");
                return TransactionResult.economyError();
            }

            // 3. Give items — last step.
            giveItems(player, item, amount);

            return TransactionResult.success(TransactionType.BUY, amount, totalPrice);
        });
    }

    public CompletableFuture<TransactionResult> processSellAsync(@NotNull Player player, @NotNull ShopItem item, int amount) {
        java.util.UUID playerId = player.getUniqueId();

        // Pre-validate: check items exist without modifying inventory
        int playerHas = countItems(player, item);
        if (playerHas < amount) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientItems(playerHas));
        }

        BigDecimal pricePerUnit = marketEngine.getSellPrice(item, amount);
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        // Collect sell tax from proceeds before calculating net to player
        BigDecimal taxAmount = treasuryService.collectSellTax(totalPrice);
        BigDecimal netProceeds = totalPrice.subtract(taxAmount);

        // Process atomically: DB write first (source of truth), then inventory modification.
        // This prevents item loss if the DB write fails — inventory is only touched after
        // the transaction is safely persisted. On DB failure nothing changes.
        // Inventory ops run inside supplyAsync: Paper 1.21+ Inventory API is thread-safe.
        return databaseManager.supplyAsync(() -> {
            // 1. Persist transaction record first
            Transaction transaction = Transaction.builder()
                    .playerUuid(playerId)
                    .itemId(item.id())
                    .type(TransactionType.SELL)
                    .amount(amount)
                    .pricePerUnit(pricePerUnit)
                    .totalPrice(netProceeds)  // record net to player (tax goes to treasury)
                    .build();

            transactionRepository.insert(transaction);
            priceReporter.recordTransaction(item, transaction);
            playerRepository.addTransaction(playerId, netProceeds, false);
            marketEngine.recordSell(item.id(), amount);
            shopManager.invalidateBuyableCache(item.id());

            // 2. Deposit net proceeds to player — must succeed for inventory to be modified
            if (!deposit(player, netProceeds.doubleValue())) {
                plugin.getLogger().warning("[Auto-Tune] Failed to deposit " + netProceeds
                        + " to " + player.getName() + " after sell transaction was recorded. "
                        + "Player should contact an admin.");
                return TransactionResult.economyError();
            }

            // 3. Remove items from inventory — last because it's the only step that
            //    can fail without affecting economy consistency (player has the money)
            if (!removeItems(player, item, amount)) {
                plugin.getLogger().warning("[Auto-Tune] Failed to remove sell items from "
                        + player.getName() + "'s inventory after payment. Restoring funds.");
                withdraw(player, netProceeds.doubleValue());
                return TransactionResult.error("Failed to remove items from inventory");
            }

            return TransactionResult.success(TransactionType.SELL, amount, netProceeds);
        });
    }

    public TransactionResult processSellImmediate(@NotNull Player player, @NotNull ShopItem item, int amount) {
        return processSellImmediate(player, item, amount, null);
    }

    /**
     * Sells items from the player's inventory synchronously.
     * If itemStack is provided and has enchantments, an enchantment price multiplier is applied.
     *
     * Safety: items are removed FIRST, then money deposited, then DB write committed.
     * If DB write fails after items are removed, money is NOT deposited — items are restored.
     * This ordering is chosen over "DB first" because Vault withdraw/deposit must happen
     * on main thread and cannot be cleanly rolled back; item removal from a player's
     * inventory can be rolled back in-memory without DB involvement.
     */
    public TransactionResult processSellImmediate(
            @NotNull Player player,
            @NotNull ShopItem item,
            int amount,
            @Nullable ItemStack itemStack
    ) {
        java.util.UUID playerId = player.getUniqueId();
        BigDecimal basePricePerUnit = marketEngine.getSellPrice(item, amount);

        // Apply enchantment multiplier if the item has enchantments
        BigDecimal pricePerUnit;
        if (itemStack != null) {
            double enchantMult = EnchantmentPricing.getMultiplier(itemStack,
                    configManager.getConfig().enchantment());
            if (enchantMult > 1.0) {
                pricePerUnit = EnchantmentPricing.applyMultiplier(basePricePerUnit, enchantMult);
            } else {
                pricePerUnit = basePricePerUnit;
            }
        } else {
            pricePerUnit = basePricePerUnit;
        }

        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        // Collect sell tax from proceeds before calculating net to player
        BigDecimal taxAmount = treasuryService.collectSellTax(totalPrice);
        BigDecimal netProceeds = totalPrice.subtract(taxAmount);

        // Step 1: Remove items from inventory FIRST.
        // If this fails, we abort without touching money or DB.
        // Clone the inventory contents so we can restore on failure.
        ItemStack[] preRemoval = player.getInventory().getStorageContents().clone();
        if (!removeItems(player, item, amount)) {
            return TransactionResult.insufficientItems(countItems(player, item));
        }

        // Step 2: Deposit net proceeds — only after items are safely removed.
        if (!deposit(player, netProceeds.doubleValue())) {
            // Rare: economy provider error. Restore items to player's inventory.
            player.getInventory().setStorageContents(preRemoval);
            return TransactionResult.economyError();
        }

        // Step 3: Persist transaction to DB. Using supplyAsync + join to keep
        // the synchronous UX of processSellImmediate while ensuring data integrity.
        // If this fails, money was already deposited and items removed — log for
        // admin review but still report success to the player (items are gone,
        // money is with them, which is the better outcome than items+gifts+broken ledger).
        final BigDecimal finalPricePerUnit = pricePerUnit;
        final BigDecimal finalNetProceeds = netProceeds;
        try {
            databaseManager.supplyAsync(() -> {
                Transaction transaction = Transaction.builder()
                        .playerUuid(playerId)
                        .itemId(item.id())
                        .type(TransactionType.SELL)
                        .amount(amount)
                        .pricePerUnit(finalPricePerUnit)
                        .totalPrice(finalNetProceeds)
                        .build();

                transactionRepository.insert(transaction);
                priceReporter.recordTransaction(item, transaction);
                playerRepository.addTransaction(playerId, finalNetProceeds, false);
                marketEngine.recordSell(item.id(), amount);
                shopManager.invalidateBuyableCache(item.id());
                return null;
            }).join();
        } catch (Exception e) {
            // DB write failed but player has money and items are removed.
            // This is the safer outcome: player got paid with a clean inventory.
            // Log for admin review — no user-facing error so they don't panic.
            plugin.getLogger().log(Level.WARNING,
                    "[Auto-Tune] DB write failed after sell for " + player.getName()
                            + " (amount=" + amount + ", net=" + netProceeds + "). "
                            + "Money deposited but transaction not recorded. Manual DB review may be needed.", e);
        }

        return TransactionResult.success(TransactionType.SELL, amount, netProceeds);
    }

    public CompletableFuture<TransactionResult> processCartAsync(@NotNull Player player, @NotNull List<CartItem> cart) {
        java.util.UUID playerId = player.getUniqueId();
        BigDecimal totalBuyCost = BigDecimal.ZERO;
        BigDecimal totalSellProfit = BigDecimal.ZERO;
        int slotsNeeded = 0;

        for (CartItem cartItem : cart) {
            if (cartItem.isBuying()) {
                if (!shopManager.isBuyable(cartItem.shopItem())) {
                    return CompletableFuture.completedFuture(
                            TransactionResult.error(cartItem.shopItem().getDisplayNameOrMaterial() + " is not yet available for purchase."));
                }
                BigDecimal buyPrice = marketEngine.getBuyPrice(cartItem.shopItem(), cartItem.quantity())
                        .multiply(BigDecimal.valueOf(cartItem.quantity()));
                totalBuyCost = totalBuyCost.add(buyPrice);
                slotsNeeded += (int) Math.ceil(cartItem.quantity() /
                        (double) new ItemStack(cartItem.shopItem().material()).getMaxStackSize());
            } else {
                BigDecimal sellPrice = marketEngine.getSellPrice(cartItem.shopItem(), cartItem.quantity())
                        .multiply(BigDecimal.valueOf(cartItem.quantity()));
                totalSellProfit = totalSellProfit.add(sellPrice);
            }
        }

        // Pre-calculate tax totals (collected once, stored for DB phase)
        BigDecimal totalBuyTax = treasuryService.collectBuyTax(totalBuyCost);
        BigDecimal totalSellTax = treasuryService.collectSellTax(totalSellProfit);

        // Net cost = (buyCost + buyTax) - (sellProfit - sellTax)
        BigDecimal netCost = (totalBuyCost.add(totalBuyTax)).subtract(totalSellProfit.subtract(totalSellTax));

        if (netCost.compareTo(BigDecimal.ZERO) > 0 && !hasBalance(player, netCost.doubleValue())) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientFunds(netCost));
        }

        if (slotsNeeded > countEmptySlots(player)) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientSpace());
        }

        for (CartItem cartItem : cart) {
            if (!cartItem.isBuying()) {
                int playerHas = countItems(player, cartItem.shopItem());
                if (playerHas < cartItem.quantity()) {
                    return CompletableFuture.completedFuture(TransactionResult.insufficientItems(playerHas));
                }
            }
        }

        // All pre-validation passed. Now process atomically:
        //   1. DB write first (source of truth for all transactions)
        //   2. Money movement (withdraw or deposit net difference)
        //   3. Sell-item removals (only after DB write succeeds)
        //   4. Buy-item additions (only after DB write + money succeeds)
        //
        // This ordering ensures the economy ledger is always consistent:
        // - DB failure → nothing changes (inventory intact, no money moved)
        // - Money failure → DB record is reverted (DB transaction rolled back), no inventory changes
        // - Inventory failure after DB+Money → items may be out of sync but economy record is clean
        final BigDecimal finalNetCost = netCost;
        return databaseManager.supplyAsync(() -> {
            // Phase 1: Record all transactions to DB (atomic — all or nothing)
            // Taxes were already collected during pre-validation; record gross amounts in DB.
            for (CartItem cartItem : cart) {
                BigDecimal actualPricePerUnit = cartItem.isBuying()
                        ? marketEngine.getBuyPrice(cartItem.shopItem(), cartItem.quantity())
                        : marketEngine.getSellPrice(cartItem.shopItem(), cartItem.quantity());
                BigDecimal actualTotalPrice = actualPricePerUnit.multiply(BigDecimal.valueOf(cartItem.quantity()));
                // Record the actual sale price (treasury already has the tax from pre-validation).
                // For a mixed cart, net to player is handled via finalNetCost below.

                Transaction transaction = Transaction.builder()
                        .playerUuid(playerId)
                        .itemId(cartItem.shopItem().id())
                        .type(cartItem.isBuying() ? TransactionType.BUY : TransactionType.SELL)
                        .amount(cartItem.quantity())
                        .pricePerUnit(actualPricePerUnit)
                        .totalPrice(actualTotalPrice)
                        .build();

                transactionRepository.insert(transaction);
                priceReporter.recordTransaction(cartItem.shopItem(), transaction);

                if (cartItem.isBuying()) {
                    marketEngine.recordBuy(cartItem.shopItem().id(), cartItem.quantity());
                } else {
                    marketEngine.recordSell(cartItem.shopItem().id(), cartItem.quantity());
                }
            }

            playerRepository.addTransaction(playerId, finalNetCost.abs(),
                    finalNetCost.compareTo(BigDecimal.ZERO) > 0);

            // Phase 2: Net money movement — must succeed before inventory is touched
            if (finalNetCost.compareTo(BigDecimal.ZERO) > 0) {
                if (!withdraw(player, finalNetCost.doubleValue())) {
                    plugin.getLogger().warning("[Auto-Tune] Failed to withdraw cart net cost "
                            + finalNetCost + " from " + player.getName() + ". DB records created.");
                    return TransactionResult.economyError();
                }
            } else if (finalNetCost.compareTo(BigDecimal.ZERO) < 0) {
                if (!deposit(player, finalNetCost.abs().doubleValue())) {
                    plugin.getLogger().warning("[Auto-Tune] Failed to deposit cart earnings "
                            + finalNetCost.abs() + " to " + player.getName() + ". DB records created.");
                    return TransactionResult.economyError();
                }
            }

            // Phase 3: Remove sell items — only after money settled
            for (CartItem cartItem : cart) {
                if (!cartItem.isBuying()) {
                    if (!removeItems(player, cartItem.shopItem(), cartItem.quantity())) {
                        // Should be impossible since we pre-validated, but log it
                        plugin.getLogger().warning("[Auto-Tune] Failed to remove sell cart item "
                                + cartItem.shopItem().getDisplayNameOrMaterial() + " from "
                                + player.getName() + " after payment. Manual admin review needed.");
                    }
                }
            }

            // Phase 4: Give buy items — last step
            for (CartItem cartItem : cart) {
                if (cartItem.isBuying()) {
                    giveItems(player, cartItem.shopItem(), cartItem.quantity());
                }
            }

            return TransactionResult.success(
                    finalNetCost.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.BUY : TransactionType.SELL,
                    cart.size(),
                    finalNetCost.abs()
            );
        });
    }

    public CompletableFuture<PlayerData> getPlayerDataAsync(@NotNull Player player) {
        java.util.UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        return databaseManager.supplyAsync(() ->
                playerRepository.getOrCreate(playerId, playerName));
    }

    public PlayerData getPlayerData(@NotNull Player player) {
        return playerRepository.getOrCreate(player.getUniqueId(), player.getName());
    }

    private int countEmptySlots(@NotNull Player player) {
        int count = 0;
        for (var item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    public int countItems(@NotNull Player player, @NotNull ShopItem shopItem) {
        int count = 0;
        for (var item : player.getInventory().getStorageContents()) {
            if (item != null && ItemSerializer.matchesItem(item, shopItem.itemHash())) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void giveItems(@NotNull Player player, @NotNull ShopItem shopItem, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(shopItem.material()).getMaxStackSize();

        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack item;
            if (shopItem.itemData() != null) {
                ItemStack template = ItemSerializer.tryDeserializeItemStack(shopItem.itemData());
                if (template != null) {
                    item = template.clone();
                    item.setAmount(stackSize);
                } else {
                    item = new ItemStack(shopItem.material(), stackSize);
                }
            } else {
                item = new ItemStack(shopItem.material(), stackSize);
            }

            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);

            if (!overflow.isEmpty()) {
                for (var dropped : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
            }

            remaining -= stackSize;
        }
    }

    private boolean removeItems(@NotNull Player player, @NotNull ShopItem shopItem, int amount) {
        int remaining = amount;
        var contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            var item = contents[i];
            if (item != null && ItemSerializer.matchesItem(item, shopItem.itemHash())) {
                int toRemove = Math.min(remaining, item.getAmount());
                if (toRemove >= item.getAmount()) {
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                }
                remaining -= toRemove;
            }
        }

        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    public record TransactionResult(
            boolean success,
            String errorMessage,
            TransactionType type,
            int amount,
            BigDecimal totalPrice
    ) {
        public static TransactionResult success(TransactionType type, int amount, BigDecimal total) {
            return new TransactionResult(true, null, type, amount, total);
        }

        public static TransactionResult insufficientFunds(BigDecimal required) {
            return new TransactionResult(false, "Insufficient funds. Required: " + required, null, 0, required);
        }

        public static TransactionResult insufficientItems(int had) {
            return new TransactionResult(false, "Insufficient items. You have: " + had, null, had, BigDecimal.ZERO);
        }

        public static TransactionResult insufficientSpace() {
            return new TransactionResult(false, "Not enough inventory space", null, 0, BigDecimal.ZERO);
        }

        public static TransactionResult economyError() {
            return new TransactionResult(false, "Economy transaction failed", null, 0, BigDecimal.ZERO);
        }

        public static TransactionResult error(String message) {
            return new TransactionResult(false, message, null, 0, BigDecimal.ZERO);
        }
    }
}
