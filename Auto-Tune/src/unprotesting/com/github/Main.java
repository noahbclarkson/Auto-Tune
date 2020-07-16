package unprotesting.com.github;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.sun.net.httpserver.HttpServer;
import unprotesting.com.github.Commands.AutoTuneCommand;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.JoinEventHandler;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;
import unprotesting.com.github.util.StaticFileHandler;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import lombok.Getter;
import lombok.Setter;

public final class Main extends JavaPlugin implements Listener {

    JavaPlugin instance = this;

    @Getter
    public static Main INSTANCE;

    private static final Logger log = Logger.getLogger("Minecraft");
    public static Economy econ;
    private static JavaPlugin plugin;
    File playerdata = new File("plugins/Auto-Tune/", "playerdata.yml");
    public static final String BASEDIR = "plugins/Auto-Tune/Trade";
    public static final String BASEDIRMAIN = "plugins/Auto-Tune/data.csv";
    public FileConfiguration playerDataConfig;
    public final String playerdatafilename = "playerdata.yml";

    public static DB db, memDB;

    public static ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>> map;

    public static HTreeMap<Integer, String> memMap;

    public static ConcurrentHashMap<String, ConcurrentHashMap<Integer, Double[]>> tempmap;

    public static ConcurrentMap<Integer, Material> ItemMap;

    @Getter
    private File configf, shopf;

    public String basicVolatilityAlgorithim;
    public String priceModel;


    @Getter
    @Setter
    private FileConfiguration mainConfig, shopConfig;

    @Getter
    @Setter
    public static Integer materialListSize;


    @Override
    public void onDisable() {
        cancelAllTasks(this);
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    private void cancelAllTasks(Main main) {
    }

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(new JoinEventHandler(), this);
        createFiles();
        INSTANCE = this;
        if (!setupEconomy()) {
            log.severe(
                    String.format("Disabled Auto-Tune due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadDefaults();
        if (Config.isWebServer()) {
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
                server.createContext("/static", new StaticFileHandler(BASEDIR));
                server.setExecutor(null);
                server.start();
                log.info("[Auto Tune] Web server has started on port " + Config.getPort());

            } catch (IOException e) {
                debugLog("Error Creating Server on port: " + Config.getPort() + ". Please try restarting or changing your port.");
                e.printStackTrace();
            }
        }
        DB db = DBMaker.fileDB("data.db").closeOnJvmShutdown().make();
        map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
        playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
        DB memDb = DBMaker.memoryDB().closeOnJvmShutdown().make();
        memMap = db.hashMap("memMap", Serializer.INTEGER, Serializer.STRING).createOrOpen();
        saveplayerdata();
        loadShopsFile();
        loadShopData();
        materialListSize = memMap.size();
        this.getCommand("at").setExecutor(new AutoTuneCommand());
        this.getCommand("shop").setExecutor(new AutoTuneGUIShopUserCommand());
        basicVolatilityAlgorithim = Config.getBasicVolatilityAlgorithim();
        priceModel = Config.getPricingModel().toString();
        if (priceModel.contains("Basic") == true){
            log("Loaded Basic Price Algorithim");
            if (basicVolatilityAlgorithim.contains("Variable") == true){
                log("Loaded Algorithim under Variable Configuration");
        }
                if (basicVolatilityAlgorithim.contains("fixed") == true){
                log("Loaded Algorithim under Variable Configuration");
                }
        }
        runnable();
    }


    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static String[] convert(Set<String> setOfString) {

        // Create String[] from setOfString
        String[] arrayOfString = setOfString

                // Convert Set of String
                // to Stream<String>
                .stream()

                // Convert Stream<String>
                // to String[]
                .toArray(String[]::new);

        // return the formed String[]
        return arrayOfString;
    }

    public Double buys = 0.0;
    public Double sells = 0.0;

    public double tempbuys = 0.0;
    public double tempsells = 0.0;

    public void runnable(){
        new BukkitRunnable(){
            @Override
            public void run(){
                debugLog("Starting price calculation task... ");
                debugLog("Price algorithim settings: ");
                debugLog("BasicMaxFixedVolatility: " + Config.getBasicMaxFixedVolatility());
                debugLog("BasicMinFixedVolatility: " + Config.getBasicMinFixedVolatility());
                tempbuys = 0.0;
                tempsells = 0.0;
                buys = 0.0;
                sells = 0.0;
                Set<String> strSet = map.keySet();
                for (String str : strSet){
                    ConcurrentHashMap<Integer,Double[]> tempMap = map.get(str);
                    for (Integer key1 : tempMap.keySet()){
                        Double[] key = tempMap.get(key1);
                        tempbuys = key[1];
                        buys = buys + tempbuys;
                        tempsells = key[2];
                        sells = sells + tempsells;
                    }

                    Double avBuy = buys/(tempMap.size());
                    Double avSells = sells/(tempMap.size());
                    if (avBuy > avSells){
                        debugLog("AvBuy > AvSells for " + str);
                        Double[] temp2 = tempMap.get(tempMap.size()-1);
                        Double temp3 = temp2[0];
                        Integer tsize = tempMap.size();
                        if (priceModel.contains("Basic") == true && basicVolatilityAlgorithim.contains("Fixed") == true){
                            Double newSpotPrice = (temp3)+(((1-(avSells / avBuy)) * Config.getBasicMaxFixedVolatility() + Config.getBasicMinFixedVolatility()));
                            Double[] temporary = { newSpotPrice, 0.0, 0.0};
                            debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice) + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = " + Double.toString(avSells));
                            tempMap.put(tsize, temporary);
                            map.put(str, tempMap);
                        
                        }
                        if (priceModel.contains("Basic") == true && basicVolatilityAlgorithim.contains("Variable") == true){
                            Double newSpotPrice = (temp3)+(temp3*((1-(avSells / avBuy))* Config.getBasicMaxVariableVolatility()*0.01)) + Config.getBasicMinVariableVolatility()*0.01*temp3;
                            Double[] temporary = { newSpotPrice, 0.0, 0.0};
                            debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice) + " because Average buys = " + Double.toString(avBuy) + " and Average sells = " + Double.toString(avSells));
                            tempMap.put(tsize, temporary);
                            map.put(str, tempMap);
                        }
                    }
                    
                    if (avBuy < avSells){
                        debugLog("AvBuy < AvSells for " + str);
                        Double[] temp2 = tempMap.get(tempMap.size()-1);
                        Double temp3 = temp2[0];
                        Integer tsize = tempMap.size();
                        if (priceModel.contains("Basic") == true && basicVolatilityAlgorithim.contains("Fixed")){
                            Double newSpotPrice = (temp3)-(((1-(avBuy / avSells)) * Config.getBasicMaxFixedVolatility() + Config.getBasicMinFixedVolatility()));
                            Double[] temporary = { newSpotPrice, 0.0, 0.0};
                            debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice) + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = " + Double.toString(avSells));
                            tempMap.put(tsize, temporary);
                            map.put(str, tempMap);
                    }
                    if (priceModel.contains("Basic") == true && basicVolatilityAlgorithim.contains("Variable") == true){
                        Double newSpotPrice = (temp3)-(temp3*((1-(avBuy / avSells))* Config.getBasicMaxVariableVolatility()*0.01)) - Config.getBasicMinVariableVolatility()*0.01*temp3;
                        Double[] temporary = { newSpotPrice, 0.0, 0.0};
                        debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice) + " because Average buys = " + Double.toString(avBuy) + " and Average sells = " + Double.toString(avSells));
                        tempMap.put(tsize, temporary);
                        map.put(str, tempMap);
                    }


                }

                if (avBuy == avSells){
                    debugLog("AvBuy = AvSells for " + str);
                    Double[] temp2 = tempMap.get(tempMap.size()-1);
                    Double temp3 = temp2[0];
                    Integer tsize = tempMap.size();
                    if (priceModel.contains("Basic") == true){
                        Double newSpotPrice = temp3;
                        Double[] temporary = { newSpotPrice, 0.0, 0.0};
                        debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice) + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = " + Double.toString(avSells));
                        tempMap.put(tsize, temporary);
                        map.put(str, tempMap);
                }


            }
            }
            tempbuys = 0.0;
            tempsells = 0.0;
            buys = 0.0;
            sells = 0.0;
            Date date = Calendar.getInstance().getTime();
            Date newDate = addMinutesToJavaUtilDate(date, Config.getTimePeriod());
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            String strDate = dateFormat.format(newDate);
            debugLog("Done running price Algorithim, a new check will occur at: " + strDate);


            }

            
        }.runTaskTimerAsynchronously(Main.getINSTANCE(), Config.getTimePeriod()*20*60, Config.getTimePeriod()*20*60+1);
        

        
    }

    public void createFiles() {

        configf = new File(getDataFolder(), "config.yml");
        shopf = new File(getDataFolder(), "shops.yml");

        if (!configf.exists()) {
            configf.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        if (!shopf.exists()) {
            shopf.getParentFile().mkdirs();
            saveResource("shops.yml", false);
        }

        mainConfig = new YamlConfiguration();
        shopConfig = new YamlConfiguration();

        try {
            mainConfig.load(configf);
            shopConfig.load(shopf);

        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

    }

    public boolean onCommand(CommandSender sender, Command testcmd, String trade, String[] help) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String hostIP = "";
            try {
                URL url_name = new URL("http://bot.whatismyipaddress.com");

                BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));

                // reads system IPAddress
                hostIP = sc.readLine().trim();

                int PORT = Config.getPort();
                InetAddress address = InetAddress.getLocalHost();
                String hostName = address.getHostName();
                this.getCommand("at").setExecutor(new AutoTuneCommand());
                TextComponent message = new TextComponent(ChatColor.YELLOW + "" + ChatColor.BOLD
                        + player.getDisplayName() + ", go to http://" + hostIP + ":" + PORT + "/static/index.html");
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("Click to begin trading online").create()));
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        "http://" + hostIP + ":" + PORT + "/static/index.html"));
                player.spigot().sendMessage(message);
                player.sendMessage(ChatColor.ITALIC + "Hostname : " + hostName);
            } catch (Exception e) {
                hostIP = "Cannot Execute Properly";
            }
            return true;
        }
        return false;
    }

    public Date addMinutesToJavaUtilDate(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

    public void loadDefaults() {
        Config.setWebServer(getMainConfig().getBoolean("web-server-enabled", false));
        Config.setDebugEnabled(getMainConfig().getBoolean("debug-enabled", false));
        Config.setPort(getMainConfig().getInt("port", 8321));
        Config.setTimePeriod(getMainConfig().getInt("time-period", 10));
        Config.setMenuRows(getMainConfig().getInt("menu-rows", 3));
        Config.setServerName(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("server-name", "Survival Server - (Change this in Config)")));
        Config.setMenuTitle(
                ChatColor.translateAlternateColorCodes('&', getMainConfig().getString("menu-title", "Auto-Tune Shop")));
        Config.setPricingModel(
                ChatColor.translateAlternateColorCodes('&', getMainConfig().getString("pricing-model", "Basic")));
        Config.setBasicVolatilityAlgorithim(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("Basic-Volatility-Algorithim", "Fixed")));
        Config.setNoPermission(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("no-permission", "You do not have permission to perform this command")));
        Config.setBasicMaxFixedVolatility(getMainConfig().getDouble("Basic-Fixed-Max-Volatility", 2.00));
        Config.setBasicMaxVariableVolatility(getMainConfig().getDouble("Basic-Variable-Max-Volatility", 2.00));
        Config.setBasicMinFixedVolatility(getMainConfig().getDouble("Basic-Fixed-Min-Volatility", 0.05));
        Config.setBasicMinVariableVolatility(getMainConfig().getDouble("Basic-Variable-Min-Volatility", 0.05));
        Config.setSellPriceDifference(getMainConfig().getDouble("sell-price-difference", 2.5));
    }

    public void saveplayerdata() {
        try {
            YamlConfiguration.loadConfiguration(playerdata);
            playerDataConfig.save(playerdata);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + playerdatafilename); // shouldn't really happen, but save
                                                                                // throws the
            // exception
        }

    }

    public static void log(String input) {
        Main.getINSTANCE().getLogger().log(Level.WARNING, "[AUTO-TUNE]: " + input);
    }

    public static void debugLog(String input) {
        if (Config.isDebugEnabled()) {
            Main.getINSTANCE().getLogger().log(Level.WARNING, "[AUTO-TUNE][DEBUG]: " + input);
        }
    }


    @Getter
    @Setter
    public static Gui gui;

    public ArrayList<String> itemStringArray;

    @Getter
    public static Set<String> testset = null;

    @Getter
    public static ArrayList<String> publicItemStringArray;

    public static ConcurrentHashMap<Integer, OutlinePane> pageArray = new ConcurrentHashMap<Integer, OutlinePane>();


    public static void sendMessage(CommandSender commandSender, String message) {
        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void loadShopData(){
        Integer i = 0;
        if (testset.isEmpty() != true && testset != null){
            for (String key : Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getKeys(false)){
                debugLog("Data from shops.yml file found: " + key);
                String str = key;
                memMap.put(i, str);
                if (map.containsKey(str) == false){
                    ConfigurationSection config = Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getConfigurationSection(key);
                    Double temp_a = config.getDouble("price");
                    Double[] tempDArray = {temp_a, 0.0, 0.0};
                    ConcurrentHashMap<Integer,Double[]> tempMap3 = new ConcurrentHashMap<Integer,Double[]>();
                    tempMap3.put(0, tempDArray);
                    map.put(str, tempMap3);
                }
                i++;
            }
        }
    }

    @Getter
    public static Set<String> tempCollection;


    public void loadShopsFile(){
        testset = map.keySet();
        if (testset.isEmpty() == true){
            log("No data-file/usable-data found!");
            Integer i = 0;
            for (String key : Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getKeys(false)){
                ConfigurationSection config = Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getConfigurationSection(key);
                if (config == null){
                    log("Check the section for shop " + key + " in the shops.yml. It was not found.");
                    continue;
                }
                assert config != null;
                Double temp_a = config.getDouble("price");
                Double[] x = { temp_a, 0.0, 0.0 };
                ConcurrentHashMap<Integer, Double[]> start = (new ConcurrentHashMap<Integer, Double[]>());
                start.put(0, x);
                String str = key;
                map.put(key, start);
                debugLog("Loaded shop: " + key + " at price: " + Double.toString(temp_a));
                i++;
                }
            log("Default shops loaded from shop file");
        
        }
        if (testset.isEmpty() == false && getMainConfig().getBoolean("debug-enabled") == true){
            Integer b = testset.size();
                debugLog(b.toString() + " Items Loaded: " + testset.toString());
        }
    }

}

