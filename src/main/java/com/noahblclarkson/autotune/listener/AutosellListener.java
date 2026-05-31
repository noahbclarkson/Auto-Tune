package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Singleton
@SuppressWarnings("PMD")
public class AutosellListener implements Listener {

    private final AutosellManager autosellManager;
    private final ShopManager shopManager;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;

    @Inject
    public AutosellListener(
            AutosellManager autosellManager,
            ShopManager shopManager,
            ConfigManager configManager,
            DatabaseManager databaseManager
    ) {
        this.autosellManager = autosellManager;
        this.shopManager = shopManager;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!autosellManager.hasAnyItemEnabled(player.getUniqueId())) {
            return;
        }

        var stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        Optional<ShopItem> shopItemOpt = autosellManager.getSellableItem(stack);
        if (shopItemOpt.isEmpty()) {
            return;
        }

        ShopItem shopItem = shopItemOpt.get();

        if (!autosellManager.isItemEnabled(player.getUniqueId(), shopItem.id())) {
            return;
        }

        // Check minimum price threshold
        if (!passesMinimumPrice(player, shopItem)) {
            return;
        }

        ItemStack saleStack = stack.clone();
        event.setCancelled(true);
        event.getItem().remove();

        autosellManager.sellPickupAsync(player, shopItem, saleStack, saleStack.getAmount())
                .whenComplete((result, error) -> databaseManager.runOnMain(() -> {
                    if (error != null || result == null || !result.success()) {
                        returnItem(player, saleStack);
                        return;
                    }

                    autosellManager.sendAutosellActionBar(player, shopItem, result.amount(), result.totalPrice());
                    playPickupSound(player);
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!autosellManager.hasAnyItemEnabled(player.getUniqueId())) {
            return;
        }

        scanAndSellInventory(player);
    }

    public void scanAndSellInventory(Player player) {
        Set<Integer> enabledItems = autosellManager.getEnabledItems(player.getUniqueId());
        if (enabledItems.isEmpty()) {
            return;
        }

        List<AutosellStack> pendingSales = new ArrayList<>();

        ItemStack[] storageContents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            ItemStack stack = storageContents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.matchSellItem(stack);
            if (shopItemOpt.isEmpty()) {
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            if (!enabledItems.contains(shopItem.id())) {
                continue;
            }

            // Check minimum price threshold
            if (!passesMinimumPrice(player, shopItem)) {
                continue;
            }
            ItemStack saleStack = stack.clone();
            player.getInventory().setItem(slot, null);
            storageContents[slot] = null;
            pendingSales.add(new AutosellStack(shopItem, saleStack));
        }

        if (!pendingSales.isEmpty()) {
            processAutosellSales(player, pendingSales).whenComplete((summary, error) ->
                    databaseManager.runOnMain(() -> finishInventoryAutosell(player, pendingSales, summary, error)));
        }
    }

    /**
     * Returns true if the item's current sell price is at or above the minimum threshold.
     * Uses the player's per-item override if set, otherwise falls back to the global config minimum.
     */
    private boolean passesMinimumPrice(Player player, ShopItem shopItem) {
        double effectiveMinPrice = autosellManager.getEffectiveMinPrice(player.getUniqueId(), shopItem.id());
        if (effectiveMinPrice <= 0.0) {
            return true; // disabled
        }
        BigDecimal sellPrice = shopManager.getSellPrice(shopItem);
        return sellPrice.doubleValue() >= effectiveMinPrice;
    }

    private CompletableFuture<AutosellBatchResult> processAutosellSales(
            Player player,
            List<AutosellStack> pendingSales
    ) {
        AutosellBatchResult summary = new AutosellBatchResult();
        CompletableFuture<AutosellBatchResult> chain = CompletableFuture.completedFuture(summary);

        for (AutosellStack sale : pendingSales) {
            chain = chain.thenCompose(current -> autosellManager
                    .sellPickupAsync(player, sale.item(), sale.stack(), sale.stack().getAmount())
                    .handle((result, error) -> {
                        if (error != null || result == null || !result.success()) {
                            current.failedStacks.add(sale.stack());
                            return current;
                        }

                        current.totalSold += result.amount();
                        current.totalEarned = current.totalEarned.add(result.totalPrice());
                        return current;
                    }));
        }

        return chain;
    }

    private void finishInventoryAutosell(
            Player player,
            List<AutosellStack> pendingSales,
            AutosellBatchResult summary,
            Throwable error
    ) {
        AutosellBatchResult finalSummary = summary;
        if (error != null || finalSummary == null) {
            finalSummary = new AutosellBatchResult();
            for (AutosellStack sale : pendingSales) {
                finalSummary.failedStacks.add(sale.stack());
            }
        }

        for (ItemStack stack : finalSummary.failedStacks) {
            returnItem(player, stack);
        }

        if (finalSummary.totalSold > 0) {
            autosellManager.sendAutosellActionBar(player, finalSummary.totalSold, finalSummary.totalEarned);
            playInventorySellSound(player);
        }
    }

    private void returnItem(Player player, ItemStack stack) {
        var overflow = player.getInventory().addItem(stack);
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }

    private void playPickupSound(Player player) {
        String soundName = configManager.getConfig().autosell().soundOnPickup();
        if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("NONE")) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 0.5f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound name — silently skip
        }
    }

    private record AutosellStack(ShopItem item, ItemStack stack) {
    }

    private static final class AutosellBatchResult {
        private int totalSold;
        private BigDecimal totalEarned = BigDecimal.ZERO;
        private final List<ItemStack> failedStacks = new ArrayList<>();
    }

    private void playInventorySellSound(Player player) {
        String soundName = configManager.getConfig().autosell().soundOnInventorySell();
        if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("NONE")) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 0.8f, 1.2f);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound name — silently skip
        }
    }
}
