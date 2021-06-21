package unprotesting.com.github.data.ephemeral.data;

import java.text.DecimalFormat;
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

    public MessagesData(){
        this.tutorialData = new ConcurrentHashMap<String, Integer>();
        this.tutorial = new ArrayList<String>();
        this.onJoin = new ArrayList<String>();
        List<String> input = Main.getDfiles().getMessages().getStringList("tutorial");
        for (String str : input){
            this.tutorial.add(ChatColor.translateAlternateColorCodes('&', str));
        }
        input = Main.getDfiles().getMessages().getStringList("on-join");
        for (String str : input){
            this.onJoin.add(ChatColor.translateAlternateColorCodes('&', str));
        }
    }

    private static String replaceLocalPlaceholders(Player player, String base, String item, Double i_price, Integer i_amount, Double i_sell_price,
     String ench, Double ench_price, Integer ench_level){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        if (item != null){
            base = base.replace("%GENERAL_ITEM%", item);
        }
        if (i_price != null){
            base = base.replace("%GENERAL_ITEM_PRICE%", Config.getCurrencySymbol() + df.format(i_price));
            if (i_amount != null){
                base = base.replace("%GENERAL_ITEM_PRICEXAMOUNT%", Config.getCurrencySymbol() + df.format(i_price*i_amount));
            }
        }
        if (i_amount != null){
            base = base.replace("%GENERAL_ITEM_AMOUNT%", Integer.toString(i_amount));
        }
        if (i_sell_price != null){
            base = base.replace("%GENERAL_ITEM_PRICE_SELL%", Config.getCurrencySymbol() + df.format(i_sell_price));
            if (i_amount != null){
                base = base.replace("%GENERAL_ITEM_PRICEXAMOUNT_SELL%", Config.getCurrencySymbol() + df.format(i_sell_price*i_amount));
            }
        }
        if (ench != null){
            base = base.replace("%GENERAL_ENCHANTMENT%", ench);
        }
        if (ench_price != null){
            base = base.replace("%GENERAL_ENCHANTMENT_PRICE%", Config.getCurrencySymbol() + df.format(ench_price));
        }
        if (ench_level != null){
            base = base.replace("%GENERAL_ENCHANTMENT_LEVEL%", Integer.toString(ench_level));
        }
        base = ChatColor.translateAlternateColorCodes('&', base);
        if (Main.isPlaceholderAPI()){
            base = PlaceholderAPI.setPlaceholders(player, base);
        }
        return base;
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



    public static String getPlayerBuyItemString(String message, Player player, String item, double i_price, int i_amount){
        String base = Main.getDfiles().getMessages().getString(message);
        return replaceLocalPlaceholders(player, base, item, i_price, i_amount, null, null, null, null);
    }

    public static String getPlayerSellItemString(String message, Player player, String item, double i_price, int i_amount, double i_sell_price){
        String base = Main.getDfiles().getMessages().getString(message);
        return replaceLocalPlaceholders(player, base, item, i_price, i_amount, i_sell_price, null, null, null);
    }

    public static String getPlayerBuyEnchantmentString(String message, Player player, String item, double i_price, int i_amount, double i_sell_price,
    String ench, double ench_price, int ench_level){
        String base = Main.getDfiles().getMessages().getString(message);
        return replaceLocalPlaceholders(player, base, item, i_price, i_amount, i_sell_price, ench, ench_price, ench_level);
    }
    
}
