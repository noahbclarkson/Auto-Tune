package unprotesting.com.github.Data.Persistent.TimePeriods;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Data.Ephemeral.Data.GDPData;

public class GDPTimePeriod {

    @Getter
    private double GDP, balance, debt, loss;

    @Getter
    private int playerCount;

    public GDPTimePeriod(){
        GDPData data = Main.getCache().getGDPDATA();
        this.GDP = data.getGDP();
        this.balance = data.getBalance();
        this.playerCount = data.getPlayerCount();
        this.debt = data.getDebt();
        this.loss = data.getLoss();
    }
    
}
