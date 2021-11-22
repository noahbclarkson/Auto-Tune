package unprotesting.com.github.config;

import org.bukkit.ChatColor;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;

public class Config {

 @Getter
 @Setter
 private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled, 
                         tutorial, sendPlayerTopMoversOnJoin, dataTransactions, disableMaxBuysSells,
                          ignoreAFK, usePermissionsForShop, readFromCSV, enableEnchantments;

 @Getter
 @Setter
 private static Integer port, timePeriod, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod, autoSellUpdatePeriod, 
                         autoSellProfitUpdatePeriod, maximumShortTradeLength, InterestRateUpdateRate,
                          tutorialMessagePeriod, updatePricesThreshold;

 @Getter
 @Setter
 private static String menuTitle, apiKey, email, currencySymbol, economyShopConfig,
                         dataLocation, background, numberFormat;

 @Getter
 @Setter
 private static Double basicMaxFixedVolatility, basicMaxVariableVolatility, basicMinFixedVolatility, basicMinVariableVolatility, 
                        sellPriceDifference, sellPriceDifferenceVariationStart, dataSelectionM, dataSelectionC, dataSelectionZ,
                         interestRate, maxDebt, shopConfigGUIShopSellValue, enchantmentLimiter,
                          durabilityLimiter;

    public static void loadDefaults() {

        
        Config.setChecksumHeaderBypass(Main.getDataFiles().getConfig().getBoolean("checksum-header-bypass", false));
        Config.setDataTransactions(Main.getDataFiles().getConfig().getBoolean("data-transactions", false));
        Config.setDebugEnabled(Main.getDataFiles().getConfig().getBoolean("debug-enabled", false));
        Config.setDisableMaxBuysSells(Main.getDataFiles().getConfig().getBoolean("disable-max-buys-sells", false));
        Config.setIgnoreAFK(Main.getDataFiles().getConfig().getBoolean("ignore-afk", true));
        Config.setReadFromCSV(Main.getDataFiles().getConfig().getBoolean("read-from-csv", false));
        Config.setSellPriceDifferenceVariationEnabled(Main.getDataFiles().getConfig().getBoolean("sell-price-difference-variation-enabled", true));
        Config.setSendPlayerTopMoversOnJoin(Main.getDataFiles().getConfig().getBoolean("send-player-top-movers-on-join", true));
        Config.setTutorial(Main.getDataFiles().getConfig().getBoolean("tutorial", true));
        Config.setUsePermissionsForShop(Main.getDataFiles().getConfig().getBoolean("use-permission-for-shop", false));
        Config.setWebServer(Main.getDataFiles().getConfig().getBoolean("web-server-enabled", true));
        Config.setEnableEnchantments(Main.getDataFiles().getConfig().getBoolean("enable-enchantments", true));


        Config.setAutoSellProfitUpdatePeriod(Main.getDataFiles().getConfig().getInt("auto-sell-profit-update-period", 1200));
        Config.setAutoSellUpdatePeriod(Main.getDataFiles().getConfig().getInt("auto-sell-update-period", 10));
        Config.setInterestRateUpdateRate(Main.getDataFiles().getConfig().getInt("interest-rate-update-period", 1200));
        Config.setMaximumShortTradeLength(Main.getDataFiles().getConfig().getInt("maximum-short-trade-length", 100));
        Config.setPort(Main.getDataFiles().getConfig().getInt("port", 8321));
        Config.setSellPriceVariationTimePeriod(Main.getDataFiles().getConfig().getInt("sell-price-variation-time-period", 10800));
        Config.setSellPriceVariationUpdatePeriod(Main.getDataFiles().getConfig().getInt("sell-price-variation-update-period", 30));
        Config.setTimePeriod(Main.getDataFiles().getConfig().getInt("time-period", 10));
        Config.setTutorialMessagePeriod(Main.getDataFiles().getConfig().getInt("tutorial-message-period", 300));
        Config.setUpdatePricesThreshold(Main.getDataFiles().getConfig().getInt("update-prices-threshold", 1));


        Config.setApiKey(Main.getDataFiles().getConfig().getString("api-key", "xyz"));
        Config.setBackground(Main.getDataFiles().getConfig().getString("background", "BLACK_STAINED_GLASS_PANE"));
        Config.setCurrencySymbol(ChatColor.translateAlternateColorCodes('&', Main.getDataFiles().getConfig().getString("currency-symbol", "$")));
        Config.setDataLocation(Main.getDataFiles().getConfig().getString("data-location", ""));
        Config.setEconomyShopConfig(Main.getDataFiles().getConfig().getString("economy-shop-config", "default"));
        Config.setEmail(Main.getDataFiles().getConfig().getString("email", "xyz@gmail.com"));
        Config.setMenuTitle(ChatColor.translateAlternateColorCodes('&', Main.getDataFiles().getConfig().getString("menu-title", "Auto-Tune Shop")));
        Config.setNumberFormat(Main.getDataFiles().getConfig().getString("number-format", "###,###,###,##0.00"));


        Config.setBasicMaxFixedVolatility(Main.getDataFiles().getConfig().getDouble("Fixed-Max-Volatility", 2.00));
        Config.setBasicMaxVariableVolatility(Main.getDataFiles().getConfig().getDouble("max-volatility", 0.5));
        Config.setBasicMinFixedVolatility(Main.getDataFiles().getConfig().getDouble("Fixed-Min-Volatility", 0.05));
        Config.setBasicMinVariableVolatility(Main.getDataFiles().getConfig().getDouble("min-volatility", 0.025));
        Config.setDataSelectionC(Main.getDataFiles().getConfig().getDouble("data-selection-c", 1.05));
        Config.setDataSelectionM(Main.getDataFiles().getConfig().getDouble("data-selection-m", 0.05));
        Config.setDataSelectionZ(Main.getDataFiles().getConfig().getDouble("data-selection-z", 1.6));
        Config.setDurabilityLimiter(Main.getDataFiles().getConfig().getDouble("durability-limiter", 5.0));
        Config.setEnchantmentLimiter(Main.getDataFiles().getConfig().getDouble("enchantment-limiter", 7.5));
        Config.setInterestRate(Main.getDataFiles().getConfig().getDouble("interest-rate", 0.005));
        Config.setMaxDebt(Main.getDataFiles().getConfig().getDouble("max-debt-value", -100.00));
        Config.setSellPriceDifference(Main.getDataFiles().getConfig().getDouble("sell-price-difference", 2.5));
        Config.setSellPriceDifferenceVariationStart(Main.getDataFiles().getConfig().getDouble("sell-price-difference-variation-start", 25.0));
        Config.setShopConfigGUIShopSellValue(Main.getDataFiles().getConfig().getDouble("shop-config-guishop-sell-value", 20.00));
    }

}
