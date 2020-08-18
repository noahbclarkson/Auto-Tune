package unprotesting.com.github.util;

import unprotesting.com.github.Main;

public class TextHandler {
    public static void sendPriceModelData(String priceModel){
        if (priceModel.contains("Basic") == true) {
            Main.log("Loaded Basic Price Algorithim");
            if (Main.basicVolatilityAlgorithim.contains("Variable") == true) {
              Main.log("Loaded Algorithim under Variable Configuration");
            }
            if (Main.basicVolatilityAlgorithim.contains("fixed") == true) {
              Main.log("Loaded Algorithim under Variable Configuration");
            }
          }
          if (priceModel.contains("Advanced") == true) {
            Main.log("Loaded Advanced Price Algorithim");
            if (Main.basicVolatilityAlgorithim.contains("Variable") == true) {
              Main.log("Loaded Advanced Algorithim under Variable Configuration");
            }
            if (Main.basicVolatilityAlgorithim.contains("fixed") == true) {
              Main.log("Loaded Advanced Algorithim under Variable Configuration");
            }
          }
    }
}