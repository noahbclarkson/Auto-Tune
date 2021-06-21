package unprotesting.com.github.events.sync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class TutorialSendEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public TutorialSendEvent(){
        if (Config.isTutorial()){
            sendTutorialMessages();
        }
    }

    private void sendTutorialMessages(){
        for (Player player : Bukkit.getOnlinePlayers()){
            if (player.hasPermission("at.tutorial") || player.isOp()){
                String uuid = player.getUniqueId().toString();
                Main.getCache().updatePlayerTutorialData(uuid);
                String message = Main.getCache().getMESSAGES().getTutorial().get(Main.getCache().getMESSAGES().getTutorialData().get(uuid)-1);
                if (Main.isPlaceholderAPI()){
                    message = PlaceholderAPI.setPlaceholders(player, message);
                }
                player.sendMessage(message);
            }
        }
    }
    
}
