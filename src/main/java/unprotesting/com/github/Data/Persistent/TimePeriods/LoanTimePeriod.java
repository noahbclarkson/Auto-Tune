package unprotesting.com.github.data.persistent.timePeriods;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

//  Loan time period object for storing loan value, interest-rate, player and date-of-creation

public class LoanTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable{

    @Getter
    private double[] values,
                     interest_rates;
    @Getter
    private String[] players;
    @Getter
    private int[][] time;


    public LoanTimePeriod(){
        int size = Main.getCache().getNEW_LOANS().size();
        init(size);
        this.values = new double[size];
        this.interest_rates = new double[size];
        this.time = new int[size][6];
        this.players = new String[size];
        int i = 0;
        for (LoanData data : Main.getCache().getNEW_LOANS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, LoanData data){
        this.values[pos] = data.getValue();
        this.interest_rates[pos] = data.getInterest_rate();
        this.players[pos] = data.getPlayer();
        LocalDateTime date = data.getDate();
        this.time[pos] = dateToIntArray(date);
    }


    private void init(int size){
        this.values = new double[size];
        this.interest_rates = new double[size];
        this.players = new String[size];
        this.time = new int[size][6];
    }
    
}
