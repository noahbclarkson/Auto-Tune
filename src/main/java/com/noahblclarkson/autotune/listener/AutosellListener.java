package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
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
import java.util.Optional;
import java.util.Set;

@Singleton
public class AutosellListener implements Listener {

    private final AutosellManager autosellManager;
    private final ShopManager shopManager;
    private final ConfigManager configManager;

    @Inject
    public AutosellListener(AutosellManager autosellManager, ShopManager shopManager, ConfigManager configManager) {
        this.autosellManager = autosellManager;
        this.shopManager = shopManager;
        this.configManager = configManager;
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

        event.setCancelled(true);
        EconomyManager.TransactionResult result = autosellManager.sellPickup(player, shopItem, stack, stack.getAmount());
        if (result != null && result.success()) {
            event.getItem().remove();
            autosellManager.sendAutosellActionBar(player, shopItem, result.amount(), result.totalPrice());
            playPickupSound(player);
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

            int amount = stack.getAmount();
            EconomyManager.TransactionResult result = autosellManager.sellPickup(player, shopItem, stack, amount);

            if (result != null && result.success()) {
                // Don't manually clear the slot here — sellPickup calls processSellImmediate
                // which removes items from the player's *real* inventory directly.
                // Clearing the slot in our copy would incorrectly null out restored items
                // if a DB failure caused processSellImmediate to restore them.
                // The sellPickup call already updated the real inventory; leave our copy alone.
                totalSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
            }
        }

        if (totalSold > 0) {
            autosellManager.sendAutosellActionBar(player, totalSold, totalEarned);
            playInventorySellSound(player);
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
