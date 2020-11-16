package unprotesting.com.github.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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
            UUID uuid = p.getUniqueId();
            String name = p.getName();
            Main.playerDataConfig.set(uuid + ".name", name);
            Main.saveplayerdata();
            ConcurrentHashMap<String, Integer> test = new ConcurrentHashMap<>();
            try{
            test = Main.maxBuyMap.get(p);
            }
            catch(NullPointerException ex){
                ConcurrentHashMap<String, Integer> cMap = Main.loadMaxStrings(Main.map);
                ConcurrentHashMap<String, Integer> cMap2 = Main.loadMaxStrings(Main.map);
                Main.maxBuyMap.put(p, cMap);
                Main.maxSellMap.put(p, cMap2);
            }
            if (test == null){
                ConcurrentHashMap<String, Integer> cMap = Main.loadMaxStrings(Main.map);
                ConcurrentHashMap<String, Integer> cMap2 = Main.loadMaxStrings(Main.map);
                Main.maxBuyMap.put(p, cMap);
                Main.maxSellMap.put(p, cMap2);
            }
        }
    }

  