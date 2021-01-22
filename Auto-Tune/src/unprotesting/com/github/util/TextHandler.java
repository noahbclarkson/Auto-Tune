package unprotesting.com.github.util;

import org.bukkit.entity.Player;

public class TextHandler {

    public static void noPermssion(Player p){
      p.sendMessage(Config.getNoPermission());
    }
}