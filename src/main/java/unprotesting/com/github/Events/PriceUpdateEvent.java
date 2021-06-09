package unprotesting.com.github.Events;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Util.UtilFunctions;



public class PriceUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public PriceUpdateEvent(){
        try {
            calculateAndLoad();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    private void calculateAndLoad() throws IOException{
        int playerCount = UtilFunctions.calculatePlayerCount();
        if (playerCount >= Config.getUpdatePricesThreshold()){
            Main.updateTimePeriod();
            Main.getRequestor().updatePrices(loadItemJSON());
            Main.getRequestor().updatePrices(loadEnchantmentJSON());
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject loadItemJSON(){
        JSONObject obj = new JSONObject();
        JSONArray itemData = new JSONArray();
        for (String item : Main.getCache().getITEMS().keySet()){
            double price = Main.getCache().getItemPrice(item, false);
            HashMap<String, Object> priceDetails = new HashMap<String, Object>();
            Double[] sbdata = loadAverageBuySellValue(item, price, false);
            priceDetails.put("n", item);
            priceDetails.put("p",  price);
            priceDetails.put("b", sbdata[0]);
            priceDetails.put("s", sbdata[1]);
            JSONObject priceData = new JSONObject(priceDetails);
            itemData.add(priceData);
        }
        obj.put("itemData", itemData);
        obj.put("maxVolatility", Config.getBasicMaxVariableVolatility());
        obj.put("minVolatility", Config.getBasicMinVariableVolatility());
        return obj;
    }

    @SuppressWarnings("unchecked")
    private JSONObject loadEnchantmentJSON(){
        JSONObject obj = new JSONObject();
        JSONArray itemData = new JSONArray();
        for (String enchantment : Main.getCache().getENCHANTMENTS().keySet()){
            double price = Main.getCache().getEnchantmentPrice(enchantment, false);
            HashMap<String, Object> priceDetails = new HashMap<String, Object>();
            Double[] sbdata = loadAverageBuySellValue(enchantment, price, true);
            priceDetails.put("n", enchantment);
            priceDetails.put("p",  price);
            priceDetails.put("b", sbdata[0]);
            priceDetails.put("s", sbdata[1]);
            JSONObject priceData = new JSONObject(priceDetails);
            itemData.add(priceData);
        }
        obj.put("itemData", itemData);
        obj.put("maxVolatility", Config.getBasicMaxVariableVolatility()*5);
        obj.put("minVolatility", Config.getBasicMinVariableVolatility()*5);
        return obj;
    }

    private Double[] loadAverageBuySellValue(String item, Double price, boolean enchantment){
        double x = 0;
        int size = Main.getDatabase().map.getSize();
        Database db = Main.getDatabase();
        double final_y = 1;
        double final_buys = 0;
        double final_sells = 0;
        for (;x < 100000;){
            double y = Config.getDataSelectionM() * Math.pow(x, Config.getDataSelectionZ()) + Config.getDataSelectionC();
            y = Math.round(y);
            if (y > size){
                final_y = y;
                break;
            }
            int input = (int) y;
            TimePeriod period = db.map.get(input);
            double buys = 0;
            double sells = 0;
            try{
                if (enchantment){
                    int loc = Arrays.asList(period.getEtp().getItems()).indexOf(item);
                    buys = period.getEtp().getBuys()[loc];
                    sells = period.getEtp().getSells()[loc];
                }
                if (!enchantment){
                    int loc = Arrays.asList(period.getItp().getItems()).indexOf(item);
                    buys = period.getItp().getBuys()[loc];
                    sells = period.getItp().getSells()[loc];
                }
            }
            catch(NullPointerException e){
                break;
            }
            final_buys += buys * price;
            final_sells += sells * price;
            x++;
        }
        if (Config.isInflationEnabled()){
            final_buys += final_buys * 0.01 * Config.getInflationValue();
        }
        double avBuy = final_buys / final_y;
        double avSell = final_sells / final_y;
        return new Double[]{avBuy, avSell};
    }
    
}
