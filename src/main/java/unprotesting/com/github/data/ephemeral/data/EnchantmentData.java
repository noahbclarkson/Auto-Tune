package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;

//  Enchantment data class for storing general enchantment data

public class EnchantmentData{

    @Getter
    private int buys,
                sells;
    @Getter @Setter
    private double price,
                   ratio;

    public EnchantmentData(double price, double ratio){
        this.buys = 0;
        this.sells = 0;
        this.price = price;
        this.ratio = ratio;
    }

    public void increaseBuys(int amount){
        buys = buys+amount;
    }

    public void increaseSells(int amount){
        sells = sells + amount;
    }

}
