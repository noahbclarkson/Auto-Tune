package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SellGuiListener implements Listener {

    private static SellGuiListener instance;

    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Inventory> activeSellInventories = new ConcurrentHashMap<>();

    @Inject
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public SellGuiListener(
            ShopManager shopManager,
            EconomyManager economyManager,
            ConfigManager configManager,
            DatabaseManager databaseManager
    ) {
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        instance = this;
    }

    @Nullable
    public static SellGuiListener getInstance() {
        return instance;
    }

    public void trackInventory(UUID playerId, Inventory inventory) {
        activeSellInventories.put(playerId, inventory);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Inventory tracked = activeSellInventories.get(playerId);
        if (tracked == null || !event.getInventory().equals(tracked)) {
            return;
        }
        activeSellInventories.remove(playerId, tracked);

        List<SellStack> pendingSales = new ArrayList<>();

        Inventory sellInventory = event.getInventory();
        for (int slot = 0; slot < sellInventory.getSize(); slot++) {
            ItemStack stack = sellInventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.matchSellItem(stack);
            if (shopItemOpt.isEmpty()) {
                // Item not in shop — return it to the player instead of losing it
                returnItem(player, stack);
                sellInventory.setItem(slot, null);
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            pendingSales.add(new SellStack(shopItem, stack.clone()));
            sellInventory.setItem(slot, null);
        }

        if (pendingSales.isEmpty()) {
            player.sendMessage(configManager.getMessage("sell.nothing-sold"));
            return;
        }

        processSales(player, pendingSales).whenComplete((summary, error) ->
                databaseManager.runOnMain(() -> finishSales(player, pendingSales, summary, error)));
    }

    private void returnItem(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }

    private CompletableFuture<SellBatchResult> processSales(Player player, List<SellStack> pendingSales) {
        SellBatchResult summary = new SellBatchResult();
        CompletableFuture<SellBatchResult> chain = CompletableFuture.completedFuture(summary);

        for (SellStack sale : pendingSales) {
            chain = chain.thenCompose(current -> economyManager
                    .processDetachedSellAsync(player, sale.item(), sale.stack().getAmount(), sale.stack())
                    .handle((result, error) -> {
                        if (error != null || result == null || !result.success()) {
                            current.failedStacks.add(sale.stack());
                            return current;
                        }

                        current.totalItemsSold += result.amount();
                        current.totalEarned = current.totalEarned.add(result.totalPrice());
                        return current;
                    }));
        }

        return chain;
    }

    private void finishSales(
            Player player,
            List<SellStack> pendingSales,
            @Nullable SellBatchResult summary,
            @Nullable Throwable error
    ) {
        SellBatchResult finalSummary = summary;
        if (error != null || finalSummary == null) {
            finalSummary = new SellBatchResult();
            for (SellStack sale : pendingSales) {
                finalSummary.failedStacks.add(sale.stack());
            }
        }

        for (ItemStack stack : finalSummary.failedStacks) {
            returnItem(player, stack);
        }

        if (finalSummary.totalItemsSold > 0) {
            player.sendMessage(configManager.getMessage("sell.summary", Map.of(
                    "amount", String.valueOf(finalSummary.totalItemsSold),
                    "price", configManager.formatCurrency(finalSummary.totalEarned)
            )));
        } else {
            player.sendMessage(configManager.getMessage("sell.nothing-sold"));
        }
    }

    private record SellStack(ShopItem item, ItemStack stack) {
    }

    private static final class SellBatchResult {
        private int totalItemsSold;
        private BigDecimal totalEarned = BigDecimal.ZERO;
        private final List<ItemStack> failedStacks = new ArrayList<>();
    }
}
