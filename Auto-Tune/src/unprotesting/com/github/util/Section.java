package unprotesting.com.github.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;

import unprotesting.com.github.Main;

public class Section {
    
    public List<String> items;
    public String name;
    public Material image = Material.matchMaterial("GRASS_BLOCK");

    public Section(String name){
        this.name = name;
        items = new ArrayList<String>();
        for (String section : Main.getShopConfig().getConfigurationSection("sections").getKeys(false)){
            if (section.equals(name)){
                image = Material.matchMaterial(Main.getShopConfig().getConfigurationSection("sections." + section).getString("block"));
                for (String shop : Main.getShopConfig().getConfigurationSection("shops").getKeys(false)){
                    String shopSection = Main.getShopConfig().getConfigurationSection("shops." + shop).getString("section");
                    if (shopSection.equals(section)){
                        Main.log(shop);
                        items.add(shop);
                    }
                }
            }
        }
    }
}
