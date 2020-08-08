package unprotesting.com.github.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.bind.JsonTreeReader;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.apache.http.util.EntityUtils;

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
                + ", minVolatility: " + minVolatility + ", APIKey: " + apikey + ", Email: " + email);
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
        ;
        if (statusCode != 200) {
            client.close();
            Main.log("Error on status code");
        }
        ;
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
            Main.debugLog("New price for " + item + " = $" + (NewPrice));
            newPrice = Double.parseDouble(NewPrice);
        }
        Main.debugLog("Status code: " + Integer.toString(statusCode));
        return newPrice;
    }

    public static Double sendRequestForPrice(String model, String algorithm, String apikey, String email, String item, Double price, Double averageBuy, Double averageSell, Double maxVolatility, Double minVolatility) throws ParseException {
        Double newPrice = price;
        try{
            newPrice = sendPostRequestUsingHttpClient(model, algorithm, apikey, email, item, price, averageBuy, averageSell, maxVolatility, minVolatility);
            return newPrice;
            }
            catch (IOException e){
                e.printStackTrace();

            }
        return newPrice;
    }
    
            
}