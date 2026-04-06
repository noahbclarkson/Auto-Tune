package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.MarketDigestConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.MarketEventService;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends a periodic market digest to Discord (or any HTTP webhook) summarizing
 * the economy's state since the last digest.
 *
 * Runs on a configurable schedule (daily or weekly at a specific UTC hour).
 * Sections include: top price movers, health stats, active events, loan stats.
 */
@Singleton
public class MarketDigestService {

    private static final Logger LOGGER = Logger.getLogger(MarketDigestService.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int EMBED_COLOR = 0x10b981; // emerald
    private static final int TOP_MOVERS_COUNT = 5;
    private static final int SUMMARY_WINDOW_DAYS = 7;

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final MarketEventService marketEventService;
    private final EconomyMetricsManager economyMetricsManager;
    private final LoanManager loanManager;
    private final MarketEngine marketEngine;
    private final ItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final ShopManager shopManager;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "autotune-market-digest");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> scheduledTask;

    @Inject
    public MarketDigestService(
            AutoTune plugin,
            ConfigManager configManager,
            MarketEventService marketEventService,
            EconomyMetricsManager economyMetricsManager,
            LoanManager loanManager,
            MarketEngine marketEngine,
            ItemRepository itemRepository,
            TransactionRepository transactionRepository,
            ShopManager shopManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketEventService = marketEventService;
        this.economyMetricsManager = economyMetricsManager;
        this.loanManager = loanManager;
        this.marketEngine = marketEngine;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.shopManager = shopManager;
    }

    /**
     * Start the scheduled digest task. Called once after all services are initialized.
     */
    public void start() {
        scheduleNext();
        LOGGER.info("[Auto-Tune MarketDigest] Digest scheduler started.");
    }

    /**
     * Stop the scheduled task. Called on plugin disable.
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        executor.shutdown();
        LOGGER.info("[Auto-Tune MarketDigest] Digest scheduler stopped.");
    }

    private void scheduleNext() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        MarketDigestConfig cfg = configManager.getConfig().marketDigest();
        if (!cfg.enabled()) return;

        long delayMs = computeDelayUntilNextRun(cfg);
        LOGGER.info("[Auto-Tune MarketDigest] Next digest in " + (delayMs / 1000 / 60) + " minutes.");

        scheduledTask = executor.schedule(() -> {
            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> {
                        try {
                            sendDigest();
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error sending market digest", e);
                        }
                        scheduleNext(); // reschedule
                    }
            );
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Compute milliseconds until the next digest run based on interval and hourOfDay.
     */
    private long computeDelayUntilNextRun(MarketDigestConfig cfg) {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime target;

        if ("weekly".equals(cfg.interval())) {
            // Find next occurrence of the target day-of-week at the target hour
            DayOfWeek targetDow = DayOfWeek.of(cfg.dayOfWeek() + 1); // 1=Mon..7=Sun
            LocalDateTime nextDow = nowUtc.with(targetDow).withHour(cfg.hourOfDay()).withMinute(0).withSecond(0);
            if (!nextDow.isAfter(nowUtc)) {
                nextDow = nextDow.plusWeeks(1);
            }
            target = nextDow;
        } else {
            // Daily: next occurrence of target hour
            target = nowUtc.withHour(cfg.hourOfDay()).withMinute(0).withSecond(0).withNano(0);
            if (!target.isAfter(nowUtc)) {
                target = target.plusDays(1);
            }
        }

        return Duration.between(nowUtc, target).toMillis();
    }

    /**
     * Trigger a digest send immediately (e.g. via command). Reschedules afterward.
     */
    public void sendDigestNow() {
        executor.execute(() -> {
            try {
                sendDigest();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error sending market digest", e);
            }
            Bukkit.getScheduler().runTask(plugin, this::scheduleNext);
        });
    }

    private void sendDigest() {
        MarketDigestConfig cfg = configManager.getConfig().marketDigest();
        String webhookUrl = cfg.webhookUrl() != null && !cfg.webhookUrl().isBlank()
                ? cfg.webhookUrl()
                : configManager.getConfig().webhook().webhookUrl();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.warning("[Auto-Tune MarketDigest] No webhook URL configured for digest.");
            return;
        }

        Instant since = Instant.now().minus(Duration.ofDays(SUMMARY_WINDOW_DAYS));

        StringBuilder description = new StringBuilder();

        // ── Health Stats ────────────────────────────────────────────────
        if (cfg.includeHealthStats()) {
            if (description.length() > 0) description.append("\n");
            description.append(buildHealthSection());
        }

        // ── Loan Stats ─────────────────────────────────────────────────
        if (cfg.includeLoanStats()) {
            if (description.length() > 0) description.append("\n");
            description.append(buildLoanSection());
        }

        // ── Top Movers ──────────────────────────────────────────────────
        if (cfg.includeTopMovers()) {
            if (description.length() > 0) description.append("\n");
            description.append(buildTopMoversSection(since));
        }

        // ── Active Events ───────────────────────────────────────────────
        if (cfg.includeActiveEvents()) {
            if (description.length() > 0) description.append("\n");
            description.append(buildActiveEventsSection());
        }

        String username = configManager.getConfig().webhook().username();
        if (username == null) username = "Auto-Tune Economy";
        String avatarUrl = configManager.getConfig().webhook().avatarUrl();

        String interval = "weekly".equals(cfg.interval()) ? "Weekly" : "Daily";
        String title = interval + " Market Digest";

        postToWebhook(webhookUrl, username, avatarUrl, title, description.toString());
    }

    private String buildHealthSection() {
        boolean frozen = marketEngine.isFrozen();

        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();
        String cbLabel = switch (cb.tier()) {
            case "NORMAL" -> "Normal";
            case "TIER1" -> "Tier 1 — Caution";
            case "TIER2" -> "Tier 2 — Danger";
            case "TIER3" -> "Tier 3 — Frozen";
            default -> cb.tier();
        };

        BigDecimal gdp = BigDecimal.ZERO;
        if (economyMetricsManager.getLatestSnapshot().isPresent()) {
            gdp = economyMetricsManager.getLatestSnapshot().get().gdp();
        }
        double debtGdpRatio = cb.debtGdpRatio();
        String debtGdpLabel = debtGdpRatio < 0 ? "N/A" : String.format("%.2fx", debtGdpRatio);

        BigDecimal buyVol = transactionRepository.getGlobalBuyVolume(Instant.now().minus(Duration.ofDays(1)));
        BigDecimal totalVol = transactionRepository.getGlobalVolume(Instant.now().minus(Duration.ofDays(1)));
        double buyPct = totalVol.compareTo(BigDecimal.ZERO) > 0
                ? buyVol.divide(totalVol, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        String gdpStr = gdp.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(gdp) : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("**Health**\n");
        sb.append("• GDP: ").append(gdpStr).append("\n");
        sb.append("• Market: ").append(frozen ? "❄️ Frozen" : "✅ Active").append("\n");
        sb.append("• Circuit Breaker: ").append(cbLabel).append("\n");
        sb.append("• Debt/GDP: ").append(debtGdpLabel).append("\n");
        sb.append("• 24h Buy Ratio: ").append(String.format("%.0f%%", buyPct));

        return sb.toString();
    }

    private String buildLoanSection() {
        int activeLoans = 0;
        BigDecimal totalDebt = BigDecimal.ZERO;
        if (economyMetricsManager.getLatestSnapshot().isPresent()) {
            activeLoans = economyMetricsManager.getLatestSnapshot().get().activeLoans();
            totalDebt = economyMetricsManager.getLatestSnapshot().get().totalDebt();
        }

        LoanManager.CircuitBreakerStatus cb = loanManager.getCircuitBreakerStatus();
        double debtGdpRatio = cb.debtGdpRatio();
        String debtGdpLabel = debtGdpRatio < 0 ? "N/A" : String.format("%.2fx", debtGdpRatio);

        StringBuilder sb = new StringBuilder();
        sb.append("**Loans**\n");
        sb.append("• Active Loans: ").append(activeLoans).append("\n");
        sb.append("• Total Debt: ")
                .append(totalDebt.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(totalDebt) : "N/A")
                .append("\n");
        sb.append("• Debt/GDP: ").append(debtGdpLabel).append("\n");
        sb.append("• Tier: ").append(cb.tier())
                .append(" (").append(String.format("%.2fx", debtGdpRatio)).append(")");

        return sb.toString();
    }

    private String buildTopMoversSection(Instant since) {
        List<ShopItem> allItems = shopManager.getAllItems();
        List<ItemChange> changes = new ArrayList<>();

        for (ShopItem item : allItems) {
            List<PriceHistory> history = itemRepository.getPriceHistorySince(item.id(), since, 10);
            if (history.size() < 2) continue;
            BigDecimal newest = history.get(0).price();
            BigDecimal oldest = history.get(history.size() - 1).price();
            if (oldest.compareTo(BigDecimal.ZERO) <= 0) continue;
            double pctChange = newest.subtract(oldest)
                    .divide(oldest, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100.0;
            changes.add(new ItemChange(item.getDisplayNameOrMaterial(), pctChange));
        }

        if (changes.isEmpty()) {
            return "**Top Price Movers** _(not enough data yet)_";
        }

        changes.sort(Comparator.<ItemChange>comparingDouble(c -> Math.abs(c.pct())).reversed());
        int count = Math.min(TOP_MOVERS_COUNT, changes.size());
        List<ItemChange> topMovers = changes.subList(0, count);

        StringBuilder sb = new StringBuilder("**Top Price Movers (")
                .append(SUMMARY_WINDOW_DAYS).append("d)**\n");

        for (ItemChange ic : topMovers) {
            String arrow = ic.pct() >= 0 ? "▲" : "▼";
            sb.append("• ").append(ic.name()).append(": ").append(arrow)
                    .append(" ").append(String.format("%+.1f%%", ic.pct())).append("\n");
        }

        return sb.toString();
    }

    private String buildActiveEventsSection() {
        List<MarketEvent> active = marketEventService.getActiveEvents();
        if (active.isEmpty()) {
            return "**Active Events** — none currently running";
        }

        StringBuilder sb = new StringBuilder("**Active Events (").append(active.size()).append(")**\n");
        for (MarketEvent evt : active) {
            String icon = switch (evt.type()) {
                case DEMAND_SURGE -> "📈";
                case SUPPLY_GLUT -> "📉";
                case INFLATION_BOOST -> "💰";
                case DEFLATION_DROP -> "💸";
                case GOLD_RUSH -> "⛏️";
                case CUSTOM -> "📌";
            };
            sb.append("• ").append(icon).append(" ").append(evt.name()).append("\n");
        }
        return sb.toString();
    }

    private void postToWebhook(String webhookUrl, String username, String avatarUrl, String title, String description) {
        try {
            String usernameField = username != null ? "\"username\": \"" + jsonEscape(username) + "\"," : "";
            String avatarField = avatarUrl != null ? "\"avatar_url\": \"" + jsonEscape(avatarUrl) + "\"," : "";

            String payload = "{"
                    + usernameField
                    + avatarField
                    + "\"embeds\": [{"
                    + "\"title\": \"" + jsonEscape(title) + "\","
                    + "\"description\": \"" + jsonEscape(description) + "\","
                    + "\"color\": " + EMBED_COLOR + ","
                    + "\"footer\": {\"text\": \"Auto-Tune Market Digest\"},"
                    + "\"timestamp\": \"" + Instant.now().toString() + "\""
                    + "}]}";

            URL url = new URL(webhookUrl);
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
                LOGGER.warning("[Auto-Tune MarketDigest] POST returned HTTP " + status);
            } else {
                LOGGER.info("[Auto-Tune MarketDigest] Digest sent successfully.");
            }
            conn.disconnect();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Auto-Tune MarketDigest] Failed to send digest", e);
        }
    }

    private static String formatCurrency(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) return "$0";
        if (value.abs().compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return "$" + value.divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP) + "M";
        }
        if (value.abs().compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return "$" + value.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP) + "K";
        }
        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ItemChange(String name, double pct) {}
}
