package unprotesting.com.github.events;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;


public class AutoTuneInventoryCheckEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  private static List<String> shopNames;

  /**
   * Checks all online players inventories for autosell items
   * and to update collect first settings.
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

      if (!checkIfValidShop(item)) {
        continue;
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
          continue;
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

  private boolean checkIfValidShop(ItemStack item) {
    if (item == null) {
      return false;
    }

    String name = item.getType().toString().toLowerCase();

    if (!shopNames.contains(name)) {
      return false;
    }

    return true;
  }
  
}
