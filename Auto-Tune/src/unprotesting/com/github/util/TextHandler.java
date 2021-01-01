package unprotesting.com.github.util;

import org.bukkit.entity.Player;

import unprotesting.com.github.Main;

public class TextHandler {

    public static void noPermssion(Player p){
      p.sendMessage(Config.getNoPermission());
    }
}