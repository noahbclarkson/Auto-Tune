package unprotesting.com.github.util;

import org.json.simple.JSONObject;

public class JsonManager {

    public static JSONObject returnJSONFromParams(String model, String algorithm, Double price, Double averageBuy, Double averageSell, Double maxVolatility, Double minVolatility){
        JSONObject obj = new JSONObject();
        obj.put("algorithm", model);
        obj.put("model", algorithm);
        obj.put("price", price);
        obj.put("averageBuy", averageBuy);
        obj.put("averageSell", averageSell);
        obj.put("maxVolatility", maxVolatility);
        obj.put("minVolatility", minVolatility);
        return obj;
    }

}