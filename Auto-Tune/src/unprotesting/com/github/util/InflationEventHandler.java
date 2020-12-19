package unprotesting.com.github.util;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;

public class InflationEventHandler implements Runnable {
    @Override
    public void run() {
        Integer playerCount = Bukkit.getServer().getOnlinePlayers().size();
        for (String str : Main.map.keySet()){
            if (Config.isUpdatePricesWhenInactive() || (!Config.isUpdatePricesWhenInactive() && playerCount > 0)){
                increaseItemPrice(str, Config.getDynamicInflationValue(), true);
            }
        }
        PriceCalculationHandler.loadItemPriceData();
        Main.debugLog("Dynamic Inflation Value: " + Config.getDynamicInflationValue());
    }

    public static Double increaseItemPrice(String item, Double value, boolean percentage){
        Double price  = AutoTuneGUIShopUserCommand.getItemPrice(item, false);
        Double newPrice = 0.0;
        if (percentage){
        newPrice = price+price*0.01*value;
        }
        if (!percentage){
            newPrice = price+value;
        }
        ConcurrentHashMap<Integer, Double[]> outputMap = Main.map.get(item);
        int size = outputMap.size();
        Double[] arr = {newPrice, outputMap.get((size-1))[1], outputMap.get((size-1))[2]};
        outputMap.put(size-1, arr);
        Main.map.put(item, outputMap);
        Main.debugLog("Increased item price of: " + item + " from " + price + " to " + newPrice);
        return newPrice;
    }

    public static Double decreaseItemPrice(String item, Double value, boolean percentage){
        Double price  = AutoTuneGUIShopUserCommand.getItemPrice(item, false);
        Double newPrice = 0.0;
        if (percentage){
        newPrice = price-price*0.01*value;
        }
        if (!percentage){
            newPrice = price-value;
        }
        ConcurrentHashMap<Integer, Double[]> outputMap = Main.map.get(item);
        int size = outputMap.size();
        Double[] arr = {newPrice, outputMap.get((size-1))[1], outputMap.get((size-1))[2]};
        outputMap.put(size-1, arr);
        Main.map.put(item, outputMap);
        Main.debugLog("Increased item price of: " + item + " from " + price + " to " + newPrice);
        return newPrice;
    }
    
}