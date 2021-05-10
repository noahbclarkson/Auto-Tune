package unprotesting.com.github.Data.Util;

import lombok.Getter;

//  Abstract class for transactable time period objects

public abstract class BuyableTimePeriodFunctions {
    
    @Getter
    int[] buys, sells;
    @Getter
    double[] prices;
    @Getter
    String[] items;

    public void init(int size){
        this.buys = new int[size];
        this.sells = new int[size];
        this.prices = new double[size];
        this.items = new String[size];
    }


}
