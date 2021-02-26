package unprotesting.com.github.Commands;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.apache.commons.lang.ObjectUtils.Null;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.EnchantmentAlgorithm;
import unprotesting.com.github.util.TextHandler;
import unprotesting.com.github.util.Transaction;

public class AutoTuneSellCommand implements CommandExecutor {

    public Integer menuRows = Config.getMenuRows();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String sell, String[] args) {

        if (command.getName().equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(sender, "&cPlayers only.");
                return true;
            }
            CommandSender sender2 = sender;
            Player p = (Player) sender;
            if (args.length == 0){
                if (p.hasPermission("at.sell") || p.isOp()){loadSellGUI(p, sender2);}
                else if (!(p.hasPermission("at.sell")) && !(p.isOp())){TextHandler.noPermssion(p);}
                return true;
            }
            if (args.length > 0){
                return false;
            }

        }
        return false;

    }

    public void sell(Player player, ItemStack[] items) {
		sellItems(player, items, false);
	}

    public static String getItemStringForItemStack(ItemStack item) {
		return item.getType().toString().toUpperCase();
	}

    @Deprecated
    public static void sellItems(Player player, ItemStack[] items, Boolean autoSell){
        double money = 0.0;
        for (ItemStack item : items) {
            if (item == null){
                continue;
            }
            String itemString = getItemStringForItemStack(item);
            if (Config.isUsePermissionsForShop()){
                if (!player.hasPermission("at.sell." + itemString)){
                    player.getInventory().addItem(item);
                    TextHandler.noPermssion(player);
                    continue;
                }
            }
            int quantity = item.getAmount();
            Double pricePerItem = 0.0;
            if (item.getItemMeta().hasEnchants()){
                pricePerItem =  EnchantmentAlgorithm.calculatePriceWithEnch(item, false);
            }
            if (!item.getItemMeta().hasEnchants()){
                pricePerItem = AutoTuneGUIShopUserCommand.getItemPrice(item, true);
            }
            if (pricePerItem == null || pricePerItem == 0 || pricePerItem.isInfinite() || pricePerItem.isNaN()){
                player.sendMessage(ChatColor.RED + "Couldn't sell " + item.getType().toString());
                player.getInventory().addItem(item);
                continue;
            }
            double totalPrice = pricePerItem * quantity;
            int maxSells = getMaxSells(itemString);
            int currentMaxSells = getCurrentSellsMax(itemString, player);
            int newTempCurrentMaxSells = currentMaxSells + quantity;
            if (newTempCurrentMaxSells > maxSells){
                player.sendMessage(ChatColor.RED + "Max Sells Reached for " + item.getType().toString());
                if (currentMaxSells >= maxSells){
                    player.getInventory().addItem(item);
                    continue;
                }
                else{
                    int difference = newTempCurrentMaxSells - (maxSells);
                    totalPrice = pricePerItem * difference;
                    item = loadADifferentAmountFromItemStack(item, difference);
                    player.getInventory().addItem(loadADifferentAmountFromItemStack(item, (quantity - difference)));
                    quantity = difference;
                }
            }
            String item_name = item.getType().toString();
            increaseMaxSells(player, quantity, item_name);
            increaseSells(item_name, quantity);
            Transaction transaction = new Transaction(player.getName(), item, "Sell", totalPrice);
            transaction.loadIntoMap();
            loadEnchantmentTransactions(item, player);
            increaseMaxSells(player, quantity, itemString);
            money += totalPrice;
        }
        if (money > 0) {
            roundAndGiveMoney(player, money, autoSell);
        }
    }

    public static void increaseSells(String item_name, int amount) {
        try{
            ConcurrentHashMap<Integer, Double[]> map = Main.map.get(item_name);
            int size = map.size()-1;
            Double[] arr = map.get(size);
            arr[2] = arr[2] + amount;
            map.put(size, arr);
            Main.map.put(item_name, map);
        }
        catch (NullPointerException ex) {
            return;
        }
    }

    @Deprecated
    public static void loadEnchantmentTransactions (ItemStack item, Player player){
        if (item.getEnchantments().size() < 1){
            return;
        }
        else {
            Map<Enchantment, Integer> ench = item.getEnchantments();
            for (Map.Entry<Enchantment, Integer> enchants : ench.entrySet()){
                Enchantment enchant = enchants.getKey();
                Transaction transaction = new Transaction(player, enchant, "Sell");
                transaction.loadIntoMap();
                EnchantmentAlgorithm.updateEnchantSellData(enchant, enchants.getValue());
            }
        }
    }

    public static void increaseMaxSells (Player player, int amount, String item) {
        ConcurrentHashMap<String, Integer> map = Main.maxSellMap.get(player.getUniqueId());
        if (map.get(item) == null){
            map.put(item, 0);
        }
        map.put(item, map.get(item)+amount);
        Main.maxSellMap.put(player.getUniqueId(), map);
    }

    public static ItemStack loadADifferentAmountFromItemStack (ItemStack item, int new_size){
        ItemMeta im = item.getItemMeta();
        ItemStack output = new ItemStack(item.getType(), new_size);
        output.setItemMeta(im);
        return output;
    }

    public static int getCurrentSellsMax(String item, Player player){
        UUID uuid = player.getUniqueId();
        try{
            return Main.maxSellMap.get(uuid).get(item);
        }
        catch(NullPointerException ex){
            return 0;
        }
    }

    public static int getMaxSells(String item){
        Integer max = 10000;
        try{
            max = (Integer)Main.getShopConfig().get("shops." + item + "." + "max-sell");
        }
        catch(ClassCastException ex){
            max = Integer.parseInt(AutoTuneGUIShopUserCommand.df5.format(Main.getShopConfig().get("shops." + item + "." + "max-sell")));
        }
        if (max == null){
            max = 100000;
        }
        return max;
    }

    public Gui loadSellGUI(Player player, CommandSender sender2) {
        Gui GUI = new Gui(menuRows, "Selling Panel");
        StaticPane SellingPane = new StaticPane(0, 0, 9, menuRows, Priority.HIGH);
        GUI.addPane(SellingPane);
        GUI.setOnClose(this::onSellClose);
        GUI.show((HumanEntity) sender2);
        return GUI;
    }
    
    private void onSellClose(InventoryCloseEvent event) {
		Player player = (Player) event.getPlayer();
        sell(player, event.getInventory().getStorageContents());
        event.getInventory().clear();
    }
    
    public static void roundAndGiveMoney(Player player, double moneyToGive, Boolean autoSell) {

		if (moneyToGive > 0) {
            
            if (autoSell == false){
            Main.econ.depositPlayer(player, moneyToGive);
            player.sendMessage(ChatColor.GOLD + "Your items were sold, and "+ Config.getCurrencySymbol() + AutoTuneGUIShopUserCommand.df2.format(moneyToGive) + " was added to your account.");
            if (Config.isCalculateGlobalGDP()){Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+ moneyToGive));}
            }
            if (autoSell == true){
                if (Main.tempdatadata.get(player.getUniqueId().toString()) == null){
                    Main.tempdatadata.put(player.getUniqueId().toString(), 0.0);
                }
                Main.tempdatadata.put(player.getUniqueId().toString(), Main.tempdatadata.get(player.getUniqueId().toString())+moneyToGive);
            }
		}
        else {
            player.sendMessage(ChatColor.RED + "Error on sale!");
        }
	}

    
}

