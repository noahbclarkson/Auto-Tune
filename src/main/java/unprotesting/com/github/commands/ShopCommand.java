package unprotesting.com.github.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
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
import unprotesting.com.github.commands.util.ShopFormat;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.logging.Logging;

public class ShopCommand extends ShopFormat implements CommandExecutor{

    private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand((Player) sender, args, "at.shop");
    }

    public StaticPane loadSectionsPane(CommandSender sender, int lines){
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

    public GuiItem getGUIItem(Section section, String s_item, Player player, CommandSender sender, DecimalFormat df){
        ItemStack item = new ItemStack(Material.BARRIER);
            if (section.isEnchantmentSection() && !Config.isEnableEnchantments()){
                return null;
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
                return null;
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
                list.add(ChatColor.YELLOW + "Ratio: " + df.format(Main.getCache().getEnchantmentRatio(s_item)));
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
            return gItem;
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
        gui.addPane(generateMenuBackPane(sender));
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
