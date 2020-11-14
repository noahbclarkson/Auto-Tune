package unprotesting.com.github.Commands;

import java.util.Set;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTunePaybackLoanCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String payloan, String[] args) {
        if (sender instanceof Player && command.getName().equalsIgnoreCase("payloan")){
            if (args[0] == null || args[0].equals(" ") || args[0].equals("")) {
                return false;
            }
            Player p = (Player) sender;
            if (p.hasPermission("at.loan") || p.isOp()){
                paybackLoan(p, args[0]);
                return true;
            }
            else if (!(p.hasPermission("at.loan")) && !(p.isOp())){
                TextHandler.noPermssion(p);
                return true;
            }
        }
        return false;
    }

    public static void paybackLoan(OfflinePlayer p, String args){
        UUID uuid = p.getUniqueId();
        Set<String> set = Main.loanMap.getKeys();
        boolean loaded = false;
        for (String str : set){
            if (str.contains(uuid.toString())){
                String lastChar = str.substring(str.length() - 1);
                if (lastChar.equals(args)){
                    double[] arr = Main.loanMap.get(uuid.toString() + args);
                    double curVal = arr[0];
                    curVal = Double.parseDouble(AutoTuneGUIShopUserCommand.df4.format(curVal));
                    Main.loanMap.remove(uuid.toString() + args);
                    if (p.isOnline()){
                        Player player = (Player)p;
                        player.sendMessage(ChatColor.YELLOW + "Removed loan No: "+ ChatColor.GREEN + args + ChatColor.YELLOW + ". Withdrew " + ChatColor.GREEN + Config.getCurrencySymbol() + AutoTuneGUIShopUserCommand.df2.format(curVal) + ChatColor.YELLOW + " from your balance.");
                        Main.getEconomy().withdrawPlayer(p, (Math.round(curVal*10000)/10000));
                    }
                    else if (!(p.isOnline())){
                        Main.log("Removed loan No: "+ args + ". Withdrew " + Config.getCurrencySymbol() + AutoTuneGUIShopUserCommand.df2.format(curVal) + " from " + p.getName() +"'s balance.");
                        Main.getEconomy().withdrawPlayer(p, (Math.round(curVal*10000)/10000));
                    }
                    loaded = true;
                }
            }
        }
        if (!loaded){
            if (p.isOnline()){
                Player player = (Player)p;
                player.sendMessage(ChatColor.RED + "Loan is not present, do /loans to view your current loans or /loan to make one");
            }
        }
    }
}