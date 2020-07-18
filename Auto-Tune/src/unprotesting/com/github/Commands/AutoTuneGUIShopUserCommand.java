package unprotesting.com.github.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class AutoTuneGUIShopUserCommand implements CommandExecutor {






    private static DecimalFormat df2 = new DecimalFormat("###,###,###,##0.00");

    public Economy economy = Main.getINSTANCE().getEconomy();





    @Override
    public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {

        if (command.getName().equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(sender, "&cPlayers only.");
                return true;
            } 

                Player p = (Player) sender;
                loadGUIMAIN(p ,sender);


            return true;

        }
        return true;

    }

    public void loadGUIMAIN(Player player, CommandSender senderpub) {
        StaticPane back = new StaticPane(2, 5, 1, 1);
        OutlinePane SBPane = new OutlinePane(1, 2, 7, 2);
        ItemStack is;
        GuiItem a;
        final Player playerpub = player;
        Integer i = 0;
        Material b;
        Double price1;
        Gui gui1;
        Integer menuRows = Config.getMenuRows();

        player = (Player) senderpub;
            gui1 = new Gui(menuRows, Config.getMenuTitle());
            Integer size = Main.getMaterialListSize();
            PaginatedPane pane = new PaginatedPane(0, 0, 9, menuRows);
            Integer paneSize = (menuRows-2)*7;


            // page one
                StaticPane pageOne = new StaticPane(1, 1, 7, menuRows - 2);
                for(i = 0; i<size;){
                        if (Main.memMap.isEmpty() == false){
                        b = (Material.matchMaterial(Main.memMap.get(i)));
                        is = new ItemStack(b);
                        a = new GuiItem(is, event -> {if (event.getClick() == ClickType.LEFT)
                            {
                                Gui gui = gui1;
                                final Player playernew = playerpub;
                                final Double price;
                                final Double buyAmount;
                                final Double sellAmount;
                                String matClickedString = "";
                                ItemStack tempis = event.getCurrentItem();
                                Material tempmat = tempis.getType();
                                matClickedString = tempmat.toString();
                                ConcurrentHashMap<Integer, Double[]> tempmap = Main.map.get(matClickedString);
                                Integer tempMapSize = tempmap.size();
                                Double[] tempDoublearray = tempmap.get(tempMapSize-1);
                                price = tempDoublearray[0];
                                Double buyValueD = tempDoublearray[1];
                                buyAmount = buyValueD;
                                Double sellValueD = tempDoublearray[2];
                                sellAmount = sellValueD;
                                createTradingPanel(gui, matClickedString, playernew, SBPane, sellAmount, buyAmount, price);
                                pane.setPage(pane.getPage() + 1);
                                back.setVisible(true);
                                gui.update();
                                playernew.setItemOnCursor(null);
                                event.setCancelled(true);
                            }
                        if (event.getClick() != ClickType.LEFT){
                            final Player playernew = playerpub;
                            event.setCancelled(true);
                            playernew.setItemOnCursor(null);
                        }});
                        ItemMeta im = is.getItemMeta();
                        im.setDisplayName(ChatColor.AQUA + Main.memMap.get(i));
                        ConcurrentHashMap<Integer, Double[]> tempmap = Main.map.get(Main.memMap.get(i));
                        Integer tempMapSize = tempmap.size();
                        Double[] tempDoublearray = tempmap.get(tempMapSize-1);
                        price1 = tempDoublearray[0];
                        String priceString = df2.format(price1);
                        String fullprice = "Price: " + "$" + priceString;
                        im.setLore(Arrays.asList(ChatColor.GOLD + fullprice));
                        is.setItemMeta(im);
                        if (i < 7){
                        pageOne.addItem(a, i, 0);
                        }
                        if (i >= 7 && i < 13){
                            pageOne.addItem(a, i-7, 1);
                        }

                        i++;
                }
               
            }
            pane.addPane(0, pageOne);
            pane.addPane(1, SBPane);



            // page two
            //**if (size > menuRows*7){
               // OutlinePane pageTwo = new OutlinePane(1, 1, 7, menuRows-2);
               // pageTwo.addItem(new GuiItem(new ItemStack(Material.GLASS), event -> event.getWhoClicked().sendMessage("Glass")));
                //pane.addPane(1, pageTwo);
           // }
            

            // page three
           // if (size > menuRows*14){
              //  OutlinePane pageThree = new OutlinePane(1, 1, 7, menuRows-2);
            //    pageThree.addItem(new GuiItem(new ItemStack(Material.BLAZE_ROD),event -> event.getWhoClicked().sendMessage("Blaze rod")));
            //    pane.addPane(2, pageThree);
           // }

            gui1.addPane(pane);

            // page selection
            ItemStack isback = new ItemStack(Material.ARROW);
            ItemMeta imback = isback.getItemMeta();
            imback.setDisplayName(ChatColor.WHITE + "Back");
            imback.setLore(Arrays.asList(ChatColor.BOLD + "Click to return to Shop Menu"));
            isback.setItemMeta(imback);
            GuiItem guiitemback = new GuiItem(isback, event -> {
                pane.setPage(pane.getPage() - 1);

                if (pane.getPage() == 0) {
                    back.setVisible(false);
                }

                gui1.update();
            });
            back.addItem(guiitemback, 0, 0);
            back.setVisible(false);



            gui1.addPane(back);

            gui1.show((HumanEntity) senderpub);


}


public Double sellpricedif;
public Double sellpricedif2;


public void createTradingPanel(Gui gui, String matClickedString, Player player, OutlinePane SBPane, Double sellAmount, Double buyAmount, Double price){
    sellpricedif2 = null;
    sellpricedif = null;
    sellpricedif = Config.getSellPriceDifference();
    ConfigurationSection config = Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getConfigurationSection((matClickedString));
    sellpricedif2 = config.getDouble("sell-difference", sellpricedif);
    if (sellpricedif2 != null){
        sellpricedif = sellpricedif2;
    }
    SBPane.clear();
    ItemStack is1 = new ItemStack(Material.matchMaterial(matClickedString), 1);
    ItemMeta is1im = is1.getItemMeta();
    is1im.setDisplayName(ChatColor.GOLD + "Buy 1x "+ "$" + ChatColor.AQUA + matClickedString);
    is1im.setLore(Arrays.asList(ChatColor.GREEN + df2.format(price)));
    is1.setItemMeta(is1im);
    ItemStack is2 = new ItemStack(Material.matchMaterial(matClickedString), 2);
    ItemMeta is2im = is2.getItemMeta();
    is2im.setDisplayName(ChatColor.GOLD + "Buy 2x " + ChatColor.AQUA + matClickedString);
    is2im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format(price*2)));
    is2.setItemMeta(is2im);
    ItemStack is4 = new ItemStack(Material.matchMaterial(matClickedString), 4);
    ItemMeta is4im = is4.getItemMeta();
    is4im.setDisplayName(ChatColor.GOLD + "Buy 4x " + ChatColor.AQUA + matClickedString);
    is4im.setLore(Arrays.asList(ChatColor.GREEN + "$" +df2.format(price * 4)));
    is4.setItemMeta(is4im);
    ItemStack is8 = new ItemStack(Material.matchMaterial(matClickedString), 8);
    ItemMeta is8im = is8.getItemMeta();
    is8im.setDisplayName(ChatColor.GOLD + "Buy 8x " + ChatColor.AQUA + matClickedString);
    is8im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format(price * 8)));
    is8.setItemMeta(is8im);
    ItemStack is16 = new ItemStack(Material.matchMaterial(matClickedString), 16);
    ItemMeta is16im = is16.getItemMeta();
    is16im.setDisplayName(ChatColor.GOLD + "Buy 16x " + ChatColor.AQUA + matClickedString);
    is16im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format(price * 16)));
    is16.setItemMeta(is16im);
    ItemStack is32 = new ItemStack(Material.matchMaterial(matClickedString), 32);
    ItemMeta is32im = is2.getItemMeta();
    is32im.setDisplayName(ChatColor.GOLD + "Buy 32x " + ChatColor.AQUA + matClickedString);
    is32im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format(price * 32)));
    is32.setItemMeta(is32im);
    ItemStack is64 = new ItemStack(Material.matchMaterial(matClickedString), 64);
    ItemMeta is64im = is2.getItemMeta();
    is64im.setDisplayName(ChatColor.GOLD + "Buy 64x " + ChatColor.AQUA + matClickedString);
    is64im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format(price * 64)));
    is64.setItemMeta(is64im);
    GuiItem isa = new GuiItem(is1, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
            Integer tempMap2Size = tempMap2.size();
            Double[] tempDArray = {price, buyAmount+1, sellAmount};
            tempMap2.put(tempMap2Size-1, tempDArray);
            Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 1));
        player.sendMessage(ChatColor.GOLD + "Purchased 1x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*1));
        player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa2 = new GuiItem(is2, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+2, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*2);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 2));
        player.sendMessage(ChatColor.GOLD + "Purchased 2x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*2));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa4 = new GuiItem(is4, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+4, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*4);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 4));
        player.sendMessage(ChatColor.GOLD + "Purchased 4x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*4));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa8 = new GuiItem(is8, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+8, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*8);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 8));
        player.sendMessage(ChatColor.GOLD + "Purchased 8x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*8));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa16 = new GuiItem(is16, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+16, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*16);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 16));
        player.sendMessage(ChatColor.GOLD + "Purchased 16x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*16));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa32 = new GuiItem(is32, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+32, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*32);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 32));
        player.sendMessage(ChatColor.GOLD + "Purchased 32x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*32));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
    GuiItem isa64 = new GuiItem(is64, event -> {
        player.setItemOnCursor(null);
        if ((Player) event.getWhoClicked() == player){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount+64, sellAmount};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        Main.econ.withdrawPlayer(player, price*64);
        player.getInventory().addItem(new ItemStack(Material.matchMaterial(matClickedString), 64));
        player.sendMessage(ChatColor.GOLD + "Purchased 64x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format(price*64));
            player.setItemOnCursor(null);
            SBPane.clear();
            createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }});
        ItemStack iss1 = new ItemStack(Material.matchMaterial(matClickedString), 1);
        ItemMeta iss1im = iss1.getItemMeta();
        iss1im.setDisplayName(ChatColor.GOLD + "Sell 1x " + "$" + ChatColor.AQUA + matClickedString);
        iss1im.setLore(Arrays.asList(ChatColor.GREEN + df2.format((price-price*0.01*sellpricedif))));
        iss1.setItemMeta(iss1im);
        ItemStack iss2 = new ItemStack(Material.matchMaterial(matClickedString), 2);
        ItemMeta iss2im = iss2.getItemMeta();
        iss2im.setDisplayName(ChatColor.GOLD + "Sell 2x " + ChatColor.AQUA + matClickedString);
        iss2im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*2)));
        iss2.setItemMeta(iss2im);
        ItemStack iss4 = new ItemStack(Material.matchMaterial(matClickedString), 4);
        ItemMeta iss4im = iss4.getItemMeta();
        iss4im.setDisplayName(ChatColor.GOLD + "Sell 4x " + ChatColor.AQUA + matClickedString);
        iss4im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif) * 4)));
        iss4.setItemMeta(iss4im);
        ItemStack iss8 = new ItemStack(Material.matchMaterial(matClickedString), 8);
        ItemMeta iss8im = iss8.getItemMeta();
        iss8im.setDisplayName(ChatColor.GOLD + "Sell 8x " + ChatColor.AQUA + matClickedString);
        iss8im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif) * 8)));
        iss8.setItemMeta(iss8im);
        ItemStack iss16 = new ItemStack(Material.matchMaterial(matClickedString), 16);
        ItemMeta iss16im = iss16.getItemMeta();
        iss16im.setDisplayName(ChatColor.GOLD + "Sell 16x " + ChatColor.AQUA + matClickedString);
        iss16im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif) * 16)));
        iss16.setItemMeta(iss16im);
        ItemStack iss32 = new ItemStack(Material.matchMaterial(matClickedString), 32);
        ItemMeta iss32im = iss32.getItemMeta();
        iss32im.setDisplayName(ChatColor.GOLD + "Sell 32x " + ChatColor.AQUA + matClickedString);
        iss32im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif) * 32)));
        iss32.setItemMeta(iss32im);
        ItemStack iss64 = new ItemStack(Material.matchMaterial(matClickedString), 64);
        ItemMeta iss64im = iss64.getItemMeta();
        iss64im.setDisplayName(ChatColor.GOLD + "Sell 64x " + ChatColor.AQUA + matClickedString);
        iss64im.setLore(Arrays.asList(ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif) * 64)));
        iss64.setItemMeta(iss64im);
        GuiItem issa = new GuiItem(iss1, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 1)){
            try{
            player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 1));
            sellable = true;
            }
            catch(IllegalArgumentException e){
                player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
            }
            if (sellable != true){
                event.setCancelled(true);
                SBPane.clear();
                createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
            }
            if (sellable == true){
            ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+1};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
            player.sendMessage(ChatColor.GOLD + "Sold 1x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*1));
            Main.econ.depositPlayer(player, (price-price*0.01*sellpricedif));
            sellable = false;
            }
            player.setItemOnCursor(null);
                SBPane.clear();
                createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                event.setCancelled(true);
            }
            else{
                SBPane.clear();
                createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                event.setCancelled(true);
            }});
        GuiItem issa2 = new GuiItem(iss2, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 2)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 2));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+2};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 2x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*2));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif)*2));
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }});
        GuiItem issa4 = new GuiItem(iss4, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 4)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 4));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+4};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 4x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*4));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif)*4));
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }});
        GuiItem issa8 = new GuiItem(iss8, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 8)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 8));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+8};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 8x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*8));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif)*8));
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }});
        GuiItem issa16 = new GuiItem(iss16, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 16)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 16));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+16};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 16x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*16));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif)*16));
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }});
        GuiItem issa32 = new GuiItem(iss32, event -> {
            Boolean sellable = false;
           
            player.setItemOnCursor(null);
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 32)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 32));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+32};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 32x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*32));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif))*32);
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
            });
        GuiItem issa64 = new GuiItem(iss64, event -> {
            Boolean sellable = false;
            player.setItemOnCursor(null);
            if ((Player) event.getWhoClicked() == player && player.getInventory().contains(Material.matchMaterial(matClickedString), 64)){
                try{
                player.getInventory().removeItem(new ItemStack(Material.matchMaterial(matClickedString), 64));
                sellable = true;
                }
                catch(IllegalArgumentException e){
                    player.sendMessage(ChatColor.RED + "No items present of that type in your inventory!");
                }
                if (sellable != true){
                    player.setItemOnCursor(null);
                    event.setCancelled(true);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                }
                if (sellable == true){
                    ConcurrentHashMap<Integer,Double[]> tempMap2 = Main.map.get(matClickedString);
        Integer tempMap2Size = tempMap2.size();
        Double[] tempDArray = {price, buyAmount, sellAmount+64};
        tempMap2.put(tempMap2Size-1, tempDArray);
        Main.map.put(matClickedString, tempMap2);
        
                player.sendMessage(ChatColor.GOLD + "Sold 64x " + matClickedString + " for " + ChatColor.GREEN + "$" + df2.format((price-price*0.01*sellpricedif)*64));
                Main.econ.depositPlayer(player, ((price-price*0.01*sellpricedif))*64);
                sellable = false;
                }
                player.setItemOnCursor(null);
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }
                else{
                    SBPane.clear();
                    createTradingPanel(gui, matClickedString, player, SBPane, sellAmount, buyAmount, price);
                    event.setCancelled(true);
                }});
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

}