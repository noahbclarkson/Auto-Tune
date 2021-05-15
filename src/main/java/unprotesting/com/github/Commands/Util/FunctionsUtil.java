package unprotesting.com.github.Commands.Util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;
import unprotesting.com.github.Economy.EconomyFunctions;

public class FunctionsUtil {

    public static boolean buyItem(Player player, String item, int amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double bal = EconomyFunctions.economy.getBalance(player);
        double price = Main.cache.getItemPrice(item);
        if (bal < price){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(price) + " to purchase this item");
            return false;
        }
        if (bal < (price*amount)){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(price*amount) + " to purchase x" + amount + " of this item.");
            amount = (int) Math.floor(bal/price);
        }
        if (Main.cache.getBuysLeft(item, player) < amount){
            player.sendMessage(ChatColor.RED + "You have run out of buys for this item.");
            return false;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().addItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1){
            player.sendMessage(ChatColor.RED + "Not enough space in inventory.");
            return false;
        }
        EconomyFunctions.economy.withdrawPlayer(player, (amount*price));
        player.sendMessage(ChatColor.GREEN + "Purchased x" + amount + " of " + ChatColor.GOLD + item + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(price) + ".");
        Main.cache.addSale(player, item, price, amount, SalePositionType.BUY);
        return true;
    }

    public static boolean sellItem(Player player, String item, int amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double price = Main.cache.getItemPrice(item, true);
        HashMap<Integer, ItemStack> map = player.getInventory().removeItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1){
            player.sendMessage(ChatColor.RED + "You do not have this item.");
            return false;
        }
        if (Main.cache.getSellsLeft(item, player) < amount){
            player.sendMessage(ChatColor.RED + "You have run out of sells for this item.");
            return false;
        }
        EconomyFunctions.economy.depositPlayer(player, (amount*price));
        player.sendMessage(ChatColor.GREEN + "Sold x" + amount + " of " + ChatColor.GOLD + item + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(price) + ".");
        Main.cache.addSale(player, item, price, amount, SalePositionType.SELL);
        return true;
    }
    
}
