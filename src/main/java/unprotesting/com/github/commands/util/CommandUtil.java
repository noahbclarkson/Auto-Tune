package unprotesting.com.github.commands.util;

import java.util.List;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.MessagesData;
import unprotesting.com.github.logging.Logging;

public class CommandUtil {

    public static boolean checkIfSenderPlayer(CommandSender sender){
        if (!(sender instanceof Player)) {Logging.error(0);return false;}
        return true;
    }

    public static void noPermission(Player p){p.sendMessage(MessagesData.getNoPermission(p));}

    public static Player closeInventory(CommandSender sender){
        Player player = (Player)sender;
        player.getOpenInventory().close();
        return player;
    }

    public static StaticPane getArrowPane(int page, String dName, PaginatedPane pane, boolean back, ChestGui GUI){
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
            GUI.addPane(pane);
            GUI.update();
        });
        output.addItem(gItem, 0, 0);
        return output;
    }

    public static void loadGuiItemsIntoPane(List<GuiItem> items, ChestGui gui, PaginatedPane pages, List<OutlinePane> panes, Material background, CommandSender sender){
        int page = 0;
        int k = 0;
        OutlinePane pane = new OutlinePane(1, 1, 7, 4);
        if (items.size() > 28){
            pages.addPane(page, getArrowPane(page+1, ChatColor.GRAY + "NEXT", pages, false, gui));
        }
        panes.add(pane);
        for (int i = 0; i < items.size(); i++){
            pane = panes.get(panes.size()-1);
            if (k > 27){
                pane = new OutlinePane(1, 1, 7, 4);
                page++;
                pages.addPane(page, getArrowPane(page-1, ChatColor.GRAY + "BACK", pages, true, gui));
                if (i+28 < items.size()){
                    pages.addPane(page, getArrowPane(page+1, ChatColor.GRAY + "NEXT", pages, false, gui));
                }
                panes.add(pane);
                k=-1;
            }
            pane.addItem(items.get(i));
            k++;
        }
        int i = 0;
        for (OutlinePane outlinePane : panes){
            pages.addPane(i, outlinePane);
            i++;
        }
        gui.addPane(pages);
        gui = getBackground(gui, 6, background);
        gui.show((HumanEntity)(sender));
    }

    public static ChestGui getBackground(ChestGui GUI, int lines, Material bItem){
        GUI.setOnGlobalClick(event -> event.setCancelled(true));
        OutlinePane background = new OutlinePane(0, 0, 9, lines, Priority.LOWEST);
        if (bItem == null || bItem.equals(Material.BARRIER)){
            return GUI;
        }
        ItemStack item = new ItemStack(bItem);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.MAGIC + "|");
        item.setItemMeta(meta);
        background.addItem(new GuiItem(item));
        background.setRepeat(true);
        GUI.addPane(background);
        return GUI;
    }

    public static boolean getPlayerAutoSellSetting(Player player, String item){
        String uuid = player.getUniqueId().toString();
        return Main.getDataFiles().getPlayerData().getBoolean(uuid + ".autosell." + item, false);
    }
    
}
