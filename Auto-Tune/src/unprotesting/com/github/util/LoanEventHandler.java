package unprotesting.com.github.util;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneLoanCommand;
import unprotesting.com.github.Commands.AutoTunePaybackLoanCommand;

public class LoanEventHandler implements Runnable{

    @Override
    public void run() {
        Set<String> set = Main.loanMap.getKeys();
        for (String str : set){
                double[] arr = Main.loanMap.get(str);
                double curPrice = arr[0];
                double inflationRate = arr[1];
                double newPrice = curPrice + curPrice * inflationRate * 0.01;
                arr[0] = newPrice;
                Main.loanMap.put(str, arr);
            }
        Set<String> set2 = Main.playerDataConfig.getKeys(false);
        for (String str2 : set2){
            UUID id = UUID.fromString(str2);
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            double totalLoanVal = AutoTuneLoanCommand.getTotalLoanValue(p);
            if ((Main.getEconomy().getBalance(p) - totalLoanVal) < Config.getMaxDebt()){
                for (String str : set){
                    if (str.contains(id.toString())){
                        AutoTunePaybackLoanCommand.paybackLoan(p, str.substring(str.length()-1));
                    }
                }
            }
        }
        }
    }
    
