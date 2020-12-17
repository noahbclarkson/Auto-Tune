package unprotesting.com.github.util;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class Loan implements Serializable{
    
    private static final long serialVersionUID = 5728658725837419843L;
    public double interest_rate;
    public double value;
    public double current_value;
    public double interest_rate_update;
    public Instant instant;
    public boolean compound_enabled;

    public Loan(double value, double interest_rate, double interest_rate_update, Instant instant, Player player, boolean compound_enabled){
        this.interest_rate = interest_rate*0.01;
        this.value = value;
        this.current_value = value;
        this.interest_rate_update = interest_rate_update;
        this.instant = instant;
        this.compound_enabled = compound_enabled;
        double playerBalance = Main.getEconomy().getBalance(player);
        double loanValue = LoanEventHandler.getPlayerLoanValue(player);
        if (playerBalance-loanValue > Config.getMaxDebt()){
            inputLoanToMap(this, player);
        }
        else{
            player.sendMessage(ChatColor.RED + "You cannot take out a loan of this size!");
            double moneyLeft = Config.getMaxDebt()-(playerBalance-loanValue);
            if (moneyLeft < 0){
                moneyLeft = moneyLeft * -1;
            }
            player.sendMessage(ChatColor.YELLOW + "You can take out " + ChatColor.GREEN + Config.getCurrencySymbol() + moneyLeft);
        }
    }

    public void inputLoanToMap(Loan loan, Player player){
        UUID uuid = player.getUniqueId();
        ArrayList<Loan> mainLoans = Main.loanMap.get(uuid);
        Main.getEconomy().depositPlayer(player, loan.value);
        player.sendMessage(ChatColor.YELLOW + "New Loan Created! This is your " + ChatColor.GREEN +  MathHandler.ordinal(mainLoans.size()+1) + " loan");
        player.sendMessage(ChatColor.YELLOW + "Loan Value: " + ChatColor.GREEN + Config.getCurrencySymbol() + AutoTuneGUIShopUserCommand.df6.format(loan.value));
        player.sendMessage(ChatColor.YELLOW + "Interest Rate: " + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df6.format(loan.interest_rate) + ChatColor.YELLOW + "% per " + ChatColor.GREEN + (loan.interest_rate_update/60) + "min");
        if (!loan.compound_enabled){
            player.sendMessage(ChatColor.YELLOW + "Compound Interest is" + ChatColor.RED + " disabled" + ChatColor.YELLOW + " for this loan");
        }
        else{
            player.sendMessage(ChatColor.YELLOW + "Compound Interest is" + ChatColor.GREEN + " enabled" + ChatColor.YELLOW + " for this loan");
        }
        mainLoans.add(loan);
        Main.loanMap.put(uuid, mainLoans);
    }

}
