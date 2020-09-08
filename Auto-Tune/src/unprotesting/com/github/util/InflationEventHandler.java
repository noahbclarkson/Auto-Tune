package unprotesting.com.github.util;

import java.util.concurrent.ConcurrentHashMap;

import unprotesting.com.github.Main;

public class InflationEventHandler implements Runnable {

    @Override
    public void run() {
        for (String str : Main.map.keySet()){
            increaseItemPrice(str, Config.getDynamicInflationValue(), true);
        }
        Main.debugLog("Dynamic Inflation Value: " + Config.getDynamicInflationValue());
    }

    public static Double increaseItemPrice(String item, Double value, boolean percentage){
        ConcurrentHashMap<Integer, Double[]> retMap = Main.map.get(item);
        int size = retMap.size();
        Double[] arr = retMap.get(size-1);
        Double price = arr[0];
        Double newPrice = 0.0;
        if (percentage){
        newPrice = price+price*0.01*value;
        }
        if (!percentage){
            newPrice = price+value;
        }
        arr[0] = newPrice;
        retMap.put(size-1, arr);
        Main.map.put(item, retMap);
        Main.debugLog("Increased item price of: " + item + " from " + price + " to " + newPrice);
        return newPrice;
    }

    public static Double decreaseItemPrice(String item, Double value, boolean percentage){
        ConcurrentHashMap<Integer, Double[]> retMap = Main.map.get(item);
        int size = retMap.size();
        Double[] arr = retMap.get(size-1);
        Double price = arr[0];
        Double newPrice = 0.0;
        if (percentage){
        newPrice = price-price*0.01*value;
        }
        if (!percentage){
            newPrice = price-value;
        }
        arr[0] = newPrice;
        retMap.put(size-1, arr);
        Main.map.put(item, retMap);
        Main.debugLog("Increased item price of: " + item + " from " + price + " to " + newPrice);
        return newPrice;
    }
    
}