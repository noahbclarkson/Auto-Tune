package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.objects.SectionItemData;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.commands.util.FunctionsUtil;
import unprotesting.com.github.commands.util.ShopFormat;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.objects.Shop;

public class ShopCommand extends ShopFormat implements CommandExecutor {

  private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

  @Override
  public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
    if (!CommandUtil.checkIfSenderPlayer(sender)) {
      return true;
    }
    return interpretCommand((Player) sender, args, "at.shop");
  }

  public StaticPane loadSectionsPane(CommandSender sender, int lines) {

    StaticPane navigationPane = new StaticPane(0, 0, 9, lines);
    for (Section section : Main.getInstance().getDb().getSections()) {

      if (section.isEnchantmentSection() && !Config.getConfig().isEnableEnchantments()) {
        continue;
      }

      ItemStack item = new ItemStack(section.getImage());
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(section.getDisplayName());

      meta.setLore(Arrays.asList(new String[] {
          ChatColor.WHITE + "Click to enter " + section.getName() + " shop" }));

      item.setItemMeta(meta);

      GuiItem guiItem = new GuiItem(item, event -> {

        event.setCancelled(true);
        loadShopPane(sender, section);

      });

      navigationPane.addItem(guiItem, section.getPosition() % 9, section.getPosition() / 9);
    }

    return navigationPane;
  }

  public GuiItem getGuiItem(Section section, SectionItemData itemInput, CommandSender sender) {

    if (section.isEnchantmentSection() && !Config.getConfig().isEnableEnchantments()) {
      return null;
    }

    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    ItemStack item = new ItemStack(Material.BARRIER);

    try {
      if (section.isEnchantmentSection()) {
        item = new ItemStack(Material.ENCHANTED_BOOK);
      }
      if (!section.isEnchantmentSection()) {
        item = new ItemStack(Material.matchMaterial(itemInput.getName()));
      }
    } catch (NullPointerException e) {

      Main.getInstance().getLogger().severe("Could not find item "
          + itemInput.getName() + " in section " + section.getName());

      return null;

    }

    Shop shop = Main.getInstance().getDb().getShop(itemInput.getName());
    List<String> list = new ArrayList<String>();
    Player player = (Player) sender;

    list.add(ChatColor.GREEN + Config.getConfig().getCurrencySymbol()
        + df.format(shop.getPrice()));

    double change = shop.getChange();

    if (change > 0) {
      list.add(ChatColor.GREEN
          + df.format(change) + "%");
    } else if (change < 0) {
      list.add(ChatColor.RED
          + df.format(change) + "%");
    } else {
      list.add(ChatColor.GRAY + "0.00%");
    }

    if (!Config.getConfig().isDisableMaxBuysSells()) {

      int buysLeft = Main.getInstance().getDb().getPurchasesLeft(
          itemInput.getName(), player, true);

      int sellsLeft = Main.getInstance().getDb().getPurchasesLeft(
          itemInput.getName(), player, false);

      if (buysLeft != 99999 && sellsLeft != 99999) {

        list.add(ChatColor.WHITE + "Remaining Buys: " + ChatColor.GRAY + buysLeft);
        list.add(ChatColor.WHITE + "Remaining Sells: " + ChatColor.GRAY + sellsLeft);

      }
    
    }

    ItemMeta meta = item.getItemMeta();
    meta.setLore(list);
    meta.displayName(itemInput.getDisplayName());
    item.setItemMeta(meta);

    GuiItem guiItem = new GuiItem(item, event -> {
      event.setCancelled(true);
      if (section.isEnchantmentSection()) {
        loadPurchasePane(section, itemInput, sender);
      } else {
        loadPurchasePane(section, itemInput, sender);
      }
    });

    return guiItem;
  }


  private void loadPurchasePane(Section section, SectionItemData item,
      CommandSender sender) {

    CommandUtil.closeInventory(sender);
    ChestGui gui = new ChestGui(4, Config.getConfig().getMenuTitle());
    Material mat = Material.BARRIER;

    if (!Config.getConfig().getBackground().equalsIgnoreCase("none")) {
      mat = Material.matchMaterial(Config.getConfig().getBackground());
    }

    gui = CommandUtil.getBackground(gui, 4, mat);
    gui.addPane(getPurchasePane(item, sender, section));
    gui.addPane(generateMenuBackPane(sender));
    gui.show((HumanEntity) sender);

  }

  private OutlinePane getPurchasePane(SectionItemData itemInput,
      CommandSender sender, Section section) {

    Player player = (Player) sender;
    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    OutlinePane pane = new OutlinePane(1, 1, 7, 2);
    Shop shop =  Main.getInstance().getDb().getShop(itemInput.getName());
    int k = -1;

    for (int amount : amounts) {

      String itemName;
      k++;

      if (section.isEnchantmentSection()) {
        itemName = "ENCHANTED_BOOK";
      } else {
        itemName = itemInput.getName();
      }

      ItemStack item = getPurchasePaneItem(itemName,
            ChatColor.GREEN + "Buy for " + Config.getConfig().getCurrencySymbol() 
            + df.format(shop.getPrice() * amount), amount);

      ItemMeta meta = item.getItemMeta();
      meta.displayName(itemInput.getDisplayName());
      item.setItemMeta(meta);


      if (item.getMaxStackSize() < amount) {

        if (Config.getConfig().getBackground().equalsIgnoreCase("none")) {
          
          pane.setLength(k);
          k--;
          continue;

        }

        ItemStack background = new ItemStack(
            Material.matchMaterial(Config.getConfig().getBackground()));

        GuiItem guiItem = new GuiItem(background, event -> {
          event.setCancelled(true);
        });

        pane.addItem(guiItem);
        continue;

      }

      GuiItem guiItem = new GuiItem(item, event -> {

        event.setCancelled(true);

        if (!section.isEnchantmentSection()) {

          FunctionsUtil.buyItem(player, new ItemStack(
              Material.matchMaterial(itemInput.getName()), amount));

        } else {

          FunctionsUtil.buyEnchantment(player, itemInput.getName());
        }

      });

      pane.addItem(guiItem);
    }

    if (section.isEnchantmentSection()) {
      return pane;
    }

    for (int amount : amounts) {

      ItemStack item = getPurchasePaneItem(itemInput.getName(), ChatColor.RED + "Sell for "
          + Config.getConfig().getCurrencySymbol() 
          + df.format(shop.getSellPrice() * amount), amount);

      if (item.getMaxStackSize() < amount) {
        continue;
      }

      GuiItem guiItem = new GuiItem(item, event -> {

        event.setCancelled(true);

        FunctionsUtil.sellItem(player, new ItemStack(
            Material.matchMaterial(itemInput.getName()), amount));

      });

      pane.addItem(guiItem);
    }

    return pane;
  }

  private ItemStack getPurchasePaneItem(String itemInput,
      String prefix, int amount) {

    ItemStack item = new ItemStack(Material.matchMaterial(itemInput), amount);
    ItemMeta meta = item.getItemMeta();
    meta.lore(Arrays.asList(new Component[] { Component.text(ChatColor.WHITE + prefix)}));
    item.setItemMeta(meta);
    return item;

  }

}
