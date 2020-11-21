package unprotesting.com.github.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import unprotesting.com.github.Main;

public class JoinEventHandler implements Listener {

    public static Logger log = Logger.getLogger("Minecraft");
    
    @EventHandler
    public void onPlayerJoin(PlayerLoginEvent e) {
        Player p = e.getPlayer();
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
}
    

  