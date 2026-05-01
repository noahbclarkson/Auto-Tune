package com.noahblclarkson.autotune.manager;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.util.ItemSerializer;
import com.noahblclarkson.autotune.model.Transaction;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Singleton
@SuppressWarnings("PMD")
public class PriceReporter {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int MAX_QUEUED = 5;
    private static final int MAX_RETRIES = 3;

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ConcurrentMap<Integer, PriceAccumulator> accumulators = new ConcurrentHashMap<>();
    /** Bounded retry queue for failed submissions. Each entry carries its own payload so the
     *  accumulators are not cleared until the retry succeeds. */
    private final ConcurrentLinkedQueue<QueuedSubmission> retryQueue = new ConcurrentLinkedQueue<>();

    private ShopManager shopManager;
    private ItemRepository itemRepository;

    @Inject
    public PriceReporter(AutoTune plugin, ConfigManager configManager,
                          ShopManager shopManager, ItemRepository itemRepository) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.itemRepository = itemRepository;
    }

    public boolean isEnabled() {
        return configManager.getConfig().priceReporter().enabled();
    }

    public long getIntervalMinutes() {
        return Math.max(1, configManager.getConfig().priceReporter().reportIntervalMinutes());
    }

    public void recordTransaction(@NotNull ShopItem item, @NotNull Transaction transaction) {
        if (!isEnabled()) {
            return;
        }

        accumulators.compute(item.id(), (id, prev) -> {
            PriceAccumulator next = prev != null ? prev : new PriceAccumulator(item.getDisplayNameOrMaterial());
            next.observe(transaction);
            return next;
        });
    }

    /** Called by the market tick scheduler (every getIntervalMinutes()). */
    public void submitSnapshot() {
        if (!isEnabled()) {
            return;
        }

        AutoTuneConfig.PriceReporterConfig cfg = configManager.getConfig().priceReporter();
        if (cfg.apiKey().isBlank() || cfg.serverId().isBlank()) {
            plugin.getLogger().warning("Price reporter is enabled, but api-key/server-id are missing.");
            return;
        }

        if (accumulators.isEmpty()) {
            return;
        }

        Map<Integer, PriceAccumulator> snapshot = buildSnapshot();
        if (snapshot == null) {
            return;
        }

        SubmitPayload payload = buildPayload(snapshot, cfg);
        if (payload == null) {
            return;
        }

        sendPayload(payload, snapshot, cfg);
    }

    /** Drains the retry queue, attempting each queued submission once.
     *  Called every 1 minute by the task scheduler. */
    public void drainRetryQueue() {
        if (!isEnabled()) {
            return;
        }

        AutoTuneConfig.PriceReporterConfig cfg = configManager.getConfig().priceReporter();
        if (cfg.apiKey().isBlank() || cfg.serverId().isBlank()) {
            return;
        }

        // Drain all entries — we'll re-queue on failure with incremented retry count
        List<QueuedSubmission> batch = new ArrayList<>();
        QueuedSubmission entry;
        while ((entry = retryQueue.poll()) != null) {
            batch.add(entry);
        }

        if (batch.isEmpty()) {
            return;
        }

        plugin.getLogger().fine("Retrying " + batch.size() + " queued price submission(s).");

        for (QueuedSubmission queued : batch) {
            if (queued.retryCount() >= MAX_RETRIES) {
                plugin.getLogger().warning("Price reporter giving up on submission after "
                        + MAX_RETRIES + " attempts (" + queued.payload().item_names().size() + " items).");
                continue; // drop permanently
            }

            retrySend(queued, cfg);
        }
    }

    /**
     * Sends a heartbeat to the API server to signal the server is still alive.
     * Called periodically (every getIntervalMinutes()) even when there is no
     * new price data to submit — keeps the server's last_seen fresh in the registry.
     *
     * Heartbeats are fire-and-forget; failures are logged but not retried.
     */
    public void sendHeartbeat() {
        if (!isEnabled()) {
            return;
        }
        AutoTuneConfig.PriceReporterConfig cfg = configManager.getConfig().priceReporter();
        if (cfg.apiKey().isBlank() || cfg.serverId().isBlank()) {
            return;
        }

        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        String baseUrl = cfg.apiUrl().replaceAll("/$", "");
        String endpoint = baseUrl + "/api/servers/" + cfg.serverId() + "/heartbeat";

        HeartbeatPayload payload = new HeartbeatPayload(onlinePlayers, plugin.getPluginMeta().getVersion());

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        plugin.getLogger().fine("Heartbeat sent to api-server (players: " + onlinePlayers + ").");
                    } else {
                        plugin.getLogger().fine("Heartbeat to api-server failed: HTTP " + response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().fine("Heartbeat to api-server failed: " + error.getMessage());
                    return null;
                });
    }

    // -------------------------------------------------------------------------

    private Map<Integer, PriceAccumulator> buildSnapshot() {
        if (accumulators.isEmpty()) {
            return null;
        }

        Map<Integer, PriceAccumulator> snapshot = new LinkedHashMap<>(accumulators);
        List<Integer> itemIds = new ArrayList<>(snapshot.keySet());
        List<String> itemNames = new ArrayList<>();
        List<BigDecimal> basePrices = new ArrayList<>();

        for (Integer itemId : itemIds) {
            PriceAccumulator stats = snapshot.get(itemId);
            if (stats == null) {
                continue;
            }
            BigDecimal representative = stats.representativePrice();
            if (representative.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            itemNames.add(stats.itemName);
            basePrices.add(representative);
        }

        if (itemNames.size() < 2) {
            return null;
        }

        return snapshot;
    }

    private SubmitPayload buildPayload(Map<Integer, PriceAccumulator> snapshot,
                                       AutoTuneConfig.PriceReporterConfig cfg) {
        List<String> itemNames = new ArrayList<>();
        List<BigDecimal> basePrices = new ArrayList<>();

        for (Map.Entry<Integer, PriceAccumulator> entry : snapshot.entrySet()) {
            PriceAccumulator stats = entry.getValue();
            BigDecimal representative = stats.representativePrice();
            if (representative.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            itemNames.add(stats.itemName);
            basePrices.add(representative);
        }

        if (itemNames.size() < 2) {
            return null;
        }

        List<List<Double>> ratioMatrix = new ArrayList<>();
        for (int i = 0; i < basePrices.size(); i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < basePrices.size(); j++) {
                if (i == j) {
                    row.add(1.0);
                } else {
                    BigDecimal ratio = basePrices.get(i).divide(basePrices.get(j), MC);
                    row.add(ratio.doubleValue());
                }
            }
            ratioMatrix.add(row);
        }

        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        return new SubmitPayload(itemNames, ratioMatrix, onlinePlayers);
    }

    private void sendPayload(SubmitPayload payload,
                             Map<Integer, PriceAccumulator> snapshot,
                             AutoTuneConfig.PriceReporterConfig cfg) {

        String baseUrl = cfg.apiUrl().replaceAll("/$", "");
        String endpoint = baseUrl + "/api/servers/" + cfg.serverId() + "/prices";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        accumulators.keySet().removeAll(snapshot.keySet());
                        plugin.getLogger().fine("Submitted " + payload.item_names().size()
                                + " item prices to api-server.");
                    } else {
                        plugin.getLogger().warning("Price reporter submit failed: HTTP "
                                + response.statusCode() + " - " + response.body());
                        enqueueForRetry(payload, 0);
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Price reporter submit failed: " + error.getMessage());
                    enqueueForRetry(payload, 0);
                    return null;
                });
    }

    /** Retry a previously-queued submission. Re-queues with incremented count on failure. */
    private void retrySend(QueuedSubmission queued, AutoTuneConfig.PriceReporterConfig cfg) {
        String baseUrl = cfg.apiUrl().replaceAll("/$", "");
        String endpoint = baseUrl + "/api/servers/" + cfg.serverId() + "/prices";
        SubmitPayload payload = queued.payload();
        int attempt = queued.retryCount() + 1;

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        plugin.getLogger().fine("Retry succeeded for " + payload.item_names().size()
                                + " items (attempt " + attempt + ").");
                    } else {
                        plugin.getLogger().warning("Price reporter retry failed: HTTP "
                                + response.statusCode() + " (attempt " + attempt + "/" + MAX_RETRIES + ")");
                        enqueueForRetry(payload, attempt);
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Price reporter retry failed: " + error.getMessage()
                            + " (attempt " + attempt + "/" + MAX_RETRIES + ")");
                    enqueueForRetry(payload, attempt);
                    return null;
                });
    }

    private void enqueueForRetry(SubmitPayload payload, int retryCount) {
        if (retryCount >= MAX_RETRIES) {
            plugin.getLogger().warning("Price reporter: max retries reached, dropping "
                    + payload.item_names().size() + " items.");
            return;
        }
        // Evict oldest if full
        while (retryQueue.size() >= MAX_QUEUED) {
            QueuedSubmission evicted = retryQueue.poll();
            if (evicted != null) {
                plugin.getLogger().fine("Retry queue full — dropping oldest ("
                        + evicted.payload().item_names().size() + " items).");
            }
        }
        retryQueue.add(new QueuedSubmission(payload, retryCount));
        plugin.getLogger().fine("Queued " + payload.item_names().size()
                + " items for retry (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ").");
    }

    // -------------------------------------------------------------------------

    /**
     * Seed initial prices from the shared true-price API.
     * Called on startup when economy.seedFromSharedPrices is enabled.
     *
     * Fetches true prices from GET /api/prices/true and updates local item prices
     * for any items that match by material name. This gives new servers a
     * sensible starting point derived from cross-server data instead of
     * arbitrary shops.yml defaults.
     */
    public void seedPricesFromApi() {
        if (!configManager.getConfig().economy().seedFromSharedPrices()) {
            return;
        }
        AutoTuneConfig.PriceReporterConfig cfg = configManager.getConfig().priceReporter();
        if (cfg.apiKey().isBlank() || cfg.serverId().isBlank()) {
            plugin.getLogger().warning("seed-from-shared-prices is enabled but price-reporter "
                    + "api-key/server-id are missing.");
            return;
        }

        String baseUrl = cfg.apiUrl().replaceAll("/$", "");
        String endpoint = baseUrl + "/api/prices/true";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        plugin.getLogger().warning("Failed to fetch shared prices: HTTP "
                                + response.statusCode());
                        return;
                    }
                    try {
                        TruePricesResponse apiResponse = gson.fromJson(
                                response.body(), TruePricesResponse.class);
                        int updated = 0;
                        for (TruePriceEntry entry : apiResponse.prices()) {
                            if (entry.price() <= 0) continue;
                            // Map API item name (e.g. "DIAMOND") to a ShopItem
                            String itemName = entry.item().toUpperCase(java.util.Locale.ROOT);
                            Material material = Material.matchMaterial(itemName);
                            if (material == null) {
                                plugin.getLogger().fine("Unknown material from API: " + itemName);
                                continue;
                            }
                            String hash = ItemSerializer.getMaterialHash(material);
                            var optItem = itemRepository.findByHash(hash);
                            if (optItem.isEmpty()) {
                                plugin.getLogger().fine("Item not in shop: " + itemName);
                                continue;
                            }
                            int itemId = optItem.get().id();
                            BigDecimal newPrice = BigDecimal.valueOf(entry.price());
                            itemRepository.updatePrice(itemId, newPrice);
                            updated++;
                            plugin.getLogger().fine("Seeded " + itemName + " = " + newPrice
                                    + " (conf=" + String.format("%.2f", entry.confidence()) + ")");
                        }
                        plugin.getLogger().info("Seeded " + updated + " item prices from shared true-price API.");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse shared prices response: " + e.getMessage());
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Failed to fetch shared prices: " + error.getMessage());
                    return null;
                });
    }

    // -------------------------------------------------------------------------

    private static final class PriceAccumulator {
        private final String itemName;
        private BigDecimal buyTotal = BigDecimal.ZERO;
        private BigDecimal buyAmount = BigDecimal.ZERO;
        private BigDecimal sellTotal = BigDecimal.ZERO;
        private BigDecimal sellAmount = BigDecimal.ZERO;

        private PriceAccumulator(String itemName) {
            this.itemName = itemName;
        }

        private void observe(Transaction tx) {
            BigDecimal amount = BigDecimal.valueOf(tx.amount());
            BigDecimal total = tx.totalPrice();
            if (tx.type() == Transaction.TransactionType.BUY) {
                buyTotal = buyTotal.add(total);
                buyAmount = buyAmount.add(amount);
            } else {
                sellTotal = sellTotal.add(total);
                sellAmount = sellAmount.add(amount);
            }
        }

        private BigDecimal representativePrice() {
            BigDecimal buyAvg = buyAmount.compareTo(BigDecimal.ZERO) > 0
                    ? buyTotal.divide(buyAmount, MC)
                    : BigDecimal.ZERO;
            BigDecimal sellAvg = sellAmount.compareTo(BigDecimal.ZERO) > 0
                    ? sellTotal.divide(sellAmount, MC)
                    : BigDecimal.ZERO;

            if (buyAvg.compareTo(BigDecimal.ZERO) > 0 && sellAvg.compareTo(BigDecimal.ZERO) > 0) {
                return buyAvg.add(sellAvg).divide(BigDecimal.valueOf(2), MC);
            }
            return buyAvg.compareTo(BigDecimal.ZERO) > 0 ? buyAvg : sellAvg;
        }
    }

    /** A failed submission waiting for retry. */
    private record QueuedSubmission(SubmitPayload payload, int retryCount) {
        QueuedSubmission withRetry() {
            return new QueuedSubmission(payload, retryCount + 1);
        }
    }

    private record SubmitPayload(
            List<String> item_names,
            List<List<Double>> ratio_matrix,
            int player_count
    ) {
    }

    /** Response from GET /api/prices/true */
    private record TruePricesResponse(
            List<TruePriceEntry> prices,
            String last_updated
    ) {}

    /** Single item entry in the true-prices response */
    private record TruePriceEntry(
            String item,
            double price,
            double confidence,
            int servers,
            boolean anchored
    ) {}

    /** Request body for POST /api/servers/{id}/heartbeat */
    private record HeartbeatPayload(int player_count, String plugin_version) {}
}
