package unprotesting.com.github.Data.Ephemeral.Data;

import lombok.Getter;
import unprotesting.com.github.Data.Ephemeral.Data.Util.Transactable;

//  Enchantment data class for storing general item data

public class EnchantmentData extends Transactable{

    @Getter
    private int buys, sells;
    @Getter
    private double price, ratio;

    public EnchantmentData(double price, double ratio){
        this.buys = 0;
        this.sells = 0;
        this.price = price;
        this.ratio = ratio;
    }

}
