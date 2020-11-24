package unprotesting.com.github.Commands;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.Section;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneGUIShopUserCommand implements CommandExecutor {

	public static DecimalFormat df1 = new DecimalFormat("###########0.00");
	public static DecimalFormat df2 = new DecimalFormat("###,###,###,##0.00");
	public static DecimalFormat df3 = new DecimalFormat("###,###,###,##0.00000");
	public static DecimalFormat df4 = new DecimalFormat("###########0.0000");
	public static DecimalFormat df5 = new DecimalFormat("###########0");

	public static Integer SBPanePos = 1;

	public static Integer[] amounts = {1, 2, 4, 8, 16, 32, 64};

	@Override
	public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
		if (command.getName().equalsIgnoreCase("shop")) {
			if (!(sender instanceof Player)) {
				Main.sendMessage(sender, "&cPlayers only.");
				return true;
			}
			Player p = (Player) sender;
			if (Config.getMenuRows() == 6) {
				SBPanePos = 2;
			}
			if (args.length == 0){
				if (p.hasPermission("at.shop") || p.isOp())
				{
					loadGUISECTIONS(p);
				}
				else if (!(p.hasPermission("at.shop")) && !(p.isOp())){TextHandler.noPermssion(p);}
				return true;
			}
			if (args.length == 1){
				String inputSection = null;
				try{
					inputSection = args[0];
				}
				catch(ClassCastException ex){
					p.sendMessage("Unknown shop format: " + args[0]);
					return false;
				}
				catch(ArrayIndexOutOfBoundsException ex){
					return false;
				}
				for (int i = 0; i < Main.sectionedItems.length; i++){
					if (Main.sectionedItems[i].name.toLowerCase().equals(inputSection)){
						loadGUIMAIN(p, Main.sectionedItems[i], true);
						return true;
					}
				}
			}
			else{
				return false;
			}
		}
		return false;
	}

	public void loadGUISECTIONS(Player player){
		int lines = (int)Math.floor(((Main.sectionedItems.length)/7)+1);
		if (lines > 4){
			lines = 4;
		}
		Gui front = new Gui((lines+2), Config.getMenuTitle());
		OutlinePane pane = new OutlinePane(1, 1, 7, lines);
		for (int i = 0; i < Main.sectionedItems.length; i++){
			ItemStack is = new ItemStack((Main.sectionedItems[i].image));
			ItemMeta im = is.getItemMeta();
			im.setDisplayName(ChatColor.GOLD + Main.sectionedItems[i].name);
			im.setLore(Arrays.asList(ChatColor.WHITE + "Click to enter the " + (Main.sectionedItems[i].name.toLowerCase()) + " shop!"));
			is.setItemMeta(im);
			final Section inputSection = Main.sectionedItems[i];
			GuiItem gItem = new GuiItem(is, event ->{
				final Player playernew = player;
				if (event.getClick() == ClickType.LEFT) {
					event.setCancelled(true);
					player.getOpenInventory().close();
					loadGUIMAIN(player, inputSection, false);
				}
				else if (event.getClick() != ClickType.LEFT) {
					event.setCancelled(true);
					playernew.setItemOnCursor(null);
				}
			});
			pane.addItem(gItem);
			front.addPane(pane);
		}
		front.update();
		CommandSender playerSender = player;
		front.show((HumanEntity) playerSender);
	}

	public void loadGUIMAIN(Player player, Section sec, boolean twoArgs) {
		int itemAmount = sec.items.size();
		int lines = (int)Math.floor(((itemAmount-1)/7)+1);
		Main.log("Lines: " + lines);
		if (lines > 4){
			lines = 4;
		}
		Gui main = new Gui((lines+2), Config.getMenuTitle());
		Integer paneSize = (lines+1)*7;
		Integer paneAmount = (int) Math.ceil(itemAmount/paneSize)+1;
		Main.log("Pane Amount: " + paneAmount);
		Main.log("Pane Size: " + paneSize);
		PaginatedPane pPane = new PaginatedPane(0, 0, 9, (lines+2));
		OutlinePane[] shopPanes = new OutlinePane[paneAmount];
		for (int i = 0; i < shopPanes.length; i++){
			shopPanes[i] = new OutlinePane(1, 1, 7, lines);
			for(int k = 0; k < paneSize; k++){
				if(k+(i*paneSize) < sec.items.size()){
					String itemName = sec.items.get(k+(i*paneSize));
					ItemStack iStack = loadShopItem(itemName, sec);
					GuiItem item = new GuiItem(iStack, event ->{
						if (event.getClick() == ClickType.LEFT){
							event.setCancelled(true);
							player.getOpenInventory().close();
							loadGUITRADING(player, itemName);
						}
						else{
							event.setCancelled(true);
						}
					});
					shopPanes[i].addItem(item);
				}
				else{
					break;
				}
			}
		}
		for (int i = 0; i < shopPanes.length; i++){
			pPane.addPane(i, shopPanes[i]);
		}
		main.addPane(pPane);
		StaticPane[] panes = loadPagePanes(pPane, (lines+1), main);
		panes[0].setVisible(false);
		panes[1].setVisible(true);
		if (pPane.getPages() == 1){
			panes[1].setVisible(false);
		}
		main.addPane(panes[0]);
		main.addPane(panes[1]);
		if (twoArgs == false || (twoArgs == true && sec.showBackButton == true)){
			main.addPane(loadMainMenuBackPane(pPane));
		}
		CommandSender cSender = player;
		main.update();
		main.show((HumanEntity) cSender);
	}

	public void loadGUITRADING(Player player, String itemName){
		Gui main = new Gui(4, Config.getMenuTitle());
		OutlinePane front = new OutlinePane(1, 1, 7, 2);
		for (int i = 0; i < 14; i++){
			final int finalI = i;
			ItemStack iStack;
			GuiItem gItem;
			if (i < 7){
				iStack = loadTradingItem(itemName, amounts[i], true);
				gItem = new GuiItem(iStack, event ->{
					event.setCancelled(true);
					try {
						HashMap<Integer, ItemStack> unableItems = player.getInventory().addItem(new ItemStack(Material.matchMaterial(itemName), amounts[finalI]));
						if (unableItems.size() > 0){
							player.sendMessage(ChatColor.BOLD + "Cant sell " + Integer.toString(amounts[finalI]) + "x of " + itemName);
						}
						else{
							sendPlayerShopMessageAndUpdateGDP(amounts[finalI], player, itemName, false);
						}
					}
					catch(IllegalArgumentException ex){
					}
				});
			}
			else{
				iStack = loadTradingItem(itemName, amounts[i-7], false);
				gItem = new GuiItem(iStack, event ->{
					event.setCancelled(true);
					try {
						HashMap<Integer, ItemStack> takenItems = player.getInventory().removeItem(new ItemStack(Material.matchMaterial(itemName), amounts[finalI]));
						if (takenItems.size() > 0){
							player.sendMessage(ChatColor.BOLD + "Cant sell " + Integer.toString(amounts[finalI]) + "x of " + itemName);
						}
						else{
							sendPlayerShopMessageAndUpdateGDP(amounts[finalI-8], player, itemName, true);
						}
					}
					catch(IllegalArgumentException ex){
					}
				});
			}
			front.addItem(gItem);
		}
		main.addPane(front);
		CommandSender cSender = player;
		main.update();
		main.show((HumanEntity) cSender);
	}

	public ItemStack loadShopItem(String itemName, Section sec){
		ItemStack iStack = new ItemStack(Material.matchMaterial(itemName));
		ItemMeta iMeta = iStack.getItemMeta();
		Integer[] maxBuySellForItem = sec.itemMaxBuySell.get(itemName);
		iMeta.setDisplayName(ChatColor.GOLD + itemName);
		iMeta.setLore(Arrays.asList((ChatColor.GRAY + "Click to purchase/sell"),
			(ChatColor.WHITE + "Max Buys: " + maxBuySellForItem[0] + " per " + Config.getTimePeriod() + "min"),
			(ChatColor.WHITE + "Max Sells: " + maxBuySellForItem[1] + " per " + Config.getTimePeriod() + "min")));
		iStack.setItemMeta(iMeta);
		return iStack;
	}

	public ItemStack loadTradingItem(String itemName, int number, boolean buy){
		ItemStack iStack = new ItemStack(Material.matchMaterial(itemName));
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.GOLD + itemName);
		if (buy){
			iMeta.setLore(Arrays.asList((ChatColor.WHITE + "$" + df2.format(getItemPrice(itemName)*number)),
				(ChatColor.GRAY + "Purchase " + number + "x ")));
		iStack.setItemMeta(iMeta);
		}
		if (!buy){
			iMeta.setLore(Arrays.asList((ChatColor.WHITE + "$" + df2.format(getItemPrice(itemName)*number)),
				(ChatColor.GRAY + "Sell " + number + "x ")));
		iStack.setItemMeta(iMeta);
		}
		return iStack;
	}

	public boolean hasAvaliableSlot(Player player) {
		InventoryView invview = player.getOpenInventory();
		Inventory inv = invview.getBottomInventory();
		Boolean check = true;
		int a = inv.firstEmpty();
		if (a == -1) {
			check = false;
			return check;
		}
		return check;
	}

	public Double getItemPrice(String item){
		ConcurrentHashMap<Integer, Double[]> inputMap = Main.map.get(item);
		Double[] arr = inputMap.get(inputMap.size()-1);
		return arr[0];
	}

	public StaticPane[] loadPagePanes(PaginatedPane pPane, int lines, Gui main){
		StaticPane output = new StaticPane(0, (lines), 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + "Back");
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (pPane.getPage()+1)));
		iStack.setItemMeta(iMeta);
		StaticPane forward = loadForwardPane(pPane, lines, main, output);
		GuiItem gItem = new GuiItem(iStack, event ->{
			event.setCancelled(true);
			int page = pPane.getPage();
			pPane.setPage(page-1);
			forward.setVisible(true);
			if (pPane.getPage() == 0){
				output.setVisible(false);
				forward.setVisible(true);
			}
			main.update();
		});
		output.addItem(gItem, 0, 0);
		StaticPane[] realOut = {output, forward};
		return realOut;
	}

	public StaticPane loadForwardPane(PaginatedPane pPane, int lines, Gui main, StaticPane backPane){
		StaticPane output = new StaticPane(8, (lines), 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + "Next");
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (pPane.getPage()+2)));
		iStack.setItemMeta(iMeta);
		GuiItem gItem = new GuiItem(iStack, event ->{
			event.setCancelled(true);
			int page = pPane.getPage();
			int pages = pPane.getPages();
			pPane.setPage(page+1);
			if (pPane.getPage() > (pages-2)){
				output.setVisible(false);
			}
			if (pPane.getPage() > page){
				backPane.setVisible(true);
			}
			main.update();
		});
		output.addItem(gItem, 0, 0);
		return output;
	}

	public StaticPane loadMainMenuBackPane(PaginatedPane pPane){
		StaticPane output = new StaticPane(0, 0, 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + "Main Menu");
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to go to " + ChatColor.WHITE + "Main Menu"));
		iStack.setItemMeta(iMeta);
		GuiItem gItem = new GuiItem(iStack, event ->{
			event.setCancelled(true);
			Player player = (Player) event.getWhoClicked();
			player.getOpenInventory().close();
			loadGUISECTIONS(player);
		});
		output.addItem(gItem, 0, 0);
		return output;
	}

	public static void sendPlayerShopMessageAndUpdateGDP(int amount, Player player, String matClickedString, boolean sell){
		if (!sell){
			ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player.getUniqueId());
			cMap.put(matClickedString, (cMap.get(matClickedString)+amount));
			Main.maxBuyMap.put(player.getUniqueId(), cMap);
			ConcurrentHashMap<Integer, Double[]> inputMap = Main.map.get(matClickedString);
			Double[] arr = inputMap.get(inputMap.size()-1);
			Double[] outputArr = {arr[0], (arr[1]+amount), arr[2]};
			Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+(arr[0]*amount)));
			player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), amount));
			inputMap.put((inputMap.size()-1), outputArr);
			Main.map.put(matClickedString, inputMap);
			Main.getEconomy().withdrawPlayer(player, (arr[0]*amount));
			player.sendMessage(ChatColor.GOLD + "Purchased " + amount + "x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(arr[0]*amount));
			
		}
		else if (sell){
			ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player.getUniqueId());
			cMap.put(matClickedString, (cMap.get(matClickedString)+amount));
			Main.maxSellMap.put(player.getUniqueId(), cMap);
			ConcurrentHashMap<Integer, Double[]> inputMap = Main.map.get(matClickedString);
			Double[] arr = inputMap.get(inputMap.size()-1);
			Double[] outputArr = {arr[0], arr[1], (arr[2]+amount)};
			Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+(arr[0]*amount)));
			inputMap.put((inputMap.size()-1), outputArr);
			Main.map.put(matClickedString, inputMap);
			Main.getEconomy().depositPlayer(player, (arr[0]*amount));
			player.sendMessage(ChatColor.GOLD + "Sold " + amount + "x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(arr[0]*amount));
		}
	}
}