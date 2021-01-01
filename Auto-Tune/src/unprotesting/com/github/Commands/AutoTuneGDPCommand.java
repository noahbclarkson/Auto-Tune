package unprotesting.com.github.Commands;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Loan;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneGDPCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String gdp, String[] args) {
        Player p = (Player) sender;
        if (command.getName().equalsIgnoreCase("gdp")){
            if (args.length < 1){
                if (p.hasPermission("at.gdp") || p.isOp()){
                    String GDP = AutoTuneGUIShopUserCommand.df2.format(Main.tempdatadata.get("GDP"));
                    double[] serverBalance = getServerBalance();
                    double loanBalance = getLoanBalance();
                    p.sendMessage(ChatColor.GOLD + "The Current GDP is: $" + ChatColor.GREEN + GDP);
                    p.sendMessage(ChatColor.GOLD + "The Current GDP per capita is: $" + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(Main.tempdatadata.get("GDP")/serverBalance[1]));
                    p.sendMessage(ChatColor.GOLD + "The Current Average Balance is: $" + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(serverBalance[0]/serverBalance[1]));
                    p.sendMessage(ChatColor.GOLD + "The Current Average Debt is: $" + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(loanBalance/serverBalance[1]));
                }
                else if (!(p.hasPermission("at.gdp")) && !(p.isOp())){
                    TextHandler.noPermssion(p);
                    return true; 
                }
            }
            if (args.length == 1){
                if(args[0] == "reset"){
                    if (p.hasPermission("at.gdp.reset") || p.isOp()){
                        Main.tempdatadata.put("GDP", 0.0);
                    }
                    else if (!(p.hasPermission("at.gdp")) && !(p.isOp())){
                        TextHandler.noPermssion(p);
                        return true; 
                    }
                }
            }
        }
        return true;
    }

    public double[] getServerBalance(){
        double [] output = new double[2];
        output[0] = 0.0;
        output[1] = 0.0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()){
            if (player == null){
                continue;
            }
            double x = Main.getEconomy().getBalance(player);
            output[0] += x;
            output[1] += 1;
        }
        return output;
    }

    public double getLoanBalance(){
        double output = 0.0;
        for (UUID uuid : Main.loanMap.keySet()){
            ArrayList<Loan> map = Main.loanMap.get(uuid);
            for (Loan loan : map){
                output += loan.current_value;
            }

        }
        return output;
    }

}
