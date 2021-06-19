package unprotesting.com.github.events;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.LoanData;

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
            output.add(loan);
        }
        Main.getCache().setLOANS(output);
    }
    
}
