package unprotesting.com.github.Data.Persistent.TimePeriods;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Ephemeral.Data.LoanData;
import unprotesting.com.github.Data.Util.LocalDateTimeArrayUtilizer;

//  Loan time period object for storing loan value, intrest-rate, player and date-of-creation

public class LoanTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable{

    @Getter
    private double[] values, intrest_rates;
    @Getter
    private String[] players;
    @Getter
    private int[][] time;


    public LoanTimePeriod(){
        int size = Main.cache.getLOANS().size();
        init(size);
        this.values = new double[size];
        this.intrest_rates = new double[size];
        this.time = new int[size][6];
        this.players = new String[size];
        int i = 0;
        for (LoanData data : Main.cache.getLOANS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, LoanData data){
        this.values[pos] = data.getValue();
        this.intrest_rates[pos] = data.getIntrest_rate();
        this.players[pos] = data.getPlayer().getUniqueId().toString();
        LocalDateTime date = data.getDate();
        this.time[pos] = dateToIntArray(date);
    }


    private void init(int size){
        this.values = new double[size];
        this.intrest_rates = new double[size];
        this.players = new String[size];
        this.time = new int[size][6];
    }
    
}
