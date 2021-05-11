package unprotesting.com.github.Commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Commands.Util.CommandUtil;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;

public class ShopCommand implements CommandExecutor{

    private Integer[] amounts = { 1, 2, 4, 8, 16, 32, 64 };
    private int[][] sizes = {{0,0,1,1}, {4,1,1,1}, {3,1,3,1}, {2,1,5,1}, {1,1,7,1},
     {2,2,5,3}, {2,2,5,3}, {1,2,7,3}, {1,2,7,3}};

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
        int size = Main.cache.getSECTIONS().size();
        int lines = sizes[size][3]+2;
        ChestGui gui = new ChestGui(lines, Config.getMenuTitle());
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        OutlinePane background = new OutlinePane(0, 0, 9, lines, Priority.LOWEST);
        background.addItem(new GuiItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        background.setRepeat(true);
        gui.addPane(background);
    }

    private OutlinePane loadSectionsPane(Player player){
        OutlinePane navigationPane = new OutlinePane(3, 1, 3, 1);
        for (Section section : Main.cache.getSECTIONS()){

        }
        return navigationPane;
    }


    
}
