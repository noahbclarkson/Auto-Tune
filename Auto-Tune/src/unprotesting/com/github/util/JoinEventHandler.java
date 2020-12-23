package unprotesting.com.github.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class JoinEventHandler implements Listener {

    public static Logger log = Logger.getLogger("Minecraft");
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        sendTopMoversMessages(p);
        OfflinePlayer player = (OfflinePlayer) p;
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        Main.playerDataConfig.set(uuid + ".name", name);
        Main.saveplayerdata();
        if (!Main.maxBuyMap.containsKey(player.getUniqueId())){
            ConcurrentHashMap<String, Integer> cMap = Main.loadMaxStrings(Main.map);
            Main.maxBuyMap.put(player.getUniqueId(), cMap);
        }  
        if (!Main.maxSellMap.containsKey(player.getUniqueId())){
            ConcurrentHashMap<String, Integer> cMap2 = Main.loadMaxStrings(Main.map);
            Main.maxSellMap.put(player.getUniqueId(), cMap2);
        }     
    }

    public void sendTopMoversMessages(Player player){
        if (Config.isSendPlayerTopMoversOnJoin()){
            player.sendMessage(ChatColor.GREEN + "***** | Top Buyers Today: | ******");
            for (TopMover mover : Main.topBuyers){
                player.sendMessage(ChatColor.GOLD + mover.name + ": " +  ChatColor.GREEN + " %+" + AutoTuneGUIShopUserCommand.df2.format(mover.percentage_change));
            }
            player.sendMessage(ChatColor.RED + "***** | Top Sellers Today: | ******");
            for (TopMover mover : Main.topSellers){
                player.sendMessage(ChatColor.GOLD + mover.name + ": " +  ChatColor.RED + " %" + AutoTuneGUIShopUserCommand.df2.format(mover.percentage_change));
            }
        }
    }

}
    

  