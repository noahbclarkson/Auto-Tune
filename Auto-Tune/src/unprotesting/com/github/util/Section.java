package unprotesting.com.github.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

import unprotesting.com.github.Main;

public class Section {
    
    public List<String> items;
    public String name;
    public Material image = Material.matchMaterial("GRASS_BLOCK");
    public boolean showBackButton = true;

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
            }
        }
    }
}
