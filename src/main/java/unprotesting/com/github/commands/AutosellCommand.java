package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import java.text.DecimalFormat;
import java.util.Arrays;

import net.kyori.adventure.text.Component;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.objects.SectionItemData;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.commands.util.ShopFormat;
import unprotesting.com.github.config.Config;

public class AutosellCommand extends ShopFormat implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String autosell, String[] args) {

    if (!CommandUtil.checkIfSenderPlayer(sender)) {
      return true;
    }

    return interpretCommand(sender, args, "at.autosell");

  }

  /**
   * Loads the sections panel for the autosell command.
   */
  public StaticPane loadSectionsPane(CommandSender sender, int lines) {
    StaticPane navigationPane = new StaticPane(0, 0, 9, lines);

    for (Section section : Main.getInstance().getCache().getSections()) {

      if (section.isEnchantmentSection()) {
        continue;
      }

      ItemStack item = new ItemStack(section.getImage());
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(section.getDisplayName()));
      int posX = section.getPosition() % 9;
      int posY = section.getPosition() / 9;

      meta.lore(Arrays.asList(new Component[] { Component.text(
          ChatColor.WHITE + "Click to change "
          + section.getName() + " autosell settings.")}));

      item.setItemMeta(meta);

      GuiItem guiItem = new GuiItem(item, event -> {

        event.setCancelled(true);
        loadShopPane(sender, section);
        
      });

      navigationPane.addItem(guiItem, posX, posY);

    }

    return navigationPane;

  }

  /**
   * Get a Gui Item for a given section, itemName, and display name.
   * @param section The section to get the item for.
   * @param itemInput The item data to get the item for.
   * @param sender The sender to send messages to.
   * @return The Gui Item.
   */
  public GuiItem getGuiItem(Section section, SectionItemData itemInput, CommandSender sender) {

    Player player = (Player) sender;
    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    ItemStack item = new ItemStack(Material.matchMaterial(itemInput.getName()));
    ItemMeta meta = item.getItemMeta();
    String lore = "Click to turn on auto-selling!";
    boolean setting = CommandUtil.getPlayerAutoSellSetting(player, item.getType().toString());

    if (setting) {
      lore = ChatColor.GREEN + "Click to turn off auto-selling!";
    }

    meta.setLore(Arrays.asList(new String[] { lore, ChatColor.WHITE + "Sell-Price: "
        + ChatColor.GOLD + Config.getConfig().getCurrencySymbol()
        + df.format(Main.getInstance().getCache().getItemPrice(
          item.getType().toString(), true)) }));

    item.setItemMeta(meta);

    GuiItem guiItem = new GuiItem(item, event -> {
      event.setCancelled(true);
      changePlayerAutoSellSetting(player, item.getType().toString());
      player.getOpenInventory().close();
      loadShopPane(sender, section);
    });

    return guiItem;

  }

  private void changePlayerAutoSellSetting(Player player, String item) {

    String uuid = player.getUniqueId().toString();
    YamlConfiguration config = Main.getInstance().getDataFiles().getPlayerData();

    if (!config.contains(uuid + ".autosell")) {

      config.createSection(uuid + ".autosell");
      config.set(uuid + ".autosell." + item, true);
      Main.getInstance().getDataFiles().setPlayerData(config);
      return;

    } else if (!config.contains(uuid + ".autosell." + item)) {

      config.createSection(uuid + ".autosell." + item);
      config.set(uuid + ".autosell." + item, true);
      Main.getInstance().getDataFiles().setPlayerData(config);
      return;

    } else {

      boolean setting = false;
      setting = config.getBoolean(uuid + ".autosell." + item, false);
      config.set(uuid + ".autosell." + item, !setting);

    }

  }

}
