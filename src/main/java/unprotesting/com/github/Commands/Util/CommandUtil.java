package unprotesting.com.github.Commands.Util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Logging.Logging;

public class CommandUtil {

    public static boolean checkIfSenderPlayer(CommandSender sender){
        if (!(sender instanceof Player)) {Logging.error(0);return false;}
        return true;
    }

    public static void noPermssion(Player p){p.sendMessage(Config.getNoPermission());}
    
}
