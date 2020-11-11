package unprotesting.com.github.util;

import java.io.IOException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import unprotesting.com.github.Main;

public class HttpPostRequestor {

    public static Double sendPostRequestUsingHttpClient(String model, String algorithm, String apikey, String email,
            String item, Double price, Double averageBuy, Double averageSell, Double maxVolatility,
            Double minVolatility) throws ClientProtocolException, IOException, ParseException {
        Double newPrice = price;
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://safe-refuge-09383.herokuapp.com");
        JSONObject json = JSONManager.returnJSONFromParams(model, algorithm, price, averageBuy, averageSell,
                maxVolatility, minVolatility);
        Main.debugLog("Sending data to API for " + item + ": - " + "model: " + model + ", price: " + price
                + ", averageBuy: " + averageBuy + ", averageSell: " + averageSell + ", maxVolatility: " + maxVolatility
                + ", minVolatility: " + minVolatility + ", APIKey: " + ("**********" + apikey.substring(10)) + ", Email: " + ("********" + email.substring(8)));
        StringEntity entity = new StringEntity(json.toJSONString());
        httpPost.setEntity(entity);
        httpPost.setHeader("content-type", "application/json");
        httpPost.setHeader("apikey", apikey);
        httpPost.setHeader("email", email);
        CloseableHttpResponse response = client.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            client.close();
        }
        if (statusCode != 200) {
            client.close();
            Main.log("Error on status code");
        }
        Main.debugLog(response.getStatusLine().getReasonPhrase());
        HttpEntity entityResponse = response.getEntity();
        if (entityResponse != null) {
            JsonParser parser = new JsonParser();
            String result = EntityUtils.toString(entityResponse);
            Main.debugLog("Result: " + result);
            JsonElement jsonElement = parser.parse(result);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            Main.debugLog("JsonElement: " + jsonElement.toString());
            JsonElement NewPriceElement = (jsonObject.get("newPrice"));
            String NewPrice = NewPriceElement.getAsString();
            Main.debugLog("New price for " + item + " = " + Config.getCurrencySymbol() + (NewPrice));
            newPrice = Double.parseDouble(NewPrice);
        }
        Main.debugLog("Status code: " + Integer.toString(statusCode));
        return newPrice;
    }

    public static Double sendRequestForPrice(String model, String algorithm, String apikey, String email, String item,
            Double price, Double averageBuy, Double averageSell, Double maxVolatility, Double minVolatility)
            throws ParseException {
        Double newPrice = price;
        try {
            newPrice = sendPostRequestUsingHttpClient(model, algorithm, apikey, email, item, price, averageBuy,
                    averageSell, maxVolatility, minVolatility);
            return newPrice;
            }
            catch (IOException e){
                e.printStackTrace();

            }
        return newPrice;
    }


    public static boolean ghostCheckAPIKey() throws ClientProtocolException, IOException {
        if (Config.getApiKey() == "xyz"){
            Main.log("Please change your API key in the config.yml file");
            return false;
        }
        if (Config.getEmail() == "xyz@gmail.com"){
            Main.log("Please change your Email in the config.yml file");
            return false;
        }
        else{
            Main.debugLog("Api-Key has been changed in config");
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("https://safe-refuge-09383.herokuapp.com");
            httpPost.setHeader("content-type", "application/json");
            httpPost.setHeader("apikey", Config.getApiKey());
            httpPost.setHeader("email", Config.getEmail());
            JSONObject json = new JSONObject();
            json.put("apikey", Config.getApiKey());
            StringEntity entity = new StringEntity(json.toJSONString());
            httpPost.setEntity(entity);
            CloseableHttpResponse response = client.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                client.close();
                return true;
            }
            else if (statusCode != 200) {
                client.close();
                Main.log("Error on status code");
                return false;
            }
        }
        return false;
        
    }

    public static boolean checkAPIKey(){
        try {
            boolean vaildKey = ghostCheckAPIKey();
            if (vaildKey == true){
                return true;
            }
            else if (vaildKey != true){
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

            
}