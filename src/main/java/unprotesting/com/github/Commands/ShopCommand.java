package unprotesting.com.github.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Commands.Util.CommandUtil;
import unprotesting.com.github.Commands.Util.FunctionsUtil;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;

public class ShopCommand implements CommandExecutor{

    private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand((Player) sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = (Player)sender;
        if (!(player.hasPermission("at.shop") || player.isOp())){CommandUtil.noPermssion(player);return true;};
        if (args.length > 1){
            player.sendMessage(ChatColor.RED + "Correct usage: /<shop> <shop-section>");
            return true;
        }
        if (args.length == 0){
            loadGUI(sender);
            return true;
        };
        if (args.length == 1){
            for (Section section : Main.cache.getSECTIONS()){
                if (args[0].toLowerCase().replaceAll("-", "").replaceAll(" ", "").equals(section.getName().toLowerCase().replaceAll("-", "").replaceAll(" ", ""))){
                    loadShopPane(sender, section);
                    return true;
                }
            }
            player.sendMessage(ChatColor.RED + "Correct usage: /<shop> <shop-section>");
        }
        return true;
    }

    private void loadGUI(CommandSender sender){
        closeInventory(sender);
        int highest = Section.getHighest(Main.cache.getSECTIONS());
        int lines = (highest/9)+2;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui = getBackground(gui, lines, Config.getBackground());
        gui.addPane(loadSectionsPane(sender, lines));
        gui.show((HumanEntity)(sender));
    }

    private StaticPane loadSectionsPane(CommandSender sender, int lines){
        StaticPane navigationPane = new StaticPane(0, 0, 9, lines);
        for (Section section : Main.cache.getSECTIONS()){
            int x = section.getPosition() % 9;
            int y = section.getPosition() / 9;
            ItemStack item = new ItemStack(section.getImage());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + section.getName());
            meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + "CLick to enter " + section.getName() + " shop"}));
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                loadShopPane(sender, section);
            });
            navigationPane.addItem(gItem, x, y);
        }
        return navigationPane;
    }

    private void loadShopPane(CommandSender sender, Section section){
        Player player = closeInventory(sender);
        ChestGui gui = new ChestGui(6, Config.getMenuTitle());
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<GuiItem> items = getListFromSection(section, player);
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        int page = 0;
        int k = 0;
        OutlinePane pane = new OutlinePane(1, 1, 7, 4);
        pages.addPane(page, getArrowPane(page, ChatColor.GRAY + "BACK", pages, true, gui));
        pages.addPane(page, getArrowPane(page, ChatColor.GRAY + "NEXT", pages, false, gui));
        panes.add(pane);
        for (int i = 0; i < items.size(); i++){
            pane = panes.get(panes.size()-1);
            if (k > 27){
                pane = new OutlinePane(1, 1, 7, 4);
                pages.addPane(page, getArrowPane(page, ChatColor.GRAY + "BACK", pages, true, gui));
                pages.addPane(page, getArrowPane(page, ChatColor.GRAY + "NEXT", pages, false, gui));
                panes.add(pane);
                page++;
            }
            pane.addItem(items.get(i));
            k++;
        }
        int i = 0;
        for (OutlinePane opane : panes){
            pages.addPane(i, opane);
            i++;
        }
        gui.addPane(pages);
        gui = getBackground(gui, 6, section.getBackground());
        gui.show((HumanEntity)(sender));
    }

    private StaticPane getArrowPane(int page, String dName, PaginatedPane pane, boolean back, ChestGui GUI){
        StaticPane output = new StaticPane(0, 5, 1, 1);
        if (!back){
            output = new StaticPane(8, 5, 1, 1);
        }
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + dName);
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event -> {
            event.setCancelled(true);
            pane.setPage(page);
            GUI.update();
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    private List<GuiItem> getListFromSection(Section section, CommandSender sender){
        Player player = (Player)sender;
        List<GuiItem> output = new ArrayList<GuiItem>();
        for (String s_item : section.getItems()){
            ItemStack item = new ItemStack(Material.matchMaterial(s_item));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + s_item);
            meta.setLore(Arrays.asList(new String[]{
            ChatColor.GREEN + Config.getCurrencySymbol() + Main.cache.getItemPrice(s_item),
            Main.cache.getPChangeString(s_item),
            ChatColor.WHITE + "Remaining Buys: " + ChatColor.GRAY + Main.cache.getBuysLeft(s_item, player), 
            ChatColor.WHITE + "Remaining Sells: " + ChatColor.GRAY + Main.cache.getSellsLeft(s_item, player)}));
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                loadPurchasePane(s_item, sender);
            });
            output.add(gItem);
        }
        return output;
    }

    private void loadPurchasePane(String item, CommandSender sender){
        closeInventory(sender);
        ChestGui gui = new ChestGui(4, Config.getMenuTitle());
        gui = getBackground(gui, 4, Config.getBackground());
        gui.addPane(getPurchasePane(item, sender));
        gui.show((HumanEntity)sender);
    }

    private OutlinePane getPurchasePane(String item_input, CommandSender sender){
        Player player = (Player)sender;
        OutlinePane pane = new OutlinePane(1, 1, 7, 2);
        for (int amount : amounts){
            ItemStack item = getPurchasePaneItem(item_input, "Buy for ", amount, Main.cache.getItemPrice(item_input));
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                FunctionsUtil.buyItem(player, item_input, amount);
            });
            pane.addItem(gItem);
        }
        for (int amount : amounts){
            ItemStack item = getPurchasePaneItem(item_input, "Sell for ", amount, Main.cache.getItemPrice(item_input, true));
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                FunctionsUtil.sellItem(player, item_input, amount);
            });
            pane.addItem(gItem);
        }
        return pane;
    }

    private ItemStack getPurchasePaneItem(String item_input, String prefix, int amount, double price){
        ItemStack item = new ItemStack(Material.matchMaterial(item_input), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + item_input);
        meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + prefix + Config.getCurrencySymbol() + price}));
        item.setItemMeta(meta);
        return item;
    }

    private ChestGui getBackground(ChestGui GUI, int lines, String bItem){
        GUI.setOnGlobalClick(event -> event.setCancelled(true));
        OutlinePane background = new OutlinePane(0, 0, 9, lines, Priority.LOWEST);
        background.addItem(new GuiItem(new ItemStack(Material.matchMaterial(bItem))));
        ItemStack item = new ItemStack(Material.matchMaterial(bItem));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.MAGIC + "|");
        item.setItemMeta(meta);
        background.setRepeat(true);
        GUI.addPane(background);
        return GUI;
    }

    private Player closeInventory(CommandSender sender){
        Player player = (Player)sender;
        player.getOpenInventory().close();
        return player;
    }

}
