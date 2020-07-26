package unprotesting.com.github.util;

import unprotesting.com.github.Main;

import lombok.Getter;
import lombok.Setter;

public final class Config {

    @Getter
    @Setter
    private static boolean webServer, debugEnabled, checksumHeaderBypass, sellPriceDifferenceVariationEnabled;

    @Getter
    @Setter
    private static Integer port, timePeriod, menuRows, sellPriceVariationTimePeriod, sellPriceVariationUpdatePeriod;

    @Getter
    @Setter
    private static String serverName, pricingModel, basicVolatilityAlgorithim, menuTitle, noPermission;

    @Getter
    @Setter
    private static Double basicMaxFixedVolatility, basicMaxVariableVolatility, basicMinFixedVolatility, basicMinVariableVolatility, sellPriceDifference, sellPriceDifferenceVariationStart;


}