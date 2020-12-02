package unprotesting.com.github.Commands;

import java.util.concurrent.ConcurrentHashMap;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.EnchantmentAlgorithm;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneSellCommand implements CommandExecutor {

    public Integer menuRows = Config.getMenuRows();
    private Gui GUI;

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

    public void sell(Player player, Gui GUI) {
		sellItems(player, GUI.getInventory().getContents(), false);
		GUI.getInventory().clear();
	}

    public static String getItemStringForItemStack(ItemStack item) {
		return item.getType().toString().toUpperCase();
	}

    public static void sellItems(Player player, ItemStack[] items, Boolean autoSell) {
		double moneyToGive = 0;
		boolean couldntSell = false;
        int countSell = 0;
        boolean totMax = false;
		for (ItemStack item : items) {

			if (item == null) {
				continue;
			}

			String itemString = getItemStringForItemStack(item);
            int quantity = item.getAmount();
            ConcurrentHashMap<Integer,Double[]> tempMap1 = Main.map.get(itemString);
            
			if ((tempMap1==null)) {
				countSell += quantity;
				couldntSell = true;
                player.getInventory().addItem(item);
				continue;
            }
            if (!autoSell){
                ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player.getUniqueId());
                Integer max = 10000;
                try{
                    max = (Integer)Main.getShopConfig().get("shops." + itemString + "." + "max-sell");
                }
                catch(ClassCastException ex){
                    max = Integer.parseInt(AutoTuneGUIShopUserCommand.df5.format(Main.getShopConfig().get("shops." + itemString + "." + "max-sell")));
                }
                if (max == null){
                    max = 100000;
                }
                if ((cMap.get(itemString)+quantity) > max){
                    couldntSell = true;
                    countSell += quantity;
                    totMax = true;
                    player.getInventory().addItem(item);
                    continue;
                }
            }
            ConcurrentHashMap<String, Integer> cMap2 = Main.maxSellMap.get(player.getUniqueId());
			cMap2.put(itemString, (cMap2.get(itemString)+quantity));
			Main.maxSellMap.put(player.getUniqueId(), cMap2);
            Integer tempMapSize = tempMap1.size();
            Double[] tempDoublearray = tempMap1.get(tempMapSize-1);
            Double sellpricedif = Config.getSellPriceDifference();
            Main.getINSTANCE();
            ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
                    .getConfigurationSection((itemString));
            Double sellpricedif2 = Config.getSellPriceDifference();
            try{
                sellpricedif2 = config.getDouble("sell-difference", sellpricedif);
            }
            catch(NullPointerException ex){
                sellpricedif2 = Config.getSellPriceDifference();
            }
            Double sellPrice = (tempDoublearray[0]) - (tempDoublearray[0]*0.01*sellpricedif2);
            Double buyAmount = tempDoublearray[1];
            Double sellAmount = tempDoublearray[2];
            sellAmount = quantity + sellAmount;
            Double[] tempPutDouble = {tempDoublearray[0], buyAmount, sellAmount};
            tempMap1.put(tempMapSize-1, tempPutDouble);
            Main.map.put(itemString, tempMap1);
            double enchPrice = EnchantmentAlgorithm.calculatePriceWithEnch(item);
            moneyToGive += quantity * enchPrice;
            moneyToGive = moneyToGive - (moneyToGive*0.01*sellpricedif2);
            EnchantmentAlgorithm.updateEnchantSellData(item);

		}
		if (couldntSell == true && !autoSell) {
            player.sendMessage(ChatColor.BOLD + "Cant sell " + Integer.toString(countSell) + "x of item");
            if (totMax == true){
                player.sendMessage(ChatColor.RED + "Maximum sells reached");
            }
        }
        if (autoSell == true){
            roundAndGiveMoney(player, moneyToGive, true);
        }
        if (autoSell == false){
        roundAndGiveMoney(player, moneyToGive, false);
        }
	}

    public void sell(Player player) {
		sellItems(player, GUI.getInventory().getContents(), false);
		GUI.getInventory().clear();
	}

    public void loadSellGUI(Player player, CommandSender sender2) {
        GUI = new Gui(menuRows, "Selling Panel");
        StaticPane SellingPane = new StaticPane(0, 0, 9, menuRows, Priority.HIGH);
        GUI.addPane(SellingPane);
        GUI.setOnClose(this::onSellClose);
        GUI.show((HumanEntity) sender2);
    }
    
    private void onSellClose(InventoryCloseEvent event) {
		Player player = (Player) event.getPlayer();
		sell(player);
    }
    
    public static void roundAndGiveMoney(Player player, double moneyToGive, Boolean autoSell) {
		Double moneyToGiveRounded = (double) Math.round(moneyToGive * 100) / 100;

		if (moneyToGiveRounded > 0 && moneyToGiveRounded != null) {
            
            if (autoSell == false){
            Main.econ.depositPlayer(player, moneyToGiveRounded);
            player.sendMessage(ChatColor.GOLD + "Your items were sold, and "+ Config.getCurrencySymbol() + moneyToGiveRounded + " was added to your account.");
            if (Config.isCalculateGlobalGDP()){Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+moneyToGiveRounded));}
            }
            if (autoSell == true){
                if (Main.tempdatadata.get(player.getUniqueId().toString()) == null){
                    Main.tempdatadata.put(player.getUniqueId().toString(), 0.0);
                }
                Main.tempdatadata.put(player.getUniqueId().toString(), Main.tempdatadata.get(player.getUniqueId().toString())+moneyToGive);
            }
		}
	}

    
}

