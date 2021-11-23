package unprotesting.com.github.commands.util;

import java.text.DecimalFormat;
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

    public static void buyItem(Player player, String item, String displayName, int item_amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double bal = EconomyFunctions.getEconomy().getBalance(player);
        double price = Main.getCache().getItemPrice(item, false);
        int amount = item_amount;
        String[] inputs = new String[]{displayName, df.format(price), Integer.toString(amount), df.format(price*amount)};
        if (bal < price){
            player.sendMessage(MessagesData.getMessageString(player, "not-enough-money", inputs));
            return;
        }
        if (bal < (price*amount)){
            player.sendMessage(MessagesData.getMessageString(player, "not-enough-moneyxamount", inputs));
            amount = (int) Math.floor(bal/price);
        }
        if (Main.getCache().getBuysLeft(item, player) < amount){
            player.sendMessage(MessagesData.getMessageString(player, "run-out-of-buys", inputs));
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().addItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1){
            player.sendMessage(MessagesData.getMessageString(player, "not-enough-space", inputs));
            return;
        }
        EconomyFunctions.getEconomy().withdrawPlayer(player, (amount*price));
        player.sendMessage(MessagesData.getMessageString(player, "shop-purchase", inputs));
        Main.getCache().addSale(player.getUniqueId(), item, price, amount, SalePositionType.BUY);
    }

    public static void sellItem(Player player, String item, String displayName, int amount){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        double price = Main.getCache().getItemPrice(item, true);
        double buyprice = Main.getCache().getItemPrice(item, false);
        String[] inputs = new String[]{displayName, df.format(buyprice), Integer.toString(amount), df.format(buyprice*amount), df.format(price), df.format(price*amount)};
        if (amount < 1){
            player.sendMessage(MessagesData.getMessageString(player, "dont-have-item", inputs));
            return;
        }
        if (Main.getCache().getSellsLeft(item, player) < amount){
            player.sendMessage(MessagesData.getMessageString(player, "run-out-of-sells", inputs));
            return;
        }
        HashMap<Integer, ItemStack> map = player.getInventory().removeItem(new ItemStack(Material.matchMaterial(item), amount));
        if ((map.size()) > 0){
            ItemStack istack = (ItemStack)(Arrays.asList(map.values().toArray())).get(0);
            amount = amount-istack.getAmount();
        }
        if (amount < 1 || price == 0){
            player.sendMessage(MessagesData.getMessageString(player, "dont-have-item", inputs));
            return;
        }
        EconomyFunctions.getEconomy().depositPlayer(player, (amount*price));
        player.sendMessage(MessagesData.getMessageString(player, "shop-sell", inputs));
        Main.getCache().addSale(player.getUniqueId(), item, price, amount, SalePositionType.SELL);
    }

    @SuppressWarnings("deprecation")
    public static void buyEnchantment(Player player, String enchantment, String displayName){
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
                player.sendMessage(MessagesData.getMessageString(player, "hold-item-in-hand", new String[]{}));
            }
        }
        double item_price = Main.getCache().getItemPrice(item.getType().toString(), false);
        double item_price_sell = Main.getCache().getItemPrice(item.getType().toString(), false);
        double bal = EconomyFunctions.getEconomy().getBalance(player);
        double price = Main.getCache().getOverallEnchantmentPrice(enchantment, item_price, false);
        Enchantment ench = Enchantment.getByName(enchantment);
        int level = 0;
        if (ench == null){
            player.sendMessage(MessagesData.getMessageString(player, "enchantment-error", new String[]{displayName}));
            return;
        }
        if (item.containsEnchantment(ench)){
            level = item.getEnchantmentLevel(ench);
        }
        String[] inputs = new String[]{displayName, df.format(item_price), Integer.toString(item.getAmount()), df.format(item.getAmount()*item_price),
         df.format(item_price_sell), df.format(item.getAmount()*item_price_sell), enchantment, Integer.toString(level), df.format(price)};
        if (bal < price){
            player.sendMessage(MessagesData.getMessageString(player, "enchantment-error", inputs));
            return;
        }
        try{
            item.addEnchantment(ench, level+1);
        }
        catch(IllegalArgumentException e){
            player.sendMessage(MessagesData.getMessageString(player, "enchantment-error", inputs));
            return;
        }
        if (!off){
            player.getInventory().setItemInMainHand(item);
        }
        else{
            player.getInventory().setItemInOffHand(item);
        }
        Main.getCache().addSale(player.getUniqueId(), enchantment, price, 1, SalePositionType.EBUY);
        EconomyFunctions.getEconomy().withdrawPlayer(player, price);
        player.sendMessage(MessagesData.getMessageString(player, "enchantment-purchase", inputs));
        player.getInventory().setItemInMainHand(item);
    }

    @SuppressWarnings("deprecation")
    public static void sellCustomItem(Player player, ItemStack item, boolean autosell){
        if (item == null){
            return;
        }
        if ((!Main.getCache().getITEMS().containsKey(item.getType().toString())) || item.getAmount() < 1){
            if (!autosell){
                player.sendMessage(MessagesData.getMessageString(player, "cannot-sell-custom", item.getType().toString()));
                System.out.println("a");
                player.getInventory().addItem(item);
            }
            return;
        }
        if (Main.getCache().getSellsLeft(item.getType().toString(), player) < item.getAmount()){
            player.sendMessage(MessagesData.getMessageString(player, "run-out-of-sells", item.getType().toString()));
            player.getInventory().addItem(item);
            return;
        }
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
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
                    player.sendMessage(MessagesData.getMessageString(player, "cannot-sell-custom", item.getType().toString()));
                    System.out.println("b");
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
            player.sendMessage(MessagesData.getMessageString(player, "cannot-sell-custom", item.getType().toString()));
            System.out.println("c");
            player.getInventory().addItem(item);
            return;
        }
        Double item_buy_price = Main.getCache().getItemPrice(item.getType().toString(), false);
        fprice = fprice + item_price*ratio;
        fprice = getNewPriceWithDurability(fprice, item);
        if (!autosell){
            EconomyFunctions.getEconomy().depositPlayer(player, (item.getAmount()*fprice));
            player.sendMessage(MessagesData.getMessageString(player, "sell-custom-item", new String[]{item.getType().toString(),
             df.format(item_buy_price), Integer.toString(item.getAmount()), df.format(item.getAmount()*item_buy_price),
              df.format(fprice), df.format(fprice*item.getAmount()), item.getEnchantments().toString()}));
        }
        if (autosell){
            AutosellData data = Main.getAutosellData();
            data.add(player.getUniqueId().toString(), item.getAmount()*fprice);
            Main.setAutosellData(data);
        }
        Main.getCache().addSale(player.getUniqueId(), item.getType().toString(), Main.getCache().getItemPrice(item.getType().toString(), true), item.getAmount(), SalePositionType.SELL);
        for (Enchantment ench : item.getEnchantments().keySet()){
            Main.getCache().addSale(player.getUniqueId(), ench.getName(), Main.getCache().getEnchantmentPrice(ench.toString(), true), item.getEnchantmentLevel(ench), SalePositionType.ESELL);
        }
    }

    @Deprecated
    private static double getNewPriceWithDurability(double price, ItemStack item){
        double durability = (double) item.getDurability();
        double maxDurability = (double) item.getType().getMaxDurability();
        if (durability == 0 ){
            return price;
        }
        double current = maxDurability - durability;
        double result = (current/maxDurability);
        double newPrice = price*result;
        newPrice = newPrice - newPrice * 0.01 * Config.getDurabilityLimiter();
        return newPrice;
    }   
}
