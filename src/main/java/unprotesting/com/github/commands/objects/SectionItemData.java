package unprotesting.com.github.commands.objects;

import lombok.Data;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import unprotesting.com.github.commands.objects.Section.CollectFirstSetting;

@Data
public class SectionItemData {

  private String name;
  private Component displayName;
  private Material image;
  private CollectFirstSetting setting;

  /**
   * Creates a new SectionItemData object.
   * @param name The name of the item.
   * @param displayName The display name of the item.
   * @param setting The collect first setting of the item.
   */
  public SectionItemData(String name, Component displayName, CollectFirstSetting setting) {
    this.name = name;
    this.image = Material.matchMaterial(name);
    this.displayName = displayName;
    this.setting = setting;
  }

}
