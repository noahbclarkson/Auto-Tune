package unprotesting.com.github.config;

import static org.bukkit.ChatColor.*;

import lombok.Getter;
import lombok.Setter;
import static unprotesting.com.github.Main.*;

public class Config {

 @Getter
 @Setter
 private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled, 
                         tutorial, dataTransactions, disableMaxBuysSells,
                         ignoreAFK, readFromCSV, enableEnchantments;

 @Getter
 @Setter
 private static Integer port, timePeriod, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod, autoSellUpdatePeriod, 
                         autoSellProfitUpdatePeriod, InterestRateUpdateRate,
                         tutorialMessagePeriod, updatePricesThreshold;

 @Getter
 @Setter
 private static String menuTitle, apiKey, email, currencySymbol,
                         dataLocation, background, numberFormat;

 @Getter
 @Setter
 private static Double maxVolatility, minVolatility, sellPriceDifference, 
                        sellPriceDifferenceVariationStart,dataSelectionM, 
                        dataSelectionC, dataSelectionZ, interestRate, 
                        maxDebt, enchantmentLimiter, durabilityLimiter;

    public static void loadDefaults() {
        Config.setWebServer(getDataFiles().getConfig().getBoolean("web-server-enabled", true));
        Config.setEnableEnchantments(getDataFiles().getConfig().getBoolean("enable-enchantments", true));
        Config.setSellPriceDifferenceVariationEnabled(getDataFiles().getConfig().getBoolean("sell-price-difference-variation-enabled", true));
        Config.setDisableMaxBuysSells(getDataFiles().getConfig().getBoolean("disable-max-buys-sells", false));
        Config.setDebugEnabled(getDataFiles().getConfig().getBoolean("debug-enabled", false));
        Config.setIgnoreAFK(getDataFiles().getConfig().getBoolean("ignore-afk", true));
        Config.setChecksumHeaderBypass(getDataFiles().getConfig().getBoolean("checksum-header-bypass", true));
        Config.setTutorial(getDataFiles().getConfig().getBoolean("tutorial", true));
        Config.setDataTransactions(getDataFiles().getConfig().getBoolean("data-transactions", false));
        Config.setReadFromCSV(getDataFiles().getConfig().getBoolean("read-from-csv", false));
        
        Config.setPort(getDataFiles().getConfig().getInt("port", 8321));
        Config.setTimePeriod(getDataFiles().getConfig().getInt("time-period", 30));
        Config.setAutoSellUpdatePeriod(getDataFiles().getConfig().getInt("auto-sell-update-period", 20));
        Config.setAutoSellProfitUpdatePeriod(getDataFiles().getConfig().getInt("auto-sell-profit-update-period", 1200));
        Config.setUpdatePricesThreshold(getDataFiles().getConfig().getInt("update-prices-threshold", 1));
        Config.setSellPriceVariationTimePeriod(getDataFiles().getConfig().getInt("sell-price-variation-time-period", 43200));
        Config.setSellPriceVariationUpdatePeriod(getDataFiles().getConfig().getInt("sell-price-variation-update-period", 30));
        Config.setInterestRateUpdateRate(getDataFiles().getConfig().getInt("interest-rate-update-period", 1200));
        Config.setTutorialMessagePeriod(getDataFiles().getConfig().getInt("tutorial-message-period", 325));
        
        Config.setApiKey(getDataFiles().getConfig().getString("api-key", "xyz"));
        Config.setEmail(getDataFiles().getConfig().getString("email", "xyz@gmail.com"));
        Config.setMenuTitle(translateAlternateColorCodes('&', getDataFiles().getConfig().getString("menu-title", "Shop")));
        Config.setBackground(getDataFiles().getConfig().getString("background", "BLACK_STAINED_GLASS_PANE"));
        Config.setNumberFormat(getDataFiles().getConfig().getString("number-format", "###,###,###,##0.00"));
        Config.setCurrencySymbol(translateAlternateColorCodes('&', getDataFiles().getConfig().getString("currency-symbol", "$")));
        Config.setDataLocation(getDataFiles().getConfig().getString("data-location", ""));
        
        Config.setSellPriceDifference(getDataFiles().getConfig().getDouble("sell-price-difference", 10.0));
        Config.setMaxVolatility(getDataFiles().getConfig().getDouble("max-volatility", 0.5));
        Config.setMinVolatility(getDataFiles().getConfig().getDouble("min-volatility", 0.05));
        Config.setDataSelectionM(getDataFiles().getConfig().getDouble("data-selection-m", 0.05));
        Config.setDataSelectionZ(getDataFiles().getConfig().getDouble("data-selection-z", 1.75));
        Config.setDataSelectionC(getDataFiles().getConfig().getDouble("data-selection-c", 0.55));
        Config.setSellPriceDifferenceVariationStart(getDataFiles().getConfig().getDouble("sell-price-difference-variation-start", 25.0));
        Config.setInterestRate(getDataFiles().getConfig().getDouble("interest-rate", 0.01));
        Config.setMaxDebt(getDataFiles().getConfig().getDouble("max-debt-value", 1000.0));
        Config.setEnchantmentLimiter(getDataFiles().getConfig().getDouble("enchantment-limiter", 45.0));
        Config.setDurabilityLimiter(getDataFiles().getConfig().getDouble("durability-limiter", 7.5));
    }

}
