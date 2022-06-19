package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
import unprotesting.com.github.data.objects.Transaction;
import unprotesting.com.github.data.objects.Transaction.SalePositionType;
import unprotesting.com.github.data.objects.Transaction.TransactionType;

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
    List<OutlinePane> panes = new ArrayList<OutlinePane>();
    UUID uuid = player.getUniqueId();
    List<GuiItem> items;

    if (args.length < 1) {

      if (!player.hasPermission("at.transactions.other") && !player.hasPermission("at.admin")) {
        items = getGuiItemsFromTransactions(uuid);
      } else {
        items = getGuiItemsFromTransactions(null);
      }

    } else if (args[0].equals("-p")) {

      if (args[1].equals(player.getName())) {
        items = getGuiItemsFromTransactions(uuid);
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

        uuid = offPlayer.getUniqueId();

        if (uuid == null) {
          return false;
        }

        items = getGuiItemsFromTransactions(uuid);

      }

    } else {
      return false;
    }

    CommandUtil.loadGuiItemsIntoPane(items, gui, pages,
        panes, Material.GRAY_STAINED_GLASS_PANE, sender);

    return true;

  }

  private List<GuiItem> getGuiItemsFromTransactions(UUID uuid) {

    List<GuiItem> output = new ArrayList<GuiItem>();

    for (long time : Main.getInstance().getDb().getTransactions().keySet()) {

      Transaction[] transactions = Main.getInstance().getDb().getTransactions().get(time);

      for (Transaction transaction : transactions) {

        if (uuid != null && !transaction.getPlayer().equals(uuid)) {
          continue;
        }

        ItemMeta meta;
        ItemStack item;
        List<String> lore = new ArrayList<String>();

        if (transaction.getType().equals(TransactionType.ITEM)) {
            
          item = new ItemStack(Material.matchMaterial(
              transaction.getItem()), transaction.getAmount());

          meta = item.getItemMeta();

          meta.setDisplayName(ChatColor.GOLD + Integer.toString(transaction.getAmount())
              + "x " + transaction.getItem());

        } else if (transaction.getType().equals(TransactionType.ENCHANTMENT)) {

          Component displayName = Enchantment.getByKey(
              NamespacedKey.minecraft(transaction.getItem().toLowerCase())).displayName(1);

          item = new ItemStack(Material.ENCHANTED_BOOK);
          meta = item.getItemMeta();
          meta.displayName(displayName);

        } else {
          continue;
        }

        if (transaction.getPosition().equals(SalePositionType.BUY)) {
          lore.add(ChatColor.GREEN + "BUY");
        } else {
          lore.add(ChatColor.RED + "SELL");
        }

        output.add(applyMetaToStack(meta, item, transaction, lore, time));
        continue;
      }
    }
    return output;
  }

  private GuiItem applyMetaToStack(ItemMeta meta, ItemStack item,
      Transaction transaction, List<String> lore, long time) {

    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());
    OfflinePlayer player = Bukkit.getOfflinePlayer(transaction.getPlayer());
    String playerName = "Unknown";
    playerName = player.getName();
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(time),
        ZoneId.systemDefault());
    lore.add(ChatColor.WHITE + "Player: " + playerName);

    lore.add(ChatColor.WHITE + "Price: " + Config.getConfig().getCurrencySymbol() 
        + df.format(transaction.getPrice()));
    
    lore.add(ChatColor.WHITE + "Total: " + Config.getConfig().getCurrencySymbol()
        + df.format(transaction.getPrice() * transaction.getAmount()));

    lore.add(ChatColor.WHITE + "Date: " + date.format(formatter));
    meta.setLore(lore);
    item.setItemMeta(meta);

    GuiItem guiItem = new GuiItem(item, event -> {
      event.setCancelled(true);
    });

    return guiItem;
  }

}
