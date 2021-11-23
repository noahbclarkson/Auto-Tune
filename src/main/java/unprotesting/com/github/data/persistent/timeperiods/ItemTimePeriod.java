package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.data.util.BuyableTimePeriodFunctions;

//  Item time period object for storing item price and buy/sell data 

public class ItemTimePeriod extends BuyableTimePeriodFunctions implements Serializable{

    private static final long serialVersionUID = -1102531407L;

    @Getter @Setter
    private int[] buys, 
                  sells;
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
        buys[pos] = data.getBuys();
        sells[pos] = data.getSells();
        prices[pos] = data.getPrice();
    }


    
}
