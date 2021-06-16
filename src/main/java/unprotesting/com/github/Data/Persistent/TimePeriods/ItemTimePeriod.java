package unprotesting.com.github.Data.Persistent.TimePeriods;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Data.Util.BuyableTimePeriodFunctions;

//  Item time period object for storing item price and buy/sell data 

public class ItemTimePeriod extends BuyableTimePeriodFunctions implements Serializable{

    @Getter
    private int[] buys, sells;
    @Getter
    private double[] prices;
    @Getter
    private String[] items;

    public ItemTimePeriod(){
        Set<String> set = Main.getCache().getITEMS().keySet();
        int size = set.size();
        init(size);
        this.buys = new int[size];
        this.sells = new int[size];
        this.prices = new double[size];
        this.items = new String[size];
        int i = 0;
        LocalDataCache cache = Main.getCache();
        for (String key : set){
            ItemData data = cache.getITEMS().get(key);
            this.items[i] = key;
            setVars(i, data);
            i++;
        }
    }

    private void setVars(int pos, ItemData data){
        this.buys[pos] = data.getBuys();
        this.sells[pos] = data.getSells();
        this.prices[pos] = data.getPrice();
    }


    
}
