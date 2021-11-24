package unprotesting.com.github.commands.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import lombok.Data;
import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;

@Data
public class Section {

    private String name,
                   displayName;
    private List<SectionItemData> items;
    private Material image, 
                     background;
    private boolean back, 
                    enchantmentSection;
    private int position;

    public Section(ConfigurationSection section, String name) {
       this.name = name;
       this.displayName = ChatColor.translateAlternateColorCodes('&', section.getString("display-name", "&6" + name));
       this.image = Material.matchMaterial(section.getString("block", "BARRIER"));
       this.image = this.image == null ? Material.BARRIER : this.image;
       this.back = section.getBoolean("back-menu-button-enabled", true);
       this.enchantmentSection = name.equalsIgnoreCase("Enchantments");
       this.background = Material.matchMaterial(section.getString("background", "GRAY_STAINED_GLASS_PANE"));
       this.position = section.getInt("position", 0);
       this.items = new ArrayList<SectionItemData>();
       if (!this.isEnchantmentSection()){
            ConfigurationSection shops = Main.getDataFiles().getShops().getConfigurationSection("shops");
            for (String key : shops.getKeys(false)){
                ConfigurationSection inner = shops.getConfigurationSection(key);
                if (inner.getString("section").equals(this.name)){
                    this.items.add(new SectionItemData(key, getItemDisplayName(key), CollectFirstSetting.valueOf(inner.getString("collect-first-setting", "NONE").toUpperCase())));
                }
            }
       }
       else{
            ConfigurationSection config = Main.getDataFiles().getEnchantments().getConfigurationSection("enchantments");
            for (String key : config.getKeys(false)){
                this.items.add(new SectionItemData(key, getItemDisplayName(key), CollectFirstSetting.valueOf(config.getString("collect-first-setting", "NONE").toUpperCase())));
            }
       }
    }

    public static String getItemDisplayName(String item_name){
        String output = item_name;
        for (String key : Main.getDataFiles().getShops().getConfigurationSection("shops").getKeys(false)){
            if (key.equalsIgnoreCase(item_name)){
                return ChatColor.translateAlternateColorCodes('&', Main.getDataFiles().getShops()
                .getConfigurationSection("shops").getConfigurationSection(key).getString("display-name", itemNameToDisplayName(output)));
            }
        }
        for (String ench : Main.getDataFiles().getEnchantments().getConfigurationSection("enchantments").getKeys(false)){
            if (ench.equalsIgnoreCase(item_name)){
                return ChatColor.translateAlternateColorCodes('&', Main.getDataFiles().getEnchantments()
                .getConfigurationSection("enchantments").getConfigurationSection(ench).getString("display-name", itemNameToDisplayName(output)));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', itemNameToDisplayName(output));
    }

    public static enum CollectFirstSetting{
        NONE,
        SERVER_WIDE,
        EACH_PLAYER
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

    public static String itemNameToDisplayName(String item_name){
        String output = item_name;
        if (item_name.contains("_")){
            String[] split = item_name.split("_");
            output = "";
            for (String s : split){
                output += s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase() + " ";
            }
            output = output.substring(0, output.length() - 1);
        }
        else{
            output = item_name.substring(0, 1).toUpperCase() + item_name.substring(1).toLowerCase();
        }
        return "&6" + output;
    }
    
    
}
