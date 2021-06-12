package unprotesting.com.github.Events;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.API.HttpPostRequestor;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Logging.Logging;
import unprotesting.com.github.Util.UtilFunctions;

public class PriceUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public PriceUpdateEvent(boolean isAsync){
        super(isAsync);
        try {
            calculateAndLoad();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void calculateAndLoad() throws IOException, InterruptedException{
        int playerCount = UtilFunctions.calculatePlayerCount();
        Logging.debug("Updating prices with player count: " + playerCount);
        int size = Main.getCache().getITEMS().size() + Main.getCache().getENCHANTMENTS().size();
        if (playerCount >= Config.getUpdatePricesThreshold()){
            Main.updateTimePeriod();
            for (int min = 0; min < size-99;){
                Main.getRequestor().updatePrices(loadItemJSON(min, min+100));
                Thread.sleep(1000);
                min = min + 100;
            }
            Main.getRequestor().updatePrices(loadEnchantmentJSON());
        }
        else{
            Logging.debug("Player count was less than threshhold: " +  Config.getUpdatePricesThreshold());
        }
        Logging.debug("Next update: " + LocalDateTime.now().plusMinutes(Config.getTimePeriod())
         .format(DateTimeFormatter.ISO_LOCAL_TIME).toString());
    }

    @SuppressWarnings("unchecked")
    private JSONObject loadItemJSON(int min, int max){
        JSONArray itemData = new JSONArray();
        int i = 0;
        for (String item : Main.getCache().getITEMS().keySet()){
            if (i < min){
                i++;
                continue;
            }
            if (i >= max){
                i++;
                break;
            }
            double price = Main.getCache().getItemPrice(item, false);
            HashMap<String, Object> priceDetails = new HashMap<String, Object>();
            Double[] sbdata = loadAverageBuySellValue(item, price, false);
            priceDetails.put("n", item);
            priceDetails.put("p",  price);
            priceDetails.put("b", sbdata[0]);
            priceDetails.put("s", sbdata[1]);
            JSONObject priceData = new JSONObject(priceDetails);
            itemData.add(priceData);
            i++;
        }
        return HttpPostRequestor.loadDefaultObject(itemData);
    }

    @SuppressWarnings("unchecked")
    private JSONObject loadEnchantmentJSON(){
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
        return HttpPostRequestor.loadDefaultObject(itemData);
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
