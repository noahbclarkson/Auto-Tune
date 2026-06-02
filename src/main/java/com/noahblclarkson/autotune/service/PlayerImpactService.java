package com.noahblclarkson.autotune.service;

import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.database.TransactionRepository.ItemVolume;
import com.noahblclarkson.autotune.database.TransactionRepository.PlayerImpact;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes a player's market impact score: how much of the market's price movement
 * the player drove through their trading activity.
 *
 * <p>Core formula per item:
 * <ul>
 *   <li>playerSharePct  = playerVolume / totalMarketVolume × 100</li>
 *   <li>priceChangePct   = (currentPrice − weekAgoPrice) / weekAgoPrice × 100</li>
 *   <li>playerImpactPct  = playerSharePct × priceChangePct / 100</li>
 * </ul>
 *
 * <p>Overall weekly/monthly impact = sum of playerImpactPct across all items.
 * Weekly rank = player's position when all players are sorted by total impact.
 */
public class PlayerImpactService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerImpactService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int TOP_ITEMS_LIMIT = 5;
    private static final int RANK_LIMIT = 100;

    private final PlayerRepository playerRepository;
    private final ItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final MarketEngine marketEngine;

    public PlayerImpactService(
            PlayerRepository playerRepository,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository,
            MarketEngine marketEngine) {
        this.playerRepository = playerRepository;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.marketEngine = marketEngine;
    }

    /**
     * Returns the market impact data for the named player, or empty if the player
     * has no transactions in the weekly window.
     */
    public Optional<PlayerImpactDto> compute(String playerName) {
        Optional<UUID> uuidOpt = playerRepository.findByName(playerName).map(PlayerData::uuid);
        if (uuidOpt.isEmpty()) {
            return Optional.empty();
        }
        UUID uuid = uuidOpt.get();
        return computeForUuid(uuid, playerName);
    }

    private Optional<PlayerImpactDto> computeForUuid(UUID uuid, String playerName) {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);

        // Per-item volumes
        List<ItemVolume> playerWeeklyVolumes = transactionRepository.findPlayerItemVolumesSince(uuid, weekAgo);
        List<ItemVolume> playerMonthlyVolumes = transactionRepository.findPlayerItemVolumesSince(uuid, monthAgo);
        List<ItemVolume> globalWeeklyVolumes = transactionRepository.findGlobalItemVolumesSince(weekAgo);
        List<ItemVolume> globalMonthlyVolumes = transactionRepository.findGlobalItemVolumesSince(monthAgo);

        // No activity in the weekly window at all
        if (playerWeeklyVolumes.isEmpty() && playerMonthlyVolumes.isEmpty()) {
            return Optional.empty();
        }

        // Build lookup maps
        Map<Integer, ItemVolume> weeklyByItem = toMap(playerWeeklyVolumes);
        Map<Integer, ItemVolume> monthlyByItem = toMap(playerMonthlyVolumes);
        Map<Integer, ItemVolume> globalWeeklyMap = toMap(globalWeeklyVolumes);
        Map<Integer, ItemVolume> globalMonthlyMap = toMap(monthlyByItem.isEmpty() ? globalWeeklyVolumes : globalMonthlyVolumes);

        // Current prices for all traded items
        Map<Integer, BigDecimal> currentPrices = buildCurrentPriceMap(
                playerWeeklyVolumes, playerMonthlyVolumes);

        // Weekly top items
        List<MarketImpactItemDto> weeklyTopItems = buildTopItems(
                playerWeeklyVolumes, globalWeeklyMap, currentPrices, weekAgo, 1);

        // Monthly top items
        List<MarketImpactItemDto> monthlyTopItems = buildTopItems(
                playerMonthlyVolumes,
                monthlyByItem.isEmpty() ? globalWeeklyMap : globalMonthlyMap,
                currentPrices, monthAgo, 1);

        // Weekly / monthly overall impact
        double weeklyImpact = computeOverallImpact(playerWeeklyVolumes, globalWeeklyMap, currentPrices, weekAgo);
        double monthlyImpact = computeOverallImpact(
                playerMonthlyVolumes,
                monthlyByItem.isEmpty() ? globalWeeklyMap : globalMonthlyMap,
                currentPrices, monthAgo);

        // Weekly rank
        int weeklyRank = computeWeeklyRank(uuid, weekAgo);

        return Optional.of(new PlayerImpactDto(
                playerName,
                weeklyImpact,
                monthlyImpact,
                weeklyRank,
                weeklyTopItems.isEmpty() ? monthlyTopItems : weeklyTopItems
        ));
    }

    private Map<Integer, ItemVolume> toMap(List<ItemVolume> volumes) {
        return volumes.stream()
                .collect(Collectors.toMap(ItemVolume::itemId, v -> v));
    }

    private Map<Integer, BigDecimal> buildCurrentPriceMap(
            List<ItemVolume> weeklyVols,
            List<ItemVolume> monthlyVols) {
        // Combine keys from both lists
        return java.util.stream.Stream.concat(
                        weeklyVols.stream(), monthlyVols.stream())
                .mapToInt(ItemVolume::itemId)
                .distinct()
                .boxed()
                .collect(Collectors.toMap(
                        itemId -> itemId,
                        itemId -> marketEngine.getCurrentPrice(itemId)));
    }

    private List<MarketImpactItemDto> buildTopItems(
            List<ItemVolume> playerVols,
            Map<Integer, ItemVolume> globalMap,
            Map<Integer, BigDecimal> currentPrices,
            Instant since,
            int historyLimit) {

        // Get item display names
        Map<Integer, ShopItem> itemCache = itemRepository.findAll().stream()
                .collect(Collectors.toMap(ShopItem::id, item -> item));

        return playerVols.stream()
                .filter(v -> globalMap.containsKey(v.itemId()))
                .map(v -> {
                    BigDecimal playerAmt = BigDecimal.valueOf(v.amount());
                    BigDecimal totalAmt = BigDecimal.valueOf(globalMap.get(v.itemId()).amount());

                    BigDecimal currentPrice = currentPrices.getOrDefault(v.itemId(), BigDecimal.ZERO);

                    // Price change this period
                    List<PriceHistory> history = itemRepository.getPriceHistorySince(
                            v.itemId(), since, historyLimit);
                    double priceChangePct = 0.0;
                    if (!history.isEmpty() && history.get(0).price().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal oldPrice = history.get(0).price();
                        priceChangePct = currentPrice.subtract(oldPrice)
                                .divide(oldPrice, 6, RoundingMode.HALF_UP)
                                .multiply(HUNDRED)
                                .doubleValue();
                    }

                    // Share and impact
                    double playerSharePct = totalAmt.compareTo(BigDecimal.ZERO) > 0
                            ? playerAmt.divide(totalAmt, 6, RoundingMode.HALF_UP)
                                .multiply(HUNDRED).doubleValue()
                            : 0.0;
                    double playerImpactPct = playerSharePct * Math.abs(priceChangePct) / 100.0;

                    ShopItem item = itemCache.get(v.itemId());
                    String itemName = item != null ? item.getDisplayNameOrMaterial() : "Item #" + v.itemId();
                    String material = item != null ? item.material().name() : "UNKNOWN";

                    return new MarketImpactItemDto(
                            itemName,
                            material,
                            v.amount(),
                            globalMap.get(v.itemId()).amount(),
                            playerSharePct,
                            priceChangePct,
                            playerImpactPct
                    );
                })
                .sorted(Comparator.comparingDouble(MarketImpactItemDto::playerImpactPct).reversed())
                .limit(TOP_ITEMS_LIMIT)
                .collect(Collectors.toList());
    }

    private double computeOverallImpact(
            List<ItemVolume> playerVols,
            Map<Integer, ItemVolume> globalMap,
            Map<Integer, BigDecimal> currentPrices,
            Instant since) {

        if (playerVols.isEmpty()) return 0.0;

        double totalImpact = 0.0;
        for (ItemVolume v : playerVols) {
            ItemVolume global = globalMap.get(v.itemId());
            if (global == null || global.amount() == 0) continue;

            BigDecimal currentPrice = currentPrices.getOrDefault(v.itemId(), BigDecimal.ZERO);
            List<PriceHistory> history = itemRepository.getPriceHistorySince(
                    v.itemId(), since, 1);
            if (history.isEmpty() || history.get(0).price().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal oldPrice = history.get(0).price();
            double priceChangePct = currentPrice.subtract(oldPrice)
                    .divide(oldPrice, 6, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .doubleValue();

            double playerShare = BigDecimal.valueOf(v.amount())
                    .divide(BigDecimal.valueOf(global.amount()), 6, RoundingMode.HALF_UP)
                    .doubleValue();

            totalImpact += playerShare * Math.abs(priceChangePct) / 100.0;
        }
        return totalImpact;
    }

    private int computeWeeklyRank(UUID uuid, Instant weekAgo) {
        List<PlayerImpact> topPlayers = transactionRepository.findTopPlayersByImpact(weekAgo, RANK_LIMIT);
        for (int i = 0; i < topPlayers.size(); i++) {
            try {
                if (UUID.fromString(topPlayers.get(i).playerUuid()).equals(uuid)) {
                    return i + 1;
                }
            } catch (IllegalArgumentException ignored) {
                // malformed UUID in DB, skip
            }
        }
        return 0;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record PlayerImpactDto(
            String playerName,
            double weeklyImpactPct,
            double monthlyImpactPct,
            int weeklyRank,
            List<MarketImpactItemDto> topItems
    ) {}

    public record MarketImpactItemDto(
            String itemName,
            String material,
            long playerVolume,
            long totalVolume,
            double playerSharePct,
            double priceChangePct,
            double playerImpactPct
    ) {}
}
