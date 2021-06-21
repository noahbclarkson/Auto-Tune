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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;

public class AutosellCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String autosell, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = CommandUtil.closeInventory(sender);
        int length = args.length;
        if (!(player.hasPermission("at.autosell") || player.isOp())){CommandUtil.noPermssion(player);return true;}
        if (length > 1){
            player.sendMessage(ChatColor.RED + "Correct usage: /autosell <shop-section>");
            return true;
        }
        if (length == 0){
            loadGUI(sender);
            return true;
        }
        if (length == 1){
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
            if (section.isEnchantmentSection()){
                continue;
            }
            int x = section.getPosition() % 9;
            int y = section.getPosition() / 9;
            ItemStack item = new ItemStack(section.getImage());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + section.getName());
            meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + "Click to change " + section.getName() + " autosell settings."}));
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
        CommandUtil.closeInventory(sender);
        ChestGui gui = new ChestGui(6, Config.getMenuTitle());
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<GuiItem> items = getListFromSection(section, sender);
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, section.getBackground(), sender);
        gui.addPane(generateMenuBackPane(sender));
        gui.update();
    }

    private List<GuiItem> getListFromSection(Section section, CommandSender sender){
        Player player = (Player)sender;
        List<GuiItem> output = new ArrayList<GuiItem>();
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        for (String s_item : section.getItems()){
            ItemStack item = new ItemStack(Material.matchMaterial(s_item));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + s_item);
            String lore;
            boolean setting = CommandUtil.getPlayerAutoSellSetting(player, item.getType().toString());
            if (setting){
                lore = ChatColor.GREEN + "Click to turn off auto-selling!";
            }
            else {
                lore = ChatColor.RED + "Click to turn on auto-selling!";
            }
            meta.setLore(Arrays.asList(new String[]{lore, ChatColor.WHITE + "Sell-Price: "
             + ChatColor.GOLD + df.format(Main.getCache().getItemPrice(item.getType().toString(), true))}));
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                changePlayerAutoSellSetting(player, item.getType().toString());
                player.getOpenInventory().close();
                loadShopPane(sender, section);
            });
            output.add(gItem);
        }
        return output;
    }

    private void changePlayerAutoSellSetting(Player player, String item){
        String uuid = player.getUniqueId().toString();
        YamlConfiguration config = Main.getDfiles().getPlayerData();
        if (!config.contains(uuid + ".autosell")){
            config.createSection(uuid + ".autosell");
            config.set(uuid + ".autosell." + item, true);
            Main.getDfiles().setPlayerData(config);
            return;
        }
        else if (!config.contains(uuid + ".autosell." + item)){
            config.createSection(uuid + ".autosell." + item);
            config.set(uuid + ".autosell." + item, true);
            Main.getDfiles().setPlayerData(config);
            return;
        }
        else{
            boolean setting = false;
            setting = config.getBoolean(uuid + ".autosell." + item, false);
            config.set(uuid + ".autosell." + item, !setting);
        }
    }

    private StaticPane generateMenuBackPane(CommandSender sender){
        StaticPane output = new StaticPane(0, 0, 1, 1);
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "MENU");
        meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + "Click to go back to the main menu"}));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
            int highest = Section.getHighest(Main.getCache().getSECTIONS());
            int lines = (highest/9)+2;
            event.getWhoClicked().getOpenInventory().close();
            loadSectionsPane(sender, lines);
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    
    
}
