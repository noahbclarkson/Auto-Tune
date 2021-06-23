package unprotesting.com.github.commands.util;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.config.Config;

public abstract class ShopFormat {

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

    protected void loadGUI(CommandSender sender){
        int highest = Section.getHighest(Main.getCache().getSECTIONS());
        int lines = (highest/9)+2;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui = CommandUtil.getBackground(gui, lines, Config.getBackground());
        gui.addPane(loadSectionsPane(sender, lines));
        gui.show((HumanEntity)(sender));
    }

    public abstract List<GuiItem> getListFromSection(Section section, CommandSender sender);

    public abstract StaticPane loadSectionsPane(CommandSender sender, int lines);
    
}
