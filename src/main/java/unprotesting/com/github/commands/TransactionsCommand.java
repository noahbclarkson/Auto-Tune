package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;

public class TransactionsCommand implements CommandExecutor {

  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public boolean onCommand(CommandSender sender, Command command,
      String transactions, String[] args) {

    if (!CommandUtil.checkIfSenderPlayer(sender)) {
      return true;
    }

    return interpretCommand(sender, args);

  }

  @Deprecated
  private boolean interpretCommand(CommandSender sender, String[] args) {

    Player player = CommandUtil.closeInventory(sender);

    if (!(player.hasPermission("at.transactions") || player.hasPermission("at.admin"))) {

      CommandUtil.noPermission(player);
      return true;

    }

    ChestGui gui = new ChestGui(6, "Transactions");
    PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
    List<TransactionData> loans = Main.getInstance().getCache().getTransactions();
    List<OutlinePane> panes = new ArrayList<OutlinePane>();
    String uuid = player.getUniqueId().toString();
    List<GuiItem> items;

    if (args.length < 1) {

      if (!player.hasPermission("at.transactions.other") && !player.hasPermission("at.admin")) {
        items = getGuiItemsFromTransactions(loans, uuid);
      } else {
        items = getGuiItemsFromTransactions(loans, null);
      }

    } else if (args[0].equals("-p")) {

      if (args[1].equals(player.getName())) {
        items = getGuiItemsFromTransactions(loans, uuid);
      } else if (!args[1].equals(player.getName())
          && (!player.hasPermission("at.transactions.other") 
          && !player.hasPermission("at.admin"))) {

        CommandUtil.noPermission(player);
        return true;

      } else {

        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(args[1]);
        if (offPlayer == null) {
          return false;
        }

        uuid = offPlayer.getUniqueId().toString();

        if (uuid == null) {
          return false;
        }

        items = getGuiItemsFromTransactions(loans, uuid);

      }

    } else {
      return false;
    }

    CommandUtil.loadGuiItemsIntoPane(items, gui, pages,
        panes, Material.GRAY_STAINED_GLASS_PANE, sender);

    return true;

  }

  private List<GuiItem> getGuiItemsFromTransactions(List<TransactionData> data,
      String uuid) {

    List<GuiItem> output = new ArrayList<GuiItem>();
    Collections.sort(data);

    for (TransactionData transaction : data) {

      if (uuid != null && !transaction.getPlayer().equals(uuid)) {
        continue;
      }

      if (transaction.getPosition().equals(SalePositionType.BUY)
          || transaction.getPosition().equals(SalePositionType.SELL)) {

        ItemStack item = new ItemStack(Material.matchMaterial(
            transaction.getItem()), transaction.getAmount());

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + Integer.toString(transaction.getAmount())
            + "x " + transaction.getItem());

        List<String> lore = new ArrayList<String>();

        if (transaction.getPosition().equals(SalePositionType.BUY)) {
          lore.add(ChatColor.GREEN + "BUY");
        } else {
          lore.add(ChatColor.RED + "SELL");
        }

        output.add(applyMetaToStack(meta, item, transaction, lore));
        continue;

      }

      Component displayName = Enchantment.getByKey(
          NamespacedKey.minecraft(transaction.getItem().toLowerCase())).displayName(1);

      ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(displayName);
      List<String> lore = new ArrayList<String>();

      if (transaction.getPosition().equals(SalePositionType.EBUY)) {
        lore.add(ChatColor.GREEN + "BUY");
      } else {
        lore.add(ChatColor.RED + "SELL");
      }

      output.add(applyMetaToStack(meta, item, transaction, lore));

    }

    return output;
  }

  private GuiItem applyMetaToStack(ItemMeta meta, ItemStack item,
      TransactionData transaction, List<String> lore) {

    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(transaction.getPlayer()));
    String playerName = "Unknown";
    playerName = player.getName();
    lore.add(ChatColor.WHITE + "Player: " + playerName);

    lore.add(ChatColor.WHITE + "Price: " + Config.getConfig().getCurrencySymbol() 
        + df.format(transaction.getPrice()));
    
    lore.add(ChatColor.WHITE + "Total: " + Config.getConfig().getCurrencySymbol()
        + df.format(transaction.getPrice() * transaction.getAmount()));

    lore.add(ChatColor.WHITE + "Date: " + transaction.getDate().format(formatter));
    meta.setLore(lore);
    item.setItemMeta(meta);

    GuiItem guiItem = new GuiItem(item, event -> {
      event.setCancelled(true);
    });

    return guiItem;
  }

}
