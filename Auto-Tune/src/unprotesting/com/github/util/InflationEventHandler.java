package unprotesting.com.github.util;

import java.util.concurrent.ConcurrentHashMap;

import unprotesting.com.github.Main;

public class InflationEventHandler implements Runnable {

    @Override
    public void run() {
        for (String str : Main.map.keySet()){
            ConcurrentHashMap<Integer, Double[]> retMap = Main.map.get(str);
            int size = retMap.size();
            Double[] arr = retMap.get(size-1);
            Double price = arr[0];
            Double newPrice = price+price*0.01*Config.getDynamicInflationValue();
            arr[0] = newPrice;
            retMap.put(size-1, arr);
            Main.map.put(str, retMap);
        }
    }
    
}