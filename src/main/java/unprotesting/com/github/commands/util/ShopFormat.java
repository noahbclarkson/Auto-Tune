package unprotesting.com.github.commands.util;

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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.config.Config;

public abstract class ShopFormat {

    protected boolean interpretCommand(CommandSender sender, String[] args, String permission){
        Player player = CommandUtil.closeInventory(sender);
        int length = args.length;
        if (!(player.hasPermission(permission) || player.hasPermission("at.admin"))){CommandUtil.noPermission(player);return true;}
        if (length > 1){
            return false;
        }
        if (length == 0){
            loadGUI(sender);
            return true;
        }
        if (length == 1){
            for (Section section : Main.getCache().getSECTIONS()){
                if (args[0].replace("-", "").replace(" ", "").equalsIgnoreCase(section.getName().replace("-", "").replace(" ", ""))){
                    loadShopPane(sender, section);
                    return true;
                }
            }
        }
        return false;
    }

    protected void loadGUI(CommandSender sender){
        int highest = Section.getHighest(Main.getCache().getSECTIONS());
        int lines = (highest/9)+2;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui = CommandUtil.getBackground(gui, lines, Config.getBackground());
        gui.addPane(loadSectionsPane(sender, lines));
        gui.show((HumanEntity)(sender));
    }

    protected List<GuiItem> getListFromSection(Section section, CommandSender sender){
        Player player = (Player)sender;
        List<GuiItem> output = new ArrayList<GuiItem>();
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        int i = 0;
        for (String s_item : section.getItems()){
            GuiItem item = getGUIItem(section, s_item, section.getDisplayNames().get(i), player, sender, df);
            i++;
            if (item == null){
                continue;
            }
            output.add(item);

        }
        return output;
    }

    protected void loadShopPane(CommandSender sender, Section section){
        CommandUtil.closeInventory(sender);
        ChestGui gui = new ChestGui(6, Config.getMenuTitle());
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<GuiItem> items = getListFromSection(section, sender);
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, section.getBackground(), sender);
        if (section.isBack()){
            gui.addPane(generateMenuBackPane(sender));
        }
        gui.update();
    }

    protected StaticPane generateMenuBackPane(CommandSender sender){
        StaticPane output = new StaticPane(0, 0, 1, 1);
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "MENU");
        meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + "Click to go back to the main menu"}));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
            event.getWhoClicked().getOpenInventory().close();
            loadGUI(sender);
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    public abstract GuiItem getGUIItem(Section section, String s_item, String displayName, Player player, CommandSender sender, DecimalFormat df);

    public abstract StaticPane loadSectionsPane(CommandSender sender, int lines);
    
}
