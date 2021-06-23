package unprotesting.com.github.data.util;

import lombok.Getter;

//  Abstract class for transactable time period objects

public abstract class BuyableTimePeriodFunctions {
    
    @Getter
    private int[] buys, 
                  sells;
    @Getter
    private double[] prices;
    @Getter
    private String[] items;

    public void init(int size){
        this.buys = new int[size];
        this.sells = new int[size];
        this.prices = new double[size];
        this.items = new String[size];
    }


}
