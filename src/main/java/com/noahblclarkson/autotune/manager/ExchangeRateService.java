package com.noahblclarkson.autotune.manager;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.model.ExchangeRate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fetches exchange rates from the cross-server API and exposes them locally.
 *
 * Exchange rates tell server admins how their economy compares to the global
 * average — a server with rate &gt; 1.0 is more expensive than average, &lt; 1.0 is cheaper.
 *
 * Fetching is enabled only when {@code priceReporter.enabled} is also true, since
 * the exchange rate feature requires the server to be registered with the API.
 *
 * The server's own entry is filtered out of the returned list (you can't compare
 * against yourself).
 */
@Singleton
public class ExchangeRateService {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** In-memory cache of all exchange rates keyed by server ID. */
    private final ConcurrentMap<String, ExchangeRate> cache = new ConcurrentHashMap<>();

    /** Timestamp of the last successful fetch. */
    private volatile Instant lastFetchedAtInstant;

    /** Whether the last fetch encountered an error. */
    private volatile boolean fetchFailed;

    @Inject
    public ExchangeRateService(AutoTune plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Whether exchange rate fetching is enabled.
     * Requires price reporter to be enabled and API key/server ID to be configured.
     */
    public boolean isEnabled() {
        AutoTuneConfig.PriceReporterConfig pr = configManager.getConfig().priceReporter();
        return pr.enabled()
                && !pr.apiKey().isBlank()
                && !pr.serverId().isBlank();
    }

    /** Returns all cached exchange rates, excluding the local server's own entry. */
    @NotNull
    public List<ExchangeRate> getExchangeRates() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        String ownId = configManager.getConfig().priceReporter().serverId();
        return cache.values().stream()
                .filter(r -> !r.serverId().equals(ownId))
                .sorted((a, b) -> Double.compare(b.rate(), a.rate()))
                .toList();
    }

    /** Returns the exchange rate for a specific server, or null if not cached. */
    @Nullable
    public ExchangeRate getExchangeRate(String serverId) {
        return cache.get(serverId);
    }

    /** Returns the local server's exchange rate vs the global baseline, or null if not fetched. */
    @Nullable
    public ExchangeRate getLocalExchangeRate() {
        if (!isEnabled()) {
            return null;
        }
        String ownId = configManager.getConfig().priceReporter().serverId();
        return cache.get(ownId);
    }

    /** Returns when the cache was last successfully refreshed. */
    @Nullable
    public Instant lastFetchedAt() {
        return lastFetchedAtInstant;
    }

    /** Whether the last fetch attempt encountered an error. */
    public boolean lastFetchFailed() {
        return fetchFailed;
    }

    // -------------------------------------------------------------------------
    // Scheduled fetch — called by TaskScheduler
    // -------------------------------------------------------------------------

    /**
     * Fetches the latest exchange rates from the API server.
     * Called on a background thread. Logs warnings on failure but never throws.
     */
    public void fetchExchangeRates() {
        if (!isEnabled()) {
            return;
        }

        AutoTuneConfig.PriceReporterConfig cfg = configManager.getConfig().priceReporter();
        String apiUrl = cfg.apiUrl().replaceAll("/$", "");
        String endpoint = apiUrl + "/api/servers/exchange-rates";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + cfg.apiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        handleSuccess(response.body());
                    } else {
                        plugin.getLogger().warning("Exchange rate fetch failed: HTTP "
                                + response.statusCode() + " — " + response.body());
                        fetchFailed = true;
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Exchange rate fetch failed: " + error.getMessage());
                    fetchFailed = true;
                    return null;
                });
    }

    // -------------------------------------------------------------------------

    private void handleSuccess(String body) {
        try {
            ExchangeRatesResponse resp = gson.fromJson(body, ExchangeRatesResponse.class);
            if (resp == null || resp.rates() == null) {
                return;
            }

            ConcurrentMap<String, ExchangeRate> newCache = new ConcurrentHashMap<>();
            for (ExchangeRateEntry entry : resp.rates()) {
                String serverIdStr = entry.serverId() != null ? entry.serverId().toString() : "unknown";
                ExchangeRate rate = new ExchangeRate(
                        serverIdStr,
                        entry.name() != null ? entry.name() : "Unknown Server",
                        entry.rate() != null ? entry.rate() : 1.0,
                        entry.player_count() != null ? entry.player_count() : 0,
                        Instant.now()
                );
                newCache.put(rate.serverId(), rate);
            }

            cache.clear();
            cache.putAll(newCache);
            lastFetchedAtInstant = Instant.now();
            fetchFailed = false;

            plugin.getLogger().fine("Exchange rates refreshed: " + newCache.size()
                    + " server(s), fetched at " + lastFetchedAtInstant);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse exchange rate response: " + e.getMessage());
            fetchFailed = true;
        }
    }

    // -------------------------------------------------------------------------
    // JSON DTOs (matches the API server response shape)
    // -------------------------------------------------------------------------

    private record ExchangeRatesResponse(
            String base,
            List<ExchangeRateEntry> rates
    ) {}

    /**
     * API response entry for a single server's exchange rate.
     * serverId is stored as a String (PostgreSQL UUID serialized as JSON string by serde).
     * Gson deserializes it directly from the string representation.
     */
    private record ExchangeRateEntry(
            @SerializedName("server_id")
            String serverId,
            String name,
            Double rate,
            Integer player_count,
            Object last_seen
    ) {}
}
