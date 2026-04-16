package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.scheduler.BukkitTask;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.manager.PluginAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Economic News Feed — broadcasts significant market events to players via action bar.
 *
 * Periodically scans price history to detect:
 * - Large price surges or crashes (configurable % threshold)
 * - Unusual volume spikes (configurable × normal multiplier)
 * - Circuit breaker tier activation (tier 1/2/3)
 * - Market freeze/unfreeze
 *
 * Design principles:
 * - Never spams: max N items per cycle, per-item cooldown prevents repetition
 * - Useful context: messages include the item name, direction, magnitude, and a click hint
 * - Low overhead: reads from existing price history table, no new DB writes
 */
@Singleton
@SuppressWarnings("PMD")
public class EconomicNewsService {

    private static final Logger log = Logger.getLogger(EconomicNewsService.class.getName());
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal VOLATILE_THRESHOLD = BigDecimal.valueOf(0.15);
    private static final BigDecimal STABLE_THRESHOLD = BigDecimal.valueOf(0.05);
    private static final int MIN_HISTORY_SIZE = 2;
    private static final int PERCENT_FMT_100 = 100;
    private static final int PERCENT_FMT_10 = 10;
    private static final String TIER_NORMAL = "NORMAL";

    private final AutoTune plugin;
    private final ItemRepository itemRepository;
    private final ShopManager shopManager;
    private final LoanManager loanManager;
    private final ConfigManager configManager;
    private final PluginAdapter adapter;
    private final AdminWebhookService webhookService;

    /** Tracks the last announced circuit breaker tier to avoid repeat announcements */
    private final AtomicReference<String> lastCircuitBreakerTier = new AtomicReference<>(null);
    /** Previous aggregate volatility for spike detection */
    private final AtomicReference<Double> previousAvgVolatility = new AtomicReference<>(null);
    /** Old announcements for cooldown pruning */
    private final CopyOnWriteArrayList<Instant> recentAnnouncements = new CopyOnWriteArrayList<>();
    /** Round-robin counter for fair item selection across cycles */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    /** The repeating news broadcast task — null when disabled */
    private BukkitTask newsTask;

    /** Rolling buffer of recent news items for the /news command — max 30 entries */
    private final CopyOnWriteArrayList<RecentNewsItem> recentNewsItems = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT_NEWS = 30;

    /**
     * Public news item record with timestamp — exposed to NewsCommand via getRecentNews().
     * The raw NewsItem uses MiniMessage format; this record stores the parsed text for display.
     */
    public record RecentNewsItem(
            Instant timestamp,
            String text,
            TextColor color,
            String clickCommand,
            String hoverText
    ) {}

    public EconomicNewsService(
            AutoTune plugin,
            ItemRepository itemRepository,
            ShopManager shopManager,
            LoanManager loanManager,
            ConfigManager configManager,
            PluginAdapter adapter,
            AdminWebhookService webhookService
    ) {
        this.plugin = plugin;
        this.itemRepository = itemRepository;
        this.shopManager = shopManager;
        this.loanManager = loanManager;
        this.configManager = configManager;
        this.adapter = adapter;
        this.webhookService = webhookService;
    }

    public void onEnable() {
        AutoTuneConfig.EconomicNewsConfig cfg = configManager.getConfig().news();
        if (!cfg.enabled()) {
            log.info("[Auto-Tune] Economic news feed is disabled.");
            return;
        }
        scheduleNewsTask(cfg.intervalMinutes());
        log.info("[Auto-Tune] Economic news feed enabled (interval: " + cfg.intervalMinutes()
                + " min, price threshold: " + cfg.priceChangeThresholdPercent()
                + "%, volume spike: " + cfg.volumeSpikeMultiplier() + "x).");
    }

    private void scheduleNewsTask(int intervalMinutes) {
        long ticksInterval = intervalMinutes * 60L * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                checkAndBroadcastNews();
            } catch (Exception e) {
                log.log(Level.WARNING, "Error in initial news check", e);
            }
            newsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickNews, ticksInterval, ticksInterval);
        }, 60L);
    }

    private void tickNews() {
        try {
            checkAndBroadcastNews();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error in news feed tick", e);
        }
    }

    /**
     * Cancels the news broadcast task. Called on plugin shutdown and reload.
     */
    public void shutdown() {
        if (newsTask != null) {
            newsTask.cancel();
            newsTask = null;
        }
    }

    /**
     * Reloads the news service: cancels the existing task and reschedules with the
     * current config. Called when an admin runs /at admin reload.
     */
    public void reload() {
        shutdown();
        AutoTuneConfig.EconomicNewsConfig cfg = configManager.getConfig().news();
        if (!cfg.enabled()) {
            log.info("[Auto-Tune] Economic news feed is disabled.");
            return;
        }
        scheduleNewsTask(cfg.intervalMinutes());
        log.info("[Auto-Tune] Economic news feed reloaded (interval: " + cfg.intervalMinutes()
                + " min, price threshold: " + cfg.priceChangeThresholdPercent()
                + "%, volume spike: " + cfg.volumeSpikeMultiplier() + "x).");
    }

    /**
     * Called by the repeating task. Scans for newsworthy events and broadcasts
     * a random subset to online players.
     */
    public void checkAndBroadcastNews() {
        AutoTuneConfig.EconomicNewsConfig cfg = configManager.getConfig().news();
        if (!cfg.enabled()) return;

        List<NewsItem> candidates = new ArrayList<>();
        gatherCandidates(cfg, candidates);

        // Check D/G for webhook high-debt alert
        AutoTuneConfig.AdminWebhookConfig webhookCfg = configManager.getConfig().webhook();
        if (webhookCfg.enabled() && webhookCfg.notifyHighDebt()) {
            double debtGdp = computeCurrentDebtGdpRatio();
            if (debtGdp > webhookCfg.notifyHighDebtThreshold()) {
                webhookService.onHighDebt(debtGdp);
            } else {
                webhookService.onDebtRecovered();
            }
        }

        // Check per-item volume for low-volume webhook alerts
        if (webhookCfg.enabled() && webhookCfg.notifyLowVolume()) {
            checkLowVolumeItems(webhookCfg);
        }

        if (candidates.isEmpty()) return;

        int maxItems = Math.min(cfg.maxItemsPerCycle(), candidates.size());
        List<NewsItem> selected = new ArrayList<>(candidates);
        // Round-robin through candidates so each cycle picks a different subset
        int start = roundRobinCounter.getAndIncrement() % selected.size();
        List<NewsItem> batch = new ArrayList<>(maxItems);
        for (int i = 0; i < maxItems && !selected.isEmpty(); i++) {
            int idx = (start + i) % selected.size();
            batch.add(selected.remove(idx));
        }

        for (NewsItem item : batch) {
            broadcast(item);
            recentAnnouncements.add(Instant.now());
        }

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(cfg.itemCooldownMinutes() * 3L));
        recentAnnouncements.removeIf(t -> t.isBefore(cutoff));
    }

    /**
     * Gather all candidate news items into the provided list.
     */
    private void gatherCandidates(AutoTuneConfig.EconomicNewsConfig cfg, List<NewsItem> out) {
        // 1. Circuit breaker status changes
        checkCircuitBreakerStatus(cfg, out);

        // 2. Price and volume anomalies per item
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(cfg.historyWindowMinutes()));
        int historyLimit = Math.max(2, cfg.historyWindowMinutes() / 5);

        for (ShopItem item : shopManager.getAllItems()) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), windowStart, historyLimit);
            if (history.size() < MIN_HISTORY_SIZE) continue;

            PriceHistory latest = history.get(history.size() - 1);
            PriceHistory oldest = history.get(0);

            // Price change detection
            BigDecimal midLatest = computeMidPrice(latest);
            BigDecimal midOldest = computeMidPrice(oldest);
            if (midOldest.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal priceDelta = midLatest.subtract(midOldest)
                    .divide(midOldest, MC)
                    .multiply(HUNDRED);
            double absChange = Math.abs(priceDelta.doubleValue());

            if (absChange >= cfg.priceChangeThresholdPercent()) {
                boolean rising = priceDelta.compareTo(BigDecimal.ZERO) > 0;
                String dir = rising ? "surged" : "crashed";
                String emoji = rising ? "⚡" : "📉";
                TextColor col = rising ? NamedTextColor.GREEN : NamedTextColor.RED;
                String mag = formatPercent(absChange);
                String mat = item.material().name();
                String hex = colorName(col);

                String msg = emoji + " <white>" + mat + "</white> prices "
                        + dir + " <" + hex + ">" + mag + "</" + hex + "> in the last "
                        + cfg.historyWindowMinutes() + " min";

                out.add(new NewsItem(msg, col, "/shop " + mat, "Click to view " + mat));
            }

            // Volume spike detection
            int totalVol = history.stream().mapToInt(PriceHistory::totalVolume).sum();
            double avgVol = (double) totalVol / history.size();
            if (avgVol > 0 && latest.totalVolume() > avgVol * cfg.volumeSpikeMultiplier()) {
                double ratio = latest.totalVolume() / avgVol;
                String emoji = "🔥";
                TextColor col = NamedTextColor.GOLD;
                String mat = item.material().name();
                String ratioStr = String.format("%.1fx", ratio);

                String msg = emoji + " <white>" + mat + "</white>: volume <gold>" + ratioStr
                        + "</gold> normal — something big is happening";

                out.add(new NewsItem(msg, col, "/shop " + mat, "Click to view " + mat));
            }
        }

        // 3. Aggregate economy volatility spike — the most critical economy health indicator
        checkVolatilitySpike(cfg, out);
    }

    /**
     * Detects when the economy's aggregate volatility crosses into the UNSTABLE zone.
     * Uses the standard deviation of all items' 24h price changes as the metric.
     * Based on 23-simulation-run dataset: <0.05=STABLE, 0.05-0.15=MODERATE, >=0.15=UNSTABLE.
     */
    private void checkVolatilitySpike(AutoTuneConfig.EconomicNewsConfig cfg, List<NewsItem> out) {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(cfg.historyWindowMinutes()));
        int historyLimit = Math.max(2, cfg.historyWindowMinutes() / 5);

        List<Double> pctChanges = new ArrayList<>();
        for (ShopItem item : shopManager.getAllItems()) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), windowStart, historyLimit);
            if (history.size() < MIN_HISTORY_SIZE) continue;
            BigDecimal newest = history.get(history.size() - 1).price();
            BigDecimal oldest = history.get(0).price();
            if (oldest.compareTo(BigDecimal.ZERO) <= 0) continue;
            double pctChange = newest.subtract(oldest)
                    .divide(oldest, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100.0;
            pctChanges.add(pctChange);
        }

        if (pctChanges.size() < MIN_HISTORY_SIZE) return;

        double sum = 0.0;
        for (double p : pctChanges) sum += p;
        double mean = sum / pctChanges.size();
        double variance = 0.0;
        for (double p : pctChanges) {
            double d = p / 100.0 - mean;
            variance += d * d;
        }
        variance /= pctChanges.size();
        double avgVolatility = Math.sqrt(variance);

        double prev = previousAvgVolatility.get() != null ? previousAvgVolatility.get() : 0.0;
        previousAvgVolatility.set(avgVolatility);

        // Alert on transition into UNSTABLE zone (from below VOLATILE_THRESHOLD to above it)
        if (avgVolatility >= VOLATILE_THRESHOLD.doubleValue() && prev < VOLATILE_THRESHOLD.doubleValue()) {
            String msg = "⚠️ <red>ECONOMY VOLATILITY SPIKE</red> — prices are oscillating wildly! "
                    + "Run <aqua>/at admin health</aqua> to diagnose.";
            out.add(new NewsItem(msg, NamedTextColor.RED, "/at admin health", "Run /at admin health"));
            // Webhook notification
            webhookService.onVolatilitySpike(avgVolatility);
        } else if (avgVolatility < VOLATILE_THRESHOLD.doubleValue() && prev >= VOLATILE_THRESHOLD.doubleValue()) {
            webhookService.onVolatilityRecovered();
        }
    }

    /**
     * Scans all shop items for low trading volume and fires a webhook alert
     * for any item whose 24h volume is below the configured threshold.
     * Per-item cooldown is handled inside AdminWebhookService.onLowVolumeAlert.
     */
    private void checkLowVolumeItems(AutoTuneConfig.AdminWebhookConfig webhookCfg) {
        int threshold = webhookCfg.lowVolumeThreshold();
        Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));
        for (ShopItem item : shopManager.getAllItems()) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(
                    item.id(), oneDayAgo, 1);
            if (history.isEmpty()) continue;
            int vol = history.get(0).totalVolume();
            if (vol < threshold) {
                webhookService.onLowVolumeAlert(item.id(), item.material().name(), vol);
            }
        }
    }

    private void checkCircuitBreakerStatus(AutoTuneConfig.EconomicNewsConfig cfg, List<NewsItem> out) {
        LoanManager.CircuitBreakerStatus status = loanManager.getCircuitBreakerStatus();
        String tier = status.tier();

        if (TIER_NORMAL.equals(tier)) {
            String prev = lastCircuitBreakerTier.getAndSet(null);
            if (prev != null) {
                out.add(new NewsItem(
                        "✅ <green>Circuit breaker cleared!</green> — loan interest rates back to normal.",
                        NamedTextColor.GREEN,
                        "/at admin health",
                        "Run /at admin health for details"
                ));
                webhookService.onCircuitBreakerChange(TIER_NORMAL);
            }
            return;
        }

        String prev = lastCircuitBreakerTier.getAndSet(tier);
        if (tier.equals(prev)) return;
        webhookService.onCircuitBreakerChange(tier);

        String msg;
        TextColor col;
        switch (tier) {
            case "TIER3" -> {
                msg = "⚠️ <red>MARKET CIRCUIT BREAKER PAUSED</red> — debt is too high! Loans frozen until economy recovers.";
                col = NamedTextColor.RED;
            }
            case "TIER2" -> {
                msg = "⚠️ <yellow>Circuit breaker TIER 2</yellow> — loan interest reduced. Economy stabilizing.";
                col = NamedTextColor.YELLOW;
            }
            case "TIER1" -> {
                msg = "ℹ️ <aqua>Circuit breaker TIER 1</aqua> — monitoring debt levels.";
                col = NamedTextColor.AQUA;
            }
            default -> { return; }
        }
        out.add(new NewsItem(msg, col, "/at admin health", "Run /at admin health for details"));
    }

    private void broadcast(NewsItem item) {
        Component component = parseComponent(item.message());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(component);
        }
        log.info("[Auto-Tune News] " + stripTags(item.message()));

        // Record in rolling news buffer for /news command
        RecentNewsItem recent = new RecentNewsItem(
                Instant.now(),
                stripTags(item.message()),
                item.color(),
                item.clickCommand(),
                item.hoverText()
        );
        recentNewsItems.add(recent);
        // Trim to max size
        while (recentNewsItems.size() > MAX_RECENT_NEWS) {
            recentNewsItems.remove(0);
        }
    }

    /**
     * Returns the most recent news items in reverse chronological order (newest first).
     */
    public List<RecentNewsItem> getRecentNews() {
        List<RecentNewsItem> copy = new ArrayList<>(recentNewsItems);
        Collections.reverse(copy);
        return copy;
    }

    private Component parseComponent(String miniMsg) {
        try {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(miniMsg);
        } catch (Exception e) {
            return Component.text(stripTags(miniMsg)).color(NamedTextColor.WHITE);
        }
    }

    private BigDecimal computeMidPrice(PriceHistory h) {
        // mid = recordedPrice / (1 - spd/2)
        // Engine stores 'price' as mid-price, and spread halves are bpd/spd
        BigDecimal spdHalf = h.spd().divide(TWO, MC);
        BigDecimal denom = BigDecimal.ONE.subtract(spdHalf);
        if (denom.compareTo(BigDecimal.ZERO) <= 0) return h.price();
        return h.price().divide(denom, MC);
    }

    private String formatPercent(double value) {
        if (value >= PERCENT_FMT_100) return String.format("%.0f%%", value);
        if (value >= PERCENT_FMT_10) return String.format("%.1f%%", value);
        return String.format("%.2f%%", value);
    }

    private String colorName(TextColor c) {
        if (c == NamedTextColor.GREEN) return "green";
        if (c == NamedTextColor.RED) return "red";
        if (c == NamedTextColor.GOLD) return "gold";
        if (c == NamedTextColor.YELLOW) return "yellow";
        if (c == NamedTextColor.AQUA) return "aqua";
        if (c == NamedTextColor.WHITE) return "white";
        return "gray";
    }

    private String stripTags(String msg) {
        return msg.replaceAll("<[^>]+>", "");
    }

    /**
     * Computes the current debt/GDP ratio via the circuit breaker status (which already has it).
     * Returns -1.0 if unavailable.
     */
    private double computeCurrentDebtGdpRatio() {
        try {
            return loanManager.getCircuitBreakerStatus().debtGdpRatio();
        } catch (Exception e) {
            return -1.0;
        }
    }

    /** Internal news item — holds the formatted message and metadata for a news broadcast. */
    private record NewsItem(
            String message,
            TextColor color,
            String clickCommand,
            String hoverText
    ) {}
}
