package unprotesting.com.github.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import unprotesting.com.github.Main;

public class ItemPriceData {
    
    public List<Double> prices = new ArrayList<Double>();
    public Double price = 0.0;
    public Double sellPrice = 0.0;
    public String name;
    
    public ItemPriceData(String name){
        this.name = name;
        ConcurrentHashMap<Integer, Double[]> newMap = Main.map.get(name);
        int size = (newMap.size())-1;
        for (int i = 0; i < size; i++){
            this.prices.add(newMap.get(i)[0]);
        }
        price = newMap.get(size)[0];
        ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops." + name);
        Double sellDifference = Config.getSellPriceDifference();
        try{
            sellDifference = config.getDouble("sell-difference", Config.getSellPriceDifference());
        }
        catch(NullPointerException ex){
            sellDifference = Config.getSellPriceDifference();
        }
        sellPrice = price - (price*0.01*sellDifference);
    }

}
