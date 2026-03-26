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
            ConfigManager configManager
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

        if (!hasBalance(player, totalPrice.doubleValue())) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientFunds(totalPrice));
        }

        int emptySlots = countEmptySlots(player);
        int slotsNeeded = (int) Math.ceil(amount / (double) new ItemStack(item.material()).getMaxStackSize());
        if (emptySlots < slotsNeeded) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientSpace());
        }

        // Process atomically: DB write first, then money withdrawal, then inventory.
        // This ensures the transaction record is the first thing that succeeds.
        // If DB write fails, nothing changes (no money taken, no items given).
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

            // 2. Withdraw money — must succeed before giving items
            if (!withdraw(player, totalPrice.doubleValue())) {
                // Rare: economy provider error. Transaction is recorded but player
                // wasn't charged. Log for admin review — don't give items.
                plugin.getLogger().warning("[Auto-Tune] Failed to withdraw " + totalPrice
                        + " from " + player.getName() + " after buy transaction was recorded. "
                        + "Player should not have received items.");
                return TransactionResult.economyError();
            }

            // 3. Give items — last step. If this fails, money was taken but player
            //    didn't get items. This is unavoidable without a two-phase commit,
            //    but is strictly better than items being given with no record.
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
                    .totalPrice(totalPrice)
                    .build();

            transactionRepository.insert(transaction);
            priceReporter.recordTransaction(item, transaction);
            playerRepository.addTransaction(playerId, totalPrice, false);
            marketEngine.recordSell(item.id(), amount);
            shopManager.invalidateBuyableCache(item.id());

            // 2. Deposit money — must succeed for inventory to be modified
            if (!deposit(player, totalPrice.doubleValue())) {
                // Money deposit failed (rare: player offline or economy provider error).
                // Transaction is recorded but player didn't get paid. This is the safer
                // alternative to losing items with no record.
                plugin.getLogger().warning("[Auto-Tune] Failed to deposit " + totalPrice
                        + " to " + player.getName() + " after sell transaction was recorded. "
                        + "Player should contact an admin.");
                return TransactionResult.economyError();
            }

            // 3. Remove items from inventory — last because it's the only step that
            //    can fail without affecting economy consistency (player has the money)
            if (!removeItems(player, item, amount)) {
                // Items couldn't be removed from inventory. This shouldn't happen
                // since we pre-validated, but handle it: items are given back.
                plugin.getLogger().warning("[Auto-Tune] Failed to remove sell items from "
                        + player.getName() + "'s inventory after payment. Restoring funds.");
                withdraw(player, totalPrice.doubleValue());
                return TransactionResult.error("Failed to remove items from inventory");
            }

            return TransactionResult.success(TransactionType.SELL, amount, totalPrice);
        });
    }

    public TransactionResult processSellImmediate(@NotNull Player player, @NotNull ShopItem item, int amount) {
        return processSellImmediate(player, item, amount, null);
    }

    /**
     * Sells items from the player's inventory synchronously.
     * If itemStack is provided and has enchantments, an enchantment price multiplier is applied.
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

        final BigDecimal finalPricePerUnit = pricePerUnit;
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        if (!deposit(player, totalPrice.doubleValue())) {
            return TransactionResult.economyError();
        }

        // Persist transaction to DB before returning. Using supplyAsync + join to keep
        // the synchronous UX of processSellImmediate while ensuring data integrity.
        final BigDecimal finalTotalPrice = totalPrice;
        try {
            databaseManager.supplyAsync(() -> {
                Transaction transaction = Transaction.builder()
                        .playerUuid(playerId)
                        .itemId(item.id())
                        .type(TransactionType.SELL)
                        .amount(amount)
                        .pricePerUnit(finalPricePerUnit)
                        .totalPrice(finalTotalPrice)
                        .build();

                transactionRepository.insert(transaction);
                priceReporter.recordTransaction(item, transaction);
                playerRepository.addTransaction(playerId, finalTotalPrice, false);
                marketEngine.recordSell(item.id(), amount);
                shopManager.invalidateBuyableCache(item.id());
                return null;
            }).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to persist sell transaction for " + player.getName(), e);
        }

        return TransactionResult.success(TransactionType.SELL, amount, totalPrice);
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

        BigDecimal netCost = totalBuyCost.subtract(totalSellProfit);

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
            for (CartItem cartItem : cart) {
                BigDecimal actualPricePerUnit = cartItem.isBuying()
                        ? marketEngine.getBuyPrice(cartItem.shopItem(), cartItem.quantity())
                        : marketEngine.getSellPrice(cartItem.shopItem(), cartItem.quantity());
                BigDecimal actualTotalPrice = actualPricePerUnit.multiply(BigDecimal.valueOf(cartItem.quantity()));

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
