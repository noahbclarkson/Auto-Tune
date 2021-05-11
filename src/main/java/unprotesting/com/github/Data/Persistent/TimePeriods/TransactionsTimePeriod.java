package unprotesting.com.github.Data.Persistent.TimePeriods;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData;
import unprotesting.com.github.Data.Util.LocalDateTimeArrayUtilizer;

//  Transaction time period object for storing transaction price, player, buy/sell and creation-date

public class TransactionsTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable {

    @Getter
    private double[] prices;
    @Getter
    private int[] amounts;
    @Getter
    private int[][] time;
    @Getter
    private String[] players, items, positions;

    public TransactionsTimePeriod(){
        int size = Main.cache.getTRANSACTIONS().size();
        init(size);
        this.prices = new double[size];
        this.amounts = new int[size];
        this.items = new String[size];
        this.players = new String[size];
        this.positions = new String[size];
        this.time = new int[size][6];
        int i = 0;
        for (TransactionData data : Main.cache.getTRANSACTIONS()){
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, TransactionData data){
        this.prices[pos] = data.getPrice();
        this.amounts[pos] = data.getAmount();
        this.players[pos] = data.getPlayer().getUniqueId().toString();
        this.items[pos] = data.getItem();
        LocalDateTime date = data.getDate();
        this.time[pos] = dateToIntArray(date);
    }

    private void init(int size){
        this.prices = new double[size];
        this.players = new String[size];
        this.items = new String[size];
        this.time = new int[size][6];
        this.amounts = new int[size];
    }
    
}
