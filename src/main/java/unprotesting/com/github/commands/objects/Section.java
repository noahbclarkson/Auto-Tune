package unprotesting.com.github.commands.objects;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;

@Data
public class Section {

  private String name;
  private String displayName;
  private List<SectionItemData> items;
  private Material image;
  private Material background;
  private boolean back;
  private boolean enchantmentSection;
  private int position;

  /**
   * Creates a new Section object.
   * The section object is used to store the data for a section.
   * @param section The configuration section to load from.
   * @param name The name of the section.
   */
  public Section(ConfigurationSection section, String name) {

    this.displayName = ChatColor.translateAlternateColorCodes(
      '&', section.getString("display-name", "&6" + name));

    this.background = Material.matchMaterial(
      section.getString("background", "GRAY_STAINED_GLASS_PANE"));

    this.name = name;
    this.image = Material.matchMaterial(section.getString("block", "BARRIER"));
    this.image = this.image == null ? Material.BARRIER : this.image;
    this.back = section.getBoolean("back-menu-button-enabled", true);
    this.enchantmentSection = name.equalsIgnoreCase("Enchantments");
    this.position = section.getInt("position", 0);
    this.items = new ArrayList<SectionItemData>();

    if (!this.isEnchantmentSection()) {

      ConfigurationSection shops = Main.getInstance().getDataFiles()
          .getShops().getConfigurationSection("shops");

      for (String key : shops.getKeys(false)) {

        ConfigurationSection inner = shops.getConfigurationSection(key);

        if (inner.getString("section").equals(this.name)) {

          this.items.add(new SectionItemData(key, 
              new ItemStack(Material.matchMaterial(key)).displayName(),
              CollectFirstSetting.valueOf(inner.getString(
              "collect-first-setting", "NONE").toLowerCase())));

        }

      }
      return;
    } 

    ConfigurationSection config = Main.getInstance().getDataFiles().getEnchantments()
        .getConfigurationSection("enchantments");

    for (String key : config.getKeys(false)) {

      CollectFirstSetting setting = CollectFirstSetting.none;
      Component displayName = Component.text("Enchantment Error");

      try {

        CollectFirstSetting.valueOf(config.getString(
            "collect-first-setting", "none").toLowerCase());

      } catch (Exception e) {

        Main.getInstance().getLogger().warning(
            "Error in enchantments.yml: collect first setting for " + key);
        
         
      }

      try {

        displayName = Enchantment.getByKey(
            NamespacedKey.minecraft(key.toLowerCase())).displayName(1);

      } catch (Exception e) {

        Main.getInstance().getLogger().warning(
            "Error in enchantments.yml: display name for " + key);
        
        e.printStackTrace();   
        continue;

      }

      this.items.add(new SectionItemData(key, displayName, setting));

    }

  }


  public static enum CollectFirstSetting {

    none,
    server_wide,
    each_player

  }

  /**
   * Get the position of the last section in the list.
   * @param sections The list of sections.
   * @return The position of the last section.
   */
  public static int getHighest(List<Section> sections) {

    int output = 0;

    for (Section section : sections) {

      if (section.getPosition() > output) {
        output = section.getPosition();
      }

    }

    return output;

  }


}
