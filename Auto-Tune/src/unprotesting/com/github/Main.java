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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.sun.net.httpserver.HttpServer;

import org.bstats.bukkit.Metrics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.simple.parser.ParseException;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import unprotesting.com.github.Commands.AutoTuneAutoSellCommand;
import unprotesting.com.github.Commands.AutoTuneAutoTuneConfigCommand;
import unprotesting.com.github.Commands.AutoTuneBuyCommand;
import unprotesting.com.github.Commands.AutoTuneCommand;
import unprotesting.com.github.Commands.AutoTuneGDPCommand;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;
import unprotesting.com.github.Commands.AutoTuneLoanCommand;
import unprotesting.com.github.Commands.AutoTuneSellCommand;
import unprotesting.com.github.util.AutoSellEventHandler;
import unprotesting.com.github.util.AutoTunePlayerAutoSellEventHandler;
import unprotesting.com.github.util.CSVHandler;
import unprotesting.com.github.util.ChatHandler;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.EconomyShopConfigManager;
import unprotesting.com.github.util.EnchantmentAlgorithm;
import unprotesting.com.github.util.EnchantmentPriceHandler;
import unprotesting.com.github.util.EnchantmentSetting;
import unprotesting.com.github.util.HttpPostRequestor;
import unprotesting.com.github.util.InflationEventHandler;
import unprotesting.com.github.util.InventoryHandler;
import unprotesting.com.github.util.ItemPriceData;
import unprotesting.com.github.util.JoinEventHandler;
import unprotesting.com.github.util.Loan;
import unprotesting.com.github.util.LoanEventHandler;
import unprotesting.com.github.util.MathHandler;
import unprotesting.com.github.util.PriceCalculationHandler;
import unprotesting.com.github.util.Section;
import unprotesting.com.github.util.StaticFileHandler;
import unprotesting.com.github.util.TextHandler;
import unprotesting.com.github.util.TopMover;
import unprotesting.com.github.util.TutorialHandler;

public final class Main extends JavaPlugin implements Listener {

  JavaPlugin instance = this;

  @Getter
  public static Main INSTANCE;

  public static Section[] sectionedItems;
  private static final Logger log = Logger.getLogger("Minecraft");
  public static Economy econ;
  public static JavaPlugin plugin;
  static File playerdata = new File("plugins/Auto-Tune/", "playerdata.yml");
  public static final String BASEDIR = "plugins/Auto-Tune/web";
  public static final String BASEDIRMAIN = "plugins/Auto-Tune/data.csv";
  public static DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
  public static FileConfiguration playerDataConfig;
  public final static String playerdatafilename = "playerdata.yml";
  public static DB db, memDB, tempDB, loanDB, enchDB;
  public static HTreeMap<String, Double> tempdatadata;
  public static ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>> map;
  public static ConcurrentMap<UUID, ConcurrentHashMap<String, Integer>> maxBuyMap = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>>();
  public static ConcurrentMap<UUID, ConcurrentHashMap<String, Integer>> maxSellMap = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>>();
  public static HTreeMap<Integer, String> memMap;
  public static HTreeMap<UUID, ArrayList<Loan>> loanMap;
  public static ConcurrentHashMap<String, ConcurrentHashMap<Integer, Double[]>> tempmap;
  public static ConcurrentMap<Integer, Material> ItemMap;
  public static BukkitScheduler scheduler;
  public File folderfile;
  public static Double buys = 0.0;
  public static Double sells = 0.0;
  public static double tempbuys = 0.0;
  public static double tempsells = 0.0;
  public static Boolean locked = null;
  public static Boolean falseBool = false;
  HttpServer server;

  @Getter
  public static ConcurrentMap<String, ConcurrentHashMap<String, EnchantmentSetting>> enchMap;

  static @Getter
  private File configf;

  @Getter
  public static File shopf, tradef, tradeShortf, enchf;

  public static String basicVolatilityAlgorithim;
  public static String priceModel;

  public Boolean vaildAPIKey = false;

  @Getter
  @Setter
  public static Gui gui;

  @Getter
  @Setter
  public static Economy economy;

  public ArrayList<String> itemStringArray;

  @Getter
  public static Set<String> testset = null;

  @Getter
  public static ArrayList<String> publicItemStringArray;

  public static ConcurrentHashMap<Integer, OutlinePane> pageArray = new ConcurrentHashMap<Integer, OutlinePane>();

  @Getter
  @Setter
  static private FileConfiguration mainConfig, shopConfig, enchantmentConfig;

  @Getter
  @Setter
  public static Integer materialListSize;

  @Getter
  public static ArrayList<TopMover> topSellers;

  @Getter
  public static ArrayList<TopMover> topBuyers;

  @Getter
  public static ConcurrentHashMap<String, ItemPriceData> itemPrices = new ConcurrentHashMap<String, ItemPriceData>();

  @Override
  public void onDisable() {
    if (scheduler == null){
      scheduler = getServer().getScheduler();
    }
    if (getINSTANCE() == null){
      INSTANCE = this;
    }
    scheduler.cancelTasks(getINSTANCE());
    server.stop(3);
    closeDataFiles();
    log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
  }

  @Override
  @Deprecated
  public void onEnable() {
    Bukkit.getServer().getPluginManager().registerEvents(new JoinEventHandler(), this);
    Bukkit.getServer().getPluginManager().registerEvents(new ChatHandler(), this);
    Bukkit.getServer().getPluginManager().registerEvents(new InventoryHandler(), this);
    folderfile = new File("plugins/Auto-Tune/web/");
    folderfile.mkdirs();
    createFiles();
    File folderfileTemp = new File("plugins/Auto-Tune/temp/");
    folderfileTemp.mkdirs();
    INSTANCE = this;
    plugin = this;
    if (!setupEconomy()) {
      log.severe(String.format("Disabled Auto-Tune due to no Vault dependency found! Please make sure Vault and another economy plugin, such as EssentialsX is installed!", getDescription().getName()));
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    Config.loadDefaults();
    if (Config.isWebServer()) {
      try {
        server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
        server.createContext("/", new StaticFileHandler(BASEDIR));
        server.setExecutor(null);
        server.start();
        log.info("[Auto Tune] Web server has started on port " + Config.getPort());

      } catch (IOException e) {
        log(
            "Error Creating Server on port: " + Config.getPort() + ". Please try restarting or changing your port.");
        e.printStackTrace();
      }
    }
    int pluginId = 9687;
    Metrics metrics = new Metrics(getINSTANCE(), pluginId);
    setupDataFiles();
    if (tempdatadata.isEmpty() == true || tempdatadata.get("SellPriceDifferenceDifference") == null) {
      tempdataresetSPDifference();
    }
    ChatHandler.message = null;
    saveplayerdata();
    loadShopsFile();
    EconomyShopConfigManager.checkForOtherEconomy();
    if (EconomyShopConfigManager.otherEconomyPresent){
      try {
        EconomyShopConfigManager.loadShopsFile((Config.getEconomyShopConfig().toLowerCase()));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (!EconomyShopConfigManager.otherEconomyPresent){
    loadShopData();
    }
    materialListSize = memMap.size();
    vaildAPIKey = HttpPostRequestor.checkAPIKey();
    if (!vaildAPIKey){
      log.severe(String.format("Disabled due to invalid API key", getDescription().getName()));
      debugLog("Please check API key is vaild in config.yml");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    if (vaildAPIKey) {
      log("API-Key found in database. Continuing to load Auto-Tune on " + Config.getServerName());
    }
    TutorialHandler.loadMessages();
    setupMaxBuySell();
    this.getCommand("at").setExecutor(new AutoTuneCommand());
    this.getCommand("shop").setExecutor(new AutoTuneGUIShopUserCommand());
    this.getCommand("sell").setExecutor(new AutoTuneSellCommand());
    this.getCommand("autosell").setExecutor(new AutoTuneAutoSellCommand());
    this.getCommand("loan").setExecutor(new AutoTuneLoanCommand());
    this.getCommand("atconfig").setExecutor(new AutoTuneAutoTuneConfigCommand());
    this.getCommand("gdp").setExecutor(new AutoTuneGDPCommand());
    this.getCommand("buy").setExecutor(new AutoTuneBuyCommand());
    basicVolatilityAlgorithim = Config.getBasicVolatilityAlgorithim();
    priceModel = Config.getPricingModel().toString();
    TextHandler.sendPriceModelData(priceModel);
    scheduler = getServer().getScheduler();
    if (Config.isAutoSellEnabled()){
    scheduler.scheduleSyncRepeatingTask(getINSTANCE(), new AutoSellEventHandler(), Config.getAutoSellUpdatePeriod() * 5,
        Config.getAutoSellUpdatePeriod());
    scheduler.scheduleSyncRepeatingTask(getINSTANCE(), new AutoTunePlayerAutoSellEventHandler(),
        Config.getAutoSellProfitUpdatePeriod() + 20, Config.getAutoSellProfitUpdatePeriod());
    }
    scheduler.scheduleAsyncRepeatingTask(getINSTANCE(), new TutorialHandler(), (Config.getTutorialMessagePeriod()*20), (Config.getTutorialMessagePeriod()*20));
    scheduler.scheduleAsyncRepeatingTask(getINSTANCE(), new LoanEventHandler(), 10,
        (int)Config.getInterestRateUpdateRate());
    if ((Config.getInflationMethod().contains("Mixed") || Config.getInflationMethod().contains("Dynamic"))
        && Config.isInflationEnabled()) {
      scheduler.scheduleAsyncRepeatingTask(getINSTANCE(), new InflationEventHandler(),
          Config.getDynamicInflationUpdatePeriod() + 40, Config.getDynamicInflationUpdatePeriod());
    }
    if (Config.isSellPriceDifferenceVariationEnabled()) {
      Config.setSellPriceDifference(
          Config.getSellPriceDifferenceVariationStart() - tempdatadata.get("SellPriceDifferenceDifference"));
      SellDifrunnable();
    }
    loadSections();
    debugLog("Loading Enchatments..");
    EnchantmentAlgorithm.loadEnchantmentSettings();
    debugLog("Loaded " + enchMap.get("Auto-Tune").size() + " enchantments");
    AutoTuneBuyCommand.shopTypes.add("enchantments");
    PriceCalculationHandler.loadItemPriceData();
    scheduler.scheduleAsyncRepeatingTask(getINSTANCE(), new PriceCalculationHandler(),  Config.getTimePeriod() * 600,  Config.getTimePeriod() * 1200);
    scheduler.scheduleAsyncRepeatingTask(getINSTANCE(), new EnchantmentPriceHandler(), 900*Config.getTimePeriod(), (Config.getTimePeriod()*2400));
    if (Config.isSendPlayerTopMoversOnJoin()){loadTopMovers();};
  }

  private boolean setupEconomy() {
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
    if (rsp == null) {
      return false;
    }
    econ = rsp.getProvider();
    setEconomy(econ);
    return econ != null;
  }

  public static String[] convert(Set<String> setOfString) {
    String[] arrayOfString = setOfString.stream().toArray(String[]::new);
    return arrayOfString;
  }

  public static ConcurrentHashMap<String, Integer> loadMaxStrings(ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>> mainMap){
    ConcurrentHashMap<String, Integer> maxMap = new ConcurrentHashMap<String, Integer>();
    for (String str : mainMap.keySet()){
    maxMap.put(str, 0);
    }
    return maxMap;
  }

  public static void tempdataresetSPDifference() {
    Main.tempdatadata.put("SellPriceDifferenceDifference", 0.0);
  }

  public static void setupMaxBuySell(){
    for (OfflinePlayer p : Bukkit.getOnlinePlayers()){
      maxBuyMap.put(p.getUniqueId(), loadMaxStrings(map));
      maxSellMap.put(p.getUniqueId(), loadMaxStrings(map));
    }
  }

  public void SellDifrunnable() {
    new BukkitRunnable() {
      @Override
      public void run() {
        Integer sellPriceVariationInt = Config.getSellPriceVariationUpdatePeriod();
        Double d = Double.valueOf(sellPriceVariationInt);
        Double updates = (Config.getSellPriceVariationTimePeriod() / d);
        Double variation = Config.getSellPriceDifferenceVariationStart()
            - (getMainConfig().getDouble("sell-price-difference", 2.5));
        Double updateVariation = variation / updates;
        Main.tempdatadata.put("SellPriceDifferenceDifference",
            (Main.tempdatadata.get("SellPriceDifferenceDifference")) + updateVariation);
        Config.setSellPriceDifference(
            Config.getSellPriceDifferenceVariationStart() - Main.tempdatadata.get("SellPriceDifferenceDifference"));
        Main.debugLog("Updates: " + Double.toString(updates));
        Main.debugLog("Variation: " + Double.toString(variation));
        Main.debugLog("Changed sell-price-difference by " + Double.toString(updateVariation) + " to "
            + Double.toString(Config.getSellPriceDifference()));
        if (Config.getSellPriceDifference() <= Main.getMainConfig().getDouble("sell-price-difference", 2.5)) {
          Config.setSellPriceDifference(Main.getMainConfig().getDouble("sell-price-difference", 2.5));
          debugLog("Finished sell difference change task as sell difference has reached: "
              + Main.getMainConfig().getDouble("sell-price-difference", 2.5));
          cancel();
        }
        PriceCalculationHandler.loadItemPriceData();
      }
    }.runTaskTimer(Main.getINSTANCE(), Config.getSellPriceVariationUpdatePeriod() * 20 * 60,
        Config.getSellPriceVariationUpdatePeriod() * 20 * 60);
  }

  public void loadSections(){
    int sectionAmount = getShopConfig().getConfigurationSection("sections").getKeys(false).size();
    sectionedItems = new Section[sectionAmount];
    int i = 0;
    for (String section : getShopConfig().getConfigurationSection("sections").getKeys(false)){
      sectionedItems[i] = new Section(section);
      i++;
    }
  }

  public void createFiles() {

    configf = new File(getDataFolder(), "config.yml");
    shopf = new File(getDataFolder(), "shops.yml");
    enchf = new File(getDataFolder(), "enchantments.yml");
    tradef = new File("plugins/Auto-Tune/web/", "trade.html");
    tradeShortf = new File("plugins/Auto-Tune/web/", "trade-short.html");

    if (!configf.exists()) {
      configf.getParentFile().mkdirs();
      saveResource("config.yml", false);
    }

    if (!tradef.exists()) {
      tradef.getParentFile().mkdirs();
      saveResource("web/trade.html", false);
    }

    if (!tradeShortf.exists()) {
      tradeShortf.getParentFile().mkdirs();
      saveResource("web/trade-short.html", false);
    }

    if (!shopf.exists()) {
      shopf.getParentFile().mkdirs();
      saveResource("shops.yml", false);
    }

    if (!enchf.exists()) {
      enchf.getParentFile().mkdirs();
      saveResource("enchantments.yml", false);
    }


    mainConfig = new YamlConfiguration();
    shopConfig = new YamlConfiguration();
    enchantmentConfig = new YamlConfiguration();

    try {
      mainConfig.load(configf);
      shopConfig.load(shopf);
      enchantmentConfig.load(enchf);

    } catch (InvalidConfigurationException | IOException e) {
      e.printStackTrace();
    }

  }

  public static FileConfiguration saveEssentialsFiles(){
    FileConfiguration worthyml;
    worthyml = YamlConfiguration.loadConfiguration(new File("plugins/Essentials/worth.yml"));
    try {
      worthyml.save(new File("plugins/Essentials/", "worth.yml"));
    } catch (IOException e) {
      plugin.getLogger().warning("Unable to save worth.yml"); // shouldn't really happen
    }
    return worthyml;
  }

  public static FileConfiguration saveGUIShopFiles(){
    FileConfiguration shopsyml;
    shopsyml = YamlConfiguration.loadConfiguration(new File("plugins/GUIShop/shops.yml"));
    try {
      shopsyml.save(new File("plugins/GUIShop/", "shops.yml"));
    } catch (IOException e) {
      plugin.getLogger().warning("Unable to save shops.yml"); // shouldn't really happen
    }
    return shopsyml;
  }

  public static void setupDataFiles() {
    if (Config.isChecksumHeaderBypass()) {
      Main.debugLog("Enabling checksum-header-bypass");
      db = DBMaker.fileDB("data.db").checksumHeaderBypass().fileChannelEnable().allocateStartSize(10240).transactionEnable().closeOnJvmShutdown().make();
      map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
      memDB = DBMaker.heapDB().checksumHeaderBypass().transactionEnable().closeOnJvmShutdown().make();
      memMap = memDB.hashMap("memMap", Serializer.INTEGER, Serializer.STRING).createOrOpen();
      enchDB = DBMaker.fileDB("enchantment-data.db").checksumHeaderBypass().fileChannelEnable().transactionEnable().closeOnJvmShutdown().make();
      enchMap = (ConcurrentMap<String, ConcurrentHashMap<String, EnchantmentSetting>>) enchDB.hashMap("enchMap", Serializer.STRING, Serializer.JAVA).createOrOpen();
    } else {
      db = DBMaker.fileDB("data.db").checksumHeaderBypass().fileChannelEnable().allocateStartSize(10240).transactionEnable().closeOnJvmShutdown().make();
      map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
      memDB = DBMaker.heapDB().closeOnJvmShutdown().transactionEnable().make();
      memMap = memDB.hashMap("memMap", Serializer.INTEGER, Serializer.STRING).createOrOpen();
      enchDB = DBMaker.fileDB("enchantment-data.db").closeOnJvmShutdown().fileChannelEnable().transactionEnable().make();
      enchMap = (ConcurrentMap<String, ConcurrentHashMap<String, EnchantmentSetting>>) enchDB.hashMap("enchMap", Serializer.STRING, Serializer.JAVA).createOrOpen();
    }
    playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
    tempDB = DBMaker.fileDB("plugins/Auto-Tune/temp/tempdata.db").checksumHeaderBypass().fileMmapEnableIfSupported().fileMmapPreclearDisable().cleanerHackEnable().closeOnJvmShutdown().transactionEnable().make();
    tempdatadata = tempDB.hashMap("tempdatadata", Serializer.STRING, Serializer.DOUBLE).createOrOpen();
    loanDB = DBMaker.fileDB("plugins/Auto-Tune/temp/loandata.db").checksumHeaderBypass().fileMmapEnableIfSupported().fileMmapPreclearDisable().cleanerHackEnable().transactionEnable().closeOnJvmShutdown().make();
    loanMap =  loanDB.hashMap("loanMap", Serializer.JAVA, Serializer.JAVA).createOrOpen();
    if (tempdatadata.get("GDP")==null){
      tempdatadata.put("GDP", 0.0);
    }
    topSellers = new ArrayList<TopMover>();
    topBuyers = new ArrayList<TopMover>();
  }

  public static void closeDataFiles(){
    db.commit();
    enchDB.commit();
    tempDB.commit();
    loanDB.commit();
    db.close();
    memDB.close();
    enchDB.close();
    tempDB.close();
    loanDB.close();
  }

  public static void loadTopMovers(){
    Main.topBuyers.clear();
    Main.topSellers.clear();
    for (String item : Main.map.keySet()){
      TopMover itemMover = new TopMover(item);
    }
    Collections.sort(Main.topBuyers);
    Collections.sort(Main.topSellers);
    if (Config.isDebugEnabled()){
      Main.debugLog("Top Buyers: ");
    for (TopMover mover : topBuyers){
      Main.debugLog(mover.toString());
    }
    Main.debugLog("Top Sellers: ");
    for (TopMover mover : topSellers){
      Main.debugLog(mover.toString());
    }
    }
  }

  @Deprecated
  public boolean onCommand(CommandSender sender, Command testcmd, String trade, String[] help) {
    if (sender instanceof Player) {
      Player player = (Player) sender;
      String hostIP = "";
      if (player.hasPermission("at.trade") || player.isOp()) {
        try {
          URL url_name = new URL("http://bot.whatismyipaddress.com");

          BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));

          // reads system IPAddress
          hostIP = sc.readLine().trim();

          int PORT = Config.getPort();
          InetAddress address = InetAddress.getLocalHost();
          String hostName = address.getHostName();
          TextComponent message = new TextComponent(ChatColor.YELLOW + "" + ChatColor.BOLD + player.getDisplayName()
              + ", go to http://" + hostIP + ":" + PORT + "/trade.html");
          message.setHoverEvent(
              new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click view item prices").create()));
          message.setClickEvent(
              new ClickEvent(ClickEvent.Action.OPEN_URL, "http://" + hostIP + ":" + PORT + "/trade.html"));
          player.spigot().sendMessage(message);
          TextComponent message2 = new TextComponent(ChatColor.YELLOW + "" + ChatColor.BOLD + player.getDisplayName()
              + ", go to http://" + hostIP + ":" + PORT + "/trade-short.html");
          message2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
              new ComponentBuilder("Click to view recent item prices").create()));
          message2.setClickEvent(
              new ClickEvent(ClickEvent.Action.OPEN_URL, "http://" + hostIP + ":" + PORT + "/trade-short.html"));
          player.spigot().sendMessage(message2);
          if (player.isOp()) {
            player.sendMessage(ChatColor.ITALIC + "Hostname : " + hostName);
          }
        } catch (Exception e) {
          hostIP = "Cannot Execute Properly";
        }
      } else if (!(player.hasPermission("at.trade")) && !(player.isOp())) {
        TextHandler.noPermssion(player);
      }
      return true;
    }
    return false;
  }

  public static void saveplayerdata() {
    try {
      YamlConfiguration.loadConfiguration(playerdata);
      playerDataConfig.save(playerdata);
    } catch (IOException e) {
      plugin.getLogger().warning("Unable to save " + playerdatafilename); 
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

  public static void sendMessage(CommandSender commandSender, String message) {
    commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
  }

  public void loadShopData() {
    Integer i = 0;
    if (testset.isEmpty() != true && testset != null) {
      Main.getINSTANCE();
      for (String key : Main.getShopConfig().getConfigurationSection("shops").getKeys(false)) {
        debugLog("Data from shops.yml file found: " + key);
        String str = key;
        memMap.put(i, str);
        if (map.containsKey(str) == false) {
          Main.getINSTANCE();
          ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
              .getConfigurationSection(key);
          Double temp_a = config.getDouble("price");
          Double[] tempDArray = {
            temp_a,
            0.0,
            0.0
          };
          ConcurrentHashMap < Integer,
          Double[] > tempMap3 = new ConcurrentHashMap < Integer,
          Double[] > ();
          tempMap3.put(0, tempDArray);
          map.put(str, tempMap3);
        }
        i++;
      }
    }
  }



  @Getter
  public static Set < String > tempCollection;

  public void loadShopsFile() {
    testset = map.keySet();
    if (testset.isEmpty() == true) {
      log("No data-file/usable-data found!");
      Integer i = 0;
      Main.getINSTANCE();
      for (String key : getShopConfig().getConfigurationSection("shops").getKeys(false)) {
        ConfigurationSection config = getShopConfig().getConfigurationSection("shops").getConfigurationSection(key);
        if (config == null) {
          log("Check the section for shop " + key + " in the shops.yml. It was not found.");
          continue;
        }
        assert config != null;
        Double temp_a = config.getDouble("price");
        Double[] x = {
          temp_a,
          0.0,
          0.0
        };
        ConcurrentHashMap < Integer,
        Double[] > start = (new ConcurrentHashMap < Integer, Double[] > ());
        start.put(0, x);
        map.put(key, start);
        debugLog("Loaded shop: " + key + " at price: " + Double.toString(temp_a));
        i++;
      }
      log("Default shops loaded from shop file");

    }
    if (testset.isEmpty() == false && getMainConfig().getBoolean("debug-enabled") == true) {
      Integer b = testset.size();
      debugLog(b.toString() + " Items Loaded: " + testset.toString());
    }
  }

}