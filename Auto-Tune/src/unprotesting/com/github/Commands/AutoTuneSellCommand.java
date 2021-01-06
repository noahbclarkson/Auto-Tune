package unprotesting.com.github.Commands;

import java.util.UUID;
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

    ConcurrentHashMap<UUID, Gui> guis = new ConcurrentHashMap<UUID, Gui>();

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
                guis.put(p.getUniqueId(), loadSellGUI(p, sender2));
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
        ItemStack[] items = GUI.getInventory().getContents();
		sellItems(player, items, false);
		GUI.getInventory().clear();
	}

    public static String getItemStringForItemStack(ItemStack item) {
		return item.getType().toString().toUpperCase();
	}

    @Deprecated
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
            Double buyAmount = tempDoublearray[1];
            Double sellAmount = tempDoublearray[2];
            sellAmount = quantity + sellAmount;
            Double[] tempPutDouble = {tempDoublearray[0], buyAmount, sellAmount};
            tempMap1.put(tempMapSize-1, tempPutDouble);
            Main.map.put(itemString, tempMap1);
            boolean enchantmentPresent = true;
            double enchPrice = EnchantmentAlgorithm.calculatePriceWithEnch(item, false);
            if (enchPrice == 0.0){
                enchantmentPresent = false;
                enchPrice = AutoTuneGUIShopUserCommand.getItemPrice(item.getType().toString(), true);
            }
            moneyToGive += (quantity * enchPrice);
            if (enchantmentPresent){
                EnchantmentAlgorithm.updateEnchantSellData(item);
            }
		}
		if (couldntSell == true && !autoSell) {
            player.sendMessage(ChatColor.BOLD + "Cant sell " + Integer.toString(countSell) + "x of item");
            if (totMax == true){
                player.sendMessage(ChatColor.RED + "Maximum sells reached");
            }
        }
        else if (autoSell == true){
            roundAndGiveMoney(player, moneyToGive, true);
        }
        else if (autoSell == false){
        roundAndGiveMoney(player, moneyToGive, false);
        }
	}

    public Gui loadSellGUI(Player player, CommandSender sender2) {
        Gui GUI = new Gui(menuRows, "Selling Panel");
        StaticPane SellingPane = new StaticPane(0, 0, 9, menuRows, Priority.HIGH);
        GUI.addPane(SellingPane);
        GUI.setOnClose(this::onSellClose);
        GUI.show((HumanEntity) sender2);
        guis.put(player.getUniqueId(), GUI);
        return GUI;
    }
    
    private void onSellClose(InventoryCloseEvent event) {
		Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
		sell(player, guis.get(uuid));
        guis.remove(uuid);
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
	}

    
}

