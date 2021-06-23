package unprotesting.com.github.events.async;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.util.UtilFunctions;

public class InflationUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public InflationUpdateEvent(boolean isAsync){
        super(isAsync);
        if (Config.getInflationMethod().equalsIgnoreCase("mixed") ||
         Config.getInflationMethod().equalsIgnoreCase("dynamic")){
            updateInflation();
        }
    }

    private void updateInflation(){
        int playerCount = UtilFunctions.calculatePlayerCount();
        if (playerCount >= Config.getUpdatePricesThreshold()){
            Main.getCache().updatePrices(updateItems());
            Main.getCache().updateEnchantments(updateEnchantments());
        }
    }

    private ConcurrentHashMap<String, ItemData> updateItems(){
        ConcurrentHashMap<String, ItemData> map = Main.getCache().getITEMS();
        for (String item : map.keySet()){
            ItemData data = map.get(item);
            double price = data.getPrice();
            double newprice = price + price * 0.01 * Config.getDynamicInflationValue();
            data.setPrice(newprice);
            map.put(item, data);
        }
        return map;
    }

    private ConcurrentHashMap<String, EnchantmentData> updateEnchantments(){
        ConcurrentHashMap<String, EnchantmentData> map = Main.getCache().getENCHANTMENTS();
        for (String item : map.keySet()){
            EnchantmentData data = map.get(item);
            double price = data.getPrice();
            double newprice = price + price * 0.01 * Config.getDynamicInflationValue();
            data.setPrice(newprice);
            map.put(item, data);
        }
        return map;
    }

}
