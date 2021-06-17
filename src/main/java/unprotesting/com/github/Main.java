package unprotesting.com.github;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import lombok.Setter;
import net.ess3.api.IEssentials;
import unprotesting.com.github.API.HttpPostRequestor;
import unprotesting.com.github.Commands.SellCommand;
import unprotesting.com.github.Commands.ShopCommand;
import unprotesting.com.github.Commands.TradeCommand;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Config.DataFiles;
import unprotesting.com.github.Data.CSV.CSVHandler;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Economy.EconomyFunctions;
import unprotesting.com.github.Events.APIKeyCheckEvent;
import unprotesting.com.github.Events.PriceUpdateEvent;
import unprotesting.com.github.LocalServer.LocalServer;
import unprotesting.com.github.Logging.Logging;

/*  
    Main initialization file for Auto-Tune
    Contains startup and shutdown methods
*/

public class Main extends JavaPlugin{

    @Getter
    private static DataFiles dfiles;
    @Getter
    private static Database database;
    @Getter
    private static LocalDataCache cache;
    @Getter
    private static Main INSTANCE;
    @Getter @Setter
    private static HttpPostRequestor requestor;
    @Getter
    private static IEssentials ess;

    public static LocalServer server;

    @Override
    public void onDisable(){
        updateTimePeriod();
        if (database != null){
            database.close();
        }
    }

    @Override
    public void onEnable(){
        INSTANCE = this;
        checkEconomy();
        getEssentials();
        setupDataFiles();
        checkAPIKey();
        setupDatabase();
        initCache();
        setupCommands();
        setupEvents();
        setupServer();
    }
    
    public static void updateTimePeriod(){
        TimePeriod TP = new TimePeriod();
        TP.addToMap();
        cache = new LocalDataCache();
        CSVHandler.writeCSV();
    }

    private void initCache(){
        cache = new LocalDataCache();
    }

    private void setupDatabase(){
        database = new Database();
    }

    private void checkEconomy(){
        if (!EconomyFunctions.setupLocalEconomy(this.getServer())){
            Logging.error(1);
            closePlugin();
            return;
        }
    }

    private void setupDataFiles(){
        dfiles = new DataFiles(getDataFolder());
        for (int i = 0; i < 6; i++){
            if (!dfiles.getFiles()[i].exists()){
                saveResource(dfiles.getFileNames()[i], false);
            }
        }
        dfiles.loadConfigs();
    }

    private void setupCommands(){
        this.getCommand("shop").setExecutor(new ShopCommand());
        this.getCommand("sell").setExecutor(new SellCommand());
        this.getCommand("trade").setExecutor(new TradeCommand());
    }

    private void setupServer(){
        try {
            server = new LocalServer();
        } catch (IOException e) {e.printStackTrace();}
    }

    private void checkAPIKey(){
        Bukkit.getScheduler().runTask(this, ()
         -> Bukkit.getPluginManager().callEvent(new APIKeyCheckEvent()));
    }

    private void setupEvents(){
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)),
          Config.getTimePeriod()*500, Config.getTimePeriod()*1200);
    }

    private void getEssentials(){
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin != null){
            ess = (IEssentials) plugin;
        }
    }

    public static void closePlugin(){
        getINSTANCE().getServer().getPluginManager().disablePlugin(getINSTANCE());
    }
    
}
