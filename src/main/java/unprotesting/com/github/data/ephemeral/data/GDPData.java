package unprotesting.com.github.data.ephemeral.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.economy.EconomyFunctions;

public class GDPData {

    @Getter @Setter
    private double GDP,
                   balance,
                   debt, 
                   loss,
                   inflation;
    @Getter
    private int playerCount;

    public GDPData(double GDP, double balance, double loss, double debt, double inflation, int playerCount){
        this.GDP = GDP;
        this.loss = loss;
        this.balance = balance;
        this.playerCount = playerCount;
        this.debt = debt;
        this.inflation = inflation;
    }

    public void increaseGDP(double d){
        this.GDP += d;
    }

    public void increaseLoss(double d){
        this.loss += d;
    }

    public void updateBalance(){
        double server_balance = 0;
        int server_player_count = 0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()){
            if (player == null){
                continue;
            }
            try{
                double bal = EconomyFunctions.getEconomy().getBalance(player);
                server_balance += bal;
            }
            catch(RuntimeException e){}
            server_player_count++;
        }
        this.balance = server_balance;
        this.playerCount = server_player_count;
    }

    public void updateDebt(){
        double server_debt = 0;
        for (LoanData data : Main.getCache().getLOANS()){
            server_debt += data.getValue();
        }
        this.debt = server_debt;
    }

    public void updateInflation(){
        double inflation_total = 0.0;
        int i = 0;
        for (String str : Main.getCache().getPERCENTAGE_CHANGES().keySet()){
            inflation_total += Main.getCache().getPERCENTAGE_CHANGES().get(str);
            i++;
        }
        this.inflation = (inflation_total/i);
    }
    
}
