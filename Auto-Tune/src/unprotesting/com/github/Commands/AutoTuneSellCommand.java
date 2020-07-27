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

import lombok.Getter;

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
            loadSellGUI(p, sender2);

            return true;

        }
        return true;

    }

    public void sell(Player player, Gui GUI) {
		sellItems(player, GUI.getInventory().getContents());
		GUI.getInventory().clear();
	}

    public static String getItemStringForItemStack(ItemStack item) {
		return item.getType().toString().toUpperCase();
	}

    public static void sellItems(Player player, ItemStack[] items) {
		double moneyToGive = 0;
		boolean couldntSell = false;
        int countSell = 0;
		for (ItemStack item : items) {

			if (item == null) {
				continue;
			}

			String itemString = getItemStringForItemStack(item);

            ConcurrentHashMap<Integer,Double[]> tempMap1 = Main.map.get(itemString);
            
			if ((tempMap1==null)) {
				countSell += 1;
				couldntSell = true;
                player.getInventory().addItem(item);
				continue;
			}

			int quantity = item.getAmount();
            Integer tempMapSize = tempMap1.size();
            Double[] tempDoublearray = tempMap1.get(tempMapSize-1);
            Double sellpricedif = Config.getSellPriceDifference();
            ConfigurationSection config = Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getConfigurationSection((itemString));
            Double sellpricedif2 = config.getDouble("sell-difference", sellpricedif);
            Double sellPrice = (tempDoublearray[0]) - (tempDoublearray[0]*0.01*sellpricedif2);
            Double buyAmount = tempDoublearray[1];
            Double sellAmount = tempDoublearray[2];
            sellAmount += quantity;
            Double[] tempPutDouble = {tempDoublearray[0], buyAmount, sellAmount};
            tempMap1.put(tempMapSize-1, tempPutDouble);
            Main.map.put(itemString, tempMap1);
			moneyToGive += quantity * sellPrice;

		}

		if (couldntSell == true) {
			player.sendMessage("Cant sell " + Integer.toString(countSell) + "x of item");
		}
		roundAndGiveMoney(player, moneyToGive);
	}

    public void sell(Player player) {
		sellItems(player, GUI.getInventory().getContents());
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
    
    public static void roundAndGiveMoney(Player player, double moneyToGive) {
		Double moneyToGiveRounded = (double) Math.round(moneyToGive * 100) / 100;

		if (moneyToGiveRounded > 0) {
			Main.econ.depositPlayer(player, moneyToGiveRounded);
			
			player.sendMessage(ChatColor.GOLD + "Your items were sold, and $" + moneyToGiveRounded + " was added to your account.");
		}
	}

    
}