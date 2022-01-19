package unprotesting.com.github.events.async;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.economy.EconomyFunctions;

public class LoanUpdateEvent extends Event {

    @Getter
    private final HandlerList Handlers = new HandlerList();
    private final Main main;

    public LoanUpdateEvent(Main main, boolean isAsync){
        super(isAsync);
        this.main = main;
        updateLoans();
    }

    private void updateLoans(){
        List<LoanData> output = new ArrayList<>();
        for (LoanData loan : Main.getCache().getLOANS()){
            loan.setValue(loan.getValue() + loan.getValue()*0.01*loan.getInterest_rate());
            OfflinePlayer offlinePlayer = main.getServer().getOfflinePlayer(UUID.fromString(loan.getPlayer()));
            double balance = EconomyFunctions.getEconomy().getBalance(offlinePlayer);
            if (balance - loan.getValue() < Config.getMaxDebt()){
                loan.payBackLoan();
            }
            output.add(loan);
        }
        Main.getCache().setLOANS(output);
    }
    
}