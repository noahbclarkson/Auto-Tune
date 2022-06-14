package unprotesting.com.github;

import java.text.DecimalFormat;
import java.util.logging.Level;

import lombok.Getter;
import lombok.Setter;

import net.ess3.api.IEssentials;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import unprotesting.com.github.commands.AutoTuneCommand;
import unprotesting.com.github.commands.AutosellCommand;
import unprotesting.com.github.commands.GdpCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.commands.TradeCommand;
import unprotesting.com.github.commands.TransactionsCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.DataFiles;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.data.csv.CsvHandler;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.economy.EconomyFunctions;
import unprotesting.com.github.events.async.AutosellProfitUpdateEvent;
import unprotesting.com.github.events.async.IpCheckEvent;
import unprotesting.com.github.events.async.LoanUpdateEvent;
import unprotesting.com.github.events.async.PriceUpdateEvent;
import unprotesting.com.github.events.async.SellPriceDifferenceUpdateEvent;
import unprotesting.com.github.events.sync.AutosellUpdateEvent;
import unprotesting.com.github.events.sync.JoinMessageEventHandler;
import unprotesting.com.github.events.sync.TutorialSendEvent;
import unprotesting.com.github.events.sync.UnlockUpdateEvent;
import unprotesting.com.github.localserver.LocalServer;
import unprotesting.com.github.util.UtilFunctions;

/**
 * The main class of AutoTune.
 */
@Getter
@Setter
public class Main extends JavaPlugin {

  @Getter
  private static Main instance;

  private DataFiles dataFiles;
  private Database database;
  private LocalDataCache cache;
  private IEssentials ess;
  private String[] serverIPs;
  private AutosellData autosellData;
  public LocalServer localServer;
  private boolean essentialsEnabled = false;
  private boolean placeholderApi = false;
  private MiniMessage mm;

  /**
   * Called when the plugin is disabled.
   */
  @Override
  public void onDisable() {

    database.saveCacheToLastTP();

    // Run a time period update to update the database before closing.
    if (cache != null) {
      updateTimePeriod();
    }

  }

  /**
   * Called when the plugin is enabled.
   */
  @Override
  public void onEnable() {

    instance = this;
    checkEconomy();
    getEssentials();
    setupDataFiles();
    mm = MiniMessage.miniMessage();
    UtilFunctions.setDf(new DecimalFormat(Config.getConfig().getNumberFormat()));
    getLogger().setLevel(Level.parse(Config.getConfig().getLogLevel().toUpperCase()));
    new Messages();

    Bukkit.getScheduler().runTaskAsynchronously(this, () ->
         Bukkit.getPluginManager().callEvent(new IpCheckEvent(true)));

    database = new Database();
    cache = new LocalDataCache();
    setupCommands();
    setupEvents();
    localServer = new LocalServer();
    setAutosellData(new AutosellData());
    new Metrics(this, 9687);

  }

  /**
   * Run a time period update.
   */
  public void updateTimePeriod() {

    new TimePeriod().addToMap();
    cache = new LocalDataCache();

  }

  /**
   * Check that an economy plugin is installed.
   */
  private void checkEconomy() {

    // Check if an economy plugin is installed and if it is enabled, if it is return.
    if (EconomyFunctions.setupLocalEconomy(getServer())) {
      return;
    }

    getLogger().severe("No economy plugin found."
        + " Auto-Tune requires Vault or a compatible economy plugin.");

    getServer().getPluginManager().disablePlugin(this);
    return;

  }

  /**
   * Setup all the data files.
   */
  public void setupDataFiles() {

    dataFiles = new DataFiles(getDataFolder());

    // Iterate through all the data files and setup them. 
    for (int i = 0; i < dataFiles.getFileNames().length; i++) {
      if (!dataFiles.getFiles()[i].exists()) {
        saveResource(dataFiles.getFileNames()[i], false);
      }
    }

    dataFiles.loadConfigs();

  }

  /**
   * Register all the commands.
   */
  private void setupCommands() {

    getCommand("shop").setExecutor(new ShopCommand());
    getCommand("sell").setExecutor(new SellCommand());
    getCommand("trade").setExecutor(new TradeCommand());
    getCommand("gdp").setExecutor(new GdpCommand());
    getCommand("transactions").setExecutor(new TransactionsCommand());
    getCommand("loan").setExecutor(new LoanCommand());
    getCommand("autosell").setExecutor(new AutosellCommand());
    getCommand("at").setExecutor(new AutoTuneCommand());

  }

  /**
   * Register all the events.
   */
  private void setupEvents() {

    // Asynchronous
    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new PriceUpdateEvent(true)),
        Config.getConfig().getTimePeriod() * 1200, Config.getConfig().getTimePeriod() * 1200);

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new LoanUpdateEvent(true)),
        Config.getConfig().getInterestRateUpdateRate(),
        Config.getConfig().getInterestRateUpdateRate());

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new SellPriceDifferenceUpdateEvent(true)),
        Config.getConfig().getSellPriceVariationUpdatePeriod() * 1200,
        Config.getConfig().getSellPriceVariationUpdatePeriod() * 1200);

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new AutosellProfitUpdateEvent(true)),
        Config.getConfig().getAutoSellProfitUpdatePeriod(),
        Config.getConfig().getAutoSellProfitUpdatePeriod());

    // Synchronous
    Bukkit.getScheduler().runTaskTimer(this, () ->
        Bukkit.getPluginManager().callEvent(new AutosellUpdateEvent()),
        Config.getConfig().getAutoSellUpdatePeriod(), Config.getConfig().getAutoSellUpdatePeriod());

    Bukkit.getScheduler().runTaskTimer(this, () ->
        Bukkit.getPluginManager().callEvent(new TutorialSendEvent()),
        Config.getConfig().getTutorialMessagePeriod() * 20,
        Config.getConfig().getTutorialMessagePeriod() * 20);

    Bukkit.getScheduler().runTaskTimer(this, () ->
        Bukkit.getPluginManager().callEvent(new UnlockUpdateEvent()), 60, 60);

    Bukkit.getServer().getPluginManager().registerEvents(new JoinMessageEventHandler(), this);

  }

  /**
   * Get the Essentials plugin.
   */
  private void getEssentials() {

    Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");

    // If essentials is not installed, set essentialsEnabled to false and return.
    if (plugin == null) {
      setEssentialsEnabled(false);
      return;
    }

    setEssentialsEnabled(true);
    setEss((IEssentials) plugin);

  }

}
