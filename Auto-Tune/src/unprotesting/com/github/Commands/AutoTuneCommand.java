package unprotesting.com.github.Commands;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;

public class AutoTuneCommand implements CommandExecutor {

    private Main main = Main.getPlugin(Main.class);
    static Logger log = Logger.getLogger("Minecraft");
    Plugin plugin = Main.getPlugin(Main.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String at, String[] args) {
        if (command.getName().equalsIgnoreCase("at")){
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            String playername = player.getDisplayName();
            if (args[0].equalsIgnoreCase("login")) {
                String AutoTunePlayerID = UUID.randomUUID().toString();
                String LoggedIn = main.playerDataConfig.getString(uuid + ".autotuneID");
                if (LoggedIn == null){
                    player.sendMessage(ChatColor.YELLOW + "No Auto-Tune Account found in Config");
                    main.playerDataConfig.set(uuid + ".autotuneID", AutoTunePlayerID);
                    main.saveplayerdata();
                    player.sendMessage(ChatColor.YELLOW + "Creating one for you..");
                    player.sendMessage(ChatColor.YELLOW + "Created Auto-Tune Account with Unique ID: " + AutoTunePlayerID);}

                        else if (LoggedIn != null){
                        player.sendMessage(ChatColor.YELLOW + "Already Logged in!");
                        player.sendMessage(ChatColor.YELLOW + "Your unique ID is " + main.playerDataConfig.getString(uuid + ".autotuneID"));}}

                else {
                player.sendMessage(ChatColor.YELLOW + "\"/at\" command usage:");
                player.sendMessage(ChatColor.YELLOW + "/at login | Login to trading plaform");
                player.sendMessage(ChatColor.YELLOW + "/at Register | Register to trading plaform");
                } 
            }
                
        return true;
                    
    }
}

 


