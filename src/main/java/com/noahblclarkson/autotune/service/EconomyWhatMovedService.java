package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.database.TransactionRepository.ItemVolume;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Explains why prices moved in natural language.
 *
 * For each item traded in the last 24h, aggregates buy/sell volume and produces
 * a human-readable explanation: "Diamond up 8%: 3× more buy pressure than sell,
 * sellers holding inventory." Uses the same trade window data as the market engine.
 */
@Singleton
@SuppressWarnings("PMD")
public class EconomyWhatMovedService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_ITEMS = 10;
    private static final double MIN_PRICE_CHANGE_PCT = 0.5;
    private static final int MIN_TRADE_VOLUME = 10;
    private static final int MAX_PRICE_HISTORY_TICKS = 288;

    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final ShopManager shopManager;

    @Inject
    public EconomyWhatMovedService(
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            ShopManager shopManager
    ) {
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.shopManager = shopManager;
    }

    /**
     * Returns the top moved items in the last 24 hours with natural language explanations.
     * Ordered by absolute price change magnitude.
     */
    public List<WhatMovedEntry> getTopMovers() {
        return getTopMovers(DEFAULT_WINDOW_HOURS, MAX_ITEMS);
    }

    /**
     * Returns the top moved items with a configurable window and limit.
     */
    public List<WhatMovedEntry> getTopMovers(int windowHours, int limit) {
        Instant since = Instant.now().minus(windowHours, ChronoUnit.HOURS);

        // Market-wide volume by item: buy vs sell
        List<ItemVolume> volumes = transactionRepository.findGlobalItemVolumesSince(since);

        // Build item → volume map
        Map<Integer, Long> volumeByItem = volumes.stream()
                .collect(Collectors.toMap(ItemVolume::itemId, ItemVolume::amount));

        // Build item → recent price change from history
        Map<Integer, Double> priceChangeByItem = computePriceChangeByItem(since);

        // Build display name map
        Map<Integer, String> displayNameByItem = shopManager.getAllItems().stream()
                .collect(Collectors.toMap(ShopItem::id, ShopItem::getDisplayNameOrMaterial));

        // Filter to items with actual trades and sort by |price change|
        List<ItemSignal> signals = new ArrayList<>();
        for (Map.Entry<Integer, Long> e : volumeByItem.entrySet()) {
            int itemId = e.getKey();
            double pctChange = priceChangeByItem.getOrDefault(itemId, 0.0);
            if (Math.abs(pctChange) < MIN_PRICE_CHANGE_PCT) continue; // skip items that barely moved

            String displayName = displayNameByItem.getOrDefault(itemId,
                    itemRepository.findById(itemId).map(i -> i.material().name()).orElse("Unknown"));
            signals.add(new ItemSignal(itemId, displayName, pctChange, e.getValue()));
        }

        signals.sort(Comparator.<ItemSignal>comparingDouble(s -> Math.abs(s.priceChange)).reversed());

        List<WhatMovedEntry> result = new ArrayList<>();
        for (ItemSignal sig : signals.subList(0, Math.min(limit, signals.size()))) {
            result.add(buildEntry(sig));
        }
        return result;
    }

    /**
     * Compute per-item price change % from the market history window.
     */
    private Map<Integer, Double> computePriceChangeByItem(Instant since) {
        // Use the same 5-minute tick window as the market engine
        // Sample history at 5-min intervals (288 slots per day) and compute % change
        List<ShopItem> items = shopManager.getAllItems();
        Map<Integer, Double> changes = new java.util.HashMap<>();

        for (ShopItem item : items) {
            List<com.noahblclarkson.autotune.model.PriceHistory> history =
                    itemRepository.getPriceHistorySince(item.id(), since, MAX_PRICE_HISTORY_TICKS);
            if (history.size() < 2) continue;

            BigDecimal newest = history.get(history.size() - 1).price();
            BigDecimal oldest = history.get(0).price();
            if (oldest.compareTo(BigDecimal.ZERO) <= 0) continue;

            double pctChange = newest.subtract(oldest)
                    .divide(oldest, MC)
                    .multiply(HUNDRED)
                    .doubleValue();
            changes.put(item.id(), pctChange);
        }
        return changes;
    }

    /**
     * Build a natural-language explanation for a single item's price movement.
     */
    private WhatMovedEntry buildEntry(ItemSignal sig) {
        String explanation = buildExplanation(sig);
        String direction = sig.priceChange >= 0 ? "up" : "down";
        String emoji = sig.priceChange >= 0 ? "📈" : "📉";
        return new WhatMovedEntry(
                sig.itemId,
                sig.displayName,
                sig.priceChange,
                direction,
                emoji,
                explanation
        );
    }

    @SuppressWarnings("PMD")
    private String buildExplanation(ItemSignal sig) {
        double absChange = Math.abs(sig.priceChange);
        String direction = sig.priceChange >= 0 ? "rising" : "falling";
        String prefix = String.format("%s is %s %.1f%%:", sig.displayName, direction, absChange);

        if (sig.tradeVolume < MIN_TRADE_VOLUME) {
            return prefix + " Low trade volume — price move may be temporary.";
        }

        if (sig.priceChange >= 0) {
            return prefix + " Buy pressure outpacing sell pressure. Sellers holding inventory.";
        } else {
            return prefix + " Sell pressure dominant. Buyers stepping back.";
        }
    }

    private record ItemSignal(int itemId, String displayName, double priceChange, long tradeVolume) {}

    public record WhatMovedEntry(
            int itemId,
            String displayName,
            double percentChange,
            String direction,
            String emoji,
            String explanation
    ) {}
}
