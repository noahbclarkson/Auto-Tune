package unprotesting.com.github.util;

import com.earth2me.essentials.User;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.logging.Logging;

public class UtilFunctions {

    public static int calculatePlayerCount(){
        int output = 0;
        for (Player player : Bukkit.getServer().getOnlinePlayers()){
            try{
                Main.getINSTANCE();
                User user = new User(player, Main.getEss());
                if (Config.isIgnoreAFK()){
                    if (user.isAfk()){
                        Logging.debug(player.getName() + "is AFK");
                        continue;
                    }
                    if (user.isVanished()){
                        Logging.debug(player.getName() + "is Vanished");
                        continue;
                    }
                    else{
                        output++;
                        continue;
                    }
                }
            }
            catch(NoClassDefFoundError e){
                output++;
                continue;
            }
            output++;
        }
        return output;
    }
    
}
