package unprotesting.com.github.Commands;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;

public class AutoTuneGUIShopUserCommand implements CommandExecutor{


    @Override
	public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")){
		    if (!(sender instanceof Player)) {
            Main.sendMessage(sender, "&cPlayers only.");
            return true;
            }
                Integer menuRows = Config.getMenuRows();
                Gui gui = new Gui(menuRows, Config.getMenuTitle());
                Integer size = Main.getMaterialListSize();
                Set<String> tempCollection = Main.getTempCollection();
                String[] tempCollectionStringArray = Main.convert(tempCollection);
                PaginatedPane pane = new PaginatedPane(0, 0, 9, menuRows);
                Integer paneSize = (menuRows-2)*7;

                // page one
                    OutlinePane pageOne = new OutlinePane(1, 1, 7, menuRows - 2);
                    for(int i = paneSize; i>0; i--){
                        for (int x = 0; x<paneSize; x++){
                            pageOne.addItem(new GuiItem(new ItemStack(Material.matchMaterial(
                                    tempCollectionStringArray[x])), event -> event.getWhoClicked().sendMessage("Bone")));
                            Main.debugLog("Loaded Item: " + tempCollectionStringArray[x]);
                    }
                    pane.addPane(0, pageOne);
                }



                // page two
                if (size > menuRows*7){
                    OutlinePane pageTwo = new OutlinePane(1, 1, 7, menuRows-2);
                    pageTwo.addItem(new GuiItem(new ItemStack(Material.GLASS), event -> event.getWhoClicked().sendMessage("Glass")));
                    pane.addPane(1, pageTwo);
                }

                // page three
                if (size > menuRows*14){
                    OutlinePane pageThree = new OutlinePane(1, 1, 7, menuRows-2);
                    pageThree.addItem(new GuiItem(new ItemStack(Material.BLAZE_ROD),event -> event.getWhoClicked().sendMessage("Blaze rod")));
                    pane.addPane(2, pageThree);
                }

                gui.addPane(pane);

                // page selection
                StaticPane back = new StaticPane(2, 5, 1, 1);
                StaticPane forward = new StaticPane(6, 5, 1, 1);

                back.addItem(new GuiItem(new ItemStack(Material.ARROW), event -> {
                    pane.setPage(pane.getPage() - 1);

                    if (pane.getPage() == 0) {
                        back.setVisible(false);
                    }

                    forward.setVisible(true);
                    gui.update();
                }), 0, 0);

                back.setVisible(false);

                forward.addItem(new GuiItem(new ItemStack(Material.ARROW), event -> {
                    pane.setPage(pane.getPage() + 1);

                    if (pane.getPage() == pane.getPages() - 1) {
                        forward.setVisible(false);
                    }

                    back.setVisible(true);
                    gui.update();
                }), 0, 0);

                gui.addPane(back);
                gui.addPane(forward);

                gui.show((HumanEntity) sender);

            return true;
            }
        return true;
    }
    
}