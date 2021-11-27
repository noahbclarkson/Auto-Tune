package unprotesting.com.github.commands;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.ephemeral.data.GDPData;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.events.async.PriceUpdateEvent;

public class AutoTuneCommand implements CommandExecutor{
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String at, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.admin"))){CommandUtil.noPermission(player);return true;}
        if (args.length == 0){
            return returnDefault(player);
        }
        else if (args[0].equalsIgnoreCase("update") && args.length == 1){
            player.sendMessage(ChatColor.GOLD + "Attempting to force Auto-Tune to update.");
            Bukkit.getScheduler().runTaskAsynchronously(Main.getINSTANCE(), ()
             -> Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)));
            player.sendMessage(ChatColor.GREEN + "Updated Auto-Tune time period.");
            return true;
        }
        else if (args[0].equalsIgnoreCase("price") && args.length == 3){
            return changePrice(player, args[1], args[2]);
        }
        return true;
    }

    private boolean returnDefault(Player player){
        player.sendMessage(ChatColor.GOLD + "<===== Welcome to Auto-Tune! =====>");
        player.sendMessage(ChatColor.GOLD + "/at price <item-name>/<gdp>/<balance>/etc. <new-price> | Change the price of an item.");
        player.sendMessage(ChatColor.GOLD + "/at update | Force a price update.");
        return true;
    }

    private boolean changePrice(Player player, String item_name, String new_price){
        Double price;
        if (new_price == null){
            return false;
        }
        try{
            price = Double.parseDouble(new_price);
        }
        catch(NumberFormatException e){
            player.sendMessage(ChatColor.RED + "Incorrect number format.");
            return true;
        }
        catch(NullPointerException e){
            return false;
        }
        item_name = item_name.toUpperCase();
        if (Main.getCache().getITEMS().containsKey(item_name)){
            ConcurrentHashMap<String, ItemData> ITEMS = Main.getCache().getITEMS();
            ItemData data = ITEMS.get(item_name);
            data.setPrice(price);
            ITEMS.put(item_name, data);
            Main.getCache().updatePrices(ITEMS);
            player.sendMessage(ChatColor.GREEN + "Changed " + Section.getItemDisplayName(item_name) + " to " + Config.getCurrencySymbol() + new_price);
            return true;
        }
        else if (Main.getCache().getENCHANTMENTS().containsKey(item_name)){
            ConcurrentHashMap<String, EnchantmentData> ENCHANTMENTS = Main.getCache().getENCHANTMENTS();
            EnchantmentData data = ENCHANTMENTS.get(item_name);
            data.setPrice(price);
            ENCHANTMENTS.put(item_name, data);
            Main.getCache().updateEnchantments(ENCHANTMENTS);
            player.sendMessage(ChatColor.GREEN + "Changed " + Section.getItemDisplayName(item_name) + " to " + Config.getCurrencySymbol() + new_price);
            return true;
        }
        else {
            GDPData data = Main.getCache().getGDP_DATA();
            if (item_name.equals("GDP")){
                data.setGDP(price);
                player.sendMessage(ChatColor.GREEN + "Changed GDP to " + Config.getCurrencySymbol() + new_price);
                return true;
            }
            else if (item_name.equals("BALANCE")){
                data.setBalance(price);
                player.sendMessage(ChatColor.GREEN + "Changed Balance to " + Config.getCurrencySymbol() + new_price);
                return true;
            }
            else if (item_name.equals("DEBT")){
                data.setDebt(price);
                player.sendMessage(ChatColor.GREEN + "Changed Debt to " + Config.getCurrencySymbol() + new_price);
                return true;
            }
            else if (item_name.equals("Loss")){
                data.setLoss(price);
                player.sendMessage(ChatColor.GREEN + "Changed Loss to " + Config.getCurrencySymbol() + new_price);
                return true;
            }
            else if (item_name.equals("GDP")){
                data.setInflation(price);
                player.sendMessage(ChatColor.GREEN + "Changed Inflation to " + Config.getCurrencySymbol() + new_price);
                return true;
            }
        }
        player.sendMessage(ChatColor.RED + item_name + " is not a valid input.");
        return false;
    }

}
