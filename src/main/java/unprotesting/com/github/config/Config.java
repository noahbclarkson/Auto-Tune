package unprotesting.com.github.config;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.configuration.file.FileConfiguration;

import unprotesting.com.github.Main;

@Getter
@Setter
public class Config {

  @Getter
  private static Config config;

  private boolean webServer;
  private boolean sellPriceDifferenceVariationEnabled;
  private boolean tutorial;
  private boolean disableMaxBuysSells;
  private boolean ignoreAfk;
  private boolean enableEnchantments;
  private Integer port;
  private Integer timePeriod;
  private Integer sellPriceVariationTimePeriod;
  private Integer sellPriceVariationUpdatePeriod;
  private Integer autoSellUpdatePeriod;
  private Integer autoSellProfitUpdatePeriod;
  private Integer interestRateUpdateRate;
  private Integer tutorialMessagePeriod;
  private Integer updatePricesThreshold;
  private String menuTitle;
  private String apiKey;
  private String email;
  private String currencySymbol;
  private String dataLocation;
  private String background;
  private String numberFormat;
  private Double maxVolatility;
  private Double minVolatility;
  private Double sellPriceDifference;
  private Double sellPriceDifferenceVariationStart;
  private Double dataSelectionM;
  private Double dataSelectionC;
  private Double dataSelectionZ;
  private Double interestRate;
  private Double maxDebt;
  private Double enchantmentLimiter;
  private Double durabilityLimiter;
  private String logLevel;

  /**
   * Initializes the config.
   */
  public Config() {

    config = this;
    loadConfig();

  }

  /**
   * Loads the config from the config.yml file.
   */
  private void loadConfig() {
    FileConfiguration configF = Main.getInstance().getDataFiles().getConfig();
    setApiKey(configF.getString("apiKey", "xyz"));
    setEmail(configF.getString("email", "xyz@gmail.com"));
    setWebServer(configF.getBoolean("web-server-enabled", true));
    setPort(configF.getInt("port", 8123));
    setTimePeriod(configF.getInt("time-period", 30));
    setMenuTitle(configF.getString("menu-title", "Shop"));
    setBackground(configF.getString("background", "BLACK_STAINED_GLASS_PANE"));
    setAutoSellUpdatePeriod(configF.getInt("auto-sell-update-period", 10));
    setAutoSellProfitUpdatePeriod(configF.getInt("auto-sell-profit-update-period", 600));
    setNumberFormat(configF.getString("number-format", "###,###,###,###,##0.00"));
    setEnableEnchantments(configF.getBoolean("enable-enchantments", true));
    setSellPriceDifference(configF.getDouble("sell-price-difference", 15.0));
    setMaxVolatility(configF.getDouble("max-volatility", 0.5));
    setMinVolatility(configF.getDouble("min-volatility", 0.05));
    setDataSelectionM(configF.getDouble("data-selection-m", 0.05));
    setDataSelectionZ(configF.getDouble("data-selection-z", 1.75));
    setDataSelectionC(configF.getDouble("data-selection-c", 0.55));
    setUpdatePricesThreshold(configF.getInt("update-prices-threshold", 2));
    setCurrencySymbol(configF.getString("currency-symbol", "$"));

    setSellPriceDifferenceVariationEnabled(configF.getBoolean(
        "sell-price-difference-variation-enabled", true));

    setSellPriceDifferenceVariationStart(configF.getDouble(
        "sell-price-difference-variation-start", 0.0));
    
    setSellPriceVariationTimePeriod(configF.getInt(
        "sell-price-variation-time-period", 43200));

    setSellPriceVariationUpdatePeriod(configF.getInt(
        "sell-price-variation-update-period", 30));

    setInterestRate(configF.getDouble("interest-rate", 0.05));
    setInterestRateUpdateRate(configF.getInt("interest-rate-update-period", 1200));
    setDisableMaxBuysSells(configF.getBoolean("disable-max-buys-sells", false));
    setMaxDebt(configF.getDouble("max-debt-value", 1000.0));
    setEnchantmentLimiter(configF.getDouble("enchantment-limiter", 50.0));
    setDurabilityLimiter(configF.getDouble("durability-limiter", 15.0));
    setIgnoreAfk(configF.getBoolean("ignore-afk", true));
    setTutorial(configF.getBoolean("tutorial", true));
    setTutorialMessagePeriod(configF.getInt("tutorial-message-period", 360));
    setDataLocation(configF.getString("data-location", "plugins/Auto-Tune/"));
    setLogLevel(configF.getString("log-level", "INFO"));
  }

}
