package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.GDPData;

public class GDPTimePeriod implements Serializable{

    private static final long serialVersionUID = -1102531406L;

    @Getter
    private double GDP,
                   balance, 
                   debt, 
                   loss,
                   inflation;

    @Getter
    private int playerCount;

    public GDPTimePeriod(){
        Main.getCache().getGDP_DATA().updateDebt();
        Main.getCache().getGDP_DATA().updateBalance();
        Main.getCache().getGDP_DATA().updateInflation();
        GDPData data = Main.getCache().getGDP_DATA();
        this.GDP = data.getGDP();
        this.balance = data.getBalance();
        this.playerCount = data.getPlayerCount();
        this.debt = data.getDebt();
        this.loss = data.getLoss();
        this.inflation = data.getInflation();
    }
    
}
