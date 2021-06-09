package unprotesting.com.github;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import lombok.Getter;
import lombok.Setter;
import net.ess3.api.IEssentials;
import unprotesting.com.github.API.HttpPostRequestor;
import unprotesting.com.github.Commands.SellCommand;
import unprotesting.com.github.Commands.ShopCommand;
import unprotesting.com.github.Config.DataFiles;
import unprotesting.com.github.Data.CSV.CSVHandler;
import unprotesting.com.github.Data.Ephemeral.LocalDataCache;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Economy.EconomyFunctions;
import unprotesting.com.github.Events.APIKeyCheckEvent;
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
        database.close();
    }

    @Override
    public void onEnable(){
        checkEconomy();
        getEssentials();
        setupDataFiles();
        setupDatabase();
        startTimePeriod();
        setupCommands();
        setupServer();
    }
    
    public static void updateTimePeriod(){
        TimePeriod TP = new TimePeriod();
        TP.addToMap();
        CSVHandler.writeCSV();
        cache = new LocalDataCache();
    }

    private void startTimePeriod(){
        cache = new LocalDataCache();
        TimePeriod TP = new TimePeriod();
        TP.addToMap();
        CSVHandler.writeCSV();
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
    }

    private void setupServer(){
        try {
            server = new LocalServer();
        } catch (IOException e) {e.printStackTrace();}
    }

    private void checkAPIKey(){
        APIKeyCheckEvent event = new APIKeyCheckEvent();
        Bukkit.getScheduler().runTaskAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(event));
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
