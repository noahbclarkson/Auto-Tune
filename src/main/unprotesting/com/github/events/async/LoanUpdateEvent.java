package unprotesting.com.github.events.async;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.economy.EconomyFunctions;

public class LoanUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public LoanUpdateEvent(boolean isAsync){
        super(isAsync);
        updateLoans();
    }

    private void updateLoans(){
        List<LoanData> output = new ArrayList<LoanData>();
        for (LoanData loan : Main.getCache().getLOANS()){
            loan.setValue(loan.getValue() + loan.getValue()*0.01*loan.getInterest_rate());
            double balance = EconomyFunctions.getEconomy().getBalance(Bukkit.getPlayer(UUID.fromString(loan.getPlayer())));
            if ((balance-loan.getValue()) < Config.getMaxDebt()){
                loan.payBackLoan();
            }
            output.add(loan);
        }
        Main.getCache().setLOANS(output);
    }
    
}
