package unprotesting.com.github.events;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import unprotesting.com.github.util.Format;

/**
 * The event tp check players inventories for items they have auto-sold and
 * to update the collect first settings.
 */
public class AutoTuneInventoryCheckEvent extends AutoTuneEvent {

    public static Map<UUID, List<String>> autosellItemMaxReached = new HashMap<>();

    /**
     * Checks all online players inventories for autosell items
     * and to update collect first settings.
     *
     * @param isAsync Whether to run this in an async task.
     */
    public AutoTuneInventoryCheckEvent(boolean isAsync) {
        super(isAsync);
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkInventory(player);
        }
    }

    private void checkInventory(Player player) {
        UUID uuid = player.getUniqueId();
        for (ItemStack item : player.getInventory().getContents()) {

            if (item == null) {
                continue;
            }

            runUpdate(item, player);

            if (item.getEnchantments().isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                String name = enchantment.getKey().getKey().toLowerCase();
                Shop shop = getShop(name);
                updateCf(name, shop, uuid);
            }

        }
    }

    private void runUpdate(ItemStack item, @NotNull Player player) {

        String name = item.getType().toString().toLowerCase();
        Shop shop = getShop(name);

        if (shop == null) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        updateCf(name, shop, uuid);
        boolean autosellEnabled = Config.get().getAutosell().getBoolean(uuid + "." + name, false);

        if (!autosellEnabled) {
            return;
        }

        if (ShopUtil.getSellsLeft(player, name) - item.getAmount() < 0) {
            if (!autosellItemMaxReached.containsKey(uuid)) {
                List<String> list = autosellItemMaxReached.get(uuid);
                if (list == null) {
                    list = Arrays.asList(name);
                    autosellItemMaxReached.put(uuid, list);
                } else {
                    list.add(name);
                }
            } else {
                List<String> list = autosellItemMaxReached.get(uuid);
                if (!list.contains(name)) {
                    list.add(name);
                    Format.sendMessage(player, Config.get().getRunOutOfSells());
                }
            }
            return;
        }

        int amount = item.getAmount();
        HashMap<Integer, ItemStack> map = player.getInventory().removeItemAnySlot();

        if (!map.isEmpty()) {
            amount = amount - map.get(0).getAmount();
        }

        shop.addAutosell(uuid, amount);
        shop.addSells(uuid, amount);

    }

    private Shop getShop(String shopName) {
        if (shopName == null) {
            return null;
        }

        Shop shop = ShopUtil.getShop(shopName);

        if (shop == null) {
            return null;
        }

        return shop;
    }

    private void updateCf(@NotNull String name, @NotNull Shop shop, @NotNull UUID uuid) {

        boolean update = false;
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

}
