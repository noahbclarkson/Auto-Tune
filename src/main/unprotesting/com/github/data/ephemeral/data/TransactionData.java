package unprotesting.com.github.data.ephemeral.data;

import java.time.LocalDateTime;

import lombok.Getter;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

//  Transaction class for storing general transaction data

public class TransactionData extends LocalDateTimeArrayUtilizer implements Comparable<TransactionData>{

    @Getter
    private LocalDateTime date;
    @Getter
    private String item,
                   player;
    @Getter
    private int amount;
    @Getter
    private double price;
    @Getter
    private TransactionPositionType position;

    //  Create new transaction data
    public TransactionData(String player_uuid, String item, int amount, double price, TransactionPositionType position){
        this.date = LocalDateTime.now();
        this.player = player_uuid;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.position = position;
    }

    //  Create new transaction data using previous persistent data
    public TransactionData(String player_uuid, String item, int amount, double price, TransactionPositionType position, int[] date){
        this.date = arrayToDate(date);
        this.player = player_uuid;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.position = position;
    }


    //  Buy/Sell Item | Buy/Sell Enchantment
    public enum TransactionPositionType{
        BI, SI, BE, SE
    }

    @Override
    public int compareTo(TransactionData o) {
        return o.getDate().compareTo(this.getDate());
    }
    
}
