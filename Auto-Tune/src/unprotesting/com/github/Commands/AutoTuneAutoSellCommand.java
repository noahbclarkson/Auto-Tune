package unprotesting.com.github.Commands;

import java.util.Arrays;
import java.util.UUID;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneAutoSellCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String autosell, String[] args) {

        if (command.getName().equalsIgnoreCase("autosell")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(sender, "&cPlayers only.");
                return true;
            } 

                Player p = (Player) sender;
                if (Config.getMenuRows() == 6){
                    AutoTuneGUIShopUserCommand.SBPanePos = 2;
                }
                if (p.hasPermission("at.autosell") || p.isOp()){loadGUIMAIN(p, sender);}
                else if (!(p.hasPermission("at.autosell")) && !(p.isOp())){TextHandler.noPermssion(p);}
            return true;

        }
        return true;

    }

    public void loadGUIMAIN(Player player, CommandSender senderpub) {
        UUID uuid = player.getUniqueId();
        Integer menuRows = Config.getMenuRows();
        OutlinePane SBPane = new OutlinePane(1, AutoTuneGUIShopUserCommand.SBPanePos, 7, 2);
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
        StaticPane back = new StaticPane(0, menuRows-1, 1, 1);
        StaticPane forward = new StaticPane(8, menuRows-1, 1, 1);
        

        player = (Player) senderpub;
            gui1 = new Gui(menuRows, Config.getMenuTitle());
            Integer size = Main.getMaterialListSize();
            PaginatedPane pane = new PaginatedPane(0, 0, 9, menuRows);
            Integer paneSize = (menuRows-2)*7;
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
                for(i = 0; i<size;){
                        if (!(Main.memMap.isEmpty())){
                        b = (Material.matchMaterial(Main.memMap.get(i)));
                        final String materialString = Main.memMap.get(i);
                        is = new ItemStack(b);
                        a = new GuiItem(is, event -> {if (event.getClick() == ClickType.LEFT)
                            {
                                Gui gui = gui1;
                                final Player playernew = playerpub;
                                changePlayerAutoSellSettings(playernew, materialString);
                                gui.update();
                                pane.clear();
                                loadGUIMAIN(playernew, senderpub);
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
                        im.setDisplayName(ChatColor.AQUA + Main.memMap.get(i) + ChatColor.RED + " - Auto Sell Disabled");
                        Boolean atonoff = Main.playerDataConfig
                                .getBoolean(uuid + ".AutoSell" + "." + Main.memMap.get(i));
                        if (atonoff == true){
                            im.setDisplayName(ChatColor.AQUA + Main.memMap.get(i) + ChatColor.GREEN + " - Auto Sell Enabled");
                        }
                        ConcurrentHashMap<Integer, Double[]> tempmap = Main.map.get(Main.memMap.get(i));
                        Integer tempMapSize = tempmap.size();
                        Double[] tempDoublearray = tempmap.get(tempMapSize - 1);
                        price1 = tempDoublearray[0];
                        String priceString = AutoTuneGUIShopUserCommand.df2.format(price1);
                        String fullprice = "Price: " + Config.getCurrencySymbol() + priceString;
                        im.setLore(Arrays.asList(ChatColor.GOLD + fullprice));
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

            public void changePlayerAutoSellSettings(Player player, String material){
                UUID uuid = player.getUniqueId();
                Boolean autosellset = false;
                Main.playerDataConfig.contains(uuid + ".AutoSell");
                autosellset = true;
                if (autosellset == false){
                    Main.playerDataConfig.createSection(uuid + ".AutoSell");
                }
                Boolean atonoff = Main.playerDataConfig.getBoolean(uuid + ".AutoSell" + "." + material);
                if (!(Main.playerDataConfig.contains(uuid + ".AutoSell" + "." + material))) {
                    Main.playerDataConfig.createSection(uuid + ".AutoSell" + "." + material);
                }
                if (atonoff == false){
                    Main.playerDataConfig.set(uuid + ".AutoSell" + "." + material, true);
                }
                if (atonoff == true){
                    Main.playerDataConfig.set(uuid + ".AutoSell" + "." + material, false);
                }
                Main.saveplayerdata();
            }

    
}