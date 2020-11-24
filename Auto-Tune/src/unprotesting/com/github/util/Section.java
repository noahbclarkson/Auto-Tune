package unprotesting.com.github.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;

import unprotesting.com.github.Main;

public class Section {
    
    public List<String> items;
    public String name;
    public Material image = Material.matchMaterial("GRASS_BLOCK");
    public boolean showBackButton = true;
    public ConcurrentHashMap<String, Integer[]> itemMaxBuySell = new ConcurrentHashMap<String, Integer[]>();

    public Section(String name){
        this.name = name;
        items = new ArrayList<String>();
        for (String section : Main.getShopConfig().getConfigurationSection("sections").getKeys(false)){
            if (section.equals(name)){
                try{
                    showBackButton = Main.getShopConfig().getConfigurationSection("sections." + section).getBoolean("back-menu-button-enabled");
                }
                catch (NullPointerException ex){
                    showBackButton = true;
                }
                image = Material.matchMaterial(Main.getShopConfig().getConfigurationSection("sections." + section).getString("block"));
                for (String shop : Main.getShopConfig().getConfigurationSection("shops").getKeys(false)){
                    String shopSection = Main.getShopConfig().getConfigurationSection("shops." + shop).getString("section");
                    try{
                        if (shopSection.equals(section)){
                            items.add(shop);
                        }
                    }
                    catch(NullPointerException ex){
                        Main.log("Shop " + shop + " doesn't have a section, please input one to continue");
                    }
                }
                for (String item : items){
                    Integer maxBuy = 100000;
                    Integer maxSell = 100000;
                    Integer test = 0;
                    try{
                        maxBuy = Main.getShopConfig().getConfigurationSection("shops." + item).getInt("max-buy");
                        test = maxBuy;
                    }
                    catch(NullPointerException ex){
                        maxBuy = 100000;
                    }
                    catch(NumberFormatException ex){
                        try{
                            maxBuy = (int)Main.getShopConfig().getConfigurationSection("shops." + item).getDouble("max-buy");
                            test = maxBuy;
                        }
                        catch (NumberFormatException ex2){
                            maxSell = 100000;
                            Main.log("Can't format " + item + " for max-buy");
                        }
                    }
                    try{
                        maxSell = Main.getShopConfig().getConfigurationSection("shops." + item).getInt("max-sell");
                        test = maxSell;
                    }
                    catch(NullPointerException ex){
                        maxSell = 100000;
                    }
                    catch(NumberFormatException ex){
                        try{
                            maxSell = (int)Main.getShopConfig().getConfigurationSection("shops." + item).getDouble("max-sell");
                            test = maxSell;
                        }
                        catch (NumberFormatException ex2){
                            maxSell = 100000;
                            Main.log("Can't format " + item + " for max-sell");
                        }
                    }
                    Integer[] outputIntegerArray = {maxBuy, maxSell};
                    itemMaxBuySell.put(item, outputIntegerArray);
                }
            }
        }
    }
}
