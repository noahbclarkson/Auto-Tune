package unprotesting.com.github.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import unprotesting.com.github.Commands.AutoTuneAutoTuneConfigCommand;

public class ChatHandler implements Listener{

    public static String message;

    @EventHandler
    public void getChatMessage(AsyncPlayerChatEvent e){
        if (AutoTuneAutoTuneConfigCommand.pList.contains(e.getPlayer())){
            Player p = e.getPlayer();
            e.setCancelled(true);
            String msg = e.getMessage();
            if (msg.contains("cancel")){
                p.sendMessage(ChatColor.RED + "Cancled");
                AutoTuneAutoTuneConfigCommand.pList.remove(p);
                message = null;
            }
            else {
                p.sendMessage(ChatColor.GRAY + "Changing setting to " + msg);
                message = msg;
            }
        }
    }
}
