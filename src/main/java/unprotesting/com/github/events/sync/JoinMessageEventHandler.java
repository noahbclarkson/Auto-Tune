package unprotesting.com.github.events.sync;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.clip.placeholderapi.PlaceholderAPI;
import unprotesting.com.github.Main;

public class JoinMessageEventHandler implements Listener{

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        if (Main.getMESSAGES().getOnJoin() != null){
            if (Main.getMESSAGES().getOnJoin().size() > 0){
                for (String message : Main.getMESSAGES().getOnJoin()){
                    Player player = e.getPlayer();
                    if (Main.isPlaceholderAPI()){
                        message = PlaceholderAPI.setPlaceholders(player, message);
                    }
                    player.sendMessage(message);
                }
            }
        }
    }

    
}
