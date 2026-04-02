package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig.AdminWebhookConfig;
import com.noahblclarkson.autotune.config.ConfigManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends economy alert notifications to a configured Discord (or generic HTTP) webhook.
 *
 * Fires on:
 * - Circuit breaker tier-2 activation (if notifyTier2=true)
 * - Circuit breaker tier-3 activation (if notifyTier3=true, default)
 * - Aggregate economy volatility spike into UNSTABLE zone (if notifyVolatility=true)
 * - Debt/GDP ratio exceeding notifyHighDebtThreshold (if notifyHighDebt=true)
 *
 * All HTTP calls run on a background thread — no main-thread blocking.
 *
 * Format: Discord-compatible webhook payload using embeds.
 * Also compatible with any endpoint that accepts POST application/json.
 */
@Singleton
public class AdminWebhookService {

    private static final Logger LOGGER = Logger.getLogger(AdminWebhookService.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final ConfigManager configManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "autotune-webhook");
        t.setDaemon(true);
        return t;
    });

    /** Track last state to avoid duplicate notifications */
    private volatile String lastCircuitBreakerTier = "NORMAL";
    private volatile boolean lastVolatilityUnstable = false;
    private volatile boolean lastHighDebtFired = false;

    @Inject
    public AdminWebhookService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Called by EconomicNewsService when circuit breaker tier changes.
     *
     * @param tier  "NORMAL", "TIER1", "TIER2", or "TIER3"
     */
    public void onCircuitBreakerChange(String tier) {
        AdminWebhookConfig cfg = configManager.getConfig().webhook();
        if (!cfg.enabled() || cfg.webhookUrl() == null) return;
        if (tier.equals(lastCircuitBreakerTier)) return;
        lastCircuitBreakerTier = tier;

        if ("TIER3".equals(tier) && cfg.notifyTier3()) {
            postAlert("🔴 Circuit Breaker TIER 3 — Loans Frozen",
                    "Debt/GDP ratio has exceeded the tier-3 threshold. All loan interest is **paused**. " +
                            "The economy needs intervention — check `/at admin health` on the server.",
                    0xFF3333);
        } else if ("TIER2".equals(tier) && cfg.notifyTier2()) {
            postAlert("🟡 Circuit Breaker TIER 2 — Interest Reduced",
                    "Debt/GDP ratio has exceeded the tier-2 threshold. Loan interest reduced to 25% of normal. " +
                            "Economy is under stress — monitor via `/at admin health`.",
                    0xFFCC00);
        } else if ("NORMAL".equals(tier)) {
            // Recovery notification (only if we previously fired tier2 or tier3)
            // Low priority: only fire if we had previously notified
        }
    }

    /**
     * Called when aggregate economy volatility spikes into the UNSTABLE zone (≥ 0.15).
     *
     * @param volatility  current aggregate volatility value
     */
    public void onVolatilitySpike(double volatility) {
        AdminWebhookConfig cfg = configManager.getConfig().webhook();
        if (!cfg.enabled() || cfg.webhookUrl() == null) return;
        if (!cfg.notifyVolatility()) return;
        if (lastVolatilityUnstable) return; // don't repeat until it recovers
        lastVolatilityUnstable = true;

        postAlert("⚠️ Economy Volatility Spike",
                String.format("Aggregate price volatility has risen to **%.4f** (threshold: 0.15 = UNSTABLE). " +
                        "Prices are oscillating wildly. Check `/at admin health` for top volatile items.", volatility),
                0xFF8800);
    }

    /**
     * Called when volatility drops back below 0.15 (optional recovery notification).
     */
    public void onVolatilityRecovered() {
        lastVolatilityUnstable = false;
    }

    /**
     * Called on each economy health check when D/G exceeds the configured threshold.
     *
     * @param debtGdpRatio  current debt/GDP ratio
     */
    public void onHighDebt(double debtGdpRatio) {
        AdminWebhookConfig cfg = configManager.getConfig().webhook();
        if (!cfg.enabled() || cfg.webhookUrl() == null) return;
        if (!cfg.notifyHighDebt()) return;
        if (lastHighDebtFired) return;
        lastHighDebtFired = true;

        postAlert("💸 High Debt/GDP Alert",
                String.format("Debt/GDP ratio is **%.2fx** (threshold: %.1fx). " +
                        "The economy may be heading toward a cascade. " +
                        "Consider reviewing loan settings or freezing new loans temporarily.", debtGdpRatio, cfg.notifyHighDebtThreshold()),
                0xCC44CC);
    }

    /**
     * Reset the high-debt fired flag when D/G recovers below threshold.
     */
    public void onDebtRecovered() {
        lastHighDebtFired = false;
    }

    /**
     * Posts a Discord-formatted embed to the configured webhook URL.
     *
     * @param title       embed title
     * @param description embed description
     * @param color       embed color as 0xRRGGBB integer
     */
    private void postAlert(String title, String description, int color) {
        AdminWebhookConfig cfg = configManager.getConfig().webhook();
        if (cfg.webhookUrl() == null || cfg.webhookUrl().isBlank()) {
            LOGGER.warning("[Auto-Tune Webhook] webhook-url is not configured.");
            return;
        }

        String usernameField = cfg.username() != null
                ? "\"username\": \"" + jsonEscape(cfg.username()) + "\","
                : "";
        String avatarField = cfg.avatarUrl() != null
                ? "\"avatar_url\": \"" + jsonEscape(cfg.avatarUrl()) + "\","
                : "";

        String payload = "{"
                + usernameField
                + avatarField
                + "\"embeds\": [{"
                + "\"title\": \"" + jsonEscape(title) + "\","
                + "\"description\": \"" + jsonEscape(description) + "\","
                + "\"color\": " + color
                + "}]}";

        executor.submit(() -> {
            try {
                URL url = new URL(cfg.webhookUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    LOGGER.warning("[Auto-Tune Webhook] POST returned HTTP " + status
                            + " for alert: " + title);
                } else {
                    LOGGER.info("[Auto-Tune Webhook] Sent alert: " + title);
                }
                conn.disconnect();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Auto-Tune Webhook] Failed to send alert: " + title, e);
            }
        });
    }

    /** Escapes a string for safe inclusion inside a JSON string value. */
    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
