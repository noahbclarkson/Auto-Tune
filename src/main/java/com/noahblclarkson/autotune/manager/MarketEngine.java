package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
public class MarketEngine {

    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);
    private static final int VOLUME_BUCKETS = 10;
    private static final double ATANH_099 = 2.6466524123622457;
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("0.01");
    private static final double TARGET_TX_DENSITY_PER_DAY = 100.0;

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ItemRepository itemRepository;
    private final TransactionRepository transactionRepository;

    private final Map<Integer, BigDecimal> priceCache = new ConcurrentHashMap<>();
    private final Map<Integer, SpreadResult> spreadCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tickBuyVolume = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tickSellVolume = new ConcurrentHashMap<>();
    private final Map<Integer, PriceTrend.Direction> trendDirectionCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> trendStreakCache = new ConcurrentHashMap<>();
    private volatile double lastGlobalVolumeMultiplier = 1.0;

    @Inject
    public MarketEngine(
            AutoTune plugin,
            ConfigManager configManager,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
    }

    public void reload() {
        priceCache.clear();
        spreadCache.clear();
        tickBuyVolume.clear();
        tickSellVolume.clear();
        trendDirectionCache.clear();
        trendStreakCache.clear();
    }

    public void tick() {
        AutoTuneConfig.EconomyConfig economyConfig = configManager.getConfig().economy();
        int onlineCount = plugin.getServer().getOnlinePlayers().size();

        try {
            List<ShopItem> items = itemRepository.findAll();
            Instant now = Instant.now();

            int windowDays = economyConfig.adaptiveWindow()
                    ? calculateAdaptiveWindow(now, economyConfig)
                    : economyConfig.tradeWindowDays();

            Instant tradeWindowStart = now.minus(Duration.ofDays(windowDays));
            long tradeWindowMs = Duration.ofDays(windowDays).toMillis();

            double globalVolumeMultiplier = calculateGlobalVolumeMultiplier(tradeWindowStart, now);
            lastGlobalVolumeMultiplier = globalVolumeMultiplier;

            Map<Integer, TradeMetrics> itemMetrics = new HashMap<>();
            for (ShopItem item : items) {
                TradeMetrics metrics = calculateTradeMetrics(item.id(), tradeWindowStart, tradeWindowMs, economyConfig);
                itemMetrics.put(item.id(), metrics);
            }

            // Pass 1: Calculate new prices and spreads (don't persist yet)
            Map<Integer, BigDecimal> newPrices = new HashMap<>();
            Map<Integer, SpreadResult> newSpreads = new HashMap<>();

            for (ShopItem item : items) {
                TradeMetrics metrics = itemMetrics.get(item.id());
                BigDecimal newPrice = calculateNewPrice(item, metrics, onlineCount, economyConfig);
                SpreadResult spread = calculateSpread(item, metrics, onlineCount, globalVolumeMultiplier, economyConfig);

                newPrices.put(item.id(), newPrice);
                newSpreads.put(item.id(), spread);
            }

            // Pass 2: Sectoral correlation
            double sectorCorrelation = economyConfig.sectorCorrelation();
            if (sectorCorrelation > 0.0001) {
                Map<String, List<ShopItem>> sectionGroups = new HashMap<>();
                for (ShopItem item : items) {
                    sectionGroups.computeIfAbsent(item.section(), k -> new ArrayList<>()).add(item);
                }

                int maxGroupSize = economyConfig.maxSectorCorrelationGroupSize();
                for (List<ShopItem> group : sectionGroups.values()) {
                    if (group.size() < 2 || group.size() > maxGroupSize) continue;

                    Map<Integer, Double> percentChanges = new HashMap<>();
                    for (ShopItem item : group) {
                        BigDecimal oldPrice = item.price();
                        BigDecimal newPrice = newPrices.get(item.id());
                        if (oldPrice.compareTo(BigDecimal.ZERO) > 0) {
                            double pct = newPrice.subtract(oldPrice)
                                    .divide(oldPrice, MATH_CONTEXT)
                                    .doubleValue();
                            percentChanges.put(item.id(), pct);
                        }
                    }

                    for (ShopItem item : group) {
                        Double selfChange = percentChanges.get(item.id());
                        if (selfChange == null) continue;

                        double othersSum = 0;
                        int othersCount = 0;
                        for (ShopItem other : group) {
                            if (other.id() == item.id()) continue;
                            Double otherChange = percentChanges.get(other.id());
                            if (otherChange != null) {
                                othersSum += otherChange;
                                othersCount++;
                            }
                        }

                        if (othersCount > 0) {
                            double othersAvg = othersSum / othersCount;
                            double nudge = sectorCorrelation * othersAvg;
                            BigDecimal currentNew = newPrices.get(item.id());
                            BigDecimal nudged = currentNew.multiply(
                                    BigDecimal.ONE.add(BigDecimal.valueOf(nudge)), MATH_CONTEXT);
                            newPrices.put(item.id(), nudged);
                        }
                    }
                }
            }

            // Pass 3: Apply price floor, persist, and track trend streaks
            for (ShopItem item : items) {
                BigDecimal finalPrice = newPrices.get(item.id())
                        .max(PRICE_FLOOR)
                        .setScale(2, RoundingMode.HALF_UP);
                SpreadResult spread = newSpreads.get(item.id());

                priceCache.put(item.id(), finalPrice);
                spreadCache.put(item.id(), spread);

                itemRepository.updatePrice(item.id(), finalPrice);

                int buyVol = tickBuyVolume.getOrDefault(item.id(), 0);
                int sellVol = tickSellVolume.getOrDefault(item.id(), 0);
                itemRepository.recordPriceHistory(
                        item.id(), finalPrice, buyVol, sellVol, spread.bpd(), spread.spd());

                updateTrendStreak(item.id(), finalPrice, item.price());
            }

            tickBuyVolume.clear();
            tickSellVolume.clear();

            computeAndStoreRatios(items);

            if (configManager.getConfig().debug().logPrices()) {
                plugin.getLogger().info("Market tick completed. Updated " + items.size() + " prices (window=" + windowDays + "d).");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during market tick", e);
        }
    }

    private void updateTrendStreak(int itemId, BigDecimal newPrice, BigDecimal oldPrice) {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            trendDirectionCache.put(itemId, PriceTrend.Direction.STABLE);
            trendStreakCache.put(itemId, 0);
            return;
        }

        double threshold = configManager.getConfig().economy().trendStreakThresholdPercent() / 100.0;
        double pctChange = newPrice.subtract(oldPrice)
                .divide(oldPrice, MATH_CONTEXT)
                .doubleValue();

        PriceTrend.Direction newDir;
        if (pctChange > threshold) {
            newDir = PriceTrend.Direction.UP;
        } else if (pctChange < -threshold) {
            newDir = PriceTrend.Direction.DOWN;
        } else {
            newDir = PriceTrend.Direction.STABLE;
        }

        PriceTrend.Direction prevDir = trendDirectionCache.getOrDefault(itemId, PriceTrend.Direction.STABLE);

        if (newDir == prevDir && newDir != PriceTrend.Direction.STABLE) {
            trendStreakCache.merge(itemId, 1, Integer::sum);
        } else if (newDir != PriceTrend.Direction.STABLE && newDir != prevDir) {
            trendStreakCache.put(itemId, 1);
        } else {
            trendStreakCache.merge(itemId, -1, (a, b) -> Math.max(0, a + b));
        }

        if (newDir != PriceTrend.Direction.STABLE) {
            trendDirectionCache.put(itemId, newDir);
        }
    }

    private TradeMetrics calculateTradeMetrics(int itemId, Instant windowStart, long windowMs,
                                               AutoTuneConfig.EconomyConfig config) {
        List<Transaction> transactions = transactionRepository.findByItemSince(itemId, windowStart);

        Set<UUID> distinctPlayers = new HashSet<>();
        int buyCount = 0;
        int sellCount = 0;
        Instant now = Instant.now();

        // Phase 1: Accumulate raw weighted volumes per player
        Map<UUID, double[]> playerVolumes = new HashMap<>();
        for (Transaction tx : transactions) {
            distinctPlayers.add(tx.playerUuid());

            long ageMs = Duration.between(tx.timestamp(), now).toMillis();
            double weight = Math.max(0, 1.0 - (double) ageMs / windowMs);
            double weightedAmount = tx.amount() * weight;

            double[] vols = playerVolumes.computeIfAbsent(tx.playerUuid(), k -> new double[2]);
            if (tx.type() == Transaction.TransactionType.BUY) {
                vols[0] += weightedAmount;
                buyCount += tx.amount();
            } else {
                vols[1] += weightedAmount;
                sellCount += tx.amount();
            }
        }

        // Phase 2: Apply per-player rate limiting
        double rateLimitMultiplier = config.playerRateLimitMultiplier();
        double weightedBuys = 0;
        double weightedSells = 0;

        if (!playerVolumes.isEmpty()) {
            double totalPlayerVolume = 0;
            for (double[] vols : playerVolumes.values()) {
                totalPlayerVolume += vols[0] + vols[1];
            }
            double meanPerPlayer = totalPlayerVolume / playerVolumes.size();
            double cap = meanPerPlayer * rateLimitMultiplier;

            for (double[] vols : playerVolumes.values()) {
                double playerTotal = vols[0] + vols[1];
                double scale = (playerTotal > cap && playerTotal > 0.001)
                        ? cap / playerTotal
                        : 1.0;
                weightedBuys += vols[0] * scale;
                weightedSells += vols[1] * scale;
            }
        }

        return new TradeMetrics(weightedBuys, weightedSells, buyCount, sellCount, distinctPlayers.size());
    }

    private BigDecimal calculateNewPrice(
            ShopItem item,
            TradeMetrics metrics,
            int onlineCount,
            AutoTuneConfig.EconomyConfig config
    ) {
        BigDecimal currentPrice = item.price();

        double totalWeighted = metrics.weightedBuys() + metrics.weightedSells();
        if (totalWeighted < 0.001) {
            return currentPrice;
        }

        double tradeRatio = (metrics.weightedBuys() - metrics.weightedSells()) / totalWeighted;

        double playerScaling = calculatePlayerScaling(onlineCount, config);
        double scaledRatio = tradeRatio * playerScaling;

        double maxChangePct = (item.maxPriceChangeOverride() != null
                ? item.maxPriceChangeOverride()
                : config.maxPriceChangePercent()) / 100.0;

        double priceChangePercent = scaledRatio * maxChangePct;

        // Sell pressure: amplify downward movements (applied before dampening)
        if (priceChangePercent < 0) {
            priceChangePercent *= config.sellPressureMultiplier();
        }

        // Directional trend dampening: only dampen when continuing the streak direction
        int streak = trendStreakCache.getOrDefault(item.id(), 0);
        if (streak > 0 && config.trendDampening() > 0) {
            PriceTrend.Direction streakDir = trendDirectionCache.getOrDefault(item.id(), PriceTrend.Direction.STABLE);
            boolean continuingStreak = (priceChangePercent > 0 && streakDir == PriceTrend.Direction.UP)
                    || (priceChangePercent < 0 && streakDir == PriceTrend.Direction.DOWN);
            if (continuingStreak) {
                double rawDampening = 1.0 / (1.0 + streak * config.trendDampening());
                double dampening = Math.max(rawDampening, config.trendDampeningFloor());
                priceChangePercent *= dampening;
            }
        }

        BigDecimal priceChange = currentPrice.multiply(BigDecimal.valueOf(priceChangePercent), MATH_CONTEXT);
        return currentPrice.add(priceChange);
    }

    private double calculatePlayerScaling(int onlineCount, AutoTuneConfig.EconomyConfig config) {
        if (onlineCount == 0) {
            return 0;
        }

        int fullEffectPlayers = config.playerScaling().fullEffectPlayers();
        double coefficient = ATANH_099 / fullEffectPlayers;
        return Math.tanh(onlineCount * coefficient);
    }

    private SpreadResult calculateSpread(
            ShopItem item,
            TradeMetrics metrics,
            int onlineCount,
            double globalVolumeMultiplier,
            AutoTuneConfig.EconomyConfig config
    ) {
        AutoTuneConfig.SpreadConfig spreadConfig = config.spread();

        double baseSpread = item.baseSpreadOverride() != null
                ? item.baseSpreadOverride()
                : spreadConfig.baseSpread();

        double halfSpread = baseSpread / 2.0;

        double bpd = halfSpread;
        double spd = halfSpread;

        double totalWeighted = metrics.weightedBuys() + metrics.weightedSells();
        if (totalWeighted > 0.001) {
            double buyRatio = metrics.weightedBuys() / totalWeighted;
            double imbalance = (buyRatio - 0.5) * 2.0;

            bpd += Math.max(0, imbalance) * halfSpread * spreadConfig.volumeImpact();
            spd += Math.max(0, -imbalance) * halfSpread * spreadConfig.volumeImpact();
        }

        int fullEffectTraders = spreadConfig.liquidityFullEffectTraders();
        int clampedTraders = Math.min(metrics.distinctTraders(), fullEffectTraders);
        double effectiveCoeff = fullEffectTraders > 0
                ? (spreadConfig.liquidityCoeff() / fullEffectTraders) * clampedTraders
                : 0;
        double liquidityReduction = 1.0 / (1.0 + totalWeighted * effectiveCoeff);
        bpd *= liquidityReduction;
        spd *= liquidityReduction;

        double playerScaling = calculatePlayerScaling(onlineCount, config);
        double playerReduction = 1.0 - spreadConfig.playerImpact() * playerScaling;
        bpd *= playerReduction;
        spd *= playerReduction;

        bpd *= globalVolumeMultiplier;
        spd *= globalVolumeMultiplier;

        return new SpreadResult(
                BigDecimal.valueOf(bpd).setScale(5, RoundingMode.HALF_UP),
                BigDecimal.valueOf(spd).setScale(5, RoundingMode.HALF_UP)
        );
    }

    private double calculateGlobalVolumeMultiplier(Instant windowStart, Instant now) {
        long windowMs = Duration.between(windowStart, now).toMillis();
        long bucketMs = windowMs / VOLUME_BUCKETS;

        double[] bucketVolumes = new double[VOLUME_BUCKETS];
        for (int i = 0; i < VOLUME_BUCKETS; i++) {
            Instant bucketStart = windowStart.plusMillis(i * bucketMs);
            Instant bucketEnd = windowStart.plusMillis((i + 1) * bucketMs);
            bucketVolumes[i] = transactionRepository.getGlobalTransactionAmountBetween(bucketStart, bucketEnd);
        }

        double mean = 0;
        for (double v : bucketVolumes) {
            mean += v;
        }
        mean /= VOLUME_BUCKETS;

        double variance = 0;
        for (double v : bucketVolumes) {
            double diff = v - mean;
            variance += diff * diff;
        }
        variance /= VOLUME_BUCKETS;
        double stddev = Math.sqrt(variance);

        if (stddev < 0.001) {
            return 1.0;
        }

        double recentVolume = bucketVolumes[VOLUME_BUCKETS - 1];
        double z = (recentVolume - mean) / stddev;

        if (z >= -1.0 && z <= 1.0) {
            return 1.0;
        }

        if (z > 1.0) {
            double t = Math.min(z - 1.0, 1.0);
            return 1.0 - 0.2 * t;
        }

        double t = Math.min(-z - 1.0, 1.0);
        return 1.0 + 0.3 * t;
    }

    private int calculateAdaptiveWindow(Instant now, AutoTuneConfig.EconomyConfig config) {
        int baseWindowDays = config.tradeWindowDays();
        Instant baseStart = now.minus(Duration.ofDays(baseWindowDays));
        int txCount = transactionRepository.getGlobalTransactionCount(baseStart);

        double density = (double) txCount / baseWindowDays;
        double scaleFactor = (density > 0.001)
                ? TARGET_TX_DENSITY_PER_DAY / density
                : (double) config.maxWindowDays();

        int adaptedDays = (int) Math.round(baseWindowDays * scaleFactor);
        return Math.max(config.minWindowDays(), Math.min(adaptedDays, config.maxWindowDays()));
    }

    private void computeAndStoreRatios(List<ShopItem> items) {
        itemRepository.deleteAllRatios();

        for (int i = 0; i < items.size(); i++) {
            ShopItem itemA = items.get(i);
            BigDecimal priceA = priceCache.getOrDefault(itemA.id(), itemA.price());

            if (priceA.compareTo(BigDecimal.ZERO) <= 0) continue;

            for (int j = i + 1; j < items.size(); j++) {
                ShopItem itemB = items.get(j);
                BigDecimal priceB = priceCache.getOrDefault(itemB.id(), itemB.price());

                if (priceB.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal ratio = priceA.divide(priceB, MATH_CONTEXT);
                itemRepository.setRatio(itemA.id(), itemB.id(), ratio);
            }
        }
    }

    public BigDecimal getCurrentPrice(int itemId) {
        return priceCache.computeIfAbsent(itemId, id ->
                itemRepository.findById(id)
                        .map(ShopItem::price)
                        .orElse(BigDecimal.ZERO)
        );
    }

    public SpreadResult getSpread(int itemId) {
        return spreadCache.getOrDefault(itemId, SpreadResult.DEFAULT);
    }

    public BigDecimal getBuyPrice(ShopItem item) {
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        return price.multiply(BigDecimal.ONE.add(spread.bpd()), MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getBuyPrice(ShopItem item, int amount) {
        double slippageCoeff = configManager.getConfig().economy().slippageCoeff();
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        BigDecimal basePrice = price.multiply(BigDecimal.ONE.add(spread.bpd()), MATH_CONTEXT);
        BigDecimal slippageFactor = BigDecimal.ONE.add(BigDecimal.valueOf(slippageCoeff * Math.sqrt(amount)));
        return basePrice.multiply(slippageFactor, MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getSellPrice(ShopItem item) {
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        return price.multiply(BigDecimal.ONE.subtract(spread.spd()), MATH_CONTEXT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getSellPrice(ShopItem item, int amount) {
        double slippageCoeff = configManager.getConfig().economy().slippageCoeff();
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        BigDecimal basePrice = price.multiply(BigDecimal.ONE.subtract(spread.spd()), MATH_CONTEXT);
        BigDecimal slippageFactor = BigDecimal.ONE.add(BigDecimal.valueOf(slippageCoeff * Math.sqrt(amount)));
        return basePrice.divide(slippageFactor, MATH_CONTEXT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void recordBuy(int itemId, int amount) {
        tickBuyVolume.merge(itemId, amount, Integer::sum);
    }

    public void recordSell(int itemId, int amount) {
        tickSellVolume.merge(itemId, amount, Integer::sum);
    }

    public Map<Integer, BigDecimal> getPriceCache() {
        return Map.copyOf(priceCache);
    }

    public Map<Integer, SpreadResult> getSpreadCache() {
        return Map.copyOf(spreadCache);
    }

    public int getTrendStreak(int itemId) {
        return trendStreakCache.getOrDefault(itemId, 0);
    }

    public PriceTrend.Direction getTrendDirection(int itemId) {
        return trendDirectionCache.getOrDefault(itemId, PriceTrend.Direction.STABLE);
    }

    public double getGlobalVolumeMultiplier() {
        return lastGlobalVolumeMultiplier;
    }

    public PriceTrend getPriceTrend(int itemId) {
        List<PriceHistory> history = itemRepository.getPriceHistory(itemId, 10);
        if (history.size() < 2) {
            return new PriceTrend(PriceTrend.Direction.STABLE, BigDecimal.ZERO, "Stable");
        }

        BigDecimal newest = history.get(0).price();
        BigDecimal oldest = history.get(history.size() - 1).price();

        if (oldest.compareTo(BigDecimal.ZERO) == 0) {
            return new PriceTrend(PriceTrend.Direction.STABLE, BigDecimal.ZERO, "Stable");
        }

        BigDecimal percentChange = newest.subtract(oldest)
                .divide(oldest, MATH_CONTEXT)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        if (percentChange.compareTo(BigDecimal.valueOf(0.5)) > 0) {
            return new PriceTrend(PriceTrend.Direction.UP, percentChange, "+" + percentChange + "%");
        } else if (percentChange.compareTo(BigDecimal.valueOf(-0.5)) < 0) {
            return new PriceTrend(PriceTrend.Direction.DOWN, percentChange, percentChange + "%");
        }

        return new PriceTrend(PriceTrend.Direction.STABLE, percentChange, percentChange + "%");
    }

    public BigDecimal get24hChange(int itemId) {
        BigDecimal currentPrice = getCurrentPrice(itemId);
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        Instant twentyFourHoursAgo = Instant.now().minus(Duration.ofHours(24));
        return itemRepository.getClosestPriceBefore(itemId, twentyFourHoursAgo)
                .map(old -> {
                    if (old.price().compareTo(BigDecimal.ZERO) == 0) {
                        return BigDecimal.ZERO;
                    }
                    return currentPrice.subtract(old.price())
                            .divide(old.price(), MATH_CONTEXT)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                })
                .orElse(BigDecimal.ZERO);
    }

    public record PriceTrend(Direction direction, BigDecimal percentChange, String label) {
        public enum Direction {
            UP,
            DOWN,
            STABLE
        }
    }

    public record SpreadResult(BigDecimal bpd, BigDecimal spd) {
        private static final SpreadResult DEFAULT = new SpreadResult(
                BigDecimal.valueOf(0.15).setScale(5, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.15).setScale(5, RoundingMode.HALF_UP)
        );
    }

    private record TradeMetrics(
            double weightedBuys,
            double weightedSells,
            int buyCount,
            int sellCount,
            int distinctTraders
    ) {
    }
}
