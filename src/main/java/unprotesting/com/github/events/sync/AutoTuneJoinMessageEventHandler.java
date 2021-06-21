package unprotesting.com.github.events.sync;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.clip.placeholderapi.PlaceholderAPI;
import unprotesting.com.github.Main;

public class AutoTuneJoinMessageEventHandler implements Listener{

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        for (String message : Main.getCache().getMESSAGES().getOnJoin()){
            Player player = e.getPlayer();
            if (Main.isPlaceholderAPI()){
                message = PlaceholderAPI.setPlaceholders(player, message);
            }
            player.sendMessage(message);
        }
    }

    
}
