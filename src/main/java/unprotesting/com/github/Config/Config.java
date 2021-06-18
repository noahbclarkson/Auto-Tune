package unprotesting.com.github.config;

import org.bukkit.ChatColor;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;

public class Config {

 @Getter
 @Setter
 private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled, inflationEnabled, 
                         autoSellEnabled, calculateGlobalGDP, tutorial, sendPlayerTopMoversOnJoin, dataTransactions, disableMaxBuysSells,
                          ignoreAFK, usePermissionsForShop, readFromCSV, enableEnchantments;

 @Getter
 @Setter
 private static Integer port, timePeriod, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod, autoSellUpdatePeriod, 
                         autoSellProfitUpdatePeriod, dynamicInflationUpdatePeriod, maximumShortTradeLength, InterestRateUpdateRate,
                          tutorialMessagePeriod, topMoversAmount, updatePricesThreshold;

 @Getter
 @Setter
 private static String serverName, pricingModel, basicVolatilityAlgorithim, menuTitle, noPermission, apiKey, email, inflationMethod,
                        currencySymbol, economyShopConfig, dataLocation, background, numberFormat;

 @Getter
 @Setter
 private static Double basicMaxFixedVolatility, basicMaxVariableVolatility, basicMinFixedVolatility, basicMinVariableVolatility, 
                        sellPriceDifference, sellPriceDifferenceVariationStart, dataSelectionM, dataSelectionC, dataSelectionZ,
                         dynamicInflationValue, inflationValue, interestRate, maxDebt, shopConfigGUIShopSellValue, enchantmentLimiter,
                          durabilityLimiter, compoundInterestRate;

    public static void loadDefaults() {

        
        Config.setAutoSellEnabled(Main.getDfiles().getConfig().getBoolean("auto-sell-enabled", true));
        Config.setCalculateGlobalGDP(Main.getDfiles().getConfig().getBoolean("calculate-global-GDP", true));
        Config.setChecksumHeaderBypass(Main.getDfiles().getConfig().getBoolean("checksum-header-bypass", false));
        Config.setDataTransactions(Main.getDfiles().getConfig().getBoolean("data-transactions", false));
        Config.setDebugEnabled(Main.getDfiles().getConfig().getBoolean("debug-enabled", false));
        Config.setDisableMaxBuysSells(Main.getDfiles().getConfig().getBoolean("disable-max-buys-sells", false));
        Config.setIgnoreAFK(Main.getDfiles().getConfig().getBoolean("ignore-afk", true));
        Config.setInflationEnabled(Main.getDfiles().getConfig().getBoolean("inflation-enabled", true));
        Config.setReadFromCSV(Main.getDfiles().getConfig().getBoolean("read-from-csv", false));
        Config.setSellPriceDifferenceVariationEnabled(Main.getDfiles().getConfig().getBoolean("sell-price-difference-variation-enabled", true));
        Config.setSendPlayerTopMoversOnJoin(Main.getDfiles().getConfig().getBoolean("send-player-top-movers-on-join", true));
        Config.setTutorial(Main.getDfiles().getConfig().getBoolean("tutorial", true));
        Config.setUsePermissionsForShop(Main.getDfiles().getConfig().getBoolean("use-permission-for-shop", false));
        Config.setWebServer(Main.getDfiles().getConfig().getBoolean("web-server-enabled", true));
        Config.setEnableEnchantments(Main.getDfiles().getConfig().getBoolean("enable-enchantments", true));


        Config.setAutoSellProfitUpdatePeriod(Main.getDfiles().getConfig().getInt("auto-sell-profit-update-period", 1200));
        Config.setAutoSellUpdatePeriod(Main.getDfiles().getConfig().getInt("auto-sell-update-period", 10));
        Config.setDynamicInflationUpdatePeriod(Main.getDfiles().getConfig().getInt("dynamic-inflation-update-period", 5000));
        Config.setInterestRateUpdateRate(Main.getDfiles().getConfig().getInt("interest-rate-update-period", 1200));
        Config.setMaximumShortTradeLength(Main.getDfiles().getConfig().getInt("maximum-short-trade-length", 100));
        Config.setPort(Main.getDfiles().getConfig().getInt("port", 8321));
        Config.setSellPriceVariationTimePeriod(Main.getDfiles().getConfig().getInt("sell-price-variation-time-period", 10800));
        Config.setSellPriceVariationUpdatePeriod(Main.getDfiles().getConfig().getInt("sell-price-variation-update-period", 30));
        Config.setTimePeriod(Main.getDfiles().getConfig().getInt("time-period", 10));
        Config.setTopMoversAmount(Main.getDfiles().getConfig().getInt("top-movers-amount", 5));
        Config.setTutorialMessagePeriod(Main.getDfiles().getConfig().getInt("tutorial-message-period", 300));
        Config.setUpdatePricesThreshold(Main.getDfiles().getConfig().getInt("update-prices-threshold", 1));


        Config.setApiKey(Main.getDfiles().getConfig().getString("api-key", "xyz"));
        Config.setBackground(Main.getDfiles().getConfig().getString("background", "BLACK_STAINED_GLASS_PANE"));
        Config.setBasicVolatilityAlgorithim(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("Volatility-Algorithim", "Variable")));
        Config.setCurrencySymbol(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("currency-symbol", "$")));
        Config.setDataLocation(Main.getDfiles().getConfig().getString("data-location", ""));
        Config.setEconomyShopConfig(Main.getDfiles().getConfig().getString("economy-shop-config", "default"));
        Config.setEmail(Main.getDfiles().getConfig().getString("email", "xyz@gmail.com"));
        Config.setInflationMethod(Main.getDfiles().getConfig().getString("inflation-method", "Mixed"));
        Config.setMenuTitle(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("menu-title", "Auto-Tune Shop")));
        Config.setNoPermission(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("no-permission", "You do not have permission to perform this command")));
        Config.setNumberFormat(Main.getDfiles().getConfig().getString("number-format", "###,###,###,##0.00"));
        Config.setPricingModel(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("pricing-model", "Exponential")));
        Config.setServerName(ChatColor.translateAlternateColorCodes('&', Main.getDfiles().getConfig().getString("server-name", "Survival Server - (Change this in Config)")));


        Config.setBasicMaxFixedVolatility(Main.getDfiles().getConfig().getDouble("Fixed-Max-Volatility", 2.00));
        Config.setBasicMaxVariableVolatility(Main.getDfiles().getConfig().getDouble("max-volatility", 0.5));
        Config.setBasicMinFixedVolatility(Main.getDfiles().getConfig().getDouble("Fixed-Min-Volatility", 0.05));
        Config.setBasicMinVariableVolatility(Main.getDfiles().getConfig().getDouble("min-volatility", 0.025));
        Config.setCompoundInterestRate(Main.getDfiles().getConfig().getDouble("compound-interest-rate", 0.0025));
        Config.setDataSelectionC(Main.getDfiles().getConfig().getDouble("data-selection-c", 1.05));
        Config.setDataSelectionM(Main.getDfiles().getConfig().getDouble("data-selection-m", 0.05));
        Config.setDataSelectionZ(Main.getDfiles().getConfig().getDouble("data-selection-z", 1.6));
        Config.setDurabilityLimiter(Main.getDfiles().getConfig().getDouble("durability-limiter", 5.0));
        Config.setDynamicInflationValue(Main.getDfiles().getConfig().getDouble("dynamic-inflation-value", 0.0025));
        Config.setEnchantmentLimiter(Main.getDfiles().getConfig().getDouble("enchantment-limiter", 7.5));
        Config.setInflationValue(Main.getDfiles().getConfig().getDouble("static-inflation-value", 0.1));
        Config.setInterestRate(Main.getDfiles().getConfig().getDouble("interest-rate", 0.005));
        Config.setMaxDebt(Main.getDfiles().getConfig().getDouble("max-debt-value", -100.00));
        Config.setSellPriceDifference(Main.getDfiles().getConfig().getDouble("sell-price-difference", 2.5));
        Config.setSellPriceDifferenceVariationStart(Main.getDfiles().getConfig().getDouble("sell-price-difference-variation-start", 25.0));
        Config.setShopConfigGUIShopSellValue(Main.getDfiles().getConfig().getDouble("shop-config-guishop-sell-value", 20.00));
    }

}
