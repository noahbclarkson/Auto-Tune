package unprotesting.com.github.util;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.json.simple.parser.ParseException;

import unprotesting.com.github.Main;

public class EnchantmentPriceHandler implements Runnable {

    @Override
    public void run() {
        try {
            PriceCalculationHandler.loadEnchantmentPricesAndCalculate();
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    } 
}
