package com.noahblclarkson.autotune.manager;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
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
import java.util.concurrent.ConcurrentMap;

@Singleton
public class PriceReporter {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ConcurrentMap<Integer, PriceAccumulator> accumulators = new ConcurrentHashMap<>();

    @Inject
    public PriceReporter(AutoTune plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
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
            return;
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
        SubmitPayload payload = new SubmitPayload(itemNames, ratioMatrix, onlinePlayers);

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
                        plugin.getLogger().fine("Submitted " + itemNames.size() + " item prices to api-server.");
                    } else {
                        plugin.getLogger().warning("Price reporter submit failed: HTTP " + response.statusCode() + " - " + response.body());
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Price reporter submit failed: " + error.getMessage());
                    return null;
                });
    }

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

    private record SubmitPayload(
            List<String> item_names,
            List<List<Double>> ratio_matrix,
            int player_count
    ) {
    }
}
