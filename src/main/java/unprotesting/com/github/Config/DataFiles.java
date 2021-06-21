package unprotesting.com.github.config;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.YamlConfiguration;

import lombok.Getter;

import org.bukkit.configuration.InvalidConfigurationException;

public class DataFiles {

    @Getter
    private File[] files;
    private final String[] filenames = {"config.yml", "shops.yml", "enchantments.yml", "playerdata.yml", "web/trade.html", "web/trade-short.html", "web/favicon.ico"};
    private YamlConfiguration[] configs;

    public DataFiles(File dataFolder){
        this.files = new File[7];
        this.configs = new YamlConfiguration[4];
        for (int i = 0; i < 4; i++){
            this.configs[i] = new YamlConfiguration();
        }
        int i = 0;
        for (;i < 7; i++){
            File file = new File(dataFolder, filenames[i]);
            files[i] = file;
        }
    }

    public void loadConfigs(){
        try{
            for (int i = 0; i < 4; i++){
                configs[i].load(this.files[i]);
            }
        }
        catch(InvalidConfigurationException | IOException e){
            e.printStackTrace();
        }
        Config.loadDefaults();
    }

    public String[] getFileNames(){
        return this.filenames;
    }

    public YamlConfiguration getConfig(){
        return this.configs[0];
    }

    public YamlConfiguration getShops(){
        return this.configs[1];
    }

    public YamlConfiguration getEnchantments(){
        return this.configs[2];
    }

    public YamlConfiguration getPlayerData(){
        return this.configs[3];
    }

    public void setPlayerData(YamlConfiguration config){
        this.configs[3] = config;
        try {
            this.configs[3].save(this.files[3]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
