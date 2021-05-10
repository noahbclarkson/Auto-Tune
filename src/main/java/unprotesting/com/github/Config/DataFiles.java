package unprotesting.com.github.Config;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

public class DataFiles {

    private File[] files;
    private final String[] filenames = {"config.yml", "shops.yml", "enchantments.yml", "trade.html", "trade-short.html", "favicon.ico"};
    private YamlConfiguration[] configs;

    public DataFiles(File dataFolder){
        new File("plugins/Auto-Tune/web/").mkdirs();
        this.files = new File[6];
        this.configs = new YamlConfiguration[3];
        int i = 0;
        for (;i < 3; i++){
            File file = new File(dataFolder, filenames[i]);
            file.getParentFile().mkdirs();
            files[i] = file;
        }
        for (;i < 6; i++){
            File file = new File("plugins/Auto-Tune/web/", filenames[i]);
            file.getParentFile().mkdirs();
            files[i] = file;
        }
    }

    public void loadConfigs(){
        try{
            for (int i = 0; i < 3; i++){
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


    
}
