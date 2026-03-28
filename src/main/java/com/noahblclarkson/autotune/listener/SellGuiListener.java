package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SellGuiListener implements Listener {

    private static SellGuiListener instance;

    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;

    private final Map<UUID, Inventory> activeSellInventories = new ConcurrentHashMap<>();

    @Inject
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public SellGuiListener(ShopManager shopManager, EconomyManager economyManager, ConfigManager configManager) {
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.configManager = configManager;
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
        Inventory tracked = activeSellInventories.remove(playerId);
        if (tracked == null || !event.getInventory().equals(tracked)) {
            return;
        }

        int totalItemsSold = 0;
        BigDecimal totalEarned = BigDecimal.ZERO;

        for (ItemStack stack : event.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.matchSellItem(stack);
            if (shopItemOpt.isEmpty()) {
                // Item not in shop — return it to the player instead of losing it
                returnItem(player, stack);
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            // Pass the actual ItemStack so enchantment pricing can be applied
            EconomyManager.TransactionResult result = economyManager.processSellImmediate(
                    player, shopItem, stack.getAmount(), stack);

            if (result.success()) {
                totalItemsSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
            } else {
                returnItem(player, stack);
            }
        }

        if (totalItemsSold > 0) {
            player.sendMessage(configManager.getMessage("sell.summary", Map.of(
                    "amount", String.valueOf(totalItemsSold),
                    "price", configManager.formatCurrency(totalEarned)
            )));
        } else {
            player.sendMessage(configManager.getMessage("sell.nothing-sold"));
        }
    }

    private void returnItem(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }
}
