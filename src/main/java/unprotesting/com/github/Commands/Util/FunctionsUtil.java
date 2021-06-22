package unprotesting.com.github.commands.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.data.ephemeral.data.MessagesData;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;
import unprotesting.com.github.economy.EconomyFunctions;

public class FunctionsUtil {

    
    public static void buyItem(Player player, String item, int amount){
        double bal = EconomyFunctions.getEconomy().getBalance(player);
        double price = Main.getCache().getItemPrice(item, false);
        if (bal < price){
            player.sendMessage(MessagesData.getPlayerBuyItemString("not-enough-money", player, item, price, amount));
            return;
        }
        if (bal < (price*amount)){
            player.sendMessage(MessagesData.getPlayerBuyItemString("not-enough-moneyxamount", player, item, price, amount));
            amount = (int) Math.floor(bal/price);
        }
        if (Main.getCache().getBuysLeft(item, player) < amount){
            player.sendMessage(MessagesData.getPlayerBuyItemString("run-out-of-buys", player, item, price, amount));
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().addItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1){
            player.sendMessage(MessagesData.getPlayerBuyItemString("not-enough-space", player, item, price, amount));
            return;
        }
        EconomyFunctions.getEconomy().withdrawPlayer(player, (amount*price));
        player.sendMessage(MessagesData.getPlayerBuyItemString("shop-purchase", player, item, price, amount));
        Main.getCache().addSale(player, item, price, amount, SalePositionType.BUY);
    }

    public static void sellItem(Player player, String item, int amount){
        double price = Main.getCache().getItemPrice(item, true);
        double buyprice = Main.getCache().getItemPrice(item, false);
        if (amount < 1){
            player.sendMessage(MessagesData.getPlayerSellItemString("dont-have-item", player, item, buyprice, amount, price));
            return;
        }
        if (Main.getCache().getSellsLeft(item, player) < amount){
            player.sendMessage(MessagesData.getPlayerSellItemString("run-out-of-sells", player, item, buyprice, amount, price));
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().removeItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        EconomyFunctions.getEconomy().depositPlayer(player, (amount*price));
        player.sendMessage(MessagesData.getPlayerSellItemString("shop-sell", player, item, buyprice, amount, price));
        Main.getCache().addSale(player, item, price, amount, SalePositionType.SELL);
    }

    @SuppressWarnings("deprecation")
    public static void buyEnchantment(Player player, String enchantment){
        if (!Config.isEnableEnchantments()){
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean off = false;
        if (item == null){
            off = true;
            item = player.getInventory().getItemInOffHand();
            if (item == null){
                player.sendMessage(MessagesData.getPlayerBuyEnchantmentString("hold-item-in-hand", player, null, 0.0, 0, 0.0, enchantment, 0.0, 0));
            }
        }
        double item_price = Main.getCache().getItemPrice(item.getType().toString(), false);
        double item_price_sell = Main.getCache().getItemPrice(item.getType().toString(), false);
        double bal = EconomyFunctions.getEconomy().getBalance(player);
        double price = Main.getCache().getOverallEnchantmentPrice(enchantment, item_price, false);
        Enchantment ench = Enchantment.getByName(enchantment);
        if (ench == null){
            player.sendMessage(MessagesData.getPlayerBuyEnchantmentString("enchantment-error", player, item.getType().toString(),
            item_price, 1, item_price_sell, enchantment, price, 0));
        }
        int level = 0;
        if (item.containsEnchantment(ench)){
            level = item.getEnchantmentLevel(ench);
        }
        if (bal < price){
            player.sendMessage(MessagesData.getPlayerBuyEnchantmentString("not-enough-money-enchantments", player, item.getType().toString(),
            item_price, 1, item_price_sell, enchantment, price, level+1));
            return;
        }
        try{
            item.addEnchantment(ench, level+1);
        }
        catch(IllegalArgumentException e){
            player.sendMessage(MessagesData.getPlayerBuyEnchantmentString("enchantment-error", player, item.getType().toString(),
            item_price, 1, item_price_sell, enchantment, price, level+1));
            return;
        }
        if (!off){
            player.getInventory().setItemInMainHand(item);
        }
        else{
            player.getInventory().setItemInOffHand(item);
        }
        Main.getCache().addSale(player, enchantment, price, 1, SalePositionType.EBUY);
        EconomyFunctions.getEconomy().withdrawPlayer(player, price);
        player.sendMessage(MessagesData.getPlayerBuyEnchantmentString("enchantment-purchase", player, item.getType().toString(),
            item_price, 1, item_price_sell, enchantment, price, level+1));
        player.getInventory().setItemInMainHand(item);
    }

    @SuppressWarnings("deprecation")
    public static void sellCustomItem(Player player, ItemStack item, boolean autosell){
        if (item == null){
            return;
        }
        if (!Main.getCache().getITEMS().contains(item.getType().toString()) || item.getAmount() < 1){
            if (!autosell){
                player.sendMessage(MessagesData.getPlayerSellItemString("cannot-sell-custom", player, item.getType().toString(), 0.0, 0, 0.0));
                player.getInventory().addItem(item);
            }
            return;
        }
        double ratio = 1;
        double fprice = 0;
        Map<Enchantment, Integer> enchantments = null;
        try{
            enchantments = item.getEnchantments();
        }
        catch(NullPointerException e){}
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
                    player.sendMessage(MessagesData.getPlayerSellItemString("cannot-sell-custom", player, item.getType().toString(), 0.0, 0, 0.0));
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
            player.sendMessage(MessagesData.getPlayerSellItemString("cannot-sell-custom", player, item.getType().toString(), 0.0, 0, 0.0));
            player.getInventory().addItem(item);
            return;
        }
        fprice = fprice + item_price*ratio;
        fprice = getnewPriceWithDurability(fprice, item);
        if (!autosell){
            EconomyFunctions.getEconomy().depositPlayer(player, (item.getAmount()*fprice));
            player.sendMessage(MessagesData.getPlayerSellItemString("sell-custom-item", player, item.getType().toString(), 0.0, item.getAmount(), fprice));
        }
        if (autosell){
            AutosellData data = Main.getAutosellData();
            data.add(player.getUniqueId().toString(), item.getAmount()*fprice);
            Main.setAutosellData(data);
        }
        Main.getCache().addSale(player, item.getType().toString(), Main.getCache().getItemPrice(item.getType().toString(), true), item.getAmount(), SalePositionType.SELL);
        for (Enchantment ench : item.getEnchantments().keySet()){
            Main.getCache().addSale(player, ench.getName(), Main.getCache().getEnchantmentPrice(ench.toString(), true), item.getEnchantmentLevel(ench), SalePositionType.ESELL);
        }
    }

    @Deprecated
    private static double getnewPriceWithDurability(double price, ItemStack item){
        double durability = (double) item.getDurability();
        double maxDurability = (double) item.getType().getMaxDurability();
        if (durability == 0 ){
            return price;
        }
        double current = maxDurability - durability;
        double result = (current/maxDurability);
        double newprice = price*result;
        newprice = newprice - newprice * 0.01 * Config.getDurabilityLimiter();
        return newprice;
    }   
}
