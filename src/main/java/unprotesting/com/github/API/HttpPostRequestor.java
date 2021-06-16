package unprotesting.com.github.API;

import java.io.IOException;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.Getter;
import unprotesting.com.github.Config.Config;
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
        CloseableHttpResponse response;
        int statusCode;
        try{
            response = client.execute(httpPost);
            statusCode = response.getStatusLine().getStatusCode();
        }
        catch(NoHttpResponseException e){
            Logging.error(7);
            response = null;
            statusCode = -1;
            return null;
        }
        if (statusCode == 200) {
            client.close();
            HttpEntity entityResponse = response.getEntity();
            return entityResponse;
        }
        else {
            client.close();
            Logging.error(5);
            return null;
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
