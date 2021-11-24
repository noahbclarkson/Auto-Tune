package unprotesting.com.github.commands;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.ephemeral.data.TransactionData.TransactionPositionType;

public class TransactionsCommand implements CommandExecutor{

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String transactions, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    @Deprecated
    private boolean interpretCommand(CommandSender sender, String[] args) {
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.transactions") || player.hasPermission("at.admin"))){CommandUtil.noPermission(player);return true;}
        ChestGui gui = new ChestGui(6, "Transactions");
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<TransactionData> loans = Main.getCache().getTRANSACTIONS();
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        String player_uuid = player.getUniqueId().toString();
        List<GuiItem> items;
        if (args.length < 1){
            if (!player.hasPermission("at.transactions.other") && !player.hasPermission("at.admin")){
                items = getGuiItemsFromTransactions(loans, player_uuid);
            }
            else{
                items = getGuiItemsFromTransactions(loans, null);
            }
        }
        else if (args[0].equals("-p")){
            if (args[1].equals(player.getName())){
                items = getGuiItemsFromTransactions(loans, player_uuid);
            }
            else if (!args[1].equals(player.getName()) && (!player.hasPermission("at.transactions.other") && !player.hasPermission("at.admin"))){
                CommandUtil.noPermission(player);
                return true;
            }
            else{
                OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(args[1]);
                items = getGuiItemsFromTransactions(loans, offPlayer.getUniqueId().toString());
            }
        }
        else{
            return false;
        }
        CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, Material.GRAY_STAINED_GLASS_PANE, sender);
        return true;
    }

    private List<GuiItem> getGuiItemsFromTransactions(List<TransactionData> data, String player_uuid){
        List<GuiItem> output = new ArrayList<GuiItem>();
        Collections.sort(data);
        for (TransactionData transaction : data){
            if (player_uuid != null){
                if (!transaction.getPlayer().equals(player_uuid)){
                    continue;
                }
            }
            if (transaction.getPosition().equals(TransactionPositionType.BI) || transaction.getPosition().equals(TransactionPositionType.SI)){
                ItemStack item = new ItemStack(Material.matchMaterial(transaction.getItem()), transaction.getAmount());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + Integer.toString(transaction.getAmount())
                 + "x " + Section.getItemDisplayName(transaction.getItem()));
                List<String> lore = new ArrayList<String>();
                if (transaction.getPosition().equals(TransactionPositionType.BI)){
                    lore.add(ChatColor.GREEN + "BUY");
                }
                else{
                    lore.add(ChatColor.RED + "SELL");
                }
                output.add(applyMetaToStack(meta, item, transaction, lore));
            }
            else{
                ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(Section.getItemDisplayName(transaction.getItem()));
                List<String> lore = new ArrayList<String>();
                if (transaction.getPosition().equals(TransactionPositionType.BE)){
                    lore.add(ChatColor.GREEN + "BUY");
                }
                else{
                    lore.add(ChatColor.RED + "SELL");
                }
                output.add(applyMetaToStack(meta, item, transaction, lore));
            }
        }
        return output;
    }

    private GuiItem applyMetaToStack(ItemMeta meta, ItemStack item, TransactionData transaction, List<String> lore){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(transaction.getPlayer()));
        String player_name = "Unknown";
        player_name = player.getName();
        lore.add(ChatColor.WHITE + "Player: " + player_name);
        lore.add(ChatColor.WHITE + "Price: " + Config.getCurrencySymbol() + df.format(transaction.getPrice()));
        lore.add(ChatColor.WHITE + "Total: " + Config.getCurrencySymbol() + df.format(transaction.getPrice()*transaction.getAmount()));
        lore.add(ChatColor.WHITE + "Date: " + transaction.getDate().format(formatter));
        meta.setLore(lore);
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }


}
