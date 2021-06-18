package unprotesting.com.github.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

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
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.commands.util.FunctionsUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.logging.Logging;

public class ShopCommand implements CommandExecutor{

    private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand((Player) sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.shop") || player.isOp())){CommandUtil.noPermssion(player);return true;}
        if (args.length > 1){
            player.sendMessage(ChatColor.RED + "Correct usage: /<shop> <shop-section>");
            return true;
        }
        if (args.length == 0){
            loadGUI(sender);
            return true;
        };
        if (args.length == 1){
            for (Section section : Main.getCache().getSECTIONS()){
                if (args[0].replaceAll("-", "").replaceAll(" ", "").equalsIgnoreCase(section.getName().replaceAll("-", "").replaceAll(" ", ""))){
                    loadShopPane(sender, section);
                    return true;
                }
            }
            player.sendMessage(ChatColor.RED + "Correct usage: /<shop> <shop-section>");
        }
        return true;
    }

    private void loadGUI(CommandSender sender){
        CommandUtil.closeInventory(sender);
        int highest = Section.getHighest(Main.getCache().getSECTIONS());
        int lines = (highest/9)+2;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui = CommandUtil.getBackground(gui, lines, Config.getBackground());
        gui.addPane(loadSectionsPane(sender, lines));
        gui.show((HumanEntity)(sender));
    }

    private StaticPane loadSectionsPane(CommandSender sender, int lines){
        StaticPane navigationPane = new StaticPane(0, 0, 9, lines);
        for (Section section : Main.getCache().getSECTIONS()){
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
        Player player = CommandUtil.closeInventory(sender);
        ChestGui gui = new ChestGui(6, Config.getMenuTitle());
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<GuiItem> items = getListFromSection(section, player);
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, section.getBackground(), sender);
    }

    private List<GuiItem> getListFromSection(Section section, CommandSender sender){
        Player player = (Player)sender;
        List<GuiItem> output = new ArrayList<GuiItem>();
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        for (String s_item : section.getItems()){
            ItemStack item = new ItemStack(Material.BARRIER);
            if (section.isEnchantmentSection() && !Config.isEnableEnchantments()){
                continue;
            }
            try{
                if (section.isEnchantmentSection()){
                    item = new ItemStack(Material.ENCHANTED_BOOK);
                }
                if (!section.isEnchantmentSection()){
                    item = new ItemStack(Material.matchMaterial(s_item));
                }
            }
            catch(NullPointerException e){
                Logging.error(3);
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + s_item);
            List<String> list = new ArrayList<String>();
            list.add(ChatColor.GREEN + Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(s_item, false)));
            if (section.isEnchantmentSection()){
                list.clear();
                list.add(ChatColor.GREEN + Config.getCurrencySymbol() + df.format(getEnchPriceWithHeld(s_item, player)));
            }
            list.add(Main.getCache().getPChangeString(s_item));
            if (section.isEnchantmentSection()){
                list.add(ChatColor.YELLOW + "Ratio: " + Main.getCache().getEnchantmentRatio(s_item));
                list.add(ChatColor.YELLOW + "Price: " + Config.getCurrencySymbol() + df.format(Main.getCache().getEnchantmentPrice(s_item, false)));
            }
            list.add(ChatColor.WHITE + "Remaining Buys: " + ChatColor.GRAY + Main.getCache().getBuysLeft(s_item, player));
            list.add(ChatColor.WHITE + "Remaining Sells: " + ChatColor.GRAY + Main.getCache().getSellsLeft(s_item, player));
            meta.setLore(list);
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                if (section.isEnchantmentSection()){
                    loadPurchasePane(section, s_item, sender);
                }
                else{
                    loadPurchasePane(section, s_item, sender);
                }
            });
            output.add(gItem);
        }
        return output;
    }

    private double getEnchPriceWithHeld(String enchantment, Player player){
        ItemStack held_item = player.getInventory().getItemInMainHand();
        double i_price = 0;
        if (held_item != null){
            i_price = Main.getCache().getItemPrice(held_item.getType().toString(), false);
        }
        return Main.getCache().getOverallEnchantmentPrice(enchantment, i_price, false);
    }

    private void loadPurchasePane(Section section, String item, CommandSender sender){
        CommandUtil.closeInventory(sender);
        ChestGui gui = new ChestGui(4, Config.getMenuTitle());
        gui = CommandUtil.getBackground(gui, 4, Config.getBackground());
        gui.addPane(getPurchasePane(item, sender, section));
        gui.show((HumanEntity)sender);
    }

    private OutlinePane getPurchasePane(String item_input, CommandSender sender, Section section){
        Player player = (Player)sender;
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        OutlinePane pane = new OutlinePane(1, 1, 7, 2);
        for (int amount : amounts){
            ItemStack item;
            if (!section.isEnchantmentSection()){
                item = getPurchasePaneItem(item_input, ChatColor.GREEN + "Buy for " + Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(item_input, false)*amount), amount);
            }
            else if (Config.isEnableEnchantments()){
                item = getPurchasePaneItem("ENCHANTED_BOOK", ChatColor.GREEN + "Buy for " + Config.getCurrencySymbol() + df.format(getEnchPriceWithHeld(item_input, player)*amount), amount);
            }
            else{
                return pane;
            }
            if (item.getMaxStackSize() < amount){
                ItemStack background = new ItemStack(Material.matchMaterial(Config.getBackground()));
                GuiItem gItem = new GuiItem(background, event->{
                    event.setCancelled(true);
                });
                pane.addItem(gItem);
                continue;
            }
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                if (!section.isEnchantmentSection()){
                    FunctionsUtil.buyItem(player, item_input, amount);
                }
                else{
                    FunctionsUtil.buyEnchantment(player, item_input);
                }
            });
            pane.addItem(gItem);
        }
        if (section.isEnchantmentSection()){
            return pane;
        }
        for (int amount : amounts){
            ItemStack item = getPurchasePaneItem(item_input, ChatColor.RED + "Sell for " + Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(item_input, true)*amount), amount);
            if (item.getMaxStackSize() < amount){
                continue;
            }
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                FunctionsUtil.sellItem(player, item_input, amount);
            });
            pane.addItem(gItem);
        }
        return pane;
    }

    private ItemStack getPurchasePaneItem(String item_input, String prefix, int amount){
        ItemStack item = new ItemStack(Material.matchMaterial(item_input), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + item_input);
        meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + prefix}));
        item.setItemMeta(meta);
        return item;
    }

}
