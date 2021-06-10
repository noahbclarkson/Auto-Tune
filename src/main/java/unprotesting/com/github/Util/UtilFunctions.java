package unprotesting.com.github.Util;

import com.earth2me.essentials.User;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;

public class UtilFunctions {

    public static int calculatePlayerCount(){
        int output = 0;
        for (Player player : Bukkit.getServer().getOnlinePlayers()){
            try{
                Main.getINSTANCE();
                User user = new User(player, Main.getEss());
                if (Config.isIgnoreAFK()){
                    if (user.isAfk()){
                        continue;
                    }
                    if (user.isVanished()){
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
