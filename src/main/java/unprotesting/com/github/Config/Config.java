package unprotesting.com.github.Config;

import org.bukkit.ChatColor;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Logging.Logging;

public class Config {

 @Getter
 @Setter
 private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled, inflationEnabled, 
                         autoSellEnabled, calculateGlobalGDP, tutorial, sendPlayerTopMoversOnJoin, dataTransactions, disableMaxBuysSells,
                          ignoreAFK, usePermissionsForShop;

 @Getter
 @Setter
 private static Integer port, timePeriod, menuRows, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod, autoSellUpdatePeriod, 
                         autoSellProfitUpdatePeriod, dynamicInflationUpdatePeriod, maximumShortTradeLength, InterestRateUpdateRate,
                          tutorialMessagePeriod, topMoversAmount, updatePricesThreshold;

 @Getter
 @Setter
 private static String serverName, pricingModel, basicVolatilityAlgorithim, menuTitle, noPermission, apiKey, email, inflationMethod,
                        currencySymbol, economyShopConfig, dataLocation, storageSetting;

 @Getter
 @Setter
 private static Double basicMaxFixedVolatility, basicMaxVariableVolatility, basicMinFixedVolatility, basicMinVariableVolatility, 
                        sellPriceDifference, sellPriceDifferenceVariationStart, dataSelectionM, dataSelectionC, dataSelectionZ,
                         dynamicInflationValue, inflationValue, interestRate, maxDebt, shopConfigGUIShopSellValue, enchantmentLimiter,
                          durabilityLimiter, compoundInterestRate;

    public static void loadDefaults() {

        
        Config.setAutoSellEnabled(Main.dfiles.getConfig().getBoolean("auto-sell-enabled", true));
        Config.setCalculateGlobalGDP(Main.dfiles.getConfig().getBoolean("calculate-global-GDP", true));
        Config.setChecksumHeaderBypass(Main.dfiles.getConfig().getBoolean("checksum-header-bypass", false));
        Config.setDataTransactions(Main.dfiles.getConfig().getBoolean("data-transactions", false));
        Config.setDebugEnabled(Main.dfiles.getConfig().getBoolean("debug-enabled", false));
        Config.setDisableMaxBuysSells(Main.dfiles.getConfig().getBoolean("disable-max-buys-sells", false));
        Config.setIgnoreAFK(Main.dfiles.getConfig().getBoolean("ignore-afk", true));
        Config.setInflationEnabled(Main.dfiles.getConfig().getBoolean("inflation-enabled", true));
        Config.setSellPriceDifferenceVariationEnabled(Main.dfiles.getConfig().getBoolean("sell-price-difference-variation-enabled", true));
        Config.setSendPlayerTopMoversOnJoin(Main.dfiles.getConfig().getBoolean("send-player-top-movers-on-join", true));
        Config.setTutorial(Main.dfiles.getConfig().getBoolean("tutorial", true));
        Config.setUsePermissionsForShop(Main.dfiles.getConfig().getBoolean("use-permission-for-shop", false));
        Config.setWebServer(Main.dfiles.getConfig().getBoolean("web-server-enabled", true));


        Config.setAutoSellProfitUpdatePeriod(Main.dfiles.getConfig().getInt("auto-sell-profit-update-period", 1200));
        Config.setAutoSellUpdatePeriod(Main.dfiles.getConfig().getInt("auto-sell-update-period", 10));
        Config.setDynamicInflationUpdatePeriod(Main.dfiles.getConfig().getInt("dynamic-inflation-update-period", 5000));
        Config.setInterestRateUpdateRate(Main.dfiles.getConfig().getInt("interest-rate-update-period", 1200));
        Config.setMaximumShortTradeLength(Main.dfiles.getConfig().getInt("maximum-short-trade-length", 100));
        Config.setMenuRows(Main.dfiles.getConfig().getInt("menu-rows", 3));
        Config.setPort(Main.dfiles.getConfig().getInt("port", 8321));
        Config.setSellPriceVariationTimePeriod(Main.dfiles.getConfig().getInt("sell-price-variation-time-period", 10800));
        Config.setSellPriceVariationUpdatePeriod(Main.dfiles.getConfig().getInt("sell-price-variation-update-period", 30));
        Config.setTimePeriod(Main.dfiles.getConfig().getInt("time-period", 10));
        Config.setTopMoversAmount(Main.dfiles.getConfig().getInt("top-movers-amount", 5));
        Config.setTutorialMessagePeriod(Main.dfiles.getConfig().getInt("tutorial-message-period", 300));
        Config.setUpdatePricesThreshold(Main.dfiles.getConfig().getInt("update-prices-threshold", 1));


        Config.setApiKey(Main.dfiles.getConfig().getString("api-key", "xyz"));
        Config.setBasicVolatilityAlgorithim(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("Volatility-Algorithim", "Variable")));
        Config.setCurrencySymbol(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("currency-symbol", "$")));
        Config.setDataLocation(Main.dfiles.getConfig().getString("data-location", ""));
        Config.setEconomyShopConfig(Main.dfiles.getConfig().getString("economy-shop-config", "default"));
        Config.setEmail(Main.dfiles.getConfig().getString("email", "xyz@gmail.com"));
        Config.setInflationMethod(Main.dfiles.getConfig().getString("inflation-method", "Mixed"));
        Config.setMenuTitle(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("menu-title", "Auto-Tune Shop")));
        Config.setNoPermission(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("no-permission", "You do not have permission to perform this command")));
        Config.setPricingModel(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("pricing-model", "Exponential")));
        Config.setServerName(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("server-name", "Survival Server - (Change this in Config)")));
        Config.setStorageSetting(ChatColor.translateAlternateColorCodes('&', Main.dfiles.getConfig().getString("storage-setting", "TP-Based")));


        Config.setBasicMaxFixedVolatility(Main.dfiles.getConfig().getDouble("Fixed-Max-Volatility", 2.00));
        Config.setBasicMaxVariableVolatility(Main.dfiles.getConfig().getDouble("max-volatility", 0.5));
        Config.setBasicMinFixedVolatility(Main.dfiles.getConfig().getDouble("Fixed-Min-Volatility", 0.05));
        Config.setBasicMinVariableVolatility(Main.dfiles.getConfig().getDouble("min-volatility", 0.025));
        Config.setCompoundInterestRate(Main.dfiles.getConfig().getDouble("compound-interest-rate", 0.0025));
        Config.setDataSelectionC(Main.dfiles.getConfig().getDouble("data-selection-c", 1.05));
        Config.setDataSelectionM(Main.dfiles.getConfig().getDouble("data-selection-m", 0.05));
        Config.setDataSelectionZ(Main.dfiles.getConfig().getDouble("data-selection-z", 1.6));
        Config.setDurabilityLimiter(Main.dfiles.getConfig().getDouble("durability-limiter", 5.0));
        Config.setDynamicInflationValue(Main.dfiles.getConfig().getDouble("dynamic-inflation-value", 0.0025));
        Config.setEnchantmentLimiter(Main.dfiles.getConfig().getDouble("enchantment-limiter", 7.5));
        Config.setInflationValue(Main.dfiles.getConfig().getDouble("static-inflation-value", 0.1));
        Config.setInterestRate(Main.dfiles.getConfig().getDouble("interest-rate", 0.005));
        Config.setMaxDebt(Main.dfiles.getConfig().getDouble("max-debt-value", -100.00));
        Config.setSellPriceDifference(Main.dfiles.getConfig().getDouble("sell-price-difference", 2.5));
        Config.setSellPriceDifferenceVariationStart(Main.dfiles.getConfig().getDouble("sell-price-difference-variation-start", 25.0));
        Config.setShopConfigGUIShopSellValue(Main.dfiles.getConfig().getDouble("shop-config-guishop-sell-value", 20.00));


        if (getTimePeriod() < 3){
            Logging.debug("Time-Period Setting reverting to 3 to reduce memory usage. If you would like lower time periods open a ticket in the offical discord.");
            Config.setTimePeriod(3);
        }
        
        if (getStorageSetting() != "TP-Based" || getStorageSetting() != "Map-Based"){
            Logging.log("Couldn't find a valid storage setting. Defaulting to TP-Based method");
            setStorageSetting("TP-Based");
        }
    }

}
