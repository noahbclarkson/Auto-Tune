package unprotesting.com.github.Commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneGDPCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String gdp, String[] args) {
        Player p = (Player) sender;
        if (command.getName().equalsIgnoreCase("gdp")){
            if (p.hasPermission("at.gdp") || p.isOp()){
                String GDP = AutoTuneGUIShopUserCommand.df2.format(Main.tempdatadata.get("GDP"));
                double returnedGDP = Double.parseDouble(GDP);
                double[] serverBalance = getServerBalance();
                double loanBalance = getLoanBalance();
                returnedGDP += serverBalance[0];
                returnedGDP -= loanBalance;
                p.sendMessage(ChatColor.GOLD + "The Current GDP is: " + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(returnedGDP));
                p.sendMessage(ChatColor.GOLD + "The Current GDP per capita is: " + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df2.format(returnedGDP/serverBalance[1]));
            }
            else if (!(p.hasPermission("at.gdp")) && !(p.isOp())){
                TextHandler.noPermssion(p);
                return true; 
            }
        }
        return true;
    }

    public double[] getServerBalance(){
        double [] output = new double[2];
        output[0] = 0.0;
        output[1] = 0.0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()){
            double x = Main.getEconomy().getBalance(player);
            output[0] += x;
            output[1] += 1;
        }
        return output;
    }

    public double getLoanBalance(){
        double output = 0.0;
        for (String str : Main.loanMap.keySet()){
            double[] arr = Main.loanMap.get(str);
            output += arr[0];
        }
        return output;
    }

}
