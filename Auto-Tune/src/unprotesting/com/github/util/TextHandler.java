package unprotesting.com.github.util;

import org.bukkit.entity.Player;

import unprotesting.com.github.Main;

public class TextHandler {
    public static void sendPriceModelData(String priceModel){
        if (priceModel.contains("Basic")) {
            Main.log("Loaded Basic Price Algorithim");
            if (Main.basicVolatilityAlgorithim.contains("Variable")) {
              Main.log("Loaded Algorithim under Variable Configuration");
            }
            if (Main.basicVolatilityAlgorithim.contains("Fixed")) {
              Main.log("Loaded Algorithim under Variable Configuration");
            }
          }
          if (priceModel.contains("Advanced")) {
            Main.log("Loaded Advanced Price Algorithim");
            if (Main.basicVolatilityAlgorithim.contains("Variable")) {
              Main.log("Loaded Advanced Algorithim under Variable Configuration");
            }
            if (Main.basicVolatilityAlgorithim.contains("Fixed")) {
              Main.log("Loaded Advanced Algorithim under Variable Configuration");
            }
          }
    }

    public static void sendDataBeforePriceCalculation(String priceModel, String basicVolatilityAlgorithim){
      Main.debugLog("Starting price calculation task... ");
      Main.debugLog("Price algorithim settings: ");
      if (priceModel.contains("Basic") && basicVolatilityAlgorithim.contains("Fixed")) {
        Main.debugLog("Basic Max Fixed Volatility: " + Config.getBasicMaxFixedVolatility());
        Main.debugLog("Basic Min Fixed Volatility: " + Config.getBasicMinFixedVolatility());
      }
      if (priceModel.contains("Basic") && basicVolatilityAlgorithim.contains("Variable")) {
        Main.debugLog("Basic Max Variable Volatility: " + Config.getBasicMaxVariableVolatility());
        Main.debugLog("Basic Min Variable Volatility: " + Config.getBasicMinVariableVolatility());
      }
      if (priceModel.contains("Advanced") && basicVolatilityAlgorithim.contains("Fixed")) {
        Main.debugLog("Advanced Max Fixed Volatility: " + Config.getBasicMaxFixedVolatility());
        Main.debugLog("Advanced Min Fixed Volatility: " + Config.getBasicMinFixedVolatility());
      }
      if (priceModel.contains("Advanced") && basicVolatilityAlgorithim.contains("Variable")) {
        Main.debugLog("Advanced Max Variable Volatility: " + Config.getBasicMaxVariableVolatility());
        Main.debugLog("Advanced Min Variable Volatility: " + Config.getBasicMinVariableVolatility());
      }
      if (priceModel.contains("Exponential") && basicVolatilityAlgorithim.contains("Variable")) {
        Main.debugLog("Exponential Max Variable Volatility: " + Config.getBasicMaxVariableVolatility());
        Main.debugLog("Exponential Min Variable Volatility: " + Config.getBasicMinVariableVolatility());
        Main.debugLog("Exponential data selection algorithim: y = " + Config.getDataSelectionM() + "(x^" + Config.getDataSelectionZ() + ") + " + Config.getDataSelectionC());
      }
    }

    public static void noPermssion(Player p){
      p.sendMessage(Config.getNoPermission());
    }
}