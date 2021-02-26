package unprotesting.com.github.util;

public class ItemTimePeriod {
    
    public String item;
    public double price;
    public int buys;
    public int sells;


    public ItemTimePeriod(String item, Double price){
        this.item = item;
        this.price = price.doubleValue();
        this.buys = 0;
        this.sells = 0;
    }

}
