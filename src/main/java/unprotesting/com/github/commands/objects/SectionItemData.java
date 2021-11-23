package unprotesting.com.github.commands.objects;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import lombok.Data;
import unprotesting.com.github.commands.objects.Section.CollectFirstSetting;

@Data
public class SectionItemData {

    private String name, displayName;
    private Material image;
    private CollectFirstSetting setting;

    public SectionItemData(String name, String displayName, CollectFirstSetting setting) {
        this.name = name;
        this.image = Material.matchMaterial(name);
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.setting = setting;
    }

    
    
}
