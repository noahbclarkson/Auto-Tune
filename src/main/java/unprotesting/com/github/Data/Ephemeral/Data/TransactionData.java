package unprotesting.com.github.Data.Ephemeral.Data;

import java.time.LocalDateTime;

import org.bukkit.entity.Player;

import lombok.Getter;
import unprotesting.com.github.Data.Util.LocalDateTimeArrayUtilizer;

//  Transaction class for storing general transaction data

public class TransactionData extends LocalDateTimeArrayUtilizer implements Comparable<TransactionData>{

    @Getter
    private LocalDateTime date;
    @Getter
    private Player player;
    @Getter
    private String item;
    @Getter
    private int amount;
    @Getter
    private double price;
    @Getter
    private TransactionPositionType position;

    //  Create new transaction data
    public TransactionData(Player player, String item, int amount, double price, TransactionPositionType position){
        this.date = LocalDateTime.now();
        this.player = player;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.position = position;
    }

    //  Create new transaction data using previous persistent data
    public TransactionData(Player player, String item, int amount, double price, TransactionPositionType position, int[] date){
        this.date = arrayToDate(date);
        this.player = player;
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
        return this.getDate().compareTo(o.getDate());
    }
    
}
