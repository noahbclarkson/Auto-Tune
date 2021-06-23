package unprotesting.com.github.data.ephemeral.data;

import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

public class AutosellData {

    //  Autosell data class for temporarily storing autosell data in cache

    @Getter
    private ConcurrentHashMap<String, Double> data;

    public AutosellData(){
        data = new ConcurrentHashMap<String, Double>();
    }

    public void add(String player_uuid, Double amount){
        if (data.containsKey(player_uuid)){
            data.put(player_uuid, data.get(player_uuid)+amount);
            return;
        }
        else{
            data.put(player_uuid, amount);
        }
    }
    
}
