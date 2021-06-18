package unprotesting.com.github.commands.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import unprotesting.com.github.Main;

public class Section {

    @Getter
    private List<String> items;
    @Getter
    private String name,
                   background;
    @Getter
    private Material image;
    @Getter
    private boolean back,
                    enchantmentSection;
    @Getter
    private int position;

    public Section(String name, String material, boolean back, int position, String background){
    this.background = background;
        this.position = position;
        this.name = name;
        this.back = back;
        this.enchantmentSection = false;
        this.image = Material.matchMaterial(material);
        this.items = new ArrayList<String>();
        if (!this.name.equals("Enchantments")){
            ConfigurationSection shops = Main.getDfiles().getShops().getConfigurationSection("shops");
            for (String key : shops.getKeys(false)){
                ConfigurationSection inner = shops.getConfigurationSection(key);
                if (inner.getString("section").equals(this.name)){
                    this.items.add(key);
                }
            }
        }
        else{
            this.enchantmentSection = true;
            this.image = Material.matchMaterial(Main.getDfiles().getEnchantments().getConfigurationSection("config").getString("block"));
            ConfigurationSection config = Main.getDfiles().getEnchantments().getConfigurationSection("enchantments");
            for (String key : config.getKeys(false)){
                this.items.add(key);
            }
        }
    }

    public static int getHighest(List<Section> sections){
        int output = 0;
        for (Section section : sections){
            if (section.getPosition() > output){
                output = section.getPosition();
            }
        }
        return output;
    }
    
}
