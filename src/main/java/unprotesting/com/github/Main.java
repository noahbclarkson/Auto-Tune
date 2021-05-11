package unprotesting.com.github;

import java.io.IOException;

import org.bukkit.plugin.java.JavaPlugin;

import unprotesting.com.github.Config.DataFiles;
import unprotesting.com.github.Data.CSV.CSVHandler;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Economy.EconomyFunctions;
import unprotesting.com.github.LocalServer.LocalServer;
import unprotesting.com.github.Logging.Logging;

/*  
    Main initialization file for Auto-Tune
    Contains startup and shutdown methods
*/

public class Main extends JavaPlugin{

    public static DataFiles dfiles;
    public static Database database;
    public static LocalDataCache cache;
    public static LocalServer server;

    @Override
    public void onDisable(){
    }

    @Override
    public void onEnable(){
        Logging.log("ENABLING AUTO-TUNE");
        checkEconomy();
        setupDataFiles();
        setupDatabase();
        cache = new LocalDataCache();
        TimePeriod TP = new TimePeriod();
        TP.addToMap();
    }

    private void setupDatabase(){
        database = new Database();
    }

    private void checkEconomy(){
        if (!EconomyFunctions.setupLocalEconomy(this.getServer())){
            Logging.error(1);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    public void setupDataFiles(){
        dfiles = new DataFiles(getDataFolder());
        dfiles.loadConfigs();
    }





    
}
