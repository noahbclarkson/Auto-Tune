package unprotesting.com.github.Data.Ephemeral.Other;

import lombok.Getter;
import lombok.Setter;

//  Sale class for storing sale general sale data

public class Sale {

    @Getter
    private final String item;
    @Getter @Setter
    private int amount;

    public Sale(String item, int amount){
        this.item = item;
        this.amount = amount;
    }

    public enum SalePositionType{
        BUY, SELL, EBUY, ESELL
    }


    
}
