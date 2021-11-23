package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

//  Loan time period object for storing loan value, interest-rate, player and date-of-creation

public class LoanTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable{

    private static final long serialVersionUID = -1102531408L;

    @Getter
    private double[] values,
                     interest_rates,
                     base_values;
    @Getter
    private String[] players;
    @Getter
    private int[][] time;


    public LoanTimePeriod(){
        int size = Main.getCache().getNEW_LOANS().size();
        init(size);
        int i = 0;
        for (LoanData data : Main.getCache().getNEW_LOANS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, LoanData data){
        values[pos] = data.getValue();
        interest_rates[pos] = data.getInterest_rate();
        players[pos] = data.getPlayer();
        LocalDateTime date = data.getDate();
        time[pos] = dateToIntArray(date);
        values[pos] = data.getBase_value();
    }


    private void init(int size){
        values = new double[size];
        interest_rates = new double[size];
        players = new String[size];
        time = new int[size][6];
        base_values = new double[size];
    }
    
}
