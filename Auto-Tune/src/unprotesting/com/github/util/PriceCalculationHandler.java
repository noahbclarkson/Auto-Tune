package unprotesting.com.github.util;

import static unprotesting.com.github.Main.buys;
import static unprotesting.com.github.Main.sells;
import static unprotesting.com.github.Main.tempbuys;
import static unprotesting.com.github.Main.tempsells;
import static unprotesting.com.github.Main.setupMaxBuySell;
import static unprotesting.com.github.Main.basicVolatilityAlgorithim;
import static unprotesting.com.github.Main.map;
import static unprotesting.com.github.Main.locked;
import static unprotesting.com.github.Main.falseBool;
import static unprotesting.com.github.Main.debugLog;
import static unprotesting.com.github.Main.dateFormat;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.json.simple.parser.ParseException;

import unprotesting.com.github.Main;

import static unprotesting.com.github.Main.priceModel;

public class PriceCalculationHandler implements Runnable {

    @Override
    public void run() {
        Main.log("Starting Price Calculation Task");
        try {
            loadItemPricesAndCalculate();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        loadItemPriceData();
    }

    public static void loadItemPriceData(){
        if (Main.getItemPrices() != null){
            Main.itemPrices.clear();
        }
        Set<String> strSet = Main.map.keySet();
        for (String str : strSet){
            Main.itemPrices.put(str, new ItemPriceData(str));
        }
    }

    public static void loadItemPricesAndCalculate() throws ParseException {
        Integer playerCount = Bukkit.getServer().getOnlinePlayers().size();
        if (Config.isUpdatePricesWhenInactive() || (!Config.isUpdatePricesWhenInactive() && playerCount > 0)){
          setupMaxBuySell();
          tempbuys = 0.0;
          tempsells = 0.0;
          buys = 0.0;
          sells = 0.0;
          if (priceModel.contains("Basic") || priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
            TextHandler.sendDataBeforePriceCalculation(priceModel, basicVolatilityAlgorithim);
            Set<String> strSet = map.keySet();
            for (String str : strSet) {
              ConcurrentHashMap<Integer, Double[]> tempMap = map.get(str);
              Integer expvalues = 0;
              Main.getINSTANCE();
              ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
                  .getConfigurationSection(str);
              locked = null;
              if (config != null) {
                Boolean lk = config.getBoolean("locked", false);
                if (lk == true) {
                  locked = falseBool;
                  debugLog("Locked item found: " + str);
                }
                tempbuys = 0.0;
                tempsells = 0.0;
                buys = 0.0;
                sells = 0.0;
    
                if (priceModel.contains("Basic")) {
                  for (Integer key1 : tempMap.keySet()) {
                    Double[] key = tempMap.get(key1);
                    tempbuys = key[1];
                    buys = buys + tempbuys;
                    tempsells = key[2];
                    sells = sells + tempsells;
                  }
                }
    
                if (priceModel.contains("Advanced")) {
                  for (Integer key1 : tempMap.keySet()) {
                    Double[] key = tempMap.get(key1);
                    tempbuys = key[1];
                    tempbuys = tempbuys * key[0];
                    if (tempbuys == 0) {
                      tempbuys = key[0];
                    }
                    buys = buys + tempbuys;
                    tempsells = key[2];
                    tempsells = tempsells * key[0];
                    if (tempsells == 0) {
                      tempsells = key[0];
                    }
                    sells = sells + tempsells;
                  }
                }
                if (priceModel.contains("Exponential")) {
                  Integer tempSize = tempMap.keySet().size();
                  Integer x = 0;
                  for (; x < 100000;) {
                    Double y = Config.getDataSelectionM() * (Math.pow(x, Config.getDataSelectionZ()))
                        + Config.getDataSelectionC();
                    Integer inty = (int) Math.round(y) - 1;
                    if (inty > tempSize - 1) {
                      expvalues = inty + 1;
                      break;
                    }
                    Double[] key = tempMap.get((tempSize - 1) - inty);
                    tempbuys = key[1];
                    tempbuys = tempbuys * key[0];
                    if (tempbuys == 0) {
                      tempbuys = key[0];
                    }
                    buys = buys + tempbuys;
                    tempsells = key[2];
                    tempsells = tempsells * key[0];
                    if (tempsells == 0) {
                      tempsells = key[0];
                    }
                    sells = sells + tempsells;
                    x++;
                  }
                }
    
                if ((Config.getInflationMethod().contains("Static") || Config.getInflationMethod().contains("Mixed"))
                    && Config.isInflationEnabled()) {
                  buys = buys + buys * 0.01 * Config.getInflationValue();
                }
    
                if (locked == falseBool) {
                  Double[] temp2 = tempMap.get(tempMap.size() - 1);
                  Double temp3 = temp2[0];
                  Integer tsize = tempMap.size();
                  Double newSpotPrice = temp3;
                  Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                  tempMap.put(tsize, temporary);
                  map.put(str, tempMap);
                  debugLog("Loading item, " + str + " with price " + Double.toString(temp3) + " as price is locked");
                }
                Double avBuy = buys / (tempMap.size());
                Double avSells = sells / (tempMap.size());
                if (priceModel.contains("Advanced") || priceModel.contains("Basic")) {
                  avBuy = buys / (tempMap.size());
                  avSells = sells / (tempMap.size());
                }
                if (priceModel.contains("Exponential")) {
                  avBuy = buys / (expvalues);
                  avSells = sells / (expvalues);
                }
                if (avBuy > avSells && locked == null) {
                  if (priceModel.contains("Basic")) {
                    debugLog("AvBuy > AvSells for " + str);
                  }
                  if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                    debugLog("AvBuyValue > AvSellValue for " + str);
                  }
                  Double[] temp2 = tempMap.get(tempMap.size() - 1);
                  Double temp3 = temp2[0];
                  Integer tsize = tempMap.size();
                  if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                      || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Fixed") == true) {
                    Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Fixed", priceModel, Config.getApiKey(),
                        Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxFixedVolatility(),
                        Config.getBasicMinFixedVolatility());
                    Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                    if (priceModel.contains("Basic")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                          + Double.toString(avSells));
                    }
                    if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                          + Double.toString(avSells));
                    }
                    tempMap.put(tsize, temporary);
                    map.put(str, tempMap);
    
                  }
                  if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                      || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Variable") == true) {
                    Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                        Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                        Config.getBasicMinVariableVolatility());
                    Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                    if (priceModel.contains("Basic")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                          + Double.toString(avSells));
                    }
                    if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                          + Double.toString(avSells));
                    }
                    tempMap.put(tsize, temporary);
                    map.put(str, tempMap);
                  }
                }
    
                if (avBuy < avSells && locked == null) {
                  if (priceModel.contains("Basic")) {
                    debugLog("AvBuy < AvSells for " + str);
                  }
                  if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                    debugLog("AvBuyValue < AvSellValue for " + str);
                  }
                  Double[] temp2 = tempMap.get(tempMap.size() - 1);
                  Double temp3 = temp2[0];
                  Integer tsize = tempMap.size();
                  if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                      || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Fixed")) {
                    Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Fixed", priceModel, Config.getApiKey(),
                        Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxFixedVolatility(),
                        Config.getBasicMinFixedVolatility());
                    Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                    if (priceModel.contains("Basic")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                          + Double.toString(avSells));
                    }
                    if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                          + Double.toString(avSells));
                    }
                    tempMap.put(tsize, temporary);
                    map.put(str, tempMap);
                  }
                  if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                      || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Variable") == true) {
                    Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                        Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                        Config.getBasicMinVariableVolatility());
                    Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                    if (priceModel.contains("Basic")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                          + Double.toString(avSells));
                    }
                    if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                      debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                          + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                          + Double.toString(avSells));
                    }
                    tempMap.put(tsize, temporary);
                    map.put(str, tempMap);
                  }
    
                }
    
                if (avBuy == avSells && locked == null) {
                  debugLog("AvBuy = AvSells for " + str);
                  Double[] temp2 = tempMap.get(tempMap.size() - 1);
                  Double temp3 = temp2[0];
                  Integer tsize = tempMap.size();
                  if (priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                      || priceModel.contains("Exponential")) {
                    Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                        Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                        Config.getBasicMinVariableVolatility());
                    Double[] temporary = { newSpotPrice, 0.0, 0.0 };
                    if (priceModel.contains("Basic")) {
                      debugLog("Loading item, " + str + ", with the same price: " + Double.toString(newSpotPrice)
                          + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                          + Double.toString(avSells));
                    }
                    if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                      debugLog("Loading item, " + str + ", with the same price: " + Double.toString(newSpotPrice)
                          + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                          + Double.toString(avSells));
                    }
                    tempMap.put(tsize, temporary);
                    map.put(str, tempMap);
                  }
                  locked = null;
                }
              }
            }
            tempbuys = 0.0;
            tempsells = 0.0;
            buys = 0.0;
            sells = 0.0;
            Date date = Calendar.getInstance().getTime();
            Date newDate = MathHandler.addMinutesToJavaUtilDate(date, Config.getTimePeriod());
            String strDate = dateFormat.format(newDate);
            debugLog("Done running price Algorithim, a new check will occur at: " + strDate);
            try {
              debugLog("Saving data to data.csv file");
              CSVHandler.writeCSV();
              CSVHandler.writeShortCSV();
              debugLog("Saved data to data.csv file");
            } catch (InterruptedException | IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
    
}
