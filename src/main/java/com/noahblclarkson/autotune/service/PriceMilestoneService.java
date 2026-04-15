package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Price Milestone Notifications — broadcasts when items cross round-number price thresholds.
 *
 * Scans all shop items every N minutes. When an item's mid-price crosses a configured
 * round-number threshold (e.g. $100, $200, $500), broadcasts to all online players via
 * action bar. Per-item+threshold cooldown prevents spam.
 *
 * Example announcements:
 *   "🚀 Diamond has broken through $300! (was $290)"
 *   "📉 Diamond has fallen below $300 (was $310)"
 */
@Singleton
public class PriceMilestoneService {

    private static final Logger log = Logger.getLogger(PriceMilestoneService.class.getName());
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final AutoTune plugin;
    private final MarketEngine marketEngine;
    private final ShopManager shopManager;
    private final ConfigManager configManager;

    /** Last mid-price we evaluated for each item. Cleared on milestone hit to avoid re-firing. */
    private final ConcurrentHashMap<Integer, BigDecimal> lastMidPrice = new ConcurrentHashMap<>();

    /** Last notification time for each (itemId, threshold) pair. */
    private final ConcurrentHashMap<String, Instant> lastNotifiedAt = new ConcurrentHashMap<>();

    /** The repeating scan task — null when disabled. */
    private BukkitTask scanTask;

    public PriceMilestoneService(
            AutoTune plugin,
            MarketEngine marketEngine,
            ShopManager shopManager,
            ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.marketEngine = marketEngine;
        this.shopManager = shopManager;
        this.configManager = configManager;
    }

    public void onEnable() {
        AutoTuneConfig.PriceMilestoneConfig cfg = configManager.getConfig().priceMilestones();
        if (!cfg.enabled()) {
            log.info("[Auto-Tune] Price milestone notifications are disabled.");
            return;
        }
        scheduleScanTask(cfg.intervalMinutes());
        log.info("[Auto-Tune] Price milestone notifications enabled (check every "
                + cfg.intervalMinutes() + " min, thresholds: " + cfg.thresholds() + ").");
    }

    private void scheduleScanTask(int intervalMinutes) {
        long ticksInterval = intervalMinutes * 60L * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                checkAndBroadcastMilestones();
            } catch (Exception e) {
                log.log(Level.WARNING, "Error in initial milestone check", e);
            }
            scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticksInterval, ticksInterval);
        }, 60L);
    }

    private void tick() {
        try {
            checkAndBroadcastMilestones();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error in price milestone scan", e);
        }
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
        }
    }

    public void reload() {
        shutdown();
        AutoTuneConfig.PriceMilestoneConfig cfg = configManager.getConfig().priceMilestones();
        if (!cfg.enabled()) {
            log.info("[Auto-Tune] Price milestone notifications are disabled.");
            return;
        }
        // Clear state on reload so we re-evaluate from current prices
        lastMidPrice.clear();
        lastNotifiedAt.clear();
        scheduleScanTask(cfg.intervalMinutes());
        log.info("[Auto-Tune] Price milestone notifications reloaded.");
    }

    /**
     * Checks all items for threshold crossings and broadcasts milestones to online players.
     */
    private void checkAndBroadcastMilestones() {
        AutoTuneConfig.PriceMilestoneConfig cfg = configManager.getConfig().priceMilestones();
        if (!cfg.enabled()) return;

        Instant now = Instant.now();
        Duration cooldown = Duration.ofMinutes(cfg.cooldownMinutes());

        for (ShopItem item : shopManager.getAllItems()) {
            BigDecimal buyPrice = marketEngine.getBuyPrice(item);
            BigDecimal sellPrice = marketEngine.getSellPrice(item);
            if (buyPrice == null || sellPrice == null) continue;
            if (buyPrice.compareTo(BigDecimal.ZERO) <= 0 || sellPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal midPrice = buyPrice.add(sellPrice).divide(TWO, 4, RoundingMode.HALF_UP);

            BigDecimal lastPrice = lastMidPrice.get(item.id());
            if (lastPrice == null) {
                // First scan — just record and skip
                lastMidPrice.put(item.id(), midPrice);
                continue;
            }

            lastMidPrice.put(item.id(), midPrice);

            // Check each threshold
            for (int threshold : cfg.thresholds()) {
                BigDecimal threshBd = BigDecimal.valueOf(threshold);

                boolean crossedUp = lastPrice.compareTo(threshBd) < 0
                        && midPrice.compareTo(threshBd) >= 0;
                boolean crossedDown = lastPrice.compareTo(threshBd) > 0
                        && midPrice.compareTo(threshBd) <= 0;

                if (!crossedUp && !crossedDown) continue;

                // Check cooldown
                String cooldownKey = item.id() + ":" + threshold;
                Instant lastNotified = lastNotifiedAt.get(cooldownKey);
                if (lastNotified != null && Duration.between(lastNotified, now).compareTo(cooldown) < 0) {
                    continue;
                }

                // Fire milestone!
                lastNotifiedAt.put(cooldownKey, now);
                broadcastMilestone(item, threshold, midPrice, lastPrice, crossedUp);
            }
        }
    }

    private void broadcastMilestone(ShopItem item, int threshold, BigDecimal currentMid, BigDecimal previousMid, boolean crossedUp) {
        String emoji = crossedUp ? "🚀" : "📉";
        TextColor color = crossedUp ? NamedTextColor.GREEN : NamedTextColor.RED;
        String direction = crossedUp ? "broken through" : "fallen below";
        String priceColorHex = crossedUp ? "green" : "red";

        String matName = item.material().name();

        String msg = emoji + " <white>" + matName + "</white> has "
                + direction + " <" + priceColorHex + ">$" + threshold + "</" + priceColorHex + ">! "
                + "(was <aqua>$" + formatPrice(previousMid) + "</aqua>)";

        Component component = parseComponent(msg);
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(component);
            count++;
        }
        log.info("[Auto-Tune Milestone] " + emoji + " " + matName + " " + direction + " $" + threshold
                + " (was $" + formatPrice(previousMid) + ") — announced to " + count + " player(s)");
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "0.00";
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Component parseComponent(String miniMsg) {
        try {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(miniMsg);
        } catch (Exception e) {
            return Component.text(miniMsg).color(NamedTextColor.WHITE);
        }
    }
}
