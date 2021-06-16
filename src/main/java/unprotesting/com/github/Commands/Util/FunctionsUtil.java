package unprotesting.com.github.Commands.Util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;
import unprotesting.com.github.Economy.EconomyFunctions;

public class FunctionsUtil {

    
    public static void buyItem(Player player, String item, int amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double bal = EconomyFunctions.economy.getBalance(player);
        double price = Main.getCache().getItemPrice(item, false);
        if (bal < price){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(price) + " to purchase this item");
            return;
        }
        if (bal < (price*amount)){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(price*amount) + " to purchase x" + amount + " of this item.");
            amount = (int) Math.floor(bal/price);
        }
        if (Main.getCache().getBuysLeft(item, player) < amount){
            player.sendMessage(ChatColor.RED + "You have run out of buys for this item.");
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().addItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1){
            player.sendMessage(ChatColor.RED + "Not enough space in inventory.");
            return;
        }
        EconomyFunctions.economy.withdrawPlayer(player, (amount*price));
        player.sendMessage(ChatColor.GREEN + "Purchased x" + amount + " of " + ChatColor.GOLD + item + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(price*amount) + ".");
        Main.getCache().addSale(player, item, price, amount, SalePositionType.BUY);
    }

    public static void sellItem(Player player, String item, int amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double price = Main.getCache().getItemPrice(item, true);
        if (amount < 1){
            player.sendMessage(ChatColor.RED + "You do not have this item.");
            return;
        }
        if (Main.getCache().getSellsLeft(item, player) < amount){
            player.sendMessage(ChatColor.RED + "You have run out of sells for this item.");
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().removeItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        EconomyFunctions.economy.depositPlayer(player, (amount*price));
        player.sendMessage(ChatColor.GREEN + "Sold x" + amount + " of " + ChatColor.GOLD + item + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(price*amount) + ".");
        Main.getCache().addSale(player, item, price, amount, SalePositionType.SELL);
    }

    @SuppressWarnings("deprecation")
    public static void buyEnchantment(Player player, String enchantment){
        if (!Config.isEnableEnchantments()){
            return;
        }
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean off = false;
        if (item == null){
            off = true;
            item = player.getInventory().getItemInOffHand();
            if (item == null){
                player.sendMessage(ChatColor.GOLD + "Hold the item you want to enchant in your hand!");
            }
        }
        double item_price = Main.getCache().getItemPrice(item.getType().toString(), false);
        double bal = EconomyFunctions.economy.getBalance(player);
        double price = Main.getCache().getOverallEnchantmentPrice(enchantment, item_price, false);
        if (bal < price){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(price) + " to purchase this item");
            return;
        }
        Enchantment ench = Enchantment.getByName(enchantment);
        if (ench == null){
            player.sendMessage(ChatColor.RED + "Enchantment "+ enchantment + " does not exist");
        }
        int level = 0;
        if (item.containsEnchantment(ench)){
            level = item.getEnchantmentLevel(ench);
        }
        try{
            item.addEnchantment(ench, level+1);
        }
        catch(IllegalArgumentException e){
            player.sendMessage(ChatColor.RED + "Cannot enchant " + item.getType().toString() + " with enchantment " + enchantment + " of level " + (level+1));
            return;
        }
        if (!off){
            player.getInventory().setItemInMainHand(item);
        }
        else{
            player.getInventory().setItemInOffHand(item);
        }
        Main.getCache().addSale(player, enchantment, price, 1, SalePositionType.EBUY);
        EconomyFunctions.economy.withdrawPlayer(player, price);
        player.sendMessage(ChatColor.GREEN + "Purchased 1x of " + ChatColor.GOLD + enchantment.toString() + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(price) + ".");
        player.getInventory().setItemInMainHand(item);
    }

    @SuppressWarnings("deprecation")
    public static void sellCustomItem(Player player, ItemStack item){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        if (item == null){
            return;
        }
        double ratio = 1;
        double fprice = 0;
        double bal = EconomyFunctions.economy.getBalance(player);
        Map<Enchantment, Integer> enchantments = null;
        try{
            enchantments = item.getEnchantments();
        }
        catch(NullPointerException e){};
        if (enchantments != null && enchantments.size() > 0 && Config.isEnableEnchantments()){
            ratio = 0;
            for (Enchantment ench : enchantments.keySet()){
                int level = item.getEnchantmentLevel(ench);
                Double cratio;
                Double price;
                try{
                    price = Main.getCache().getEnchantmentPrice(ench.getName(), true);
                    price = price - price*0.01*Config.getEnchantmentLimiter();
                    fprice = fprice + price*level;
                    cratio = Main.getCache().getEnchantmentRatio(ench.getName());
                }
                catch(NullPointerException e){
                    player.sendMessage(ChatColor.RED + "Cannot sell " + item.getType().toString());
                    player.getInventory().addItem(item);
                    return;
                }
                if (cratio > ratio){
                    ratio = cratio;
                }
            }
        }
        Double item_price;
        try{
            item_price = Main.getCache().getItemPrice(item.getType().toString(), true);
        }
        catch(NullPointerException e){
            player.sendMessage(ChatColor.RED + "Cannot sell " + item.getType().toString());
            player.getInventory().addItem(item);
            return;
        }
        fprice = fprice + item_price*ratio;
        if (bal < fprice){
            player.sendMessage(ChatColor.RED + "You need " + Config.getCurrencySymbol() + df.format(fprice) + " to purchase this item");
            int size = player.getInventory().addItem(item).size();
            if (size > 0){
                player.getLocation().getWorld().dropItemNaturally(player.getLocation().add(0, 0.5, 0), item);
            }
            return;
        }
        EconomyFunctions.economy.depositPlayer(player, (item.getAmount()*fprice));
        player.sendMessage(ChatColor.GREEN + "Sold x" + item.getAmount() + " of " + ChatColor.GOLD + item.getType().toString() + ChatColor.GREEN + " for " + Config.getCurrencySymbol() + df.format(fprice*item.getAmount()) + ".");
        Main.getCache().addSale(player, item.getType().toString(), Main.getCache().getItemPrice(item.getType().toString(), false), item.getAmount(), SalePositionType.SELL);
        for (Enchantment ench : item.getEnchantments().keySet()){
            Main.getCache().addSale(player, ench.getName(), Main.getCache().getEnchantmentPrice(ench.toString(), true), item.getEnchantmentLevel(ench), SalePositionType.ESELL);
        }
    }
}
