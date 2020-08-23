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
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.sun.net.httpserver.HttpServer;

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
import unprotesting.com.github.Commands.AutoTuneCommand;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;
import unprotesting.com.github.Commands.AutoTuneLoanCommand;
import unprotesting.com.github.Commands.AutoTuneLoansCommand;
import unprotesting.com.github.Commands.AutoTunePaybackLoanCommand;
import unprotesting.com.github.Commands.AutoTuneSellCommand;
import unprotesting.com.github.util.AutoSellEventHandler;
import unprotesting.com.github.util.AutoTunePlayerAutoSellEventHandler;
import unprotesting.com.github.util.CSVHandler;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.HttpPostRequestor;
import unprotesting.com.github.util.InflationEventHandler;
import unprotesting.com.github.util.JoinEventHandler;
import unprotesting.com.github.util.LoanEventHandler;
import unprotesting.com.github.util.MathHandler;
import unprotesting.com.github.util.StaticFileHandler;
import unprotesting.com.github.util.TextHandler;

public final class Main extends JavaPlugin implements Listener {

  JavaPlugin instance = this;

  @Getter
  public static Main INSTANCE;

  private static final Logger log = Logger.getLogger("Minecraft");
  public static Economy econ;
  private static JavaPlugin plugin;
  static File playerdata = new File("plugins/Auto-Tune/", "playerdata.yml");
  public static final String BASEDIR = "plugins/Auto-Tune/web";
  public static final String BASEDIRMAIN = "plugins/Auto-Tune/data.csv";
  public static DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
  public static FileConfiguration playerDataConfig;
  public final static String playerdatafilename = "playerdata.yml";
  public static DB db, memDB, tempDB, loanDB;
  public static HTreeMap<String, Double> tempdatadata;
  public static ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>> map;
  public static HTreeMap<Integer, String> memMap;
  public static HTreeMap<String, double[]> loanMap;
  public static ConcurrentHashMap<String, ConcurrentHashMap<Integer, Double[]>> tempmap;
  public static ConcurrentMap<Integer, Material> ItemMap;
  BukkitScheduler scheduler;
  public File folderfile;
  public static Double buys = 0.0;
  public static Double sells = 0.0;
  public static double tempbuys = 0.0;
  public static double tempsells = 0.0;
  public static Boolean locked = null;
  public static Boolean falseBool = false;

  @Getter
  private File configf, shopf, tradef, tradeShortf;

  public static String basicVolatilityAlgorithim;
  public static String priceModel;

  @Getter
  @Setter
  public static Gui gui;

  public ArrayList<String> itemStringArray;

  @Getter
  public static Set<String> testset = null;

  @Getter
  public static ArrayList<String> publicItemStringArray;

  public static ConcurrentHashMap<Integer, OutlinePane> pageArray = new ConcurrentHashMap<Integer, OutlinePane>();

  @Getter
  @Setter
  static private FileConfiguration mainConfig, shopConfig;

  @Getter
  @Setter
  public static Integer materialListSize;

  @Override
  public void onDisable() {
    log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
  }

  @Override
  public void onEnable() {
    Bukkit.getServer().getPluginManager().registerEvents(new JoinEventHandler(), this);
    folderfile = new File("plugins/Auto-Tune/web/");
    folderfile.mkdirs();
    createFiles();
    File folderfileTemp = new File("plugins/Auto-Tune/temp/");
    folderfileTemp.mkdirs();
    File folderfileJS = new File("plugins/Auto-Tune/Javascript/");
    folderfileJS.mkdirs();
    INSTANCE = this;
    if (!setupEconomy()) {
      log.severe(String.format("Disabled Auto-Tune due to no Vault dependency found!", getDescription().getName()));
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    Config.loadDefaults();
    if (Config.isWebServer()) {
      HttpServer server;
      try {
        server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
        server.createContext("/", new StaticFileHandler(BASEDIR));
        server.setExecutor(null);
        server.start();
        log.info("[Auto Tune] Web server has started on port " + Config.getPort());

      } catch (IOException e) {
        debugLog(
            "Error Creating Server on port: " + Config.getPort() + ". Please try restarting or changing your port.");
        e.printStackTrace();
      }
    }
    setupDataFiles();
    if (tempdatadata.isEmpty() == true || tempdatadata.get("SellPriceDifferenceDifference") == null) {
      tempdataresetSPDifference();
    }
    saveplayerdata();
    loadShopsFile();
    loadShopData();
    materialListSize = memMap.size();
    this.getCommand("at").setExecutor(new AutoTuneCommand());
    this.getCommand("shop").setExecutor(new AutoTuneGUIShopUserCommand());
    this.getCommand("sell").setExecutor(new AutoTuneSellCommand());
    this.getCommand("autosell").setExecutor(new AutoTuneAutoSellCommand());
    this.getCommand("loan").setExecutor(new AutoTuneLoanCommand());
    this.getCommand("loans").setExecutor(new AutoTuneLoansCommand());
    this.getCommand("payloan").setExecutor(new AutoTunePaybackLoanCommand());
    basicVolatilityAlgorithim = Config.getBasicVolatilityAlgorithim();
    priceModel = Config.getPricingModel().toString();
    TextHandler.sendPriceModelData(priceModel);
    scheduler = getServer().getScheduler();
    scheduler.scheduleSyncRepeatingTask(this, new AutoSellEventHandler(), Config.getAutoSellUpdatePeriod() * 5,
        Config.getAutoSellUpdatePeriod());
    scheduler.scheduleSyncRepeatingTask(this, new AutoTunePlayerAutoSellEventHandler(),
        Config.getAutoSellProfitUpdatePeriod() + 20, Config.getAutoSellProfitUpdatePeriod());
    scheduler.scheduleAsyncRepeatingTask(this, new LoanEventHandler(), Config.getIntrestRateUpdateRate(),
        Config.getIntrestRateUpdateRate());
    runnable();
    if ((Config.getInflationMethod().contains("Mixed") || Config.getInflationMethod().contains("Dynamic"))
        && Config.isInflationEnabled()) {
      scheduler.scheduleAsyncRepeatingTask(this, new InflationEventHandler(),
          Config.getDynamicInflationUpdatePeriod() + 40, Config.getDynamicInflationUpdatePeriod());
    }
    if (Config.isSellPriceDifferenceVariationEnabled()) {
      Config.setSellPriceDifference(
          Config.getSellPriceDifferenceVariationStart() - tempdatadata.get("SellPriceDifferenceDifference"));
      SellDifrunnable();
    }
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
    String[] arrayOfString = setOfString.stream().toArray(String[]::new);
    return arrayOfString;
  }

  public void runnable() {
    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          loadItemPricesAndCalculate();
        } catch (ParseException e) {
          e.printStackTrace();
        }
      }

    }.runTaskTimerAsynchronously(Main.getINSTANCE(), Config.getTimePeriod() * 20 * 60,
        Config.getTimePeriod() * 20 * 60);

  }

  public static void tempdataresetSPDifference() {
    Main.tempdatadata.put("SellPriceDifferenceDifference", 0.0);
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
          debugLog("Finished sell difference change task as sell differnce has reached: "
              + Main.getMainConfig().getDouble("sell-price-difference", 2.5));
          cancel();
        }

      }

    }.runTaskTimer(Main.getINSTANCE(), Config.getSellPriceVariationUpdatePeriod() * 20 * 60,
        Config.getSellPriceVariationUpdatePeriod() * 20 * 60);
  }

  public static void loadItemPricesAndCalculate() throws ParseException {
    System.out.println("Loading item prices");
    tempbuys = 0.0;
    tempsells = 0.0;
    buys = 0.0;
    sells = 0.0;
    System.out.println("Loading item prices1");
    if (priceModel.contains("Basic") || priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
      System.out.println("Loading item prices2");
      TextHandler.sendDataBeforePriceCalculation(priceModel, basicVolatilityAlgorithim);
      System.out.println("Loading item prices3");
      Set<String> strSet = map.keySet();
      System.out.println("Loading item prices4");
      for (String str : strSet) {
        ConcurrentHashMap<Integer, Double[]> tempMap = map.get(str);
        Integer expvalues = 0;
        Main.getINSTANCE();
        ConfigurationSection config = Main.getShopConfig().getConfigurationSection("shops")
            .getConfigurationSection(str);
        locked = null;
        if (config != null) {
          Boolean lk = config.getBoolean("locked", false);
          if (lk == true) {
            locked = falseBool;
            debugLog("Locked item found: " + str);
          }
          tempbuys = 0.0;
          tempsells = 0.0;
          buys = 0.0;
          sells = 0.0;

          if (priceModel.contains("Basic")) {
            for (Integer key1 : tempMap.keySet()) {
              Double[] key = tempMap.get(key1);
              tempbuys = key[1];
              buys = buys + tempbuys;
              tempsells = key[2];
              sells = sells + tempsells;
            }
          }

          if (priceModel.contains("Advanced")) {
            for (Integer key1 : tempMap.keySet()) {
              Double[] key = tempMap.get(key1);
              tempbuys = key[1];
              tempbuys = tempbuys * key[0];
              if (tempbuys == 0) {
                tempbuys = key[0];
              }
              buys = buys + tempbuys;
              tempsells = key[2];
              tempsells = tempsells * key[0];
              if (tempsells == 0) {
                tempsells = key[0];
              }
              sells = sells + tempsells;
            }
          }
          if (priceModel.contains("Exponential")) {
            Integer tempSize = tempMap.keySet().size();
            Integer x = 0;
            for (; x < 100000;) {
              Double y = Config.getDataSelectionM() * (Math.pow(x, Config.getDataSelectionZ()))
                  + Config.getDataSelectionC();
              Integer inty = (int) Math.round(y) - 1;
              if (inty > tempSize - 1) {
                expvalues = inty + 1;
                break;
              }
              Double[] key = tempMap.get((tempSize - 1) - inty);
              tempbuys = key[1];
              tempbuys = tempbuys * key[0];
              if (tempbuys == 0) {
                tempbuys = key[0];
              }
              buys = buys + tempbuys;
              tempsells = key[2];
              tempsells = tempsells * key[0];
              if (tempsells == 0) {
                tempsells = key[0];
              }
              sells = sells + tempsells;
              x++;
            }
          }

          if ((Config.getInflationMethod().contains("Static") || Config.getInflationMethod().contains("Mixed"))
              && Config.isInflationEnabled()) {
            buys = buys + buys * 0.01 * Config.getInflationValue();
          }

          if (locked == falseBool) {
            Double[] temp2 = tempMap.get(tempMap.size() - 1);
            Double temp3 = temp2[0];
            Integer tsize = tempMap.size();
            Double newSpotPrice = temp3;
            Double[] temporary = { newSpotPrice, 0.0, 0.0 };
            tempMap.put(tsize, temporary);
            map.put(str, tempMap);
            debugLog("Loading item, " + str + " with price " + Double.toString(temp3) + " as price is locked");
          }
          Double avBuy = buys / (tempMap.size());
          Double avSells = sells / (tempMap.size());
          if (priceModel.contains("Advanced") || priceModel.contains("Basic")) {
            avBuy = buys / (tempMap.size());
            avSells = sells / (tempMap.size());
          }
          if (priceModel.contains("Exponential")) {
            avBuy = buys / (expvalues);
            avSells = sells / (expvalues);
          }
          if (avBuy > avSells && locked == null) {
            if (priceModel.contains("Basic")) {
              debugLog("AvBuy > AvSells for " + str);
            }
            if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
              debugLog("AvBuyValue > AvSellValue for " + str);
            }
            Double[] temp2 = tempMap.get(tempMap.size() - 1);
            Double temp3 = temp2[0];
            Integer tsize = tempMap.size();
            if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Fixed") == true) {
              Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Fixed", priceModel, Config.getApiKey(),
                  Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxFixedVolatility(),
                  Config.getBasicMinFixedVolatility());
              Double[] temporary = { newSpotPrice, 0.0, 0.0 };
              if (priceModel.contains("Basic")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                    + Double.toString(avSells));
              }
              if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                    + Double.toString(avSells));
              }
              tempMap.put(tsize, temporary);
              map.put(str, tempMap);

            }
            if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Variable") == true) {
              Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                  Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                  Config.getBasicMinVariableVolatility());
              Double[] temporary = { newSpotPrice, 0.0, 0.0 };
              if (priceModel.contains("Basic")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                    + Double.toString(avSells));
              }
              if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                    + Double.toString(avSells));
              }
              tempMap.put(tsize, temporary);
              map.put(str, tempMap);
            }
          }

          if (avBuy < avSells && locked == null) {
            if (priceModel.contains("Basic")) {
              debugLog("AvBuy < AvSells for " + str);
            }
            if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
              debugLog("AvBuyValue < AvSellValue for " + str);
            }
            Double[] temp2 = tempMap.get(tempMap.size() - 1);
            Double temp3 = temp2[0];
            Integer tsize = tempMap.size();
            if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Fixed")) {
              Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Fixed", priceModel, Config.getApiKey(),
                  Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxFixedVolatility(),
                  Config.getBasicMinFixedVolatility());
              Double[] temporary = { newSpotPrice, 0.0, 0.0 };
              if (priceModel.contains("Basic")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                    + Double.toString(avSells));
              }
              if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                    + Double.toString(avSells));
              }
              tempMap.put(tsize, temporary);
              map.put(str, tempMap);
            }
            if ((priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                || priceModel.contains("Exponential")) && basicVolatilityAlgorithim.contains("Variable") == true) {
              Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                  Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                  Config.getBasicMinVariableVolatility());
              Double[] temporary = { newSpotPrice, 0.0, 0.0 };
              if (priceModel.contains("Basic")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                    + Double.toString(avSells));
              }
              if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                debugLog("Loading item, " + str + ", with new price: " + Double.toString(newSpotPrice)
                    + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                    + Double.toString(avSells));
              }
              tempMap.put(tsize, temporary);
              map.put(str, tempMap);
            }

          }

          if (avBuy == avSells && locked == null) {
            debugLog("AvBuy = AvSells for " + str);
            Double[] temp2 = tempMap.get(tempMap.size() - 1);
            Double temp3 = temp2[0];
            Integer tsize = tempMap.size();
            if (priceModel.contains("Basic") == true || priceModel.contains("Advanced")
                || priceModel.contains("Exponential")) {
              Double newSpotPrice = HttpPostRequestor.sendRequestForPrice("Variable", priceModel, Config.getApiKey(),
                  Config.getEmail(), str, temp3, avBuy, avSells, Config.getBasicMaxVariableVolatility(),
                  Config.getBasicMinVariableVolatility());
              Double[] temporary = { newSpotPrice, 0.0, 0.0 };
              if (priceModel.contains("Basic")) {
                debugLog("Loading item, " + str + ", with the same price: " + Double.toString(newSpotPrice)
                    + " becasue Average buys = " + Double.toString(avBuy) + " and Average sells = "
                    + Double.toString(avSells));
              }
              if (priceModel.contains("Advanced") || priceModel.contains("Exponential")) {
                debugLog("Loading item, " + str + ", with the same price: " + Double.toString(newSpotPrice)
                    + " becasue Average buy value = " + Double.toString(avBuy) + " and Average sell value = "
                    + Double.toString(avSells));
              }
              tempMap.put(tsize, temporary);
              map.put(str, tempMap);
            }
            locked = null;
          }
        }
      }
      tempbuys = 0.0;
      tempsells = 0.0;
      buys = 0.0;
      sells = 0.0;
      Date date = Calendar.getInstance().getTime();
      Date newDate = MathHandler.addMinutesToJavaUtilDate(date, Config.getTimePeriod());
      String strDate = dateFormat.format(newDate);
      debugLog("Done running price Algorithim, a new check will occur at: " + strDate);
      try {
        debugLog("Saving data to data.csv file");
        CSVHandler.writeCSV();
        CSVHandler.writeShortCSV();
        debugLog("Saved data to data.csv file");
      } catch (InterruptedException | IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void createFiles() {

    configf = new File(getDataFolder(), "config.yml");
    shopf = new File(getDataFolder(), "shops.yml");
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

    mainConfig = new YamlConfiguration();
    shopConfig = new YamlConfiguration();

    try {
      mainConfig.load(configf);
      shopConfig.load(shopf);

    } catch (InvalidConfigurationException | IOException e) {
      e.printStackTrace();
    }

  }

  public static void setupDataFiles() {
    if (Config.isChecksumHeaderBypass()) {
      Main.debugLog("Enabling checksum-header-bypass");
      db = DBMaker.fileDB("data.db").checksumHeaderBypass().closeOnJvmShutdown().make();
      map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
      playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
      memDB = DBMaker.memoryDB().checksumHeaderBypass().closeOnJvmShutdown().make();
      memMap = memDB.hashMap("memMap", Serializer.INTEGER, Serializer.STRING).createOrOpen();
    } else {
      db = DBMaker.fileDB("data.db").checksumHeaderBypass().closeOnJvmShutdown().make();
      map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
      playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
      memDB = DBMaker.memoryDB().closeOnJvmShutdown().make();
      memMap = memDB.hashMap("memMap", Serializer.INTEGER, Serializer.STRING).createOrOpen();
    }
    tempDB = DBMaker.fileDB("plugins/Auto-Tune/temp/tempdata.db").checksumHeaderBypass().closeOnJvmShutdown().make();
    tempdatadata = tempDB.hashMap("tempdatadata", Serializer.STRING, Serializer.DOUBLE).createOrOpen();
    loanDB = DBMaker.fileDB("plugins/Auto-Tune/temp/loandata.db").checksumHeaderBypass().closeOnJvmShutdown().make();
    loanMap = loanDB.hashMap("loanMap", Serializer.STRING, Serializer.DOUBLE_ARRAY).createOrOpen();
  }

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