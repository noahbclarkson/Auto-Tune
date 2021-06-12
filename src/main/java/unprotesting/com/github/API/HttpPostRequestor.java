package unprotesting.com.github.API;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Ephemeral.Data.EnchantmentData;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Logging.Logging;

public class HttpPostRequestor {

    @Getter
    private Boolean correctAPIKey;

    public HttpPostRequestor(){
        try {
            this.correctAPIKey = checkAPIKey();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean checkAPIKey() throws IOException{
        if (Config.getApiKey().equals("xyz") || Config.getEmail().equals("xyz@gmail.com")){
            Logging.log("Please change your API key and email in the config.yml file." +
             " If you don't have an API key you can get one for free on our discord: https://discord.gg/bj2US6KuXW");
            return false;
        }
        else{
            HttpPost httpPost = getDefaultHttpPost();
            HashMap<String, Object> priceDetails = new HashMap<String, Object>();
            priceDetails.put("n", "test");
            priceDetails.put("p",  1.0);
            priceDetails.put("b", 2);
            priceDetails.put("s", 1);
            JSONObject json = new JSONObject(priceDetails);
            JSONArray itemData = new JSONArray();
            itemData.add(json);
            if (sendRequest(httpPost, loadDefaultObject(itemData)) == null){
                return false;
            }
            else{
                return true;
            }
        }
    }

    public void updatePrices(JSONObject json) throws IOException{
        HttpPost httpPost = getDefaultHttpPost();
        HttpEntity entityResponse = sendRequest(httpPost, json);
        if (entityResponse != null){
            JsonParser parser = new JsonParser();
            String result = null;
            try {
                result = EntityUtils.toString(entityResponse);
            } catch (SocketException ex) {
                return;    
            }
            if (result == null) {
                return;
            }
            Logging.debug(result);
            JsonElement jsonElement = parser.parse(result);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonElement jsonArrayElement = jsonObject.get("returnData");
            JsonArray jsonArray = jsonArrayElement.getAsJsonArray();
            ConcurrentHashMap<String, ItemData> map = Main.getCache().getITEMS();
            ConcurrentHashMap<String, EnchantmentData> emap = Main.getCache().getENCHANTMENTS();
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                JsonElement priceElement = obj.get("p");
                JsonElement nameElement = obj.get("i");
                String priceString = priceElement.getAsString();
                String name = nameElement.getAsString();
                Double price = Double.parseDouble(priceString);
                ItemData data;
                EnchantmentData edata;
                try{
                    data = map.get(name);
                    data.setPrice(price);
                    map.put(name, data);
                }
                catch(NullPointerException e){
                    edata = emap.get(name);
                    edata.setPrice(price);
                    emap.put(name, edata);
                }
            }
            Main.getCache().updatePrices(map);
            Main.getCache().updateEnchantments(emap);
        }
    }

    public static JSONObject loadDefaultObject(JSONArray itemData){
        HashMap<String, Object> obj = new HashMap<String, Object>();
        obj.put("itemData", itemData);
        obj.put("maxVolatility", Config.getBasicMaxVariableVolatility());
        obj.put("minVolatility", Config.getBasicMinVariableVolatility());
        return new JSONObject(obj);
    }

    private HttpEntity sendRequest(HttpPost httpPost, JSONObject json) throws IOException{
        CloseableHttpClient client = HttpClients.createDefault();
        StringEntity entity = new StringEntity(json.toJSONString());
        httpPost.setEntity(entity);
        CloseableHttpResponse response = client.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity entityResponse = null;
        if (statusCode == 200) {
            client.close();
            entityResponse = response.getEntity();
            return entityResponse;
        }
        else {
            client.close();
            Logging.error(5);
            return entityResponse;
        }
    }

    private HttpPost getDefaultHttpPost(){
        HttpPost httpPost = new HttpPost("https://auto-tune-economy-api.herokuapp.com/");
        httpPost.setHeader("content-type", "application/json");
        httpPost.setHeader("apikey", Config.getApiKey());
        httpPost.setHeader("email", Config.getEmail());
        return httpPost;
    }

    
}
