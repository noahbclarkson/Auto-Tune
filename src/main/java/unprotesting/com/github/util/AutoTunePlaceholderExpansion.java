package unprotesting.com.github.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class AutoTunePlaceholderExpansion extends PlaceholderExpansion{

    private JavaPlugin plugin;

    public AutoTunePlaceholderExpansion(){
        this.plugin = Main.getINSTANCE();
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "AT";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public boolean canRegister(){
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier){
        if(player == null){
            return "";
        }
        identifier = identifier.toUpperCase();
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        if (identifier.contains("GDP")){
            if (identifier.equals("GDP_GDP")){
                return Config.getCurrencySymbol() + df.format(Main.getCache().getGDP_DATA().getGDP());
            }
            if (identifier.equals("GDP_BALANCE")){
                return Config.getCurrencySymbol() + df.format(Main.getCache().getGDP_DATA().getBalance());
            }
            if (identifier.equals("GDP_DEBT")){
                return Config.getCurrencySymbol() + df.format(Main.getCache().getGDP_DATA().getDebt());
            }
            if (identifier.equals("GDP_LOSS")){
                return Config.getCurrencySymbol() + df.format(Main.getCache().getGDP_DATA().getLoss());
            }
            if (identifier.equals("GDP_INFLATION")){
                return df.format(Main.getCache().getGDP_DATA().getInflation()) + "%";
            }
        }
        if (identifier.contains("_PRICE") || identifier.contains("_SELLPRICE") ||
         identifier.contains("_BUYSLEFT") || identifier.contains("_SELLSLEFT")){
            for (String item : Main.getCache().getITEMS().keySet()){
                if (identifier.equals(item + "_PRICE")){
                    return Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(item, false));
                }
                if (identifier.equals(item + "_SELLPRICE")){
                    return Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(item, true));
                }
                if (identifier.equals(item + "_BUYSLEFT")){
                    return Integer.toString(Main.getCache().getBuysLeft(item, player));
                }
                if (identifier.equals(item + "_SELLSLEFT")){
                    return Integer.toString(Main.getCache().getSellsLeft(item, player));
                }
            }
            for (String item : Main.getCache().getENCHANTMENTS().keySet()){
                if (identifier.equals(item + "_PRICE")){
                    return Config.getCurrencySymbol() + df.format(Main.getCache().getEnchantmentPrice(item, false));
                }
                if (identifier.equals(item + "_SELLPRICE")){
                    return Config.getCurrencySymbol() + df.format(Main.getCache().getEnchantmentPrice(item, true));
                }
            }
        }
        if (identifier.contains("TOP_MOVERS_")){
            ConcurrentHashMap<String, Double> input = Main.getCache().getPERCENTAGE_CHANGES();
            List<String> values = new ArrayList<>(input.keySet());
            Collections.sort(values, new Comparator<String>() {
                public int compare(String a, String b) {
                  return (int)(input.get(b)*10000000 - input.get(a)*10000000);
                }
            });
            if (identifier.contains("TOP_MOVERS_ITEM_")){
                if (identifier.contains("-")){
                    return values.get(values.size()-1-Integer.parseInt(identifier.replace("TOP_MOVERS_ITEM_-", "")));
                }
                return values.get(Integer.parseInt(identifier.replace("TOP_MOVERS_ITEM_", "")));
            }
            if (identifier.contains("TOP_MOVERS_CHANGE_")){
                if (identifier.contains("-")){
                    return df.format(input.get(values.get(values.size()-1-Integer.parseInt(identifier.replace("TOP_MOVERS_CHANGE_-", ""))))) + "%";
                }
                return df.format(input.get(values.get(Integer.parseInt(identifier.replace("TOP_MOVERS_CHANGE_", ""))))) + "%";
            }
        }
        return null;
    }
    
}
