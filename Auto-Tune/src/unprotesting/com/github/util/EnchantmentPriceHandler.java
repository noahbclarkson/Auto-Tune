package unprotesting.com.github.util;

import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.parser.ParseException;

import unprotesting.com.github.Main;

public class EnchantmentPriceHandler implements Runnable {

    @Override
    public void run() {
        Main.debugLog("Loading Enchantment Price Update Algorithm");
        for (String str : Main.enchMap.get("Auto-Tune").keySet()) {
            ConcurrentHashMap<String, EnchantmentSetting> inputMap = Main.enchMap.get("Auto-Tune");
            EnchantmentSetting setting = inputMap.get(str);
            ConcurrentHashMap<Integer, Double[]> buySellMap = setting.buySellData;
            try {
                buySellMap = EnchantmentAlgorithm.loadAverageBuyAndSellValue(buySellMap, setting);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            setting.buySellData = buySellMap;
            Double[] arr = buySellMap.get(buySellMap.size()-1);
            setting.price = arr[0];
            inputMap.put(setting.name, setting);
            Main.enchMap.put("Auto-Tune", inputMap);
        }
    }
    
}
