package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

//  Transaction time period object for storing transaction price, player, buy/sell and creation-date

public class TransactionsTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable {

    private static final long serialVersionUID = -1102531409L;

    @Getter
    private double[] prices;
    @Getter
    private int[] amounts;
    @Getter
    private int[][] time;
    @Getter
    private String[] players,
                     items, 
                     positions;

    public TransactionsTimePeriod(){
        int size = Main.getCache().getNEW_TRANSACTIONS().size();
        init(size);
        this.prices = new double[size];
        this.amounts = new int[size];
        this.items = new String[size];
        this.players = new String[size];
        this.positions = new String[size];
        this.time = new int[size][6];
        int i = 0;
        for (TransactionData data : Main.getCache().getNEW_TRANSACTIONS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, TransactionData data){
        this.prices[pos] = data.getPrice();
        this.amounts[pos] = data.getAmount();
        this.players[pos] = data.getPlayer();
        this.items[pos] = data.getItem();
        LocalDateTime date = data.getDate();
        this.time[pos] = dateToIntArray(date);
        this.positions[pos] = data.getPosition().toString();
    }

    private void init(int size){
        this.prices = new double[size];
        this.players = new String[size];
        this.items = new String[size];
        this.time = new int[size][6];
        this.amounts = new int[size];
    }
    
}
