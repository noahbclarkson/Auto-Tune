package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;

//  Item data class for storing general item data

public class ItemData{

    @Getter
    private int buys,
                sells;            
    @Getter @Setter
    private double price;

    public ItemData(double price){
        this.buys = 0;
        this.sells = 0;
        this.price = price;
    }

    public void increaseBuys(int amount){
        buys = buys+amount;
    }

    public void increaseSells(int amount){
        sells = sells + amount;
    }




    
}
