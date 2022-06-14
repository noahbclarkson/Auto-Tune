package unprotesting.com.github.commands;

import java.util.HashMap;
import java.util.Set;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.MaxBuySellData;
import unprotesting.com.github.events.async.PriceUpdateEvent;

public class AutoTuneCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String at, String[] args) {
    
    if (!CommandUtil.checkIfSenderPlayer(sender)) {
      return true;
    }
    return interpretCommand(sender, args);

  }

  private boolean interpretCommand(CommandSender sender, String[] args) {

    Player player = CommandUtil.closeInventory(sender);

    if (!(player.hasPermission("at.admin"))) {

      CommandUtil.noPermission(player);
      return true;

    }

    if (args.length == 0) {
      return returnDefault(player);
    } else if (args[0].equalsIgnoreCase("update") && args.length == 1) {

      player.sendMessage(ChatColor.GOLD + "Attempting to force Auto-Tune to update.");

      Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(),
          () -> Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)));

      player.sendMessage(ChatColor.GREEN + "Updated Auto-Tune time period.");
      return true;

    } else if (args[0].equalsIgnoreCase("price") && args.length == 3) {
      return changePrice(player, args[1], args[2]);
    } else if (args[0].equalsIgnoreCase("reload") && args.length == 1) {
      return reload(player);
    }

    return returnDefault(player);

  }

  private boolean returnDefault(Player player) {

    player.sendMessage(ChatColor.GOLD + "<===== Welcome to Auto-Tune! =====>");

    player.sendMessage(ChatColor.GOLD + "/at price <item-name>"
        + " <new-price> | Change the price of an item.");

    player.sendMessage(ChatColor.GOLD + "/at reload | Reload all files (to update settings).");
    player.sendMessage(ChatColor.GOLD + "/at update | Force a price update.");
    return true;

  }

  private boolean changePrice(Player player, String itemName, String newPrice) {

    Double price;

    if (newPrice == null) {
      return false;
    }

    try {
      price = Double.parseDouble(newPrice);
    } catch (NumberFormatException e) {
      player.sendMessage(ChatColor.RED + "Incorrect number format.");
      return true;
    } catch (NullPointerException e) {
      return false;
    }

    itemName = itemName.toUpperCase();
    Component displayName = new ItemStack(Material.matchMaterial(itemName)).displayName();

    if (Main.getInstance().getCache().getItems().containsKey(itemName)) {

      Main.getInstance().getCache().getItems().get(itemName).setPrice(price);

      player.sendMessage(ChatColor.GREEN + "Changed " + displayName
          + " to " + Config.getConfig().getCurrencySymbol() + newPrice);

      return true;

    } else if (Main.getInstance().getCache().getEnchantments().containsKey(itemName)) {

      Main.getInstance().getCache().getEnchantments().get(itemName).setPrice(price);

      player.sendMessage(ChatColor.GREEN + "Changed " + displayName
          + " to " + Config.getConfig().getCurrencySymbol() + newPrice);

      return true;

    } else {

      player.sendMessage(ChatColor.RED + itemName + " is not a valid input.");
      return false;

    }

  }

  private boolean reload(Player player) {

    Main.getInstance().setupDataFiles();

    ConfigurationSection config = Main.getInstance().getDataFiles()
        .getShops().getConfigurationSection("shops");

    Set<String> set = config.getKeys(false);

    HashMap<String, MaxBuySellData> maxPurchases = new HashMap<String, MaxBuySellData>();

    for (String key : set) {

      ConfigurationSection section = config.getConfigurationSection(key);

      MaxBuySellData maxBuySellData = new MaxBuySellData(
          section.getInt("max-buy", 9999),
          section.getInt("max-sell", 9999));

      maxPurchases.put(key, maxBuySellData);

    }

    Main.getInstance().getCache().setMaxPurchases(maxPurchases);
    player.sendMessage(ChatColor.GREEN + "Reload successful.");
    return true;
    
  }

}
