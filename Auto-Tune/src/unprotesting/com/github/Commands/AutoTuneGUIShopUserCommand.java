package unprotesting.com.github.Commands;

import java.text.DecimalFormat;
import java.util.Arrays;
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
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneGUIShopUserCommand implements CommandExecutor {

	public static DecimalFormat df1 = new DecimalFormat("###########0.00");
	public static DecimalFormat df2 = new DecimalFormat("###,###,###,##0.00");
	static DecimalFormat df3 = new DecimalFormat("###,###,###,##0.00000");
	public static DecimalFormat df4 = new DecimalFormat("###########0.0000");
	public static DecimalFormat df5 = new DecimalFormat("###########0");

	public Economy economy = Main.getEconomy();

	public static Integer SBPanePos = 1;

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
			if (p.hasPermission("at.shop") || p.isOp()){loadGUIMAIN(p, sender);}
                else if (!(p.hasPermission("at.shop")) && !(p.isOp())){TextHandler.noPermssion(p);}

			return true;

		}
		return true;

	}

	public void loadGUIMAIN(Player player, CommandSender senderpub) {
		Integer menuRows = Config.getMenuRows();
		OutlinePane SBPane = new OutlinePane(1, SBPanePos, 7, 2);
		ItemStack is;
		GuiItem a;
		final Player playerpub = player;
		Integer i = 0;
		Material b;
		Double price1;
		Gui gui1;
		StaticPane pageTwo = new StaticPane(1, 1, 7, menuRows - 2);
		StaticPane pageThree = new StaticPane(1, 1, 7, menuRows - 2);
		StaticPane pageFour = new StaticPane(1, 1, 7, menuRows - 2);
		StaticPane pageFive = new StaticPane(1, 1, 7, menuRows - 2);
		StaticPane back = new StaticPane(0, menuRows - 1, 1, 1);
		StaticPane forward = new StaticPane(8, menuRows - 1, 1, 1);

		player = (Player) senderpub;
		gui1 = new Gui(menuRows, Config.getMenuTitle());
		Integer size = Main.getMaterialListSize();
		PaginatedPane pane = new PaginatedPane(0, 0, 9, menuRows);
		Integer paneSize = (menuRows - 2) * 7;
		Integer pageAmount = 2;
		if (size > paneSize) {
			pageAmount = 3;
			pane.addPane(1, pageTwo);
		}
		if (size > paneSize * 2) {
			pageAmount = 4;
			pane.addPane(2, pageThree);
		}
		if (size > paneSize * 3) {
			pageAmount = 5;
			pane.addPane(3, pageFour);
		}
		if (size > paneSize * 4) {
			pageAmount = 6;
			pane.addPane(4, pageFive);
		}

		final Integer finalPageAmount = pageAmount;

		// page one
		StaticPane pageOne = new StaticPane(1, 1, 7, menuRows - 2);
		for (i = 0; i<size;) {
			if (Main.memMap.isEmpty() == false) {
				b = (Material.matchMaterial(Main.memMap.get(i)));
				is = new ItemStack(b);
				a = new GuiItem(is, event -> {
					if (event.getClick() == ClickType.LEFT) {
						Gui gui = gui1;
						final Player playernew = playerpub;
						final Double price;
						String matClickedString = "";
						ItemStack tempis = event.getCurrentItem();
						Material tempmat = tempis.getType();
						matClickedString = tempmat.toString();
						ConcurrentHashMap<Integer,
							Double[] > tempmap = Main.map.get(matClickedString);
						Integer tempMapSize = tempmap.size();
						Double[] tempDoublearray = tempmap.get(tempMapSize - 1);
						price = tempDoublearray[0];
						createTradingPanel(gui, matClickedString, playernew, SBPane, price, forward, back);
						if (finalPageAmount == 2) {
							pane.setPage(1);
						}
						if (finalPageAmount == 3) {
							pane.setPage(2);
						}
						if (finalPageAmount == 4) {
							pane.setPage(3);
						}
						if (finalPageAmount == 5) {
							pane.setPage(4);
						}
						if (finalPageAmount == 6) {
							pane.setPage(5);
						}
						gui.update();
						playernew.setItemOnCursor(null);
						event.setCancelled(true);
					}
					if (event.getClick() != ClickType.LEFT) {
						final Player playernew = playerpub;
						event.setCancelled(true);
						playernew.setItemOnCursor(null);
					}
				});
				ItemMeta im = is.getItemMeta();
				im.setDisplayName(ChatColor.AQUA + Main.memMap.get(i));
				ConcurrentHashMap<Integer, Double[] > tempmap = Main.map.get(Main.memMap.get(i));
				Integer tempMapSize = tempmap.size();
				Double[] tempDoublearray = tempmap.get(tempMapSize - 1);
				price1 = tempDoublearray[0];
				String priceString = df2.format(price1);
				String fullprice = "Price: " + Config.getCurrencySymbol() + priceString;
				im.setLore(Arrays.asList((ChatColor.GOLD + fullprice), (ChatColor.WHITE + "Maximum Buys: " + (Integer)Main.getShopConfig().get("shops." + Main.memMap.get(i) + "." + "max-buy") + " per " + Config.getTimePeriod() + "min"), (ChatColor.WHITE + "Maximum Sells: " + (Integer)Main.getShopConfig().get("shops." + Main.memMap.get(i) + "." + "max-sell") + " per " + Config.getTimePeriod() + "min")));
				is.setItemMeta(im);
				if (Config.getMenuRows() == 4) {
					if (i<7) {
						pageOne.addItem(a, i, 0);
					}
					else if (i >= 7 && i<14) {
						pageOne.addItem(a, i - 7, 1);
					}
					else if (i >= 14 && i<21) {
						pageTwo.addItem(a, i - 14, 0);
					}
					else if (i >= 21 && i<28) {
						pageTwo.addItem(a, i - 21, 1);
					}
					else if (i >= 28 && i<35) {
						pageThree.addItem(a, i - 28, 0);
					}
					else if (i >= 35 && i<42) {
						pageThree.addItem(a, i - 35, 1);
					}
					else if (i >= 42 && i<49) {
						pageFour.addItem(a, i - 42, 0);
					}
					else if (i >= 49 && i<56) {
						pageFour.addItem(a, i - 49, 1);
					}
					else if (i >= 56 && i<63) {
						pageFive.addItem(a, i - 56, 0);
					}
					else if (i >= 63 && i<70) {
						pageFive.addItem(a, i - 63, 1);
					}
				}
				if (Config.getMenuRows() == 5) {
					if (i<7) {
						pageOne.addItem(a, i, 0);
					}
					else if (i >= 7 && i<14) {
						pageOne.addItem(a, i - 7, 1);
					}
					else if (i >= 14 && i<21) {
						pageOne.addItem(a, i - 14, 2);
					}
					else if (i >= 21 && i<28) {
						pageTwo.addItem(a, i - 21, 0);
					}
					else if (i >= 28 && i<35) {
						pageTwo.addItem(a, i - 28, 1);
					}
					else if (i >= 35 && i<42) {
						pageTwo.addItem(a, i - 35, 2);
					}
					else if (i >= 42 && i<49) {
						pageThree.addItem(a, i - 42, 0);
					}
					else if (i >= 49 && i<56) {
						pageThree.addItem(a, i - 49, 1);
					}
					else if (i >= 56 && i<63) {
						pageThree.addItem(a, i - 56, 2);
					}
					else if (i >= 63 && i<70) {
						pageFour.addItem(a, i - 63, 0);
					}
					else if (i >= 70 && i<77) {
						pageFour.addItem(a, i - 70, 1);
					}
					else if (i >= 77 && i<84) {
						pageFour.addItem(a, i - 77, 2);
					}
					else if (i >= 84 && i<91) {
						pageFive.addItem(a, i - 84, 0);
					}
					else if (i >= 91 && i<98) {
						pageFive.addItem(a, i - 91, 1);
					}
					else if (i >= 98 && i<105) {
						pageFive.addItem(a, i - 98, 2);
					}
				}
				if (Config.getMenuRows() == 6) {
					if (i<7) {
						pageOne.addItem(a, i, 0);
					}
					else if (i >= 7 && i<14) {
						pageOne.addItem(a, i - 7, 1);
					}
					else if (i >= 14 && i<21) {
						pageOne.addItem(a, i - 14, 2);
					}
					else if (i >= 21 && i<28) {
						pageOne.addItem(a, i - 21, 3);
					}
					else if (i >= 28 && i<35) {
						pageTwo.addItem(a, i - 28, 0);
					}
					else if (i >= 35 && i<42) {
						pageTwo.addItem(a, i - 35, 1);
					}
					else if (i >= 42 && i<49) {
						pageTwo.addItem(a, i - 42, 2);
					}
					else if (i >= 49 && i<56) {
						pageTwo.addItem(a, i - 49, 3);
					}
					else if (i >= 56 && i<63) {
						pageThree.addItem(a, i - 56, 0);
					}
					else if (i >= 63 && i<70) {
						pageThree.addItem(a, i - 63, 1);
					}
					else if (i >= 70 && i<77) {
						pageThree.addItem(a, i - 70, 2);
					}
					else if (i >= 77 && i<84) {
						pageThree.addItem(a, i - 77, 3);
					}
					else if (i >= 84 && i<91) {
						pageFour.addItem(a, i - 84, 0);
					}
					else if (i >= 91 && i<98) {
						pageFour.addItem(a, i - 91, 1);
					}
					else if (i >= 98 && i<105) {
						pageFour.addItem(a, i - 98, 2);
					}
					else if (i >= 105 && i<112) {
						pageFour.addItem(a, i - 105, 3);
					}
					else if (i >= 112 && i<119) {
						pageFive.addItem(a, i - 112, 0);
					}
					else if (i >= 119 && i<126) {
						pageFive.addItem(a, i - 119, 1);
					}
					else if (i >= 126 && i<135) {
						pageFive.addItem(a, i - 126, 2);
					}
					else if (i >= 135 && i<142) {
						pageFive.addItem(a, i - 135, 3);
					}

				}

				i++;
			}

		}
		pane.addPane(0, pageOne);
		if (finalPageAmount == 3) {
			pane.addPane(1, pageTwo);
			pane.addPane(2, SBPane);
		}
		else if (finalPageAmount == 4) {
			pane.addPane(1, pageTwo);
			pane.addPane(2, pageThree);
			pane.addPane(3, SBPane);
		}
		else if (finalPageAmount == 5) {
			pane.addPane(1, pageTwo);
			pane.addPane(2, pageThree);
			pane.addPane(3, pageFour);
			pane.addPane(4, SBPane);
		}
		else if (finalPageAmount == 6) {
			pane.addPane(1, pageTwo);
			pane.addPane(2, pageThree);
			pane.addPane(3, pageFour);
			pane.addPane(4, pageFive);
			pane.addPane(5, SBPane);
		}
		else {
			pane.addPane(1, SBPane);
		}

		gui1.addPane(pane);

		// page selection
		ItemStack isback = new ItemStack(Material.ARROW);
		ItemMeta imback = isback.getItemMeta();
		imback.setDisplayName(ChatColor.WHITE + "Back");
		imback.setLore(Arrays.asList(ChatColor.BOLD + "Click to return to Shop Menu"));
		isback.setItemMeta(imback);
		ItemStack isforward = new ItemStack(Material.ARROW);
		ItemMeta imforward = isforward.getItemMeta();
		imforward.setDisplayName(ChatColor.WHITE + "Next Page");
		imforward.setLore(Arrays.asList(ChatColor.BOLD + "Click to go to the next page"));
		isforward.setItemMeta(imforward);

		if (pane.getPage() == 0 && finalPageAmount == 6) {
			back.setVisible(false);
			forward.setVisible(true);
		}

		if (pane.getPage() == 0 && finalPageAmount == 5) {
			back.setVisible(false);
			forward.setVisible(true);
		}

		if (pane.getPage() == 0 && finalPageAmount == 4) {
			back.setVisible(false);
			forward.setVisible(true);
		}

		if (pane.getPage() == 0 && finalPageAmount == 3) {
			back.setVisible(false);
			forward.setVisible(true);
		}

		if (pane.getPage() == 0 && finalPageAmount == 2) {
			forward.setVisible(false);
		}

		back.addItem(new GuiItem(new ItemStack(isback), event -> {
			if (pane.getPage() != 0) {
				pane.setPage(pane.getPage() - 1);
			}
			if (pane.getPage() != (finalPageAmount-2) && pane.getPage() != (finalPageAmount-1) && pane.getPage() != (finalPageAmount)){
				forward.setVisible(true);
			}
			else if (pane.getPage() == 0 && finalPageAmount == 2) {
				forward.setVisible(false);
			}
			if (pane.getPage() == 0){
				back.setVisible(false);
			}

			gui1.update();
		}), 0, 0);

		back.setVisible(false);

		forward.addItem(new GuiItem(new ItemStack(isforward), event -> {
			pane.setPage(pane.getPage() + 1);
			forward.setVisible(false);

			if ((pane.getPage() == (finalPageAmount)) || (pane.getPage() == (finalPageAmount)-1) || (pane.getPage() == (finalPageAmount)-2)) {
				forward.setVisible(false);
			}

			else{
				forward.setVisible(true);
			}

			back.setVisible(true);
			gui1.update();
		}), 0, 0);

		gui1.addPane(back);
		gui1.addPane(forward);

		gui1.addPane(back);
		gui1.addPane(forward);

		gui1.show((HumanEntity) senderpub);

	}

	public Double sellpricedif;
	public Double sellpricedif2;

	public void createTradingPanel(Gui gui, String matClickedString, Player player, OutlinePane SBPane, Double price, StaticPane forward, StaticPane back) {
		sellpricedif2 = null;
		sellpricedif = null;
		sellpricedif = Config.getSellPriceDifference();
		Main.getINSTANCE();
		ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
				.getConfigurationSection((matClickedString));
		sellpricedif2 = config.getDouble("sell-difference", sellpricedif);
		if (sellpricedif2 != null) {
			sellpricedif = sellpricedif2;
		}
		SBPane.clear();
		ItemStack is1 = new ItemStack(Material.matchMaterial(matClickedString), 1);
		ItemMeta is1im = is1.getItemMeta();
		is1im.setDisplayName(ChatColor.GOLD + "Buy 1x " + ChatColor.AQUA + matClickedString);
		is1im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price)));
		is1.setItemMeta(is1im);
		ItemStack is2 = new ItemStack(Material.matchMaterial(matClickedString), 2);
		ItemMeta is2im = is2.getItemMeta();
		is2im.setDisplayName(ChatColor.GOLD + "Buy 2x " + ChatColor.AQUA + matClickedString);
		is2im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 2)));
		is2.setItemMeta(is2im);
		ItemStack is4 = new ItemStack(Material.matchMaterial(matClickedString), 4);
		ItemMeta is4im = is4.getItemMeta();
		is4im.setDisplayName(ChatColor.GOLD + "Buy 4x " + ChatColor.AQUA + matClickedString);
		is4im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 4)));
		is4.setItemMeta(is4im);
		ItemStack is8 = new ItemStack(Material.matchMaterial(matClickedString), 8);
		ItemMeta is8im = is8.getItemMeta();
		is8im.setDisplayName(ChatColor.GOLD + "Buy 8x " + ChatColor.AQUA + matClickedString);
		is8im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 8)));
		is8.setItemMeta(is8im);
		ItemStack is16 = new ItemStack(Material.matchMaterial(matClickedString), 16);
		ItemMeta is16im = is16.getItemMeta();
		is16im.setDisplayName(ChatColor.GOLD + "Buy 16x " + ChatColor.AQUA + matClickedString);
		is16im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 16)));
		is16.setItemMeta(is16im);
		ItemStack is32 = new ItemStack(Material.matchMaterial(matClickedString), 32);
		ItemMeta is32im = is2.getItemMeta();
		is32im.setDisplayName(ChatColor.GOLD + "Buy 32x " + ChatColor.AQUA + matClickedString);
		is32im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 32)));
		is32.setItemMeta(is32im);
		ItemStack is64 = new ItemStack(Material.matchMaterial(matClickedString), 64);
		ItemMeta is64im = is2.getItemMeta();
		is64im.setDisplayName(ChatColor.GOLD + "Buy 64x " + ChatColor.AQUA + matClickedString);
		is64im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 64)));
		is64.setItemMeta(is64im);
		GuiItem isa = new GuiItem(is1, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 1,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}	
				if ((cMap.get(matClickedString)+1) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && (Main.econ.getBalance(player)) > (price) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 1));
					sendPlayerShopMessageAndUpdateGDP(1, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 1x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa2 = new GuiItem(is2, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 2,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+1) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*2) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 2);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 2));
					sendPlayerShopMessageAndUpdateGDP(2, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 2x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 2));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa4 = new GuiItem(is4, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 4,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+4) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*4) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 4);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 4));
					sendPlayerShopMessageAndUpdateGDP(4, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 4x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 4));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa8 = new GuiItem(is8, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 8,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+8) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*8) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 8);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 8));
					sendPlayerShopMessageAndUpdateGDP(8, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 8x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 8));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa16 = new GuiItem(is16, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 16,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+16) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*16) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 16);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 16));
					sendPlayerShopMessageAndUpdateGDP(16, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 16x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 16));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa32 = new GuiItem(is32, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 32,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+32) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*32) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 32);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 32));
					sendPlayerShopMessageAndUpdateGDP(32, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 32x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 32));
					
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		GuiItem isa64 = new GuiItem(is64, event -> {
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player) {
				ConcurrentHashMap<Integer,
					Double[] > tempMap2 = Main.map.get(matClickedString);
				Integer tempMap2Size = tempMap2.size();
				Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
				Double buyAmount = tempDarray2[1];
				Double sellAmount = tempDarray2[2];
				Double[] tempDArray = {
					price,
					buyAmount + 64,
					sellAmount
				};
				tempMap2.put(tempMap2Size - 1, tempDArray);
				ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-buy");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+64) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum buys reached");
				}
				if (hasAvaliableSlot(player) == true && Main.econ.getBalance(player) > (price*64) && notMax == true) {
					Main.map.put(matClickedString, tempMap2);
					Main.econ.withdrawPlayer(player, price * 64);
					player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 64));
					sendPlayerShopMessageAndUpdateGDP(64, price, player, matClickedString, false);
				} else {
					player.sendMessage(ChatColor.RED + "Cannot purchase 64x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price * 64));			
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				player.setItemOnCursor(null);
				event.setCancelled(true);
			}
		});
		ItemStack iss1 = new ItemStack(Material.matchMaterial(matClickedString), 1);
		ItemMeta iss1im = iss1.getItemMeta();
		iss1im.setDisplayName(ChatColor.GOLD + "Sell 1x " + ChatColor.AQUA + matClickedString);
		iss1im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif))));
		iss1.setItemMeta(iss1im);
		ItemStack iss2 = new ItemStack(Material.matchMaterial(matClickedString), 2);
		ItemMeta iss2im = iss2.getItemMeta();
		iss2im.setDisplayName(ChatColor.GOLD + "Sell 2x " + ChatColor.AQUA + matClickedString);
		iss2im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 2)));
		iss2.setItemMeta(iss2im);
		ItemStack iss4 = new ItemStack(Material.matchMaterial(matClickedString), 4);
		ItemMeta iss4im = iss4.getItemMeta();
		iss4im.setDisplayName(ChatColor.GOLD + "Sell 4x " + ChatColor.AQUA + matClickedString);
		iss4im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 4)));
		iss4.setItemMeta(iss4im);
		ItemStack iss8 = new ItemStack(Material.matchMaterial(matClickedString), 8);
		ItemMeta iss8im = iss8.getItemMeta();
		iss8im.setDisplayName(ChatColor.GOLD + "Sell 8x " + ChatColor.AQUA + matClickedString);
		iss8im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 8)));
		iss8.setItemMeta(iss8im);
		ItemStack iss16 = new ItemStack(Material.matchMaterial(matClickedString), 16);
		ItemMeta iss16im = iss16.getItemMeta();
		iss16im.setDisplayName(ChatColor.GOLD + "Sell 16x " + ChatColor.AQUA + matClickedString);
		iss16im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 16)));
		iss16.setItemMeta(iss16im);
		ItemStack iss32 = new ItemStack(Material.matchMaterial(matClickedString), 32);
		ItemMeta iss32im = iss32.getItemMeta();
		iss32im.setDisplayName(ChatColor.GOLD + "Sell 32x " + ChatColor.AQUA + matClickedString);
		iss32im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 32)));
		iss32.setItemMeta(iss32im);
		ItemStack iss64 = new ItemStack(Material.matchMaterial(matClickedString), 64);
		ItemMeta iss64im = iss64.getItemMeta();
		iss64im.setDisplayName(ChatColor.GOLD + "Sell 64x " + ChatColor.AQUA + matClickedString);
		iss64im.setLore(Arrays.asList(ChatColor.GREEN + Config.getCurrencySymbol() + df2.format((price - price * 0.01 * sellpricedif) * 64)));
		iss64.setItemMeta(iss64im);
		GuiItem issa = new GuiItem(iss1, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);

			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 1)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 1));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+1) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 1
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(1, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, (price - price * 0.01 * sellpricedif));
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa2 = new GuiItem(iss2, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);

			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 2)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 2));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+2) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 2
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(2, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif) * 2));
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa4 = new GuiItem(iss4, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);

			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 4)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 4));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+4) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 4
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(4, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif) * 4));
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa8 = new GuiItem(iss8, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);

			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 8)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 8));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+8) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 8
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(8, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif) * 8));
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa16 = new GuiItem(iss16, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);

			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 16)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 16));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+16) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 16
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(16, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif) * 16));
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa32 = new GuiItem(iss32, event -> {
			Boolean sellable = false;

			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 32)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 32));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+32) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 32
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(32, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif)) * 32);
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		GuiItem issa64 = new GuiItem(iss64, event -> {
			Boolean sellable = false;
			player.setItemOnCursor(null);
			if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 64)) {
				try {
					player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 64));
					sellable = true;
				} catch (IllegalArgumentException e) {
					player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
				}
				ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
				boolean notMax = true;
				Integer max = (Integer)Main.getShopConfig().get("shops." + matClickedString + "." + "max-sell");
				if (max == null){
					max = 100000;
				}
				if ((cMap.get(matClickedString)+64) > max){
					notMax = false;
					player.sendMessage(ChatColor.RED + "Maximum sells reached");
				}
				if (sellable != true || notMax == false) {
					player.setItemOnCursor(null);
					event.setCancelled(true);
					SBPane.clear();
					createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				}
				if (sellable == true && notMax == true) {
					ConcurrentHashMap<Integer,
						Double[] > tempMap2 = Main.map.get(matClickedString);
					Integer tempMap2Size = tempMap2.size();
					Double[] tempDarray2 = tempMap2.get(tempMap2Size - 1);
					Double buyAmount = tempDarray2[1];
					Double sellAmount = tempDarray2[2];
					Double[] tempDArray = {
						price,
						buyAmount,
						sellAmount + 64
					};
					tempMap2.put(tempMap2Size - 1, tempDArray);
					Main.map.put(matClickedString, tempMap2);

					sendPlayerShopMessageAndUpdateGDP(64, (price - price * 0.01 * sellpricedif), player, matClickedString, true);
					Main.econ.depositPlayer(player, ((price - price * 0.01 * sellpricedif)) * 64);
					sellable = false;
				}
				player.setItemOnCursor(null);
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			} else {
				SBPane.clear();
				createTradingPanel(gui, matClickedString, player, SBPane, price, forward, back);
				event.setCancelled(true);
			}
		});
		forward.setVisible(false);
		back.setVisible(true);
		SBPane.addItem(isa);
		SBPane.addItem(isa2);
		SBPane.addItem(isa4);
		SBPane.addItem(isa8);
		SBPane.addItem(isa16);
		SBPane.addItem(isa32);
		SBPane.addItem(isa64);
		SBPane.addItem(issa);
		SBPane.addItem(issa2);
		SBPane.addItem(issa4);
		SBPane.addItem(issa8);
		SBPane.addItem(issa16);
		SBPane.addItem(issa32);
		SBPane.addItem(issa64);
		gui.update();
		sellpricedif2 = null;
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

	public static void sendPlayerShopMessageAndUpdateGDP(int amount, double price, Player player, String matClickedString, boolean sell){
		if (!sell){
			ConcurrentHashMap<String, Integer> cMap = Main.maxBuyMap.get(player);
			cMap.put(matClickedString, (cMap.get(matClickedString)+amount));
			Main.maxBuyMap.put(player, cMap);
			Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+(price*amount)));
			player.sendMessage(ChatColor.GOLD + "Purchased " + amount + "x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price*amount));
			
		}
		else if (sell){
			ConcurrentHashMap<String, Integer> cMap = Main.maxSellMap.get(player);
			cMap.put(matClickedString, (cMap.get(matClickedString)+amount));
			Main.maxSellMap.put(player, cMap);
			Main.tempdatadata.put("GDP", (Main.tempdatadata.get("GDP")+(price*amount)));
			player.sendMessage(ChatColor.GOLD + "Sold " + amount + "x " + matClickedString + " for " + ChatColor.GREEN + Config.getCurrencySymbol() + df2.format(price*amount));
		}
	}

}