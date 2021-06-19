package unprotesting.com.github;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import lombok.Setter;
import net.ess3.api.IEssentials;
import unprotesting.com.github.api.*;
import unprotesting.com.github.commands.GDPCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.commands.TradeCommand;
import unprotesting.com.github.commands.TransactionsCommand;
import unprotesting.com.github.config.*;
import unprotesting.com.github.data.csv.CSVHandler;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.economy.*;
import unprotesting.com.github.events.*;
import unprotesting.com.github.localServer.LocalServer;
import unprotesting.com.github.logging.*;

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
    @Getter @Setter
    private static String[] serverIPStrings;
    @Getter @Setter
    private static boolean correctAPIKey = false;

    public static LocalServer server;

    @Override
    public void onDisable(){
        if (cache != null){
            updateTimePeriod();
        }
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
        getIP();
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
        this.getCommand("gdp").setExecutor(new GDPCommand());
        this.getCommand("transactions").setExecutor(new TransactionsCommand());
        this.getCommand("loan").setExecutor(new LoanCommand());
    }

    private void setupServer(){
        try {
            server = new LocalServer();
        } catch (IOException e) {e.printStackTrace();}
    }

    private void checkAPIKey(){
        Bukkit.getScheduler().runTaskAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new APIKeyCheckEvent(true)));
    }

    private void getIP(){
        Bukkit.getScheduler().runTaskAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new IPCheckEvent(true)));
    }

    private void setupEvents(){
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)),
          Config.getTimePeriod()*500, Config.getTimePeriod()*1200);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new InflationUpdateEvent(true)),
           Config.getDynamicInflationUpdatePeriod(), Config.getDynamicInflationUpdatePeriod());
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
