package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PriceAlertRepository;
import com.noahblclarkson.autotune.model.PriceAlert;
import com.noahblclarkson.autotune.model.PriceAlert.AlertType;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.service.BadgeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
public class PriceAlertManager {

    private static final int MAX_ALERTS_PER_PLAYER = 20;

    private final AutoTune plugin;
    private final PriceAlertRepository alertRepository;
    private final MarketEngine marketEngine;
    private final ShopManager shopManager;
    private final ConfigManager configManager;
    private final BadgeService badgeService;

    /**
     * Per-item cache of active alerts, rebuilt from DB when prices change.
     * Key: itemId → list of active alerts for that item.
     * Updated incrementally on each market tick.
     */
    private final ConcurrentHashMap<Integer, List<PriceAlert>> alertCache = new ConcurrentHashMap<>();

    @Inject
    public PriceAlertManager(
            AutoTune plugin,
            PriceAlertRepository alertRepository,
            MarketEngine marketEngine,
            ShopManager shopManager,
            ConfigManager configManager,
            BadgeService badgeService
    ) {
        this.plugin = plugin;
        this.alertRepository = alertRepository;
        this.marketEngine = marketEngine;
        this.shopManager = shopManager;
        this.configManager = configManager;
        this.badgeService = badgeService;
    }

    /**
     * Called on startup to preload the alert cache.
     */
    public void initialize() {
        rebuildCache();
        plugin.getLogger().info("Price alert system initialized.");
    }

    /**
     * Rebuild the entire alert cache from the database.
     */
    public void rebuildCache() {
        alertCache.clear();
        List<PriceAlert> active = alertRepository.findActiveAlerts();
        for (PriceAlert alert : active) {
            alertCache.computeIfAbsent(alert.itemId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(alert);
        }
    }

    /**
     * Called on each market tick (every updateInterval) to check and fire alerts.
     * For each item with active alerts, compare current price to the target and
     * notify the player if the threshold was crossed.
     */
    public void checkAlerts() {
        for (Map.Entry<Integer, List<PriceAlert>> entry : alertCache.entrySet()) {
            int itemId = entry.getKey();
            List<PriceAlert> alerts = entry.getValue();
            if (alerts.isEmpty()) {
                continue;
            }

            BigDecimal currentPrice;
            try {
                currentPrice = marketEngine.getCurrentPrice(itemId);
            } catch (Exception e) {
                // Item may not be in cache yet, skip
                continue;
            }

            for (PriceAlert alert : alerts) {
                if (alert.isCrossed(currentPrice)) {
                    fireAlert(alert, currentPrice);
                }
            }
        }
    }

    /**
     * Fire a single alert: mark it triggered in DB, remove from cache, notify player.
     */
    private void fireAlert(PriceAlert alert, BigDecimal currentPrice) {
        // Remove from cache immediately to prevent duplicate firing
        alertCache.computeIfPresent(alert.itemId(), (k, v) -> {
            v.removeIf(a -> a.id().equals(alert.id()));
            return v.isEmpty() ? null : v;
        });

        // Mark triggered in DB asynchronously
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PriceAlert triggered = alert.withTriggered();
            alertRepository.update(triggered);
        });

        // Notify player on main thread
        notifyPlayer(alert, currentPrice);
    }

    /**
     * Send the player a message about their triggered alert.
     */
    private void notifyPlayer(PriceAlert alert, BigDecimal currentPrice) {
        Player player = Bukkit.getPlayer(alert.playerUuid());
        if (player == null || !player.isOnline()) {
            return;
        }

        String itemName = shopManager.getItemById(alert.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("#" + alert.itemId());

        String direction = alert.alertType() == AlertType.ABOVE ? "risen above" : "fallen below";
        String formattedTarget = configManager.formatCurrency(alert.targetPrice());
        String formattedCurrent = configManager.formatCurrency(currentPrice);

        Component message = Component.empty()
                .append(Component.text("⚠ Price Alert!", NamedTextColor.YELLOW, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(itemName + " has " + direction + " ", NamedTextColor.GRAY))
                .append(Component.text(formattedTarget, NamedTextColor.WHITE))
                .append(Component.text("!", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Current price: ", NamedTextColor.GRAY))
                .append(Component.text(formattedCurrent, NamedTextColor.GREEN));

        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
                player.sendMessage(message);
                // Award TREND_SPOTTER badge for having an alert fire
                badgeService.onAlertFired(alert.playerUuid());
        });
    }

    /**
     * Create a new price alert for a player.
     */
    public CreateAlertResult createAlert(UUID playerUuid, int itemId, AlertType alertType, BigDecimal targetPrice) {
        if (targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new CreateAlertResult(false, "Target price must be greater than 0.", null);
        }

        int activeCount = alertRepository.countActiveByPlayer(playerUuid);
        if (activeCount >= MAX_ALERTS_PER_PLAYER) {
            return new CreateAlertResult(false,
                    "You have too many active alerts (" + MAX_ALERTS_PER_PLAYER + " max). Remove some first.", null);
        }

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        PriceAlert alert = new PriceAlert(id, playerUuid, itemId, alertType, targetPrice, now, null, true);

        alertRepository.insert(alert);

        // Add to cache
        alertCache.computeIfAbsent(itemId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(alert);

        return new CreateAlertResult(true, null, alert);
    }

    /**
     * Remove an alert (player or admin).
     */
    public boolean removeAlert(String alertId, UUID playerUuid, boolean isAdmin) {
        Optional<PriceAlert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isEmpty()) {
            return false;
        }
        PriceAlert alert = alertOpt.get();

        if (!isAdmin && !alert.playerUuid().equals(playerUuid)) {
            return false;
        }

        alertRepository.delete(alertId);

        // Remove from cache
        alertCache.computeIfPresent(alert.itemId(), (k, v) -> {
            v.removeIf(a -> a.id().equals(alertId));
            return v.isEmpty() ? null : v;
        });

        return true;
    }

    /**
     * Toggle enabled state on an alert.
     */
    public boolean toggleAlert(String alertId, UUID playerUuid, boolean isAdmin) {
        Optional<PriceAlert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isEmpty()) {
            return false;
        }
        PriceAlert alert = alertOpt.get();

        if (!isAdmin && !alert.playerUuid().equals(playerUuid)) {
            return false;
        }

        PriceAlert updated = alert.withEnabled(!alert.enabled());
        alertRepository.update(updated);

        // Update cache
        if (updated.isActive()) {
            alertCache.computeIfAbsent(updated.itemId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(updated);
        } else {
            alertCache.computeIfPresent(updated.itemId(), (k, v) -> {
                v.removeIf(a -> a.id().equals(alertId));
                return v.isEmpty() ? null : v;
            });
        }

        return true;
    }

    /**
     * Rearm a triggered alert (clears triggeredAt, makes it active again).
     */
    public boolean rearmAlert(String alertId, UUID playerUuid) {
        Optional<PriceAlert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isEmpty()) {
            return false;
        }
        PriceAlert alert = alertOpt.get();

        if (!alert.playerUuid().equals(playerUuid)) {
            return false;
        }
        if (!alert.isTriggered()) {
            return false;
        }

        PriceAlert updated = alert.rearm();
        alertRepository.update(updated);

        alertCache.computeIfAbsent(updated.itemId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(updated);

        return true;
    }

    /**
     * Get all alerts for a player (active and triggered).
     */
    public List<PriceAlert> getPlayerAlerts(UUID playerUuid) {
        return alertRepository.findByPlayer(playerUuid);
    }

    public record CreateAlertResult(boolean success, String errorMessage, PriceAlert alert) {}
}
