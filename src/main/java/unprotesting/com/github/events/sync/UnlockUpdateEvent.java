package unprotesting.com.github.events.sync;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;

public class UnlockUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  private static List<String> lockableItems;

  /**
   * Updates the unlock checker.
   */
  public UnlockUpdateEvent() {

    if (lockableItems == null) {
      loadLockableItems();
    }

    for (Player player : Bukkit.getOnlinePlayers()) {
      runPlayerUnlockEvent(player);
    }

  }

  /**
   * Runs the unlock event for a player.
   * @param player The player to run the event for.
   */
  private void runPlayerUnlockEvent(Player player) {

    String uuid = player.getUniqueId().toString();
    ItemStack[] items = player.getInventory().getContents();

    for (ItemStack item : items) {
      runItemUnlockEvent(item, uuid);
    }

  }

  /**
   * Runs the unlock event for an item.
   * @param item The item to run the event for.
   * @param uuid The players uuid.
   */
  private void runItemUnlockEvent(ItemStack item, String uuid) {

    if (item == null) {
      return;
    }

    if (lockableItems.contains(item.getType().toString())) {
      addItem(item.getType().toString(), uuid);
    }

    for (Enchantment enchantment : item.getEnchantments().keySet()) {

      if (lockableItems.contains(enchantment.toString())) {
        addItem(enchantment.toString(), uuid);
      }

    }

  }

  /**
   * Adds an item to the players unlocked items.
   * @param item The item to add.
   * @param uuid The players uuid.
   */
  private void addItem(String item, String uuid) {

    if (Main.getInstance().getDataFiles().getShops().getConfigurationSection("shops")
        .getConfigurationSection(item)
        .getString("collect-first-setting")
        .equals("SERVER_WIDE")) {

      uuid = "server";
    
    }

    YamlConfiguration config = Main.getInstance().getDataFiles().getPlayerData();

    if (!config.contains(uuid + ".unlocked")) {

      config.createSection(uuid + ".unlocked");
      config.set(uuid + ".unlocked." + item, true);
      Main.getInstance().getDataFiles().setPlayerData(config);
      return;

    } 

    if (!config.contains(uuid + ".unlocked." + item)) {
      config.createSection(uuid + ".unlocked." + item);
      config.set(uuid + ".unlocked." + item, true);
      Main.getInstance().getDataFiles().setPlayerData(config);
    }

  }

  /**
   * Checks whether an item is locked.
   * @param itemName The item to check.
   * @param player The players uuid.
   */
  public static boolean isUnlocked(OfflinePlayer player, String itemName) {

    if (lockableItems == null) {
      loadLockableItems();
    }

    if (!lockableItems.contains(itemName)) {
      return true;
    }
    
    String uuid = player.getUniqueId().toString();

    if (Main.getInstance().getDataFiles().getShops().getConfigurationSection("shops")
        .getConfigurationSection(itemName)
        .getString("collect-first-setting")
        .equals("SERVER_WIDE")) {

      uuid = "server";

    }

    if (Main.getInstance().getDataFiles().getPlayerData()
        .contains(uuid + ".unlocked." + itemName)) {

      return Main.getInstance().getDataFiles().getPlayerData()
        .getBoolean(uuid + ".unlocked." + itemName);

    } else {
      return false;
    }

  }

  /**
   * Loads the lockable items.
   */
  private static void loadLockableItems() {

    lockableItems = new ArrayList<String>();

    ConfigurationSection shops = Main.getInstance().getDataFiles()
        .getShops().getConfigurationSection("shops");

    for (String key : shops.getKeys(false)) {

      ConfigurationSection inner = shops.getConfigurationSection(key);
      
      if (!inner.getString("collect-first-setting", "NONE").equalsIgnoreCase("NONE")) {
        lockableItems.add(key);
      }
      
    }
  }


}
