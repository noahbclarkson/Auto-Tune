package unprotesting.com.github.data.persistent.timePeriods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.GDPData;

public class GDPTimePeriod implements Serializable{

    @Getter
    private double GDP,
                   balance, 
                   debt, 
                   loss;

    @Getter
    private int playerCount;

    public GDPTimePeriod(){
        Main.getCache().getGDPDATA().updateDebt();
        Main.getCache().getGDPDATA().updateBalance();
        GDPData data = Main.getCache().getGDPDATA();
        this.GDP = data.getGDP();
        this.balance = data.getBalance();
        this.playerCount = data.getPlayerCount();
        this.debt = data.getDebt();
        this.loss = data.getLoss();
    }
    
}
