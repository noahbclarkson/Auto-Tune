package unprotesting.com.github.Commands;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneCommand implements CommandExecutor {

    static Logger log = Logger.getLogger("Minecraft");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String at, String[] args) {
        if (command.getName().equalsIgnoreCase("at")){
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            if (args[0].equalsIgnoreCase("login")) {
                if (player.hasPermission("at.login") || player.isOp()){
                    String AutoTunePlayerID = UUID.randomUUID().toString();
                    String LoggedIn = Main.playerDataConfig.getString(uuid + ".autotuneID");
                    if (LoggedIn == null){
                        player.sendMessage(ChatColor.YELLOW + "No Auto-Tune Account found in Config");
                        Main.playerDataConfig.set(uuid + ".autotuneID", AutoTunePlayerID);
                        Main.saveplayerdata();
                        player.sendMessage(ChatColor.YELLOW + "Creating one for you..");
                        player.sendMessage(ChatColor.YELLOW + "Created Auto-Tune Account with Unique ID: " + AutoTunePlayerID);}

                    else if (LoggedIn != null){
                    player.sendMessage(ChatColor.YELLOW + "Already Logged in!");
                    player.sendMessage(ChatColor.YELLOW + "Your unique ID is " + Main.playerDataConfig
                            .getString(uuid + ".autotuneID"));
                }
                            }
                else if (!(player.hasPermission("at.login")) && !(player.isOp())){
                    TextHandler.noPermssion(player);
                }
                        }
                else {
                    if (player.hasPermission("at.help") || player.isOp()){
                        player.sendMessage(ChatColor.YELLOW + "\"/at\" command usage:");
                        player.sendMessage(ChatColor.YELLOW + "/at login | Login to trading plaform");
                        player.sendMessage(ChatColor.YELLOW + "/at Register | Register to trading plaform");
                    }
                    else if (!(player.hasPermission("at.help")) && !(player.isOp())){
                        TextHandler.noPermssion(player);
                    }
                } 
            }
                
        return true;
                    
    }
}

 


