package unprotesting.com.github;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;




public class JoinEventHandler implements Listener {

    private Main main = Main.getPlugin(Main.class);
    static Logger log = Logger.getLogger("Minecraft");
    Plugin plugin = Main.getPlugin(Main.class);
    
    @EventHandler
    public void onPlayerJoin(PlayerLoginEvent e) {
            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();
            main.playerDataConfig.set(uuid + ".name", p.getName());
            main.saveplayerdata();
        }

    }