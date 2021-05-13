package unprotesting.com.github.Commands;

import java.util.ArrayList;
import java.util.List;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Commands.Util.CommandUtil;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;

public class ShopCommand implements CommandExecutor{

    private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand((Player) sender, args);
    }

    private boolean interpretCommand(Player player, String[] args){
        if (!(player.hasPermission("at.shop") || player.isOp())){CommandUtil.noPermssion(player);return true;};
        if (args.length == 0){

        };
        return true;
    }

    private void loadGUI(Player player){
        int highest = Section.getHighest(Main.cache.getSECTIONS());
        int lines = highest/9;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        OutlinePane background = new OutlinePane(0, 0, 9, lines, Priority.LOWEST);
        background.addItem(new GuiItem(new ItemStack(Material.matchMaterial(Config.getBackground()))));
        background.setRepeat(true);
        gui.addPane(background);
    }

    private OutlinePane loadSectionsPane(Player player, int lines){
        OutlinePane navigationPane = new OutlinePane(0, 0, 9, lines);
        for (Section section : Main.cache.getSECTIONS()){
            
        }
        return navigationPane;
    }

    private void loadShopPane(int lines){
        PaginatedPane pages = new PaginatedPane(0, 0, 9, lines);
    }

    private List<GuiItem> getListFromSection(Section section){
        List<GuiItem> output = new ArrayList<GuiItem>();
        for (String s_item : section.getItems()){
            ItemStack item = new ItemStack(Material.matchMaterial(s_item));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + s_item);
        }
        return null;
    }

}
