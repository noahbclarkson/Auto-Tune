package unprotesting.com.github.Commands;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
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
	public static DecimalFormat df6 = new DecimalFormat("###,###,###,##0.00######");

	public static Integer SBPanePos = 1;

	public static Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

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
			if (args.length == 0) {
				if (p.hasPermission("at.shop") || p.isOp()) {
					loadGUISECTIONS(p, false);
				} else if (!(p.hasPermission("at.shop")) && !(p.isOp())) {
					TextHandler.noPermssion(p);
				}
				return true;
			}
			if (args.length == 1) {
				if (p.hasPermission("at.shop") || p.isOp()) {
				} else if (!(p.hasPermission("at.shop")) && !(p.isOp())) {
					TextHandler.noPermssion(p);
					return true;
				}
				String inputSection = null;
				try {
					inputSection = args[0];
				} catch (ClassCastException ex) {
					p.sendMessage("Unknown shop format: " + args[0]);
					return false;
				} catch (ArrayIndexOutOfBoundsException ex) {
					return false;
				}
				for (int i = 0; i < Main.sectionedItems.length; i++) {
					if (Main.sectionedItems[i].name.toLowerCase().equals(inputSection)) {
						loadGUIMAIN(p, Main.sectionedItems[i], true, false);
						return true;
					}
				}
			} else {
				return false;
			}
		}
		return false;
	}

	public static void loadGUISECTIONS(Player player, boolean autosell) {
		int lines = (int) Math.floor(((Main.sectionedItems.length) / 7) + 1);
		if (lines > 4) {
			lines = 4;
		}
		Gui front = new Gui((lines + 2), Config.getMenuTitle());
		OutlinePane pane = new OutlinePane(1, 1, 7, lines);
		for (int i = 0; i < Main.sectionedItems.length; i++) {
			ItemStack is = new ItemStack((Main.sectionedItems[i].image));
			ItemMeta im = is.getItemMeta();
			im.setDisplayName(ChatColor.GOLD + Main.sectionedItems[i].name);
			if (!autosell){
				im.setLore(Arrays.asList(
					ChatColor.WHITE + "Click to enter the " + (Main.sectionedItems[i].name.toLowerCase()) + " shop!"));
			}
			if (autosell){
				im.setLore(Arrays.asList(
					ChatColor.WHITE + "Click to enter the " + (Main.sectionedItems[i].name.toLowerCase()) + " auto-selling configuration!"));
			}
			is.setItemMeta(im);
			final Section inputSection = Main.sectionedItems[i];
			GuiItem gItem = new GuiItem(is, event -> {
				final Player playernew = player;
				if (event.getClick() == ClickType.LEFT) {
					event.setCancelled(true);
					player.getOpenInventory().close();
					loadGUIMAIN(player, inputSection, false, autosell);
				} else if (event.getClick() != ClickType.LEFT) {
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

	public static void loadGUIMAIN(Player player, Section sec, boolean twoArgs, boolean autosell) {
		int itemAmount = sec.items.size();
		int lines = (int) Math.floor(((itemAmount - 1) / 7) + 1);
		int itemNo = 0;
		if (lines > 4) {
			lines = 4;
		}
		Gui main = new Gui((lines + 2), Config.getMenuTitle());
		Integer paneSize = (lines + 1) * 7;
		Integer paneAmount = (int) Math.ceil((itemAmount+7) / paneSize) + 1;
		PaginatedPane pPane = new PaginatedPane(0, 0, 9, (lines + 2));
		OutlinePane[] shopPanes = new OutlinePane[paneAmount];
		for (int i = 0; i < shopPanes.length; i++) {
			shopPanes[i] = new OutlinePane(1, 1, 7, lines);
			for (int k = 0; k < (paneSize-7); k++) {
				if (itemNo < sec.items.size()) {
					String itemName = sec.items.get(itemNo);
					itemNo++;
					ItemStack iStack = loadShopItem(itemName, sec, player, autosell);
					if (autosell) {
						GuiItem item = new GuiItem(iStack, event -> {
							if (event.getClick() == ClickType.LEFT) {
								event.setCancelled(true);
								player.getOpenInventory().close();
								AutoTuneAutoSellCommand.changePlayerAutoSellSettings(player, itemName);
								loadGUIMAIN(player, sec, twoArgs, autosell);
							} else {
								event.setCancelled(true);
							}
						});
						shopPanes[i].addItem(item);
					} else {
						GuiItem item = new GuiItem(iStack, event -> {
							if (event.getClick() == ClickType.LEFT) {
								event.setCancelled(true);
								player.getOpenInventory().close();
								loadGUITRADING(player, itemName, sec, autosell);
							} else {
								event.setCancelled(true);
							}
						});
						shopPanes[i].addItem(item);
					}
				} else {
					break;
				}
			}
		}
		for (int i = 0; i < shopPanes.length; i++) {
			pPane.addPane(i, shopPanes[i]);
		}
		main.addPane(pPane);
		StaticPane[] panes = loadPagePanes(pPane, (lines + 1), main);
		panes[0].setVisible(false);
		panes[1].setVisible(true);
		if (pPane.getPages() == 1) {
			panes[1].setVisible(false);
		}
		main.addPane(panes[0]);
		main.addPane(panes[1]);
		if (twoArgs == false || (twoArgs == true && sec.showBackButton == true)) {
			main.addPane(loadMainMenuBackPane(pPane, autosell));
		}
		CommandSender cSender = player;
		main.update();
		main.show((HumanEntity) cSender);
	}

	public static void loadGUITRADING(Player player, String itemName, Section sec, boolean autosell) {
		Gui main = new Gui(4, Config.getMenuTitle());
		OutlinePane front = new OutlinePane(1, 1, 7, 2);
		if (!autosell) {
			front = loadTradingItems(itemName, sec, front);
		}
		main.addPane(front);
		main.addPane(loadReturnButton(sec, autosell));
		CommandSender cSender = player;
		main.update();
		main.show((HumanEntity) cSender);
	}

	public static OutlinePane loadTradingItems(String itemName, Section sec, OutlinePane front) {
		for (int i = 0; i < 14; i++) {
			final int finalI = i;
			ItemStack iStack;
			GuiItem gItem;
			if (i < 7) {
				iStack = loadTradingItem(itemName, amounts[i], true, sec);
				int maxStackSize = iStack.getMaxStackSize();
				int maxBuys = sec.itemMaxBuySell.get(itemName)[0];
				if (maxStackSize < amounts[i] || maxBuys < amounts[i]){
					Material mat = Material.RED_STAINED_GLASS_PANE;
					ItemStack itemPane = new ItemStack(mat);
					ItemMeta itemPaneMeta = itemPane.getItemMeta();
					itemPaneMeta.setDisplayName(ChatColor.MAGIC + "_");
					itemPane.setItemMeta(itemPaneMeta);
					GuiItem gItemPane = new GuiItem(itemPane, event ->{
						event.setCancelled(true);
					});
					front.addItem(gItemPane);
					continue;
				}
				gItem = new GuiItem(iStack, event -> {
					Player player = (Player) event.getWhoClicked();
					if (event.getClick() == ClickType.LEFT) {
						event.setCancelled(true);
						ConcurrentHashMap<String, Integer> maxBuyMapRec = Main.maxBuyMap.get(player.getUniqueId());
						int currentMax = maxBuyMapRec.get(itemName);
						Integer[] max = sec.itemMaxBuySell.get(itemName);
						Double price = getItemPrice(itemName, false);
						if (max[0] < (currentMax + amounts[finalI]) && !Config.isDisableMaxBuysSells()) {
							player.sendMessage(ChatColor.BOLD + "Cant Purchase " + Integer.toString(amounts[finalI])
									+ "x of " + itemName);
							int difference = (currentMax + amounts[finalI]) - max[0];
							if (difference != 0 && !(currentMax >= max[0])) {
								ItemStack is = new ItemStack(Material.matchMaterial(itemName),
										(amounts[finalI] - difference));
								is = checkForEnchantAndApply(is, sec);
								if (((amounts[finalI] - difference) * price) < Main.getEconomy().getBalance(player)) {
									HashMap<Integer, ItemStack> unableItems = player.getInventory().addItem(is);
									if (unableItems.size() > 0) {
										player.sendMessage(ChatColor.BOLD + "Cant Purchase "
												+ Integer.toString(amounts[finalI]) + "x of " + itemName);
									} else {
										sendPlayerShopMessageAndUpdateGDP((amounts[finalI] - difference), player,
												itemName, false);
										Main.maxBuyMap.put(player.getUniqueId(), maxBuyMapRec);
									}
									player.sendMessage(ChatColor.RED + "Max Buys Reached! - " + max[0] + "/" + max[0]);
								} else {
									player.sendMessage(ChatColor.BOLD + "Cant Purchase "
											+ Integer.toString(amounts[finalI]) + "x of " + itemName);
								}
							}
						} else {
							try {
								ItemStack is = new ItemStack(Material.matchMaterial(itemName), amounts[finalI]);
								is = checkForEnchantAndApply(is, sec);
								if ((price * amounts[finalI]) < Main.getEconomy().getBalance(player)) {
									HashMap<Integer, ItemStack> unableItems = player.getInventory().addItem(is);
									if (unableItems.size() > 0) {
										player.sendMessage(ChatColor.BOLD + "Cant Purchase "
												+ Integer.toString(amounts[finalI]) + "x of " + itemName);
									} else {
										sendPlayerShopMessageAndUpdateGDP(amounts[finalI], player, itemName, false);
									}
								} else {
									player.sendMessage(ChatColor.BOLD + "Cant Purchase "
											+ Integer.toString(amounts[finalI]) + "x of " + itemName);
								}
							} catch (IllegalArgumentException ex) {
							}
						}
					} else {
						event.setCancelled(true);
					}
				});
			} else {
				iStack = loadTradingItem(itemName, amounts[i - 7], false, sec);
				int maxStackSize = iStack.getMaxStackSize();
				int maxSells = sec.itemMaxBuySell.get(itemName)[1];
				if (maxStackSize < amounts[i - 7] || maxSells < amounts[i - 7]){
					Material mat = Material.RED_STAINED_GLASS_PANE;
					ItemStack itemPane = new ItemStack(mat);
					ItemMeta itemPaneMeta = itemPane.getItemMeta();
					itemPaneMeta.setDisplayName(ChatColor.MAGIC + "_");
					itemPane.setItemMeta(itemPaneMeta);
					GuiItem gItemPane = new GuiItem(itemPane, event ->{
						event.setCancelled(true);
					});
					front.addItem(gItemPane);
					continue;
				}
				gItem = new GuiItem(iStack, event -> {
					Player player = (Player) event.getWhoClicked();
					if (event.getClick() == ClickType.LEFT) {
						event.setCancelled(true);
						ConcurrentHashMap<String, Integer> maxSellMapRec = Main.maxSellMap.get(player.getUniqueId());
						int currentMax = maxSellMapRec.get(itemName);
						Integer[] max = sec.itemMaxBuySell.get(itemName);
						ItemStack test = new ItemStack(Material.matchMaterial(itemName));
						test = checkForEnchantAndApply(test, sec);
						if (!player.getInventory().containsAtLeast(test, amounts[finalI - 7])) {
							player.sendMessage(ChatColor.BOLD + "Cant Sell " + Integer.toString(amounts[finalI - 7])
									+ "x of " + itemName);
						}
						else if (max[1] < (currentMax + amounts[finalI - 7]) && !Config.isDisableMaxBuysSells()) {
							player.sendMessage(ChatColor.BOLD + "Cant Sell " + Integer.toString(amounts[finalI - 7])
									+ "x of " + itemName);
							int difference = (currentMax + amounts[finalI - 7]) - max[1];
							if (difference != 0 && !(currentMax >= max[1])) {
								removeItems(player, (finalI - 7), itemName, sec, difference);
								Main.maxSellMap.put(player.getUniqueId(), maxSellMapRec);
							}
							player.sendMessage(ChatColor.RED + "Max Sells Reached! - " + max[1] + "/" + max[1]);
						} 
						else {
							removeItems(player, (finalI - 7), itemName, sec, 0);
						}
					} else {
						event.setCancelled(true);
					}
				});
			}
			front.addItem(gItem);
		}
		return front;
	}

	public static void removeItems(Player player, int finalI, String itemName, Section sec, int difference) {
		try {
			ItemStack iStack = new ItemStack(Material.matchMaterial(itemName), (amounts[finalI]) - difference);
			iStack = checkForEnchantAndApply(iStack, sec);
			HashMap<Integer, ItemStack> takenItems = player.getInventory().removeItem(iStack);
			if (takenItems.size() > 0) {
				player.sendMessage(ChatColor.BOLD + "Cant sell " + Integer.toString(amounts[finalI] - difference)
						+ "x of " + itemName);
			} else {
				sendPlayerShopMessageAndUpdateGDP((amounts[finalI] - difference), player, itemName, true);
			}
		} catch (IllegalArgumentException ex) {
		}
	}

	public static ItemStack loadShopItem(String itemName, Section sec, Player player, boolean autosell) {
		ItemStack iStack = new ItemStack(Material.matchMaterial(itemName));
		ItemMeta iMeta = iStack.getItemMeta();
		Integer[] maxBuySellForItem = sec.itemMaxBuySell.get(itemName);
		if (!autosell) {
			iMeta.setDisplayName(ChatColor.GOLD + itemName);
			if (!Config.isDisableMaxBuysSells()){
				iMeta.setLore(Arrays.asList((ChatColor.GRAY + "Click to purchase/sell"), (loadPriceDisplay(itemName)),
					(ChatColor.WHITE + "Max Buys: " + maxBuySellForItem[0] + " per " + Config.getTimePeriod() + "min"),
					(ChatColor.WHITE + "Max Sells: " + maxBuySellForItem[1] + " per " + Config.getTimePeriod()
							+ "min")));
			}
			else{
				iMeta.setLore(Arrays.asList((ChatColor.GRAY + "Click to purchase/sell"), (loadPriceDisplay(itemName))));
			}
		}
		if (autosell) {
			Boolean atonoff = Main.playerDataConfig.getBoolean(player.getUniqueId() + ".AutoSell" + "." + itemName);
			if (!atonoff) {
				iMeta.setDisplayName(ChatColor.AQUA + itemName + ChatColor.RED + " - Auto Sell Disabled");
				iMeta.setLore(
						Arrays.asList((ChatColor.GRAY + "Click to turn on auto-sell"), (loadPriceDisplay(itemName))));
			} else {
				iMeta.setDisplayName(ChatColor.AQUA + itemName + ChatColor.GREEN + " - Auto Sell Enabled");
				iMeta.setLore(
						Arrays.asList((ChatColor.GRAY + "Click to turn off auto-sell"), (loadPriceDisplay(itemName))));
			}
		}
		iStack.setItemMeta(iMeta);
		iStack = checkForEnchantAndApply(iStack, sec);
		return iStack;
	}

	public static String loadPriceDisplay(String item) {
		double currentPrice = getItemPrice(item, false);
		float timePeriod = (float) Config.getTimePeriod();
		float timePeriodsInADay = (float) (1 / (timePeriod / 1440));
		List<Double> newMap;
		try{
		newMap = Main.getItemPrices().get(item).prices;
		}
		catch(NullPointerException ex){
			return (ChatColor.WHITE + Config.getCurrencySymbol() + df2.format(currentPrice) + ChatColor.DARK_GRAY
					+ " - " + ChatColor.GRAY + "%0.0");
		}
		if (newMap.size() <= timePeriodsInADay) {
			return (ChatColor.WHITE + Config.getCurrencySymbol() + df2.format(currentPrice) + ChatColor.DARK_GRAY
					+ " - " + ChatColor.GRAY + "%0.0");
		}
		Integer oneDayOldTP = (int) Math.floor(newMap.size() - timePeriodsInADay);
		double oneDayOldPrice = newMap.get(oneDayOldTP);
		if (oneDayOldPrice > currentPrice) {
			double percent = 100 * ((currentPrice / oneDayOldPrice) - 1);
			return (ChatColor.WHITE + Config.getCurrencySymbol() + df2.format(currentPrice) + ChatColor.DARK_GRAY
					+ " - " + ChatColor.RED + "%-" + df2.format(Math.abs(percent)));
		} else if (oneDayOldPrice < currentPrice) {
			double percent = 100 * (1 - (oneDayOldPrice / currentPrice));
			return (ChatColor.WHITE + Config.getCurrencySymbol() + df2.format(currentPrice) + ChatColor.DARK_GRAY
					+ " - " + ChatColor.GREEN + "%+" + df2.format(Math.abs(percent)));
		} else {
			return (ChatColor.WHITE + Config.getCurrencySymbol() + df2.format(currentPrice) + ChatColor.DARK_GRAY
					+ " - " + ChatColor.GRAY + "%0.0");
		}
	}

	public static ItemStack loadTradingItem(String itemName, int number, boolean buy, Section sec) {
		ItemStack iStack = new ItemStack(Material.matchMaterial(itemName), number);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.GOLD + itemName);
		if (buy) {
			iMeta.setLore(Arrays.asList((ChatColor.WHITE + "$" + df2.format(getItemPrice(itemName, false) * number)),
					(ChatColor.GRAY + "Purchase " + number + "x ")));
			iStack.setItemMeta(iMeta);
		}
		if (!buy) {
			iMeta.setLore(Arrays.asList((ChatColor.WHITE + "$" + df2.format(getItemPrice(itemName, true) * number)),
					(ChatColor.GRAY + "Sell " + number + "x ")));
			iStack.setItemMeta(iMeta);
		}
		iStack = checkForEnchantAndApply(iStack, sec);
		return iStack;
	}

	@Deprecated
	public static ItemStack checkForEnchantAndApply(ItemStack is, Section sec) {
		for (String enchantedItem : sec.enchantedItems) {
			if (is.getType().toString().contains(enchantedItem)) {
				ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
						.getConfigurationSection((is.getType().toString()));
				String enchantmentName = config.getString("enchantment", "none");
				ItemMeta meta = is.getItemMeta();
				Enchantment ench = Enchantment.getByName(enchantmentName);
				int level = config.getInt("enchantment-level", 0);
				if (level == 0) {
					return is;
				} else if (enchantmentName.contains("none")) {
					Main.debugLog("Enchantment " + enchantmentName + " is null");
					return is;
				} else if (ench == null) {
					Main.debugLog("Enchantment " + enchantmentName + " is null");
					return is;
				}
				try {
					meta.addEnchant(ench, level, true);
					is.setItemMeta(meta);
				} catch (IllegalArgumentException ex) {
					Main.debugLog("IllegalArgumentException at shop " + is.getType().toString() + " enchantment "
							+ enchantmentName + " is illegal");
					ex.printStackTrace();
					return is;
				}
				return is;
			}
		}
		return is;
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

	public double getSellPriceDifference(String item) {
		double output = Config.getSellPriceDifference();
		try {
			ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
					.getConfigurationSection((item));
			output = config.getDouble("sell-difference", output);
			return output;
		} catch (NullPointerException ex) {
			output = Config.getSellPriceDifference();
		}
		return output;
	}

	public static Double getItemPrice(String item, boolean sell) {
		Double output = 0.0;
		if (!sell) {
			try{
				output = Main.getItemPrices().get(item).price;
				return output;
			}
			catch(NullPointerException e){
				ConcurrentHashMap<Integer, Double[]> map = Main.map.get(item);
				try{
					output = map.get(map.size()-1)[0];
					return output;
				}
				catch(NullPointerException e2){
					return null;
				}
				
			}
		} else {
			try{
				output = Main.getItemPrices().get(item).sellPrice;
				return output;
			}
			catch(NullPointerException e){
				ConcurrentHashMap<Integer, Double[]> map = Main.map.get(item);
				output = map.get(map.size()-1)[0];
				try{
					output = output - output*0.01*getSellDifference(item);
					return output;
				}
				catch(NullPointerException e2){
					return null;
				}
			}
		}
	}

	public static StaticPane loadReturnButton(Section sec, boolean autosell) {
		StaticPane output = new StaticPane(0, 0, 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + sec.name);
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to go to " + ChatColor.WHITE + sec.name));
		iStack.setItemMeta(iMeta);
		GuiItem gItem = new GuiItem(iStack, event -> {
			Player player = (Player) event.getWhoClicked();
			player.getOpenInventory().close();
			loadGUIMAIN(player, sec, false, autosell);
		});
		output.addItem(gItem, 0, 0);
		return output;
	}

	public static StaticPane[] loadPagePanes(PaginatedPane pPane, int lines, Gui main) {
		StaticPane output = new StaticPane(0, (lines), 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + "Back");
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (pPane.getPage() + 1)));
		iStack.setItemMeta(iMeta);
		StaticPane forward = loadForwardPane(pPane, lines, main, output);
		GuiItem gItem = new GuiItem(iStack, event -> {
			event.setCancelled(true);
			int page = pPane.getPage();
			pPane.setPage(page - 1);
			forward.setVisible(true);
			if (pPane.getPage() == 0) {
				output.setVisible(false);
				forward.setVisible(true);
			}
			main.update();
		});
		output.addItem(gItem, 0, 0);
		StaticPane[] realOut = { output, forward };
		return realOut;
	}

	public static StaticPane loadForwardPane(PaginatedPane pPane, int lines, Gui main, StaticPane backPane) {
		StaticPane output = new StaticPane(8, (lines), 1, 1);
		ItemStack iStack = new ItemStack(Material.ARROW);
		ItemMeta iMeta = iStack.getItemMeta();
		iMeta.setDisplayName(ChatColor.DARK_PURPLE + "Next");
		iMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + ChatColor.WHITE + (pPane.getPage() + 2)));
		iStack.setItemMeta(iMeta);
		GuiItem gItem = new GuiItem(iStack, event -> {
			event.setCancelled(true);
			int page = pPane.getPage();
			int pages = pPane.getPages();
			pPane.setPage(page + 1);
			if (pPane.getPage() > (pages - 2)) {
				output.setVisible(false);
			}
			if (pPane.getPage() > page) {
				backPane.setVisible(true);
			}
			main.update();
		});
		output.addItem(gItem, 0, 0);
		return output;
	}

	public static StaticPane loadMainMenuBackPane(PaginatedPane pPane, boolean autosell) {
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
			loadGUISECTIONS(player, autosell);
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
			Double price = arr[0] - (arr[0]*0.01*getSellDifference(matClickedString));
			Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+(price*amount)));
			inputMap.put((inputMap.size()-1), outputArr);
			Main.map.put(matClickedString, inputMap);
			Main.getEconomy().depositPlayer(player, (price*amount));
			player.sendMessage(ChatColor.GOLD + "Sold " + amount + "x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price*amount));
		}
	}

	public static double getSellDifference(String item){
		ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops").getConfigurationSection((item));
            Double sellpricedif = Config.getSellPriceDifference();
            try{
                sellpricedif = config.getDouble("sell-difference", sellpricedif);
            }
            catch(NullPointerException ex){
                sellpricedif = Config.getSellPriceDifference();
			}
		return sellpricedif;
	}
}