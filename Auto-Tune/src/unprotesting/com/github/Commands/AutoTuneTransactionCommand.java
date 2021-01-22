package unprotesting.com.github.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.EnchantmentAlgorithm;
import unprotesting.com.github.util.TextHandler;
import unprotesting.com.github.util.Transaction;

public class AutoTuneTransactionCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String transactions, String[] args) {
        if (command.getName().equalsIgnoreCase("transactions")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length == 0) {
                    if (player.hasPermission("at.transactions") || player.isOp() || player.hasPermission("at.transactions.other")){
                        player.sendMessage(ChatColor.RED + "Error! Correct Usage: /transactions <page-number>");
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                }
                if (args.length == 1) {
                    if (player.hasPermission("at.transactions") || player.isOp()){
                        Integer page = parsePage(args[0], player);
                        if (page == null || page < 0) {
                            player.sendMessage(ChatColor.RED + "Not enough data available / incorrect formatting");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "Setting up transaction view for " + player.getName());
                        loadTransactionGUI(player, setupTransactionViewForPlayer(page, player.getName()), page+1, player.getName());
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } 
                if (args[0].equals("all") && args.length == 2) {
                    if (player.hasPermission("at.transactions.other") || player.isOp()){
                        Integer page = parsePage(args[1], player);
                        if (page == null || page < 0) {
                            player.sendMessage(ChatColor.RED + "Not enough data available / incorrect formatting");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "Setting up transaction view for all players");
                        loadTransactionGUI(player, setupTransactionViewAll(page), page+1, null);
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } 
                if (args.length == 2) {
                    if (player.hasPermission("at.transactions.other") || player.isOp()){
                        Integer page = parsePage(args[1], player);
                        if (page == null || page < 0) {
                            player.sendMessage(ChatColor.RED + "Not enough data available / incorrect formatting");
                            return true;
                        }
                        String select_player = args[0];
                        if (select_player == null){
                            player.sendMessage(ChatColor.RED + "Not enough data available / incorrect formatting");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "Setting up transaction view for " + args[0]);
                        loadTransactionGUI(player, setupTransactionViewForPlayer(page, select_player), page+1, select_player) ;
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } 
                if (player.hasPermission("at.transactions") || player.hasPermission("at.transactions.other") || player.isOp()){
                    player.sendMessage("Error! Correct Usage: /transactions <page-number>");
                }
                else{
                    TextHandler.noPermssion(player);
                }
            }
        }
        return true;
    }

    @Deprecated
    public Transaction[] setupTransactionViewForPlayer(int page, String input_player) {
        Transaction[] output = new Transaction [28];
        int size = (Main.getTransactions().size() - 1);
        int k = 0;
        int i = size - (page*28);
        for (int l = 0; l < 50000; l++){
            Transaction transaction = Main.getTransactions().get(i-l);
            if (k > 27){
                break;
            }
            if (transaction != null){
                if (transaction.player.equals(input_player)){
                    output[k] = transaction;
                    k++;
                }
            }
        }
        return output;
    }

    @Deprecated
    public Transaction[] setupTransactionViewAll(int page) {
        Transaction[] output = new Transaction [28];
        int size = (Main.getTransactions().size() - 1);
        int k = 0;
        int i = size - (page*28);
        for (int l = 0; l < 1000; l++){
            if (k > 27){
                break;
            }
            Transaction transaction = Main.getTransactions().get(i-l);
            if (transaction != null){
                output[k] = transaction;
                k++;
            }
        }
        return output;
    }

    public Integer parsePage(String arg, Player player) {
        Integer page = 0;
        try {
            page = Integer.parseInt(arg);
        } catch (NumberFormatException ex) {
            player.sendMessage("Error! Correct Usage: /transactions all <page-number>");
            page = null;
        }
        if (page < 1){
            player.sendMessage("Error! Correct Usage: /transactions all <page-number>");
            return null;
        }
        page = page-1;
        return page;
    }

    @Deprecated
    public void loadTransactionGUI (Player player, Transaction[] transactions, int page, String queried_player) {
        if (transactions.length > 28){
            player.sendMessage(ChatColor.RED + "Error when parsing transactions to GUI!");
            return;
        }
        Gui GUI = new Gui(6, "Transactions");
        OutlinePane pane = new OutlinePane(1, 1, 7, 4);
        for (Transaction transaction : transactions){
            if (transaction == null){
                continue;
            }
            String item_name = transaction.item;
            if (item_name == null){
                continue;
            }
            int amount = transaction.amount;
            ItemStack item_stack;
            try{
                item_stack = new ItemStack(Material.matchMaterial(item_name), amount);
            }
            catch (IllegalArgumentException ex){
                item_stack = new ItemStack(Material.ENCHANTED_BOOK);
                item_stack = EnchantmentAlgorithm.addBookEnchantment(item_stack, Enchantment.getByName(item_name), 1);
            }
            ItemMeta item_meta = item_stack.getItemMeta();
            item_meta.setDisplayName(ChatColor.GOLD + item_name);
            item_meta.setLore(generateLoreFromTransaction(transaction));
            item_stack.setItemMeta(item_meta);
            GuiItem gItem = new GuiItem (item_stack, event -> {
                event.setCancelled(true);
            });
            pane.addItem(gItem);
        }
        GUI.addPane(pane);
        GUI.addPane(createNextPagePane(page, player, queried_player));
        if (page > 1){
            GUI.addPane(createBackPagePane(page, player, queried_player));
        }
        CommandSender cSender = player;
		GUI.update();
		GUI.show((HumanEntity) cSender);

    }

    @Deprecated
    public List<String> generateLoreFromTransaction (Transaction transaction) {
        List<String> output = new ArrayList<String>();
        if (transaction.type.equals("Buy")){
            output.add(ChatColor.GREEN + "" + ChatColor.MAGIC  + "⇵ " + ChatColor.GREEN + "Buy" + ChatColor.MAGIC + " ⇵");
        }
        else {
            output.add(ChatColor.RED + "" + ChatColor.MAGIC + "⇵ " + ChatColor.RED + "Sell" + ChatColor.MAGIC + " ⇵");
        }
        output.add(ChatColor.YELLOW + "Player: " + ChatColor.GREEN + transaction.player);
        output.add(ChatColor.YELLOW + "Price: " + Config.getCurrencySymbol() + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(transaction.total_price));
        output.add(ChatColor.YELLOW + "Date: " + ChatColor.GREEN + transaction.date.toLocaleString());
        return output;
    }

    public StaticPane createNextPagePane (int page, Player querying_player, String queried_player) {
        StaticPane output = new StaticPane(8, 5, 1, 1);
        ItemStack item_stack = new ItemStack(Material.ARROW);
        ItemMeta item_meta = item_stack.getItemMeta();
        item_meta.setDisplayName(ChatColor.DARK_PURPLE + "Next");
		item_meta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (page + 1)));
		item_stack.setItemMeta(item_meta);
        GuiItem gItem = new GuiItem(item_stack, event ->{
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT){
                querying_player.getOpenInventory().close();
                if (queried_player != null){
                    querying_player.sendMessage(ChatColor.GOLD + "Opening page " + (page+1));
                    loadTransactionGUI(querying_player, setupTransactionViewForPlayer(page + 1, queried_player), page + 1, queried_player);
                }
                else{
                    loadTransactionGUI(querying_player, setupTransactionViewAll(page + 1), page + 1, null);
                }
            }
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    public StaticPane createBackPagePane (int page, Player querying_player, String queried_player) {
        StaticPane output = new StaticPane(0, 5, 1, 1);
        ItemStack item_stack = new ItemStack(Material.ARROW);
        ItemMeta item_meta = item_stack.getItemMeta();
        item_meta.setDisplayName(ChatColor.DARK_PURPLE + "Back");
		item_meta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (page - 1)));
		item_stack.setItemMeta(item_meta);
        GuiItem gItem = new GuiItem(item_stack, event ->{
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT){
                querying_player.getOpenInventory().close();
                if (queried_player != null){
                    querying_player.sendMessage(ChatColor.GOLD + "Opening page " + (page-1));
                    loadTransactionGUI(querying_player, setupTransactionViewForPlayer(page - 1, queried_player), page - 1, queried_player);
                }
                else{
                    loadTransactionGUI(querying_player, setupTransactionViewAll(page - 1), page - 1, null);
                }
            }
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    
}
