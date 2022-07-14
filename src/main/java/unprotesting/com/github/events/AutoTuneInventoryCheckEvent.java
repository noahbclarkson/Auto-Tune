package unprotesting.com.github.events;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * The event tp check players inventories for items they have auto-sold and
 * to update the collect first settings.
 */
public class AutoTuneInventoryCheckEvent extends AutoTuneEvent {

    private static List<String> shopNames;

    /**
     * Checks all online players inventories for autosell items
     * and to update collect first settings.
     *
     * @param isAsync Whether to run this in an async task.
     */
    public AutoTuneInventoryCheckEvent(boolean isAsync) {
        super(isAsync);
        shopNames = Arrays.asList(ShopUtil.getShopNames());
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkInventory(player);
        }
    }

    private void checkInventory(Player player) {
        UUID uuid = player.getUniqueId();
        for (ItemStack item : player.getInventory().getContents()) {
            runUpdate(item, player, uuid);
        }
    }

    private void runUpdate(ItemStack item, @NotNull Player player, @NotNull UUID uuid) {
        if (item == null) {
            return;
        }

        if (item.getEnchantments().size() > 0) {
            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                String enchantmentName = enchantment.getKey().getKey();
                if (checkIfValidShop(enchantmentName)) {
                    Shop shop = ShopUtil.getShop(enchantmentName);
                    updateCf(enchantmentName, shop, uuid, false);
                }
            }
        }

        if (!checkIfValidShop(item.getType().toString())) {
            return;
        }

        String name = item.getType().toString().toLowerCase();
        Shop shop = ShopUtil.getShop(name);
        boolean autosellEnabled = Config.get().getAutosell().getBoolean(uuid + "." + name, false);
        boolean update = false;

        if (item.getEnchantments().size() > 0) {
            autosellEnabled = false;
        }

        if (autosellEnabled) {
            if (ShopUtil.getSellsLeft(player, name) - item.getAmount() < 0) {
                return;
            }
            HashMap<Integer, ItemStack> map = player.getInventory().removeItemAnySlot(item);
            int amount = item.getAmount();
            if (!map.isEmpty()) {
                amount = amount - map.get(0).getAmount();
            }
            shop.addAutosell(uuid, amount);
            shop.addSells(uuid, amount);
            update = true;
        }

        updateCf(name, shop, uuid, update);

    }

    private void updateCf(@NotNull String name, @NotNull Shop shop, @NotNull UUID uuid, boolean update) {
        CollectFirst cf = shop.getSetting();
        if (cf.getSetting().equals(CollectFirstSetting.SERVER)) {
            if (!cf.isFoundInServer()) {
                cf.setFoundInServer(true);
                shop.setSetting(cf);
                update = true;
            }
        } else if (cf.getSetting().equals(CollectFirstSetting.PLAYER)) {
            cf.addPlayer(uuid);
            shop.setSetting(cf);
            update = true;
        }

        if (update) {
            ShopUtil.putShop(name, shop);
        }
    }

    private boolean checkIfValidShop(@NotNull String name) {
        name = name.toLowerCase();
        return shopNames.contains(name);
    }

}
