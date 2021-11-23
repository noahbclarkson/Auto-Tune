package unprotesting.com.github.commands.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;

public class Section {

    @Getter
    private List<String> items,
                         displayNames;
    @Getter
    private String name,
                   background,
                   displayName;
    @Getter
    private Material image;
    @Getter
    private boolean back,
                    enchantmentSection;
    @Getter
    private int position;

    public Section(String name, String material, boolean back, int position, String background, String displayName){
        this.background = background;
        this.position = position;
        this.name = name;
        this.back = back;
        this.enchantmentSection = false;
        this.image = Material.matchMaterial(material);
        if (this.image == null){
            this.image = Material.BARRIER;
        }
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.items = new ArrayList<String>();
        this.displayNames = new ArrayList<String>();
        if (!this.name.equals("Enchantments")){
            ConfigurationSection shops = Main.getDataFiles().getShops().getConfigurationSection("shops");
            for (String key : shops.getKeys(false)){
                ConfigurationSection inner = shops.getConfigurationSection(key);
                if (inner.getString("section").equals(this.name)){
                    this.items.add(key);
                    this.displayNames.add(ChatColor.translateAlternateColorCodes('&', inner.getString("display-name", "&g" + key)));
                }
            }
        }
        else{
            this.enchantmentSection = true;
            ConfigurationSection config = Main.getDataFiles().getEnchantments().getConfigurationSection("enchantments");
            for (String key : config.getKeys(false)){
                this.items.add(key);
                this.displayNames.add(ChatColor.translateAlternateColorCodes('&', config.getConfigurationSection(key).getString("display-name", "&g" + key)));
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
