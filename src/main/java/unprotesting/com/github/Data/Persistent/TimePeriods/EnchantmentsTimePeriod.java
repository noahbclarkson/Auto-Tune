package unprotesting.com.github.Data.Persistent.TimePeriods;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Ephemeral.Data.EnchantmentData;
import unprotesting.com.github.Data.Util.BuyableTimePeriodFunctions;

//  Enchantment time period object for storing enchantment price, ratio and buy/sell data 

public class EnchantmentsTimePeriod extends BuyableTimePeriodFunctions implements Serializable{

    @Getter
    private int[] buys, sells;
    @Getter
    private double[] prices, ratios;
    @Getter
    private String[] items;

    public EnchantmentsTimePeriod(){
        Set<String> set = LocalDataCache.getENCHANTMENTS().keySet();
        int size = set.size();
        init(size);
        this.ratios = new double[size];
        int i = 0;
        for (String key : set){
            EnchantmentData data = LocalDataCache.getENCHANTMENTS().get(key);
            setVars(i, data);
            this.items[i] = key;
            i++;
        }
    }

    private void setVars(int pos, EnchantmentData data){
        this.buys[pos] = data.getBuys();
        this.sells[pos] = data.getSells();
        this.prices[pos] = data.getPrice();
        this.ratios[pos] = data.getRatio();
    }

    
    
}
