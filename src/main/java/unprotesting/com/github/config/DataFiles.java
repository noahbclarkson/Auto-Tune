package unprotesting.com.github.config;

import java.io.File;
import java.io.IOException;

import lombok.Getter;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import unprotesting.com.github.Main;

public class DataFiles {

  @Getter
  private File[] files;
  private YamlConfiguration[] configs;

  @Getter
  private final String[] fileNames = { "config.yml", "shops.yml", "enchantments.yml",
      "playerdata.yml", "messages.yml", "web/trade.html", "web/favicon.ico" };

  /**
   * Initializes the data files.
   * @param dataFolder The plugin data folder.
   */
  public DataFiles(File dataFolder) {

    this.files = new File[7];
    this.configs = new YamlConfiguration[5];

    for (int i = 0; i < 5; i++) {
      this.configs[i] = new YamlConfiguration();
    }

    for (int i = 0; i < files.length; i++) {

      File file = new File(dataFolder, fileNames[i]);
      files[i] = file;

    }

  }

  /**
   * Loads the configs for the data files.
   */
  public void loadConfigs() {

    try {

      for (int i = 0; i < 5; i++) {
        configs[i].load(files[i]);
      }

    } catch (InvalidConfigurationException | IOException e) {
      e.printStackTrace();
    }

    new Config();

  }

  public YamlConfiguration getConfig() {
    return configs[0];
  }

  public YamlConfiguration getShops() {
    return configs[1];
  }

  public YamlConfiguration getEnchantments() {
    return configs[2];
  }

  public YamlConfiguration getPlayerData() {
    return configs[3];
  }

  public YamlConfiguration getMessages() {
    return configs[4];
  }

  /**
   * Set the playerdata YamlConfiguration.
   * @param config The playerdata YamlConfiguration.
   */
  public void setPlayerData(YamlConfiguration config) {

    configs[3] = config;

    try {
      configs[3].save(files[3]);
    } catch (IOException e) {
      Main.getInstance().getLogger().severe("Could not save playerdata.yml!");
    }

  }



}
