package com.noahblclarkson.autotune.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.CartItem;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import com.noahblclarkson.autotune.util.ItemSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class EconomyManager {

    private final Economy economy;
    private final DatabaseManager databaseManager;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final PlayerRepository playerRepository;
    private final TransactionRepository transactionRepository;

    @Inject
    public EconomyManager(
            Economy economy,
            DatabaseManager databaseManager,
            ShopManager shopManager,
            MarketEngine marketEngine,
            PlayerRepository playerRepository,
            TransactionRepository transactionRepository
    ) {
        this.economy = economy;
        this.databaseManager = databaseManager;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.playerRepository = playerRepository;
        this.transactionRepository = transactionRepository;
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

        if (!withdraw(player, totalPrice.doubleValue())) {
            return CompletableFuture.completedFuture(TransactionResult.economyError());
        }

        giveItems(player, item, amount);

        return databaseManager.supplyAsync(() -> {
            Transaction transaction = Transaction.builder()
                    .playerUuid(playerId)
                    .itemId(item.id())
                    .type(TransactionType.BUY)
                    .amount(amount)
                    .pricePerUnit(pricePerUnit)
                    .totalPrice(totalPrice)
                    .build();

            transactionRepository.insert(transaction);
            playerRepository.addTransaction(playerId, totalPrice, true);
            marketEngine.recordBuy(item.id(), amount);

            return TransactionResult.success(TransactionType.BUY, amount, totalPrice);
        });
    }

    public CompletableFuture<TransactionResult> processSellAsync(@NotNull Player player, @NotNull ShopItem item, int amount) {
        java.util.UUID playerId = player.getUniqueId();
        int playerHas = countItems(player, item);
        if (playerHas < amount) {
            return CompletableFuture.completedFuture(TransactionResult.insufficientItems(playerHas));
        }

        BigDecimal pricePerUnit = marketEngine.getSellPrice(item, amount);
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        if (!removeItems(player, item, amount)) {
            return CompletableFuture.completedFuture(TransactionResult.error("Failed to remove items"));
        }

        if (!deposit(player, totalPrice.doubleValue())) {
            giveItems(player, item, amount);
            return CompletableFuture.completedFuture(TransactionResult.economyError());
        }

        return databaseManager.supplyAsync(() -> {
            Transaction transaction = Transaction.builder()
                    .playerUuid(playerId)
                    .itemId(item.id())
                    .type(TransactionType.SELL)
                    .amount(amount)
                    .pricePerUnit(pricePerUnit)
                    .totalPrice(totalPrice)
                    .build();

            transactionRepository.insert(transaction);
            playerRepository.addTransaction(playerId, totalPrice, false);
            marketEngine.recordSell(item.id(), amount);
            shopManager.invalidateBuyableCache(item.id());

            return TransactionResult.success(TransactionType.SELL, amount, totalPrice);
        });
    }

    public TransactionResult processSellImmediate(@NotNull Player player, @NotNull ShopItem item, int amount) {
        java.util.UUID playerId = player.getUniqueId();
        BigDecimal pricePerUnit = marketEngine.getSellPrice(item, amount);
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));

        if (!deposit(player, totalPrice.doubleValue())) {
            return TransactionResult.economyError();
        }

        databaseManager.runAsync(() -> {
            Transaction transaction = Transaction.builder()
                    .playerUuid(playerId)
                    .itemId(item.id())
                    .type(TransactionType.SELL)
                    .amount(amount)
                    .pricePerUnit(pricePerUnit)
                    .totalPrice(totalPrice)
                    .build();

            transactionRepository.insert(transaction);
            playerRepository.addTransaction(playerId, totalPrice, false);
            marketEngine.recordSell(item.id(), amount);
            shopManager.invalidateBuyableCache(item.id());
        });

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

        for (CartItem cartItem : cart) {
            if (!cartItem.isBuying()) {
                removeItems(player, cartItem.shopItem(), cartItem.quantity());
            }
        }

        if (netCost.compareTo(BigDecimal.ZERO) > 0) {
            withdraw(player, netCost.doubleValue());
        } else if (netCost.compareTo(BigDecimal.ZERO) < 0) {
            deposit(player, netCost.abs().doubleValue());
        }

        for (CartItem cartItem : cart) {
            if (cartItem.isBuying()) {
                giveItems(player, cartItem.shopItem(), cartItem.quantity());
            }
        }

        final BigDecimal finalNetCost = netCost;
        return databaseManager.supplyAsync(() -> {
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

                if (cartItem.isBuying()) {
                    marketEngine.recordBuy(cartItem.shopItem().id(), cartItem.quantity());
                } else {
                    marketEngine.recordSell(cartItem.shopItem().id(), cartItem.quantity());
                }
            }

            playerRepository.addTransaction(playerId, finalNetCost.abs(),
                    finalNetCost.compareTo(BigDecimal.ZERO) > 0);

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
