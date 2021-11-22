package unprotesting.com.github.data.ephemeral.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class MessagesData {

    @Getter
    private List<String> tutorial,
                         onJoin;
    @Getter
    private ConcurrentHashMap<String, Integer> tutorialData;

    public static String[] localVariables = new String[]{
        "GENERAL_ITEM", "GENERAL_ITEM_PRICE", "GENERAL_ITEM_AMOUNT",
        "GENERAL_ITEM_PRICEXAMOUNT", "GENERAL_ITEM_PRICE_SELL",
        "GENERAL_ITEM_PRICEXAMOUNT_SELL", "GENERAL_ENCHANTMENT",
        "GENERAL_ENCHANTMENT_LEVEL", "GENERAL_ENCHANTMENT_PRICE"
    };

    public MessagesData(){
        this.tutorialData = new ConcurrentHashMap<String, Integer>();
        this.tutorial = new ArrayList<String>();
        this.onJoin = new ArrayList<String>();
        List<String> input = Main.getDataFiles().getMessages().getStringList("tutorial");
        for (String str : input){
            this.tutorial.add(ChatColor.translateAlternateColorCodes('&', str));
        }
        input = Main.getDataFiles().getMessages().getStringList("on-join");
        if (input != null){
            for (String str : input){
                this.onJoin.add(ChatColor.translateAlternateColorCodes('&', str));
            }
        }
    }

    public void updatePlayerTutorialData(String player_uuid){
        if (tutorialData.get(player_uuid) == null){
            tutorialData.put(player_uuid, 0);
        }
        if (tutorialData.get(player_uuid) > tutorial.size()-1){
            tutorialData.put(player_uuid, 0);
        }
        tutorialData.put(player_uuid, tutorialData.get(player_uuid)+1);
    }

    public static String getNoPermission(Player player){
        String base = Main.getDataFiles().getMessages().getString("no-permission");
        if (Main.isPlaceholderAPI()){
            base = PlaceholderAPI.setPlaceholders(player, base);
        }
        return base;
    }

    private static String updatePlaceHolders(String base, Player player){
        if (Main.isPlaceholderAPI()){
            base = PlaceholderAPI.setPlaceholders(player, base);
        }
        return base;
    }

    private static String replaceLocalPlaceholder(String message, Player player, String input, String placeholder){
        message = message.replace(("%" + placeholder + "%"), input);
        return message;
    }

    public static String getMessageString(Player player, String message, String... inputs){
        String base = Main.getDataFiles().getMessages().getString(message);
        for (int i = 0; i < inputs.length; i++){
            String input = inputs[i];
            if (input != null){
                if (i == 1 || i == 3 || i == 4 || i == 5 || i == 8){
                    input = Config.getCurrencySymbol() + input;
                }
                base = replaceLocalPlaceholder(base, player, input, localVariables[i]);
            }
        }
        base = updatePlaceHolders(base, player);
        return base;
    }
    
}
