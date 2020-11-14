package unprotesting.com.github.Commands;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.InflationEventHandler;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneCommand implements CommandExecutor {

    public static Logger log = Logger.getLogger("Minecraft");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String at, String[] args) {
        if (args[0] == null){
            return false;
        }
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
                        player.sendMessage(ChatColor.YELLOW + "Created Auto-Tune Account with Unique ID: " + AutoTunePlayerID);return true;}

                    else if (LoggedIn != null){
                    player.sendMessage(ChatColor.YELLOW + "Already Logged in!");
                    player.sendMessage(ChatColor.YELLOW + "Your unique ID is " + Main.playerDataConfig
                            .getString(uuid + ".autotuneID"));
                            return true;
                }
                            }
                else if (!(player.hasPermission("at.login")) && !(player.isOp())){
                    TextHandler.noPermssion(player);
                    return true;
                }

                else {
                    if (player.hasPermission("at.help") || player.isOp()){
                        player.sendMessage(ChatColor.YELLOW + "\"/at\" command usage:");
                        player.sendMessage(ChatColor.YELLOW + "- /at login | Login to trading plaform");
                        player.sendMessage(ChatColor.YELLOW + "- /at Register | Register to trading plaform");
                        player.sendMessage(ChatColor.YELLOW + "- /at Increase <Item-Name> <Value | %Value> | Increase Item Price");
                        player.sendMessage(ChatColor.YELLOW + "- /at Decrease <Item-Name> <Value | %Value> | Decrease Item Price");
                        return true;
                    }
                    else if (!(player.hasPermission("at.help")) && !(player.isOp())){
                        TextHandler.noPermssion(player);
                        return true;
                    }
                } 
                
            }
            if (args[0].equalsIgnoreCase("increase")) {
                if (player.hasPermission("at.increase") || player.isOp()){
                    Double value = Double.valueOf(args[2]);
                    if (!(value == 0 || value == 0.0 || value == 0f || value > 99999999 || args[1] == null || args[2] == null)){
                        if (args[1].contains("%")){
                            args[1].replace("%", "");
                            Double newPrice = InflationEventHandler.increaseItemPrice(args[1].toUpperCase(), value, true);
                            player.sendMessage(ChatColor.YELLOW + "Increased " + args[1].toUpperCase() +  " price to " + ChatColor.GREEN + Config.getCurrencySymbol() + newPrice);
                            return true;
                        }
                        else {
                            Double newPrice = InflationEventHandler.increaseItemPrice(args[1].toUpperCase(), value, true);
                            player.sendMessage(ChatColor.YELLOW + "Increased " + args[1].toUpperCase() +  " price to " + ChatColor.GREEN + Config.getCurrencySymbol() + newPrice);
                            return true;
                        }
                    }
                }
                else if (!(player.hasPermission("at.increase")) && !(player.isOp())){
                    TextHandler.noPermssion(player);
                    return true;
                }
            }
            if (args[0].equalsIgnoreCase("decrease")) {
                if (player.hasPermission("at.decrease") || player.isOp()){
                    Double value = Double.valueOf(args[2]);
                    if (!(value == 0 || value == 0.0 || value == 0f || value > 99999999 || args[1] == null || args[2] == null)){
                        if (args[1].contains("%")){
                            args[1].replace("%", "");
                            Double newPrice = InflationEventHandler.decreaseItemPrice(args[1].toUpperCase(), value, true);
                            player.sendMessage(ChatColor.YELLOW + "Decreased " + args[1].toUpperCase() +  " price to " + ChatColor.GREEN + Config.getCurrencySymbol() + newPrice);
                            return true;
                        }
                        else {
                            Double newPrice = InflationEventHandler.decreaseItemPrice(args[1].toUpperCase(), value, true);
                            player.sendMessage(ChatColor.YELLOW + "Decreased " + args[1].toUpperCase() +  " price to " + ChatColor.GREEN + Config.getCurrencySymbol() + newPrice);
                            return true;
                        }
                    }
                }
                else if (!(player.hasPermission("at.decrease")) && !(player.isOp())){
                    TextHandler.noPermssion(player);
                    return true;
                }
            }
            else{
                if (player.hasPermission("at.help") || player.isOp()){
                    player.sendMessage(ChatColor.YELLOW + "\"/at\" command usage:");
                    player.sendMessage(ChatColor.YELLOW + "- /at login | Login to trading plaform");
                    player.sendMessage(ChatColor.YELLOW + "- /at Register | Register to trading plaform");
                    player.sendMessage(ChatColor.YELLOW + "- /at Increase <Item-Name> <Value | %Value> | Increase Item Price");
                    player.sendMessage(ChatColor.YELLOW + "- /at Decrease <Item-Name> <Value | %Value> | Decrease Item Price");
                    return true;
                }
                else if (!(player.hasPermission("at.help")) && !(player.isOp())){
                    TextHandler.noPermssion(player);
                    return true;
                }
                return false;
            }
            return false;
        }
        return false;
    }
}

 


