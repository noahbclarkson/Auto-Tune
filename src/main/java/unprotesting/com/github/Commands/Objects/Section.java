package unprotesting.com.github.Commands.Objects;

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
    private String name;
    @Getter
    private Material image;
    @Getter
    private boolean back;

    public Section(String name, String material, boolean back){
        this.name = name;
        this.back = back;
        this.image = Material.matchMaterial(material);
        this.items = new ArrayList<String>();
        ConfigurationSection shops = Main.dfiles.getShops().getConfigurationSection("shops");
        for (String key : shops.getKeys(false)){
            ConfigurationSection inner = shops.getConfigurationSection(key);
            if (inner.getString("section").equals(this.name)){
                this.items.add(key);
            }
        }
    }
    
}
