package unprotesting.com.github.util;

import lombok.Getter;
import lombok.Setter;

public final class Config {

    @Getter
    @Setter
    private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled;

    @Getter
    @Setter
    private static Integer port, timePeriod, menuRows, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod, autoSellUpdatePeriod, autoSellProfitUpdatePeriod;

    @Getter
    @Setter
    private static String serverName, pricingModel, basicVolatilityAlgorithim, menuTitle, noPermission;

    @Getter
    @Setter
    private static Double basicMaxFixedVolatility, basicMaxVariableVolatility, 
    basicMinFixedVolatility, basicMinVariableVolatility, sellPriceDifference, 
    sellPriceDifferenceVariationStart, dataSelectionM, dataSelectionC, dataSelectionZ;


}