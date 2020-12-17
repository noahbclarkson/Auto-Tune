package unprotesting.com.github.util;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import unprotesting.com.github.Main;

public class InventoryHandler implements Listener{

    public static ArrayList<Player> lockedPlayerList = new ArrayList<Player>();

    @EventHandler
    public void onInvClick(InventoryClickEvent e){
        Player player = (Player) e.getWhoClicked();
        Inventory inv = e.getClickedInventory();
        if (e.getView().getTitle().equals(Config.getMenuTitle()) && inv != null){
            if (inv.getType().equals(InventoryType.PLAYER)){
                if (lockedPlayerList.contains(player)){
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onShopOpen(InventoryOpenEvent e){
        String title = e.getView().getTitle();
        if (title.equals(Config.getMenuTitle())){
            lockedPlayerList.add((Player)e.getPlayer());
        }
    }
    
    @EventHandler
    public void onShopClose(InventoryCloseEvent e){
        String title = e.getView().getTitle();
        if (title.equals(Config.getMenuTitle())){
            lockedPlayerList.remove((Player)e.getPlayer());
        }
    }

}
 