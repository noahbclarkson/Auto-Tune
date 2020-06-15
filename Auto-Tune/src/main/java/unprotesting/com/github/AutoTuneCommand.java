package unprotesting.com.github;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;


public class AutoTuneCommand implements CommandExecutor{
    
    @Override
    public boolean onCommand(CommandSender sender, Command testcmd, String autotune, String[] help) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Welcome to Auto-Tune, " + player.getDisplayName() + "!");
            player.sendMessage(ChatColor.ITALIC + "Do /trade to begin");
            
            return true;
        }
        return false;
    }   


}