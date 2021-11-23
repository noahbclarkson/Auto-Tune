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
        int i = 0;
        for (TransactionData data : Main.getCache().getNEW_TRANSACTIONS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, TransactionData data){
        prices[pos] = data.getPrice();
        amounts[pos] = data.getAmount();
        players[pos] = data.getPlayer();
        if (players[pos] == null){
            throw new NullPointerException("Transaction player can't be null in database");
        }
        items[pos] = data.getItem();
        LocalDateTime date = data.getDate();
        time[pos] = dateToIntArray(date);
        positions[pos] = data.getPosition().toString();
    }

    private void init(int size){
        prices = new double[size];
        players = new String[size];
        items = new String[size];
        time = new int[size][6];
        amounts = new int[size];
        positions = new String[size];
    }
    
}
