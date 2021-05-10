package unprotesting.com.github.Data.Ephemeral.Data;

import lombok.Getter;
import unprotesting.com.github.Data.Ephemeral.Data.Util.Transactable;

//  Item data class for storing general item data

public class ItemData extends Transactable{

    @Getter
    private int buys, sells;
    @Getter
    private double price;

    public ItemData(double price){
        this.buys = 0;
        this.sells = 0;
        this.price = price;
    }




    
}
