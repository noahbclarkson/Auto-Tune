package unprotesting.com.github.events.sync;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.FunctionsUtil;

public class AutosellUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  private final YamlConfiguration config;

  /**
   * Updates the autosell checker.
   */
  public AutosellUpdateEvent() {

    config = Main.getInstance().getDataFiles().getPlayerData();

    for (Player player : Bukkit.getOnlinePlayers()) {
      runPlayerAutosellEvent(player);
    }

  }

  /**
   * Runs the autosell event for a player.
   * @param player The player to run the event for.
   */
  private void runPlayerAutosellEvent(Player player) {

    List<String> data = getAutoSellItems(player);

    // If the data has two or more items in it then run the event.
    // If the player is offline then skip the event.
    if (data.size() < 1 || !player.isOnline()) {
      return;
    }

    ItemStack[] items = player.getInventory().getContents();

    // Loop through the players inventory and check if any of the items
    // are in the autosell list.
    for (ItemStack item : items) {

      // If the item is null continue.
      if (item == null) {
        continue;
      }

      // If the item is not in the autosell list continue.
      if (!data.contains(item.getType().toString())) {
        continue;
      }
        
      player.getInventory().remove(item);
      FunctionsUtil.sellCustomItem(player, item, true);

    }

  }

  private List<String> getAutoSellItems(OfflinePlayer player) {

    List<String> data = new ArrayList<String>();

    ConfigurationSection section = config.getConfigurationSection(
        player.getUniqueId().toString() + ".autosell");

    // If the section is null return.
    if (section == null) {
      return data;
    }

    // Loop through the players autosell settings and add items that have 
    // been set to autosell.
    for (String key : section.getKeys(false)) {
      if (section.getBoolean(key)) {
        data.add(key);
      }
    }

    return data;
  }
}