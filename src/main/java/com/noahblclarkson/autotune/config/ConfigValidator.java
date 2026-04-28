package com.noahblclarkson.autotune.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link AutoTuneConfig} values at startup.
 * Fails fast with descriptive error messages rather than silently using
 * invalid values that would cause wrong economy behavior.
 *
 * Validation rules are evidence-based:
 * - Spread/price values are bounded to prevent degenerate engine behavior
 * - Intervals/timeouts must be positive
 * - Tiered circuit breaker ratios must be ordered correctly
 * - Tax/retention values must be in valid ranges
 */
@SuppressWarnings("PMD")
public class ConfigValidator {

    /** Names of config sections for clear error messages */
    private static final String S_ECONOMY = "economy";
    private static final String S_SPREAD = "economy.spread";
    private static final String S_PLAYER_SCALING = "economy.playerScaling";
    private static final String S_LOANS = "loans";
    private static final String S_GUI = "gui";
    private static final String S_PRICE_REPORTER = "priceReporter";
    private static final String S_AUTOSELL = "autosell";
    private static final String S_CLEANUP = "cleanup";
    private static final String S_TAX = "tax";
    private static final String S_SCOREBOARD = "scoreboard";
    private static final String S_WEB = "web";
    private static final String S_AUCTION = "auction";
    private static final String S_MARKET_EVENTS = "marketEvents";
    private static final String S_NEWS = "news";
    private static final String S_EXCHANGE_RATE = "exchangeRate";
    private static final String S_ADMIN_WEBHOOK = "admin-webhook";
    private static final String S_PRICE_MILESTONE = "price-milestone";
    private static final String S_MARKET_DIGEST = "market-digest";
    private static final String S_ONBOARDING = "onboarding";

    /**
     * Validates all config values. Returns a list of violations, or an empty
     * list if the config is valid.
     *
     * Call this early in {@code onEnable()} to fail fast with a descriptive
     * message before any economy logic runs.
     */
    public static List<String> validate(AutoTuneConfig config) {
        List<String> violations = new ArrayList<>();

        validateEconomy(config.economy(), violations);
        validateSpread(config.economy().spread(), violations);
        validatePlayerScaling(config.economy().playerScaling(), violations);
        validateLoans(config.loans(), violations);
        validateGui(config.gui(), violations);
        validatePriceReporter(config.priceReporter(), violations);
        validateAutosell(config.autosell(), violations);
        validateCleanup(config.cleanup(), violations);
        validateTax(config.tax(), violations);
        validateScoreboard(config.scoreboard(), violations);
        validateWeb(config.web(), violations);
        validateAuction(config.auction(), violations);
        validateMarketEvents(config.marketEvents(), violations);
        validateEconomicNews(config.news(), violations);
        validateExchangeRate(config.exchangeRate(), violations);
        validateAdminWebhook(config.webhook(), violations);
        validatePriceMilestone(config.priceMilestones(), violations);
        validateMarketDigest(config.marketDigest(), violations);
        validateOnboarding(config.onboarding(), violations);

        return violations;
    }

    private static void validateEconomy(AutoTuneConfig.EconomyConfig c, List<String> v) {
        if (c.updateInterval() <= 0) {
            v.add(S_ECONOMY + ".updateInterval must be > 0 ticks (currently " + c.updateInterval() + "). "
                    + "Zero or negative intervals would prevent price updates.");
        }
        if (c.maxPriceChangePercent() <= 0) {
            v.add(S_ECONOMY + ".maxPriceChangePercent must be > 0 (currently " + c.maxPriceChangePercent() + "). "
                    + "Zero prevents all price movement; negative reverses price direction.");
        }
        if (c.tradeWindowDays() <= 0) {
            v.add(S_ECONOMY + ".tradeWindowDays must be > 0 (currently " + c.tradeWindowDays() + "). "
                    + "Price history requires at least 1 day of data.");
        }
        if (c.minWindowDays() <= 0) {
            v.add(S_ECONOMY + ".minWindowDays must be > 0 (currently " + c.minWindowDays() + ").");
        }
        if (c.maxWindowDays() < c.minWindowDays()) {
            v.add(S_ECONOMY + ".maxWindowDays (" + c.maxWindowDays() + ") must be >= "
                    + "minWindowDays (" + c.minWindowDays() + ").");
        }
        if (c.slippageCoeff() < 0) {
            v.add(S_ECONOMY + ".slippageCoeff must be >= 0 (currently " + c.slippageCoeff() + "). "
                    + "Negative slippage would amplify rather than dampen large trades.");
        }
        if (c.sectorCorrelation() < 0 || c.sectorCorrelation() > 1) {
            v.add(S_ECONOMY + ".sectorCorrelation must be between 0 and 1 (currently " + c.sectorCorrelation() + "). "
                    + "Values > 1 cause unrelated items to move together; negative values invert correlations.");
        }
        if (c.playerRateLimitMultiplier() < 0) {
            v.add(S_ECONOMY + ".playerRateLimitMultiplier must be >= 0 (currently " + c.playerRateLimitMultiplier() + ").");
        }
        if (c.trendDampening() < 0 || c.trendDampening() > 1) {
            v.add(S_ECONOMY + ".trendDampening must be between 0 and 1 (currently " + c.trendDampening() + "). "
                    + "1 = no continuation bias, 0 = prices trend forever.");
        }
        if (c.trendStreakThresholdPercent() < 0 || c.trendStreakThresholdPercent() > 100) {
            v.add(S_ECONOMY + ".trendStreakThresholdPercent must be 0-100 (currently " + c.trendStreakThresholdPercent() + ").");
        }
        if (c.trendDampeningFloor() < 0) {
            v.add(S_ECONOMY + ".trendDampeningFloor must be >= 0 (currently " + c.trendDampeningFloor() + ").");
        }
        if (c.maxSectorCorrelationGroupSize() <= 0) {
            v.add(S_ECONOMY + ".maxSectorCorrelationGroupSize must be > 0 (currently " + c.maxSectorCorrelationGroupSize() + ").");
        }
        if (c.minBuyQuantity() < 1) {
            v.add(S_ECONOMY + ".minBuyQuantity must be >= 1 (currently " + c.minBuyQuantity() + "). "
                    + "A minimum of 1 prevents buying zero items.");
        }
        if (c.minSellQuantity() < 1) {
            v.add(S_ECONOMY + ".minSellQuantity must be >= 1 (currently " + c.minSellQuantity() + "). "
                    + "A minimum of 1 prevents selling zero items.");
        }
        if (c.minBuyValue() < 0) {
            v.add(S_ECONOMY + ".minBuyValue must be >= 0 (currently " + c.minBuyValue() + "). "
                    + "Set to 0 to disable this check.");
        }
        if (c.minSellValue() < 0) {
            v.add(S_ECONOMY + ".minSellValue must be >= 0 (currently " + c.minSellValue() + "). "
                    + "Set to 0 to disable this check.");
        }
    }

    private static void validateSpread(AutoTuneConfig.SpreadConfig c, List<String> v) {
        if (c.baseSpread() <= 0) {
            v.add(S_SPREAD + ".baseSpread must be > 0 (currently " + c.baseSpread() + "). "
                    + "Zero spread means no profit margin for the server on any trade — "
                    + "the economy engine requires positive spread to function correctly.");
        }
        if (c.baseSpread() > 10) {
            v.add(S_SPREAD + ".baseSpread is very large (" + c.baseSpread() + "). "
                    + "A spread > 10 (1000% half-spread) means buy/sell prices are extremely far apart. "
                    + "Consider values 0.05-0.50 for normal economies.");
        }
        if (c.volumeImpact() < 0 || c.volumeImpact() > 10) {
            v.add(S_SPREAD + ".volumeImpact must be between 0 and 10 (currently " + c.volumeImpact() + "). "
                    + "Very high values cause spread to explode under high volume; values < 0 invert spread direction.");
        }
        if (c.playerImpact() < 0 || c.playerImpact() > 1) {
            v.add(S_SPREAD + ".playerImpact must be between 0 and 1 (currently " + c.playerImpact() + "). "
                    + "1 = full player-count spread reduction; 0 = players don't affect spread.");
        }
        if (c.liquidityCoeff() < 0) {
            v.add(S_SPREAD + ".liquidityCoeff must be >= 0 (currently " + c.liquidityCoeff() + "). "
                    + "Negative liquidity coefficient would INCREASE spread as traders join — inverting engine behavior.");
        }
        if (c.liquidityFullEffectTraders() <= 0) {
            v.add(S_SPREAD + ".liquidityFullEffectTraders must be > 0 (currently " + c.liquidityFullEffectTraders() + "). "
                    + "Zero traders means spread never benefits from liquidity scaling.");
        }
    }

    private static void validatePlayerScaling(AutoTuneConfig.PlayerScalingConfig c, List<String> v) {
        if (c.fullEffectPlayers() <= 0) {
            v.add(S_PLAYER_SCALING + ".fullEffectPlayers must be > 0 (currently " + c.fullEffectPlayers() + "). "
                    + "Zero or negative would prevent the player-count scaling curve from ever reaching full effect.");
        }
    }

    private static void validateLoans(AutoTuneConfig.LoanConfig c, List<String> v) {
        if (c.baseInterestRate() < 0) {
            v.add(S_LOANS + ".baseInterestRate must be >= 0 (currently " + c.baseInterestRate() + "). "
                    + "Negative interest would make loans generate free money for borrowers.");
        }
        if (c.baseInterestRate() > 1) {
            v.add(S_LOANS + ".baseInterestRate is very high (" + (c.baseInterestRate() * 100) + "%). "
                    + "Normal economies use 1-20% daily rates. Values > 100% cause exponential debt explosions.");
        }
        if (c.maxLoanMultiplier() <= 0) {
            v.add(S_LOANS + ".maxLoanMultiplier must be > 0 (currently " + c.maxLoanMultiplier() + "). "
                    + "Zero prevents any loans; negative would allow infinite loans.");
        }
        if (c.minCreditScore() < 0) {
            v.add(S_LOANS + ".minCreditScore must be >= 0 (currently " + c.minCreditScore() + ").");
        }
        if (c.defaultDurationDays() <= 0) {
            v.add(S_LOANS + ".defaultDurationDays must be > 0 (currently " + c.defaultDurationDays() + ").");
        }
        if (c.minTermDays() <= 0 || c.maxTermDays() <= 0) {
            v.add(S_LOANS + ".minTermDays and maxTermDays must be > 0.");
        } else if (c.maxTermDays() < c.minTermDays()) {
            v.add(S_LOANS + ".maxTermDays (" + c.maxTermDays() + ") must be >= minTermDays (" + c.minTermDays() + ").");
        }
        if (c.compoundIntervalHours() <= 0) {
            v.add(S_LOANS + ".compoundIntervalHours must be > 0 (currently " + c.compoundIntervalHours() + "). "
                    + "Zero or negative interval prevents interest from ever compounding.");
        }
        if (c.overdueCheckIntervalHours() <= 0) {
            v.add(S_LOANS + ".overdueCheckIntervalHours must be > 0 (currently " + c.overdueCheckIntervalHours() + ").");
        }
        if (c.termPremiumPerDay() < 0) {
            v.add(S_LOANS + ".termPremiumPerDay must be >= 0 (currently " + c.termPremiumPerDay() + "). "
                    + "Negative premium would reduce cost for longer terms.");
        }
        if (c.debtGdpTier1Ratio() < 0 || c.debtGdpTier2Ratio() < 0 || c.debtGdpTier3Ratio() < 0) {
            v.add(S_LOANS + ".debtGdpTier*Ratio values must be >= 0.");
        } else if (c.debtGdpTier3Ratio() <= c.debtGdpTier2Ratio()
                || c.debtGdpTier2Ratio() <= c.debtGdpTier1Ratio()) {
            v.add(S_LOANS + ".Tier ratios must be strictly ascending: tier1 (" + c.debtGdpTier1Ratio()
                    + ") < tier2 (" + c.debtGdpTier2Ratio() + ") < tier3 (" + c.debtGdpTier3Ratio()
                    + "). If tiers overlap, the circuit breaker may not fire correctly.");
        }
        if (c.tier1InterestCap() < 0 || c.tier1InterestCap() > 1 || c.tier2InterestCap() < 0 || c.tier2InterestCap() > 1) {
            v.add(S_LOANS + ".tier*InterestCap values must be between 0 and 1 (fraction of normal interest). "
                    + "Current: tier1=" + c.tier1InterestCap() + ", tier2=" + c.tier2InterestCap());
        }
        if (c.earlyRepaymentBonusMultiplier() < 0) {
            v.add(S_LOANS + ".earlyRepaymentBonusMultiplier must be >= 0 (currently " + c.earlyRepaymentBonusMultiplier() + ").");
        }
        if (c.inflationRateImpact() < 0) {
            v.add(S_LOANS + ".inflationRateImpact must be >= 0 (currently " + c.inflationRateImpact() + ").");
        }
        if (c.postDefaultCooldownHours() < 0) {
            v.add(S_LOANS + ".postDefaultCooldownHours must be >= 0 (currently " + c.postDefaultCooldownHours() + "). "
                    + "Use 0 to disable the post-default cooldown.");
        }
        if (c.singleLoanGdpCap() <= 0) {
            v.add(S_LOANS + ".singleLoanGdpCap must be > 0 (currently " + c.singleLoanGdpCap() + "). "
                    + "A value of 1.0 caps single loans at economy GDP. Values < 1 would be trivially small.");
        }
        if (c.totalDebtGdpCap() < 0) {
            v.add(S_LOANS + ".totalDebtGdpCap must be >= 0 (currently " + c.totalDebtGdpCap() + "). "
                    + "Set to 0 to disable the economy-wide debt cap. Positive values set the cap as a multiple of GDP.");
        }
        if (c.tier3HysteresisBand() < 0 || c.tier3HysteresisBand() >= 1) {
            v.add(S_LOANS + ".tier3HysteresisBand must be >= 0 and < 1 (currently " + c.tier3HysteresisBand() + "). "
                    + "Values >= 1 would prevent the circuit breaker from ever unlocking.");
        }
        if (c.minInterestMultiplier() < 0 || c.minInterestMultiplier() > 1) {
            v.add(S_LOANS + ".minInterestMultiplier must be between 0 and 1 (currently " + c.minInterestMultiplier() + "). "
                    + "A value of 0.05 means interest never drops below 5% even at max D/G.");
        }
        if (c.guildbuyerTotalDebtCap() < 0) {
            v.add(S_LOANS + ".guildbuyerTotalDebtCap must be >= 0 (currently " + c.guildbuyerTotalDebtCap() + "). "
                    + "Set to 0 to disable the per-GuildBuyer debt cap.");
        }
        if (c.tier3ExitMultiplierCap() < 0 || c.tier3ExitMultiplierCap() > 1) {
            v.add(S_LOANS + ".tier3ExitMultiplierCap must be between 0 and 1 (currently " + c.tier3ExitMultiplierCap() + "). "
                    + "Set to 1.0 to disable the graduated TIER3 exit cap.");
        }
        if (c.tier3ExitDelayTicks() < 0) {
            v.add(S_LOANS + ".tier3ExitDelayTicks must be >= 0 (currently " + c.tier3ExitDelayTicks() + "). "
                    + "Set to 0 to disable the graduated TIER3 exit delay window.");
        }
        // ── Dangerous config warnings (soft errors — admins may have good reasons) ──
        if (c.debtGdpTier3Ratio() < 20.0) {
            v.add(S_LOANS + ".debtGdpTier3Ratio is " + c.debtGdpTier3Ratio() + " (below 20). "
                    + "Simulation evidence shows tier3 < 20 causes severe D/G instability at 60+ days. "
                    + "Recommended minimum: 30.0.");
        }
        if (c.postDefaultCooldownHours() > 0 && c.postDefaultCooldownHours() < 72) {
            v.add(S_LOANS + ".postDefaultCooldownHours is " + c.postDefaultCooldownHours() + "h (below 72h). "
                    + "Short cooldowns allow cascade re-borrowing after defaults. "
                    + "Recommended minimum: 168h (7 days) to prevent exploit cycles.");
        }
    }

    private static void validateGui(AutoTuneConfig.GuiConfig c, List<String> v) {
        if (c.itemsPerPage() <= 0 || c.itemsPerPage() > 54) {
            v.add(S_GUI + ".itemsPerPage must be 1-54 (currently " + c.itemsPerPage() + "). "
                    + "A standard chest has 54 slots; values > 54 can't be displayed in one GUI page.");
        }
        if (c.searchTimeoutTicks() <= 0) {
            v.add(S_GUI + ".searchTimeoutTicks must be > 0 (currently " + c.searchTimeoutTicks() + "). "
                    + "Zero timeout would immediately expire all searches.");
        }
        if (c.titles().shop().length() > 32 || c.titles().sell().length() > 32
                || c.titles().autosell().length() > 32) {
            v.add(S_GUI + ".titles: Minecraft scoreboard titles are capped at 32 characters. "
                    + "Current lengths — shop: " + c.titles().shop().length()
                    + ", sell: " + c.titles().sell().length()
                    + ", autosell: " + c.titles().autosell().length());
        }
        if (c.buyQuantities() == null || c.buyQuantities().isEmpty()) {
            v.add(S_GUI + ".buyQuantities must have at least one value (currently "
                    + (c.buyQuantities() == null ? "null" : "empty") + ").");
        }
        for (int i = 0; i < c.buyQuantities().size(); i++) {
            if (c.buyQuantities().get(i) <= 0) {
                v.add(S_GUI + ".buyQuantities[" + i + "] must be > 0 (found " + c.buyQuantities().get(i) + ").");
            }
        }
    }

    private static void validatePriceReporter(AutoTuneConfig.PriceReporterConfig c, List<String> v) {
        if (c.reportIntervalMinutes() <= 0) {
            v.add(S_PRICE_REPORTER + ".reportIntervalMinutes must be > 0 (currently " + c.reportIntervalMinutes() + "). "
                    + "Zero or negative interval prevents price reporting to the central server.");
        }
        if (c.apiUrl() == null || c.apiUrl().isBlank()) {
            v.add(S_PRICE_REPORTER + ".apiUrl must not be blank.");
        }
        if (c.serverId() == null || c.serverId().isBlank()) {
            v.add(S_PRICE_REPORTER + ".serverId must not be blank.");
        }
        if (c.apiKey() == null || c.apiKey().isBlank()) {
            v.add(S_PRICE_REPORTER + ".apiKey is not set — price reporting will be skipped until a valid key is configured.");
        }
    }

    private static void validateAutosell(AutoTuneConfig.AutosellConfig c, List<String> v) {
        if (c.minimumPrice() < 0) {
            v.add(S_AUTOSELL + ".minimumPrice must be >= 0 (currently " + c.minimumPrice() + "). "
                    + "Negative minimum prices would never trigger — and allow free money exploits.");
        }
    }

    private static void validateCleanup(AutoTuneConfig.CleanupConfig c, List<String> v) {
        if (c.cleanupIntervalHours() < 0) {
            v.add(S_CLEANUP + ".cleanupIntervalHours must be >= 0 (currently " + c.cleanupIntervalHours() + "). "
                    + "Set to 0 to disable cleanup entirely. Negative values are invalid.");
        }
        validateRetention(S_CLEANUP + ".transactions", c.transactions(), v);
        validateRetention(S_CLEANUP + ".marketHistory", c.marketHistory(), v);
        validateRetention(S_CLEANUP + ".economySnapshots", c.economySnapshots(), v);
        validateRetention(S_CLEANUP + ".auctionOrders", c.auctionOrders(), v);
        validateRetention(S_CLEANUP + ".auctionFills", c.auctionFills(), v);
        validateRetention(S_CLEANUP + ".marketEvents", c.marketEvents(), v);
    }

    private static void validateRetention(String path, AutoTuneConfig.CleanupConfig.RetentionConfig c, List<String> v) {
        if (c.enabled() && c.retentionDays() <= 0) {
            v.add(path + ".retentionDays must be > 0 when enabled (currently " + c.retentionDays() + "). "
                    + "Zero retention would delete all data immediately.");
        }
        if (c.retentionDays() < 0) {
            v.add(path + ".retentionDays must be >= 0 (currently " + c.retentionDays() + ").");
        }
        if (c.retentionDays() > 3650) {
            v.add(path + ".retentionDays is very large (" + c.retentionDays() + " days ≈ " + (c.retentionDays() / 365) + " years). "
                    + "This effectively disables cleanup and will cause unbounded database growth.");
        }
    }

    private static void validateTax(AutoTuneConfig.TaxConfig c, List<String> v) {
        validateTaxRate(S_TAX + ".buyTaxPercent", c.buyTaxPercent(), v);
        validateTaxRate(S_TAX + ".sellTaxPercent", c.sellTaxPercent(), v);
        validateTaxRate(S_TAX + ".auctionTaxPercent", c.auctionTaxPercent(), v);
        validateTaxRate(S_TAX + ".loanInterestTaxPercent", c.loanInterestTaxPercent(), v);
    }

    private static void validateTaxRate(String path, double rate, List<String> v) {
        if (rate < 0 || rate > 100) {
            v.add(path + " must be 0-100 (currently " + rate + "). "
                    + "Negative tax would subsidize trades instead of collecting revenue; "
                    + ">100% tax on sales would give players free money.");
        }
    }

    private static void validateScoreboard(AutoTuneConfig.ScoreboardConfig c, List<String> v) {
        if (c.title().length() > 32) {
            v.add(S_SCOREBOARD + ".title is " + c.title().length() + " chars — Minecraft caps titles at 32. "
                    + "The scoreboard will display a truncated title. Current: '" + c.title() + "'");
        }
        if (c.updateIntervalSeconds() <= 0) {
            v.add(S_SCOREBOARD + ".updateIntervalSeconds must be > 0 (currently " + c.updateIntervalSeconds() + ").");
        }
        if (c.updateIntervalSeconds() < 10) {
            v.add(S_SCOREBOARD + ".updateIntervalSeconds is very low (" + c.updateIntervalSeconds() + "s). "
                    + "Values < 10s may cause scoreboard flicker. Recommended: 30-60s.");
        }
    }

    private static void validateWeb(AutoTuneConfig.WebConfig c, List<String> v) {
        if (c.port() <= 0 || c.port() > 65535) {
            v.add(S_WEB + ".port must be 1-65535 (currently " + c.port() + "). "
                    + "Port " + c.port() + " is invalid for server binding.");
        }
        if (c.host() == null || c.host().isBlank()) {
            v.add(S_WEB + ".host must not be blank.");
        }
    }

    private static void validateAuction(AutoTuneConfig.AuctionConfig c, List<String> v) {
        if (c.expirationCheckIntervalMinutes() < 0) {
            v.add(S_AUCTION + ".expirationCheckIntervalMinutes must be >= 0 (currently " + c.expirationCheckIntervalMinutes() + "). "
                    + "Set to 0 to disable automatic order expiration processing.");
        }
        if (c.defaultDurationHours() <= 0) {
            v.add(S_AUCTION + ".defaultDurationHours must be > 0 (currently " + c.defaultDurationHours() + "). "
                    + "Zero expiration means orders never expire, accumulating forever.");
        }
    }

    private static void validateMarketEvents(AutoTuneConfig.MarketEventConfig c, List<String> v) {
        if (c.checkIntervalMinutes() <= 0) {
            v.add(S_MARKET_EVENTS + ".checkIntervalMinutes must be > 0 (currently " + c.checkIntervalMinutes() + "). "
                    + "Zero or negative would prevent event lifecycle checks.");
        }
        if (c.defaultEvents() != null) {
            for (int i = 0; i < c.defaultEvents().size(); i++) {
                AutoTuneConfig.MarketEventConfigEntry evt = c.defaultEvents().get(i);
                if (evt.multiplier() <= 0) {
                    v.add(S_MARKET_EVENTS + ".defaultEvents[" + i + "].multiplier must be > 0 (currently " + evt.multiplier() + "). "
                            + "Zero or negative multipliers would break event price calculations.");
                }
                if (evt.durationMinutes() <= 0) {
                    v.add(S_MARKET_EVENTS + ".defaultEvents[" + i + "].durationMinutes must be > 0 (currently " + evt.durationMinutes() + "). "
                            + "Zero-duration events end immediately.");
                }
            }
        }
    }

    private static void validateEconomicNews(AutoTuneConfig.EconomicNewsConfig c, List<String> v) {
        if (c.intervalMinutes() <= 0) {
            v.add("news.interval-minutes must be > 0 (currently " + c.intervalMinutes() + "). "
                    + "Zero or negative would disable the news feed.");
        }
        if (c.intervalMinutes() > 60) {
            v.add("news.interval-minutes is very high at " + c.intervalMinutes() + " minutes. "
                    + "Consider ≤ 15 for an engaging news feed.");
        }
        if (c.priceChangeThresholdPercent() <= 0) {
            v.add("news.price-change-threshold-percent must be > 0 (currently " + c.priceChangeThresholdPercent() + "). "
                    + "Zero or negative would spam every tiny price movement.");
        }
        if (c.volumeSpikeMultiplier() < 1.0) {
            v.add("news.volume-spike-multiplier must be ≥ 1.0 (currently " + c.volumeSpikeMultiplier() + "). "
                    + "A value < 1.0 would trigger on below-average volume.");
        }
        if (c.historyWindowMinutes() <= 0) {
            v.add("news.history-window-minutes must be > 0 (currently " + c.historyWindowMinutes() + ").");
        }
        if (c.maxItemsPerCycle() <= 0) {
            v.add("news.max-items-per-cycle must be > 0 (currently " + c.maxItemsPerCycle() + ").");
        }
        if (c.itemCooldownMinutes() <= 0) {
            v.add("news.item-cooldown-minutes must be > 0 (currently " + c.itemCooldownMinutes() + ").");
        }
    }

    private static void validateExchangeRate(AutoTuneConfig.ExchangeRateConfig c, List<String> v) {
        if (c.fetchIntervalMinutes() <= 0) {
            v.add(S_EXCHANGE_RATE + ".fetchIntervalMinutes must be > 0 (currently " + c.fetchIntervalMinutes() + "). "
                    + "Zero or negative would prevent exchange rate updates.");
        }
    }

    private static void validateAdminWebhook(AutoTuneConfig.AdminWebhookConfig c, List<String> v) {
        if (c.notifyHighDebtThreshold() < 0) {
            v.add(S_ADMIN_WEBHOOK + ".notify-high-debt-threshold must be >= 0 (currently " + c.notifyHighDebtThreshold() + ").");
        }
        if (c.lowVolumeThreshold() < 0) {
            v.add(S_ADMIN_WEBHOOK + ".low-volume-threshold must be >= 0 (currently " + c.lowVolumeThreshold() + ").");
        }
    }

    private static void validatePriceMilestone(AutoTuneConfig.PriceMilestoneConfig c, List<String> v) {
        if (c.intervalMinutes() <= 0) {
            v.add(S_PRICE_MILESTONE + ".interval-minutes must be > 0 (currently " + c.intervalMinutes() + ").");
        }
        if (c.cooldownMinutes() <= 0) {
            v.add(S_PRICE_MILESTONE + ".cooldown-minutes must be > 0 (currently " + c.cooldownMinutes() + ").");
        }
        if (c.thresholds() == null || c.thresholds().isEmpty()) {
            v.add(S_PRICE_MILESTONE + ".thresholds must contain at least one value (currently "
                    + (c.thresholds() == null ? "null" : "empty") + ").");
        } else {
            for (int i = 0; i < c.thresholds().size(); i++) {
                if (c.thresholds().get(i) < 0) {
                    v.add(S_PRICE_MILESTONE + ".thresholds[" + i + "] must be >= 0 (found " + c.thresholds().get(i) + ").");
                }
                if (i > 0 && c.thresholds().get(i) <= c.thresholds().get(i - 1)) {
                    v.add(S_PRICE_MILESTONE + ".thresholds must be strictly ascending (found "
                            + c.thresholds().get(i) + " at index " + i + " <= " + c.thresholds().get(i - 1) + " at index " + (i - 1) + ").");
                }
            }
        }
    }

    private static void validateMarketDigest(AutoTuneConfig.MarketDigestConfig c, List<String> v) {
        if (!"daily".equals(c.interval()) && !"weekly".equals(c.interval())) {
            v.add(S_MARKET_DIGEST + ".interval must be 'daily' or 'weekly' (currently '" + c.interval() + "').");
        }
        if (c.dayOfWeek() < 0 || c.dayOfWeek() > 6) {
            v.add(S_MARKET_DIGEST + ".day-of-week must be 0-6 (Sunday=0, currently " + c.dayOfWeek() + ").");
        }
        if (c.hourOfDay() < 0 || c.hourOfDay() > 23) {
            v.add(S_MARKET_DIGEST + ".hour-of-day must be 0-23 (currently " + c.hourOfDay() + ").");
        }
    }

    private static void validateOnboarding(AutoTuneConfig.OnboardingConfig c, List<String> v) {
        if (c.checkIntervalHours() <= 0) {
            v.add(S_ONBOARDING + ".check-interval-hours must be > 0 (currently " + c.checkIntervalHours() + ").");
        }
        if (c.milestones() == null || c.milestones().isEmpty()) {
            v.add(S_ONBOARDING + ".milestones must contain at least one entry (currently "
                    + (c.milestones() == null ? "null" : "empty") + ").");
        } else {
            for (int i = 0; i < c.milestones().size(); i++) {
                AutoTuneConfig.OnboardingMilestoneConfig m = c.milestones().get(i);
                if (m.dayOffset() < 0) {
                    v.add(S_ONBOARDING + ".milestones[" + i + "].day-offset must be >= 0 (found " + m.dayOffset() + ").");
                }
                if (m.message() == null || m.message().isBlank()) {
                    v.add(S_ONBOARDING + ".milestones[" + i + "].message must not be blank (category: " + m.category() + ").");
                }
            }
        }
    }

    /**
     * Formats violations as a multi-line string for logging or exception messages.
     */
    public static String format(List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("Auto-Tune config validation failed — ").append(violations.size())
                .append(" error(s) found:\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append("\n");
        }
        sb.append("Please fix these values in config.yml before starting the server.");
        return sb.toString();
    }
}
