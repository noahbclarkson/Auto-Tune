package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.PriceOverride;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
@SuppressWarnings("PMD")
public class MarketEngine {

    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);
    private static final int VOLUME_BUCKETS = 10;
    private static final double ATANH_099 = 2.6466524123622457;
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("0.01");
    private static final double TARGET_TX_DENSITY_PER_DAY = 100.0;

    private final PluginAdapter adapter;
    private final ConfigManager configManager;
    private final ItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final PriceOverrideRepository priceOverrideRepository;
    private final MarketEventService marketEventService;

    private final Map<Integer, BigDecimal> priceCache = new ConcurrentHashMap<>();
    private final Map<Integer, SpreadResult> spreadCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tickBuyVolume = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tickSellVolume = new ConcurrentHashMap<>();
    private final Map<Integer, PriceTrend.Direction> trendDirectionCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> trendStreakCache = new ConcurrentHashMap<>();
    private final Map<Integer, PriceOverride> overrideCache = new ConcurrentHashMap<>();
    private volatile double lastGlobalVolumeMultiplier = 1.0;

    // Whale anti-dump spread shock state
    private volatile double spreadShock = 1.0;
    private volatile int shockRemainingTicks = 0;
    private final Map<Integer, Integer> itemSellCooldowns = new ConcurrentHashMap<>();

    @Inject
    public MarketEngine(
            PluginAdapter adapter,
            ConfigManager configManager,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository,
            PriceOverrideRepository priceOverrideRepository,
            MarketEventService marketEventService
    ) {
        this.adapter = adapter;
        this.configManager = configManager;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.priceOverrideRepository = priceOverrideRepository;
        this.marketEventService = marketEventService;
        loadOverrideCache();
    }

    /**
     * Load active price overrides from the database into memory.
     * Called on startup and whenever overrides are modified.
     */
    public final void loadOverrideCache() {
        overrideCache.clear();
        overrideCache.putAll(priceOverrideRepository.getActiveOverrides());
        adapter.getLogger().info("Loaded " + overrideCache.size() + " active price overrides.");
    }

    public void reload() {
        priceCache.clear();
        spreadCache.clear();
        tickBuyVolume.clear();
        tickSellVolume.clear();
        trendDirectionCache.clear();
        trendStreakCache.clear();
        loadOverrideCache();
    }

    public void tick() {
        AutoTuneConfig.EconomyConfig economyConfig = configManager.getConfig().economy();
        int onlineCount = adapter.getOnlineCount();
        boolean frozen = configManager.isMarketFrozen();

        // Process market event lifecycle (activate/deactivate scheduled events)
        marketEventService.onMarketTick();

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

            // Whale anti-dump spread shock trigger: check if any item's tick sell volume
            // exceeds the configured threshold and trigger spread widening if so.
            AutoTuneConfig.WhaleAntiDumpConfig antiDump = configManager.getConfig().whaleAntiDump();
            if (antiDump.enabled() && antiDump.spreadShockTriggerBps() > 0.0) {
                double triggerBps = antiDump.spreadShockTriggerBps();
                for (ShopItem item : items) {
                    int tickSell = tickSellVolume.getOrDefault(item.id(), 0);
                    if (tickSell > 0) {
                        double sellValue = tickSell * item.price().doubleValue();
                        double threshold = triggerBps * item.price().doubleValue() * 1000.0;
                        if (sellValue > threshold && shockRemainingTicks == 0) {
                            spreadShock = antiDump.spreadShockMultiplier();
                            shockRemainingTicks = antiDump.spreadShockDurationTicks();
                            break; // one shock per tick
                        }
                    }
                }
            }

            // Pass 1: Calculate new prices and spreads (don't persist yet)
            Map<Integer, BigDecimal> newPrices = new HashMap<>();
            Map<Integer, SpreadResult> newSpreads = new HashMap<>();

            for (ShopItem item : items) {
                TradeMetrics metrics = itemMetrics.get(item.id());
                SpreadResult spread = calculateSpread(item, metrics, onlineCount, globalVolumeMultiplier, economyConfig);
                newSpreads.put(item.id(), spread);

                // When market is globally frozen OR this item is individually frozen,
                // keep current price (no engine calculation — spreads still update)
                if (frozen || item.priceFrozen()) {
                    newPrices.put(item.id(), item.price());
                    continue;
                }

                // When item has a price override, use it directly (no engine calculation)
                PriceOverride over = overrideCache.get(item.id());
                if (over != null && !over.isExpired()) {
                    newPrices.put(item.id(), over.price().setScale(2, RoundingMode.HALF_UP));
                    continue;
                }

                BigDecimal newPrice = calculateNewPrice(item, metrics, onlineCount, economyConfig);
                newPrices.put(item.id(), newPrice);
            }

            // Pass 2: Sectoral correlation (only when not frozen)
            double sectorCorrelation = economyConfig.sectorCorrelation();
            if (!frozen && sectorCorrelation > 0.0001) {
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

            // Pass 3: Apply price floor/ceiling, persist, and track trend streaks
            for (ShopItem item : items) {
                BigDecimal finalPrice = newPrices.get(item.id());

                // Apply hard floor ($0.01 minimum — prevents zero/negative prices, not an economic cap)
                finalPrice = finalPrice.max(PRICE_FLOOR);
                finalPrice = finalPrice.setScale(2, RoundingMode.HALF_UP);

                SpreadResult spread = newSpreads.get(item.id());

                priceCache.put(item.id(), finalPrice);
                spreadCache.put(item.id(), spread);

                // When frozen (global or per-item), don't overwrite the DB price — just update in-memory cache
                if (!frozen && !item.priceFrozen()) {
                    itemRepository.updatePrice(item.id(), finalPrice);
                }

                int buyVol = tickBuyVolume.getOrDefault(item.id(), 0);
                int sellVol = tickSellVolume.getOrDefault(item.id(), 0);
                itemRepository.recordPriceHistory(
                        item.id(), finalPrice, buyVol, sellVol, spread.bpd(), spread.spd());

                // When frozen (global or per-item), skip trend streak updates (price didn't change)
                if (!frozen && !item.priceFrozen()) {
                    updateTrendStreak(item.id(), finalPrice, item.price());
                }
            }

            tickBuyVolume.clear();
            tickSellVolume.clear();

            // Decay spread shock and clear cooldowns
            if (shockRemainingTicks > 0) {
                shockRemainingTicks--;
                spreadShock = 1.0 + (spreadShock - 1.0) * 0.95; // 5% decay per tick
                if (shockRemainingTicks == 0) {
                    spreadShock = 1.0;
                }
            }
            // Cooldowns decay by 1 tick each
            itemSellCooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

            if (!frozen) {
                computeAndStoreRatios(items);
            }

            if (configManager.getConfig().debug().logPrices()) {
                String status = frozen ? "FROZEN" : "active";
                adapter.getLogger().info("Market tick completed. Updated " + items.size() + " prices (" + status + ", window=" + windowDays + "d).");
            }
        } catch (Exception e) {
            adapter.getLogger().log(Level.SEVERE, "Error during market tick", e);
        }
    }

    void updateTrendStreak(int itemId, BigDecimal newPrice, BigDecimal oldPrice) {
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
            // STABLE resets the streak — flat prices break streaks, which is correct behavior
            trendStreakCache.put(itemId, 0);
        }

        if (newDir != PriceTrend.Direction.STABLE) {
            trendDirectionCache.put(itemId, newDir);
        } else {
            // Clear direction when stable so next move is treated as fresh, not a reversal
            trendDirectionCache.remove(itemId);
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

    BigDecimal calculateNewPrice(
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
                : config.maxPriceChangePercent() * item.effectiveMaxPriceChangeMultiplier()) / 100.0;

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

        // Apply market event multiplier (amplifies or dampens price changes for matching items)
        priceChangePercent = marketEventService.applyEventMultiplier(item.material().name(), priceChangePercent);

        BigDecimal priceChange = currentPrice.multiply(BigDecimal.valueOf(priceChangePercent), MATH_CONTEXT);
        return currentPrice.add(priceChange);
    }

    double calculatePlayerScaling(int onlineCount, AutoTuneConfig.EconomyConfig config) {
        if (onlineCount == 0) {
            return 0;
        }

        int fullEffectPlayers = config.playerScaling().fullEffectPlayers();
        double coefficient = ATANH_099 / fullEffectPlayers;
        return Math.tanh(onlineCount * coefficient);
    }

    SpreadResult calculateSpread(
            ShopItem item,
            TradeMetrics metrics,
            int onlineCount,
            double globalVolumeMultiplier,
            AutoTuneConfig.EconomyConfig config
    ) {
        AutoTuneConfig.SpreadConfig spreadConfig = config.spread();

        double baseSpread = item.baseSpreadOverride() != null
                ? item.baseSpreadOverride()
                : spreadConfig.baseSpread() * item.effectiveSpreadMultiplier();

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

        // Apply whale anti-dump spread shock (decays in tick())
        if (spreadShock > 1.0) {
            bpd *= spreadShock;
            spd *= spreadShock;
        }

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

        BigDecimal finalPrice = price.multiply(BigDecimal.ONE.add(spread.bpd()), MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);

        return applyFloorCeiling(finalPrice, item, true);
    }

    public BigDecimal getBuyPrice(ShopItem item, int amount) {
        double slippageCoeff = configManager.getConfig().economy().slippageCoeff();
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        BigDecimal basePrice = price.multiply(BigDecimal.ONE.add(spread.bpd()), MATH_CONTEXT);
        BigDecimal slippageFactor = BigDecimal.ONE.add(BigDecimal.valueOf(slippageCoeff * Math.sqrt(amount)));
        BigDecimal finalPrice = basePrice.multiply(slippageFactor, MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);

        return applyFloorCeiling(finalPrice, item, true);
    }

    public BigDecimal getSellPrice(ShopItem item) {
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        BigDecimal finalPrice = price.multiply(BigDecimal.ONE.subtract(spread.spd()), MATH_CONTEXT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return applyFloorCeiling(finalPrice, item, false);
    }

    public BigDecimal getSellPrice(ShopItem item, int amount) {
        double slippageCoeff = configManager.getConfig().economy().slippageCoeff();
        BigDecimal price = priceCache.getOrDefault(item.id(), item.price());
        SpreadResult spread = spreadCache.getOrDefault(item.id(), SpreadResult.DEFAULT);

        BigDecimal basePrice = price.multiply(BigDecimal.ONE.subtract(spread.spd()), MATH_CONTEXT);
        BigDecimal slippageFactor = BigDecimal.ONE.add(BigDecimal.valueOf(slippageCoeff * Math.sqrt(amount)));
        BigDecimal finalPrice = basePrice.divide(slippageFactor, MATH_CONTEXT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return applyFloorCeiling(finalPrice, item, false);
    }

    /**
     * Apply per-item price floor and ceiling bounds to a computed price.
     * Floor: minimum price (prevents prices going too low — market support).
     * Ceiling: maximum price (prevents prices going too high — player affordability cap).
     * Only applied if the ShopItem has a corresponding override set.
     */
    private BigDecimal applyFloorCeiling(BigDecimal computedPrice, ShopItem item, boolean isBuy) {
        if (item.priceFloorOverride() != null) {
            computedPrice = computedPrice.max(item.priceFloorOverride());
        }
        if (item.priceCeilingOverride() != null) {
            computedPrice = computedPrice.min(item.priceCeilingOverride());
        }
        return computedPrice;
    }

    public void recordBuy(int itemId, int amount) {
        tickBuyVolume.merge(itemId, amount, Integer::sum);
    }

    public void recordSell(int itemId, int amount) {
        tickSellVolume.merge(itemId, amount, Integer::sum);
    }

    /**
     * Returns the number of units of this item sold in the current tick.
     */
    public int getTickSellVolume(int itemId) {
        return tickSellVolume.getOrDefault(itemId, 0);
    }

    /**
     * Returns the remaining cooldown ticks for this item's high-value sell cooldown.
     * Returns 0 if no cooldown is active.
     */
    public int getSellCooldownRemaining(int itemId) {
        return itemSellCooldowns.getOrDefault(itemId, 0);
    }

    /**
     * Sets a sell cooldown for an item (used after Epic/Legendary item sells).
     */
    public void setSellCooldown(int itemId, int ticks) {
        itemSellCooldowns.put(itemId, ticks);
    }

    /**
     * Returns how many ticks the current spread shock has remaining.
     * Used in sell-cap error messages so players know when they can sell again.
     */
    public int getShockRemainingTicks() {
        return shockRemainingTicks;
    }

    /**
     * Returns the current spread shock multiplier. 1.0 means no shock active.
     */
    public double getSpreadShockMultiplier() {
        return spreadShock;
    }

    public Map<Integer, BigDecimal> getPriceCache() {
        return Map.copyOf(priceCache);
    }

    /**
     * Directly reset the live price for one item to the given value and clear
     * its trend/streak state. Called by ShopManager.resetPriceToBase() so the
     * MarketEngine immediately reflects the reset without waiting for the next tick.
     */
    public void resetPriceCache(int itemId, BigDecimal newPrice) {
        priceCache.put(itemId, newPrice);
        trendDirectionCache.remove(itemId);
        trendStreakCache.remove(itemId);
        spreadCache.remove(itemId); // will recompute on next tick
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

    /**
     * Returns a snapshot of currently active (non-expired) price overrides.
     */
    public Map<Integer, PriceOverride> getActiveOverrides() {
        return Map.copyOf(overrideCache);
    }

    /**
     * Returns the active override for a specific item, if any.
     */
    public Optional<PriceOverride> getOverride(int itemId) {
        return Optional.ofNullable(overrideCache.get(itemId));
    }

    /**
     * Returns true if the market is currently frozen.
     */
    public boolean isFrozen() {
        return configManager.isMarketFrozen();
    }

    /**
     * Freeze or unfreeze the market.
     */
    public void setFrozen(boolean frozen) {
        configManager.setMarketFrozen(frozen);
    }

    /**
     * Refresh the override cache after a DB change.
     */
    public void refreshOverrideCache() {
        loadOverrideCache();
    }

    public PriceTrend getPriceTrend(int itemId) {
        List<PriceHistory> history = itemRepository.getPriceHistory(itemId, 10);
        if (history.size() < 2) {
            return new PriceTrend(PriceTrend.Direction.STABLE, BigDecimal.ZERO, "Stable", BigDecimal.ZERO);
        }

        BigDecimal newest = history.get(0).price();
        BigDecimal oldest = history.get(history.size() - 1).price();
        Instant newestTs = history.get(0).timestamp();
        Instant oldestTs = history.get(history.size() - 1).timestamp();

        if (oldest.compareTo(BigDecimal.ZERO) == 0) {
            return new PriceTrend(PriceTrend.Direction.STABLE, BigDecimal.ZERO, "Stable", BigDecimal.ZERO);
        }

        BigDecimal percentChange = newest.subtract(oldest)
                .divide(oldest, MATH_CONTEXT)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        // Project where the price will be in 24 hours using linear extrapolation
        BigDecimal projected24h = computeProjectedPrice(newest, newestTs, oldestTs, oldest);

        PriceTrend.Direction direction;
        String label;
        if (percentChange.compareTo(BigDecimal.valueOf(0.5)) > 0) {
            direction = PriceTrend.Direction.UP;
            label = "+" + percentChange + "%";
        } else if (percentChange.compareTo(BigDecimal.valueOf(-0.5)) < 0) {
            direction = PriceTrend.Direction.DOWN;
            label = percentChange + "%";
        } else {
            direction = PriceTrend.Direction.STABLE;
            label = percentChange + "%";
        }

        return new PriceTrend(direction, percentChange, label, projected24h);
    }

    /**
     * Linear extrapolation: given two price points (newest at t=newer, oldest at t=older),
     * estimate the price 24 hours after the newest point.
     */
    private BigDecimal computeProjectedPrice(BigDecimal newestPrice, Instant newestTs,
                                            Instant oldestTs, BigDecimal oldestPrice) {
        if (newestPrice.compareTo(BigDecimal.ZERO) <= 0 || oldestPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        long elapsedSeconds = newestTs.getEpochSecond() - oldestTs.getEpochSecond();
        if (elapsedSeconds <= 0) {
            return newestPrice;
        }
        BigDecimal priceDelta = newestPrice.subtract(oldestPrice);
        // velocity: fractional change per second
        BigDecimal velocity = priceDelta.divide(oldestPrice, MATH_CONTEXT)
                .divide(BigDecimal.valueOf(elapsedSeconds), MATH_CONTEXT);
        BigDecimal projectedFractional = BigDecimal.ONE
                .add(velocity.multiply(BigDecimal.valueOf(86400))); // 24h in seconds
        BigDecimal projected = newestPrice.multiply(projectedFractional)
                .setScale(2, RoundingMode.HALF_UP);
        // Sanity clamp: don't project more than 5x current price
        BigDecimal maxProjected = newestPrice.multiply(BigDecimal.valueOf(5));
        if (projected.compareTo(maxProjected) > 0) {
            return maxProjected;
        }
        BigDecimal minProjected = newestPrice.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        if (projected.compareTo(minProjected) < 0) {
            return minProjected;
        }
        return projected;
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

    /**
     * Price trend for an item.
     * @param direction       UP / DOWN / STABLE based on recent price velocity
     * @param percentChange   % change over the lookback window (10 ticks)
     * @param label           Human-readable label for display
     * @param projected24h    Estimated price in 24 hours (linear extrapolation, may be 0 if insufficient data)
     */
    public record PriceTrend(Direction direction, BigDecimal percentChange, String label, BigDecimal projected24h) {
        public enum Direction {
            UP,
            DOWN,
            STABLE
        }
    }

    public record SpreadResult(BigDecimal bpd, BigDecimal spd) {
        static final SpreadResult DEFAULT = new SpreadResult(
                BigDecimal.valueOf(0.15).setScale(5, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.15).setScale(5, RoundingMode.HALF_UP)
        );
    }

    record TradeMetrics(
            double weightedBuys,
            double weightedSells,
            int buyCount,
            int sellCount,
            int distinctTraders
    ) {
    }
}
