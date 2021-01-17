package unprotesting.com.github.Commands;

import java.util.ArrayList;

import com.earth2me.essentials.User;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.TextHandler;
import unprotesting.com.github.util.Transaction;

public class AutoTuneTransactionCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String transactions, String[] args) {
        if (command.getName().equalsIgnoreCase("transactions")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length < 1) {
                    if (player.hasPermission("at.transactions") || player.isOp() || player.hasPermission("at.transactions.other")){
                        player.sendMessage(ChatColor.RED + "Error! Correct Usage: /transactions <page-number>");
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                }
                else if (args.length < 2) {
                    if (player.hasPermission("at.transactions") || player.isOp()){
                        Integer page = parsePage(args[0], player);
                        if (page != null && page > 0) {
                            setupTransactionViewForPlayer(player, page, player);
                        }
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } else if (args[0].equals("all")) {
                    if (player.hasPermission("at.transactions.other") || player.isOp()){
                        if (args.length < 2){
                            player.sendMessage(ChatColor.RED + "Error! Correct Usage: /transactions <page-number>");
                            return true;
                        }
                        Integer page = parsePage(args[1], player);
                        if (page == null || page < 0) {
                            return true;
                        }
                        setupTransactionViewAll(player, page);
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } else if (args.length == 2) {
                    if (player.hasPermission("at.transactions.other") || player.isOp()){
                        Integer page = parsePage(args[1], player);
                        if (page == null || page < 0) {
                            return true;
                        }
                        Player select_player = parsePlayer(args[0], player);
                        if (select_player == null){
                            return true;
                        }
                        setupTransactionViewForPlayer(player, page, select_player);
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                    return true;
                } else {
                    if (player.hasPermission("at.transactions") || player.hasPermission("at.transactions.other") || player.isOp()){
                        player.sendMessage("Error! Correct Usage: /transactions <page-number>");
                    }
                    else{
                        TextHandler.noPermssion(player);
                    }
                }
            }
        }
        return true;
    }

    @Deprecated
    public void setupTransactionViewForPlayer(Player player, int page, Player input_player) {
        String input_player_name = input_player.getName();
        player.sendMessage(ChatColor.YELLOW + "Page: " + (page+1));
        int size = (Main.getTransactions().size() - 1);
        for (Integer i = (size - (page * 10)); i > size - ((page + 1) * 10); i--) {
            try{
                Transaction transaction = Main.getTransactions().get(i);
                if (transaction.player.equals(input_player_name)) {
                    player.sendMessage(ChatColor.RED + Integer.toString(i+1) + ": " + ChatColor.YELLOW + transaction.toDisplayString());
                }
            }
            catch(NullPointerException ex){
            }
        }
    }

    @Deprecated
    public void setupTransactionViewAll(Player player, int page) {
        player.sendMessage(ChatColor.YELLOW + "Page: " + (page+1));
        int size = (Main.getTransactions().size() - 1);
        for (Integer i = (size - (page * 10)); i > size - ((page + 1) * 10); i--) {
            try{
                player.sendMessage(ChatColor.RED + Integer.toString(i+1) + ": " + ChatColor.YELLOW
                + Main.getTransactions().get(i).toDisplayString());
            }
            catch(NullPointerException ex){
            }
        }
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
    public Player parsePlayer(String arg, Player player) {
        OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayer(arg);
        if (offlinePlayer == null){
            player.sendMessage("Error! Correct Usage: /transactions <player-name> <page-number>");
            return null;
        }
        Player output = offlinePlayer.getPlayer();
        return output;
    }
    
}
