package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Singleton
public class AutosellListener implements Listener {

    private final AutosellManager autosellManager;
    private final ShopManager shopManager;

    @Inject
    public AutosellListener(AutosellManager autosellManager, ShopManager shopManager) {
        this.autosellManager = autosellManager;
        this.shopManager = shopManager;
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

        event.setCancelled(true);
        EconomyManager.TransactionResult result = autosellManager.sellPickup(player, shopItem, stack.getAmount());
        if (result != null && result.success()) {
            event.getItem().remove();
            autosellManager.sendAutosellActionBar(player, shopItem, result.amount(), result.totalPrice());
        } else {
            event.setCancelled(false);
        }
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

        int totalSold = 0;
        BigDecimal totalEarned = BigDecimal.ZERO;

        // Use getStorageContents() (slots 0-35) to avoid accidentally iterating
        // over armor slots (36-39) and the off-hand slot (40) which getSize()
        // would include for a PlayerInventory.
        ItemStack[] storageContents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            ItemStack stack = storageContents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.getItemByStack(stack);
            if (shopItemOpt.isEmpty()) {
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            if (!enabledItems.contains(shopItem.id())) {
                continue;
            }

            int amount = stack.getAmount();
            EconomyManager.TransactionResult result = autosellManager.sellPickup(player, shopItem, amount);

            if (result != null && result.success()) {
                player.getInventory().setItem(slot, null);
                totalSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
            }
        }

        if (totalSold > 0) {
            autosellManager.sendAutosellActionBar(player, totalSold, totalEarned);
        }
    }
}
