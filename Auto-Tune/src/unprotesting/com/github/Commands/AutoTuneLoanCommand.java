package unprotesting.com.github.Commands;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.MathHandler;
import unprotesting.com.github.util.TextHandler;
import net.md_5.bungee.api.ChatColor;

public class AutoTuneLoanCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String loan, String[] args) {
        if (command.getName().equalsIgnoreCase("loan")){
            Player p = (Player) sender;
            if (p.hasPermission("at.loan") || p.isOp()){
                if (args[0] == null){
                    return false;
                }
                else{
                    try {
                        double amount = Double.parseDouble(args[0]);
                        UUID uuid = p.getUniqueId();
                        Set<String> set = Main.loanMap.getKeys();
                        boolean loanPresent = false;
                        List<String> strArray = new ArrayList<String>();
                        for (String str : set){
                            if (str.contains(uuid.toString())){
                                loanPresent = true;
                                strArray.add(str);
                                continue;
                            }
                            else {
                                continue;
                            }
                        }
                        if (!loanPresent){
                            addLoanToDB(p, uuid.toString(), 1, amount);
                        }
                        if (loanPresent && strArray != null){
                            Collections.sort(strArray);
                            boolean loaded = false;
                            int x = 0;
                            int[] lastChars = new int[strArray.size()];
                            int a = 0;
                            for (String str : strArray){
                                String lastChar = str.substring(str.length() - 1);
                                x = Integer.parseInt(lastChar);
                                lastChars[a] = x;
                            }
                            int missingno = MathHandler.getMissingNo(lastChars, strArray.size());
                            if (!(missingno == 0)){
                                addLoanToDB(p, uuid.toString(), missingno, amount);
                            }
                            if (loaded = false){
                                addLoanToDB(p, uuid.toString(), x+1, amount);
                            }
                            return true;
                        }
                    }
                    catch (NumberFormatException e){
                        return false;
                    }
                }
            }
            else if(!(p.hasPermission("at.loan")) && !(p.isOp())){
                TextHandler.noPermssion(p);
            }
        }
        return true;
    }

    public void addLoanToDB(Player p, String uuid, int number, double amount){
        double balance = Main.getEconomy().getBalance(p);
        if ((balance - amount) > (Config.getMaxDebt() - (amount*Config.getInflationValue()*0.01) - getTotalLoanValue(p))){
            String integerString = String.valueOf(number);
            long millis = Instant.now().toEpochMilli();
            Double time  = Long.valueOf(millis).doubleValue();
            double[] input = {amount, Config.getIntrestRate(), amount, time};
            double updateperiod = Config.getIntrestRateUpdateRate()/1200;
            p.sendMessage(ChatColor.GOLD + "Created loan of " + Config.getCurrencySymbol() + amount + ". The current intrest rate is " + Config.getIntrestRate().toString() + "% per " + updateperiod + " min");
            Main.loanMap.put(uuid+integerString, input);
            Main.getEconomy().depositPlayer(p, amount);
        }
        else{
            p.sendMessage(ChatColor.DARK_RED + "The maximum debt value is " + ChatColor.RED + Config.getCurrencySymbol() + Config.getMaxDebt() + ChatColor.DARK_RED + ". Your balance is " + ChatColor.RED + Config.getCurrencySymbol() + balance + ChatColor.DARK_RED + ". You can not take out a loan of "+ ChatColor.RED + Config.getCurrencySymbol() + amount);
        }
    }

    public static double getTotalLoanValue(OfflinePlayer p){
        Set<String> set = Main.loanMap.getKeys();
        double totalvalue = 0;
        for (String str : set){
            if (str.contains(p.getUniqueId().toString())){
                double[] arr = Main.loanMap.get(str);
                totalvalue += arr[0];
            }
        }
        return totalvalue;
    }
    
}