package unprotesting.com.github.util;

import java.util.ArrayList;

import unprotesting.com.github.Main;

public class TimePeriod {

    public int period;
    public ArrayList<ItemTimePeriod> item_periods;
    
    public TimePeriod(Integer period_number){
        this.period = period_number;
        this.item_periods = new ArrayList<ItemTimePeriod>();
    }

    public void loadItemTimePeriod (ItemTimePeriod item_period){
        this.item_periods.add(item_period);
    }

    public void loadShopData() {
        int i = 0;
        if (Main.time_period_map.isEmpty()){
            for (String key : Main.getShopConfig().getConfigurationSection("shops").getKeys(false)) {
                Main.memMap.put(i, key);
                
            }
        }
    }

    

}
