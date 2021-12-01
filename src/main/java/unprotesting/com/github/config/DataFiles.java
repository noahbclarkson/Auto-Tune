package unprotesting.com.github.config;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.YamlConfiguration;

import lombok.Getter;

import org.bukkit.configuration.InvalidConfigurationException;

public class DataFiles {

    @Getter
    private File[] files;
    private final String[] filenames = {"config.yml", "shops.yml", "enchantments.yml", "playerdata.yml", "messages.yml", "web/trade.html", "web/favicon.ico"};
    private YamlConfiguration[] configs;
    
    public DataFiles(File dataFolder){
        this.files = new File[7];
        this.configs = new YamlConfiguration[5];
        for (int i = 0; i < 5; i++){
            this.configs[i] = new YamlConfiguration();
        }
        int i = 0;
        for (;i < files.length; i++){
            File file = new File(dataFolder, filenames[i]);
            files[i] = file;
        }
    }

    public void loadConfigs(){
        try{
            for (int i = 0; i < 5; i++){
                configs[i].load(files[i]);
            }
        }
        catch(InvalidConfigurationException | IOException e){
            e.printStackTrace();
        }
        Config.loadDefaults();
    }

    public String[] getFileNames(){
        return filenames;
    }

    public YamlConfiguration getConfig(){
        return configs[0];
    }

    public YamlConfiguration getShops(){
        return configs[1];
    }

    public YamlConfiguration getEnchantments(){
        return configs[2];
    }

    public YamlConfiguration getPlayerData(){
        return configs[3];
    }

    public void setPlayerData(YamlConfiguration config){
        configs[3] = config;
        try {
            configs[3].save(files[3]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public YamlConfiguration getMessages(){
        return configs[4];
    }
    
}
