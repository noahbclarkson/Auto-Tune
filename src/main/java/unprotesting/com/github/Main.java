package unprotesting.com.github;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import lombok.Setter;
import net.ess3.api.IEssentials;
import unprotesting.com.github.api.*;
import unprotesting.com.github.commands.AutoTuneCommand;
import unprotesting.com.github.commands.AutosellCommand;
import unprotesting.com.github.commands.GDPCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.commands.TradeCommand;
import unprotesting.com.github.commands.TransactionsCommand;
import unprotesting.com.github.config.*;
import unprotesting.com.github.data.csv.CSVHandler;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.data.ephemeral.data.MessagesData;
import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.economy.EconomyFunctions;
import unprotesting.com.github.events.async.*;
import unprotesting.com.github.events.sync.*;
import unprotesting.com.github.localServer.LocalServer;
import unprotesting.com.github.logging.Logging;
import unprotesting.com.github.util.AutoTunePlaceholderExpansion;
import unprotesting.com.github.util.bstats.Metrics;

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
    private static boolean correctAPIKey = false,
                            placeholderAPI = false;
    @Getter @Setter
    private static AutosellData autosellData;
    @Getter @Setter
    private static MessagesData MESSAGES;


    public static LocalServer server;

    @Override
    public void onDisable(){
       database.saveCacheToLastTP();
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
        initPlaceholderAPI();
        setupServer();
        setAutosellData(new AutosellData());
        setMESSAGES(new MessagesData());
        initBStats();
    }
    
    public static void updateTimePeriod(){
        TimePeriod TP = new TimePeriod();
        TP.addToMap();
        cache = new LocalDataCache();
        CSVHandler.writeCSV();
    }

    public static void updatePlayerTutorialData(String player_uuid){
        getMESSAGES().updatePlayerTutorialData(player_uuid);
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
        for (int i = 0; i < 8; i++){
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
        this.getCommand("autosell").setExecutor(new AutosellCommand());
        this.getCommand("at").setExecutor(new AutoTuneCommand());
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
        //  Asynchronous
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)),
          Config.getTimePeriod()*1200, Config.getTimePeriod()*1200);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new InflationUpdateEvent(true)),
           Config.getDynamicInflationUpdatePeriod(), Config.getDynamicInflationUpdatePeriod());
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new LoanUpdateEvent(true)),
           Config.getInterestRateUpdateRate(), Config.getInterestRateUpdateRate());
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new SellPriceDifferenceUpdateEvent(true)),
           Config.getSellPriceVariationUpdatePeriod()*1200, Config.getSellPriceVariationUpdatePeriod()*1200);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ()
         -> Bukkit.getPluginManager().callEvent(new AutosellProfitUpdateEvent(true)),
           Config.getAutoSellProfitUpdatePeriod(), Config.getAutoSellProfitUpdatePeriod());
        
        //  Synchronous
        Bukkit.getScheduler().runTaskTimer(this, ()
         -> Bukkit.getPluginManager().callEvent(new AutosellUpdateEvent()),
           Config.getAutoSellUpdatePeriod(), Config.getSellPriceVariationUpdatePeriod());
        Bukkit.getScheduler().runTaskTimer(this, ()
         -> Bukkit.getPluginManager().callEvent(new TutorialSendEvent()),
           Config.getTutorialMessagePeriod()*20, Config.getTutorialMessagePeriod()*20);

        Bukkit.getServer().getPluginManager().registerEvents(new JoinMessageEventHandler(), this);
    }

    private void getEssentials(){
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin != null){
            ess = (IEssentials) plugin;
        }
    }

    private void initPlaceholderAPI(){
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            Logging.debug("PlaceholderAPI found");
            setPlaceholderAPI(true);
            new AutoTunePlaceholderExpansion().register();
        }
    }

    private void initBStats(){
        int pluginId = 9687;
        new Metrics(getINSTANCE(), pluginId);
    }

    public static void closePlugin(){
        getINSTANCE().getServer().getPluginManager().disablePlugin(getINSTANCE());
    }
    
}
