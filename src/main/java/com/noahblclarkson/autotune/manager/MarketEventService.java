package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.MarketEventRepository;
import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.MarketEvent.EventType;
import com.noahblclarkson.autotune.model.MarketEvent.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of market events and applies price multipliers
 * during price updates.
 *
 * Events affect price velocity — they amplify or dampen price changes for
 * matching items, making the economy feel dynamic and alive without
 * overriding natural market forces.
 *
 * Tick behavior:
 * - Runs every market tick (updateInterval config, default 6000 ticks = 5 min)
 * - Activates SCHEDULED events whose start time has passed
 * - Ends ACTIVE events whose end time has passed
 * - Applies multiplier in MarketEngine.updatePrice() via getEventMultiplier()
 */
@Singleton
public class MarketEventService {

    private static final Logger log = Logger.getLogger(MarketEventService.class.getName());

    private final AutoTune plugin;
    private final MarketEventRepository eventRepository;
    private final PluginAdapter adapter;
    private final ConfigManager configManager;

    /** In-memory cache of active events — avoids DB lookup on every price update */
    private final CopyOnWriteArrayList<MarketEvent> activeEvents = new CopyOnWriteArrayList<>();
    /** Guards concurrent access to tick processing */
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    /** Active boss bars per player — shown when market events are active */
    private final ConcurrentHashMap<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    /** Configured default events loaded at startup */
    private final CopyOnWriteArrayList<AutoTuneConfig.MarketEventConfigEntry> defaultEvents = new CopyOnWriteArrayList<>();

    public MarketEventService(
            AutoTune plugin,
            MarketEventRepository eventRepository,
            PluginAdapter adapter,
            ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.eventRepository = eventRepository;
        this.adapter = adapter;
        this.configManager = configManager;
    }

    public void onEnable() {
        // Load active events from DB into memory
        List<MarketEvent> active = eventRepository.findActive();
        activeEvents.clear();
        activeEvents.addAll(active);
        log.info("[Auto-Tune] Loaded " + active.size() + " active market events");

        // Load default events from config
        loadDefaultEvents();

        // Seed default events if none exist in DB
        if (eventRepository.findAll().isEmpty()) {
            seedDefaultEvents();
        }
    }

    /**
     * Called every market tick (every updateInterval ticks, default 5 min).
     * Activates pending events and ends expired ones.
     */
    public void onMarketTick() {
        if (!ticking.compareAndSet(false, true)) {
            return; // Already ticking — skip
        }
        try {
            Instant now = Instant.now();

            // Activate scheduled events that are ready
            List<MarketEvent> ready = eventRepository.findScheduledReadyToStart(now);
            for (MarketEvent event : ready) {
                activateEvent(event);
            }

            // End expired active events
            List<MarketEvent> expired = eventRepository.findActiveExpired(now);
            for (MarketEvent event : expired) {
                endEvent(event);
            }

            // Increment tick counter for active events (for tracking)
            for (MarketEvent event : activeEvents) {
                if (event.isActive(now)) {
                    int newTicks = event.tickCount() + 1;
                    eventRepository.updateStatusAndTicks(event.id(), Status.ACTIVE.name(), newTicks);
                }
            }
        } finally {
            ticking.set(false);
        }
    }

    /**
     * Returns the combined event multiplier for a material.
     * Called during every price update in MarketEngine.
     *
     * Multiplier is applied to the priceChangePercent, amplifying or dampening
     * the natural price movement for the affected items.
     *
     * @param material the item material name
     * @param priceChangePercent the current price change percentage (before event)
     * @return the adjusted price change percentage
     */
    public double applyEventMultiplier(String material, double priceChangePercent) {
        List<MarketEvent> active = activeEvents;
        if (active.isEmpty()) {
            return priceChangePercent;
        }

        double result = priceChangePercent;
        for (MarketEvent event : active) {
            if (!event.matchesMaterial(material)) {
                continue;
            }

            double multiplier = event.priceChangeMultiplier();
            double eventEffect = computeEventEffect(event.type(), multiplier, priceChangePercent);
            result += eventEffect;
        }

        return result;
    }

    /**
     * Returns the net event multiplier for a material (for display purposes).
     * Use this for UI display — not for actual price calculations.
     */
    public double getNetMultiplier(String material) {
        double net = 1.0;
        for (MarketEvent event : activeEvents) {
            if (event.matchesMaterial(material)) {
                // Combine multiplicatively
                net *= event.priceChangeMultiplier();
            }
        }
        return net;
    }

    /**
     * Trigger an event immediately (admin command).
     * Starts it now and ends after duration.
     */
    public MarketEvent triggerEvent(
            String name,
            EventType type,
            List<String> materials,
            double multiplier,
            Duration duration,
            String startMessage,
            String endMessage,
            String createdBy
    ) {
        Instant now = Instant.now();
        MarketEvent event = new MarketEvent(
                UUID.randomUUID(),
                name,
                type,
                materials,
                multiplier,
                now,
                now.plus(duration),
                startMessage,
                endMessage,
                createdBy,
                Status.ACTIVE,
                null,
                0
        );

        eventRepository.insert(event);
        activeEvents.add(event);
        broadcast(event.startMessage());

        log.info("[Auto-Tune] Market event started: " + name + " (" + type + ") for " + duration.toMinutes() + " min");
        return event;
    }

    /**
     * Schedule an event to start at a future time (admin command).
     * Persists it with SCHEDULED status — will be auto-activated
     * when onMarketTick() finds the start time has passed.
     */
    public MarketEvent scheduleEvent(
            String name,
            EventType type,
            List<String> materials,
            double multiplier,
            Duration duration,
            Instant startsAt,
            String startMessage,
            String endMessage,
            String createdBy
    ) {
        Instant endsAt = startsAt.plus(duration);
        MarketEvent event = new MarketEvent(
                UUID.randomUUID(),
                name,
                type,
                materials,
                multiplier,
                startsAt,
                endsAt,
                startMessage,
                endMessage,
                createdBy,
                Status.SCHEDULED,
                null,
                0
        );

        eventRepository.insert(event);
        log.info("[Auto-Tune] Market event scheduled: " + name + " (" + type + ") starting at " + startsAt + " for " + duration.toMinutes() + " min");
        return event;
    }

    /**
     * Invoke a named template from the default events config.
     * Starts it immediately.
     *
     * @return the triggered event, or empty if template not found
     */
    public Optional<MarketEvent> invokeTemplate(String templateName) {
        for (AutoTuneConfig.MarketEventConfigEntry t : defaultEvents) {
            if (t.name().equalsIgnoreCase(templateName)) {
                EventType type;
                try {
                    type = EventType.valueOf(t.type().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("[Auto-Tune] Template '" + templateName + "' has invalid type: " + t.type());
                    return Optional.empty();
                }

                return Optional.of(triggerEvent(
                        t.name(),
                        type,
                        t.materials(),
                        t.multiplier(),
                        Duration.ofMinutes(t.durationMinutes()),
                        t.startMessage(),
                        t.endMessage(),
                        "template:" + templateName
                ));
            }
        }
        return Optional.empty();
    }

    /**
     * List all available event templates from config.
     */
    public List<AutoTuneConfig.MarketEventConfigEntry> getTemplates() {
        return List.copyOf(defaultEvents);
    }

    /**
     * Cancel an active or scheduled event.
     */
    public boolean cancelEvent(UUID id) {
        Optional<MarketEvent> opt = eventRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        MarketEvent event = opt.get();
        if (event.status() == Status.ENDED || event.status() == Status.CANCELLED) {
            return false;
        }

        eventRepository.updateStatus(id, Status.CANCELLED.name());
        activeEvents.removeIf(e -> e.id().equals(id));
        dismissAllBossBars();
        broadcast("Market event cancelled: " + event.name());
        log.info("[Auto-Tune] Market event cancelled: " + event.name());
        return true;
    }

    /**
     * List all events (recent history).
     */
    public List<MarketEvent> listEvents() {
        return eventRepository.findAll().stream().limit(50).toList();
    }

    /**
     * Get currently active events.
     */
    public List<MarketEvent> getActiveEvents() {
        return List.copyOf(activeEvents);
    }

    /**
     * Get events matching a material (for display).
     */
    public List<MarketEvent> getEventsForMaterial(String material) {
        return activeEvents.stream()
                .filter(e -> e.matchesMaterial(material))
                .toList();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void activateEvent(MarketEvent event) {
        MarketEvent active = event.withStatus(Status.ACTIVE);
        eventRepository.updateStatus(event.id(), Status.ACTIVE.name());
        activeEvents.add(active);
        broadcast(event.startMessage());
        showEventBossBar(event);
        log.info("[Auto-Tune] Market event activated: " + event.name());
    }

    private void endEvent(MarketEvent event) {
        MarketEvent ended = event.withStatus(Status.ENDED);
        eventRepository.updateStatus(event.id(), Status.ENDED.name());
        activeEvents.removeIf(e -> e.id().equals(event.id()));
        dismissAllBossBars();
        broadcast(event.endMessage());
        log.info("[Auto-Tune] Market event ended: " + event.name());
    }

    /**
     * Computes the additional price change effect from an event.
     * The effect is additive — it adds to the existing price change.
     *
     * For example, if natural priceChangePercent = -2% and SUPPLY_GLUT multiplier = 2.0:
     *   supply glut effect = -2% × (2.0 - 1) = -2% additional → total = -4%
     */
    private double computeEventEffect(EventType type, double multiplier, double priceChangePercent) {
        double extra = priceChangePercent * (multiplier - 1.0);

        switch (type) {
            case DEMAND_SURGE -> {
                // Amplifies upward movements, suppresses downward
                if (priceChangePercent >= 0) {
                    return extra; // amplify rising prices
                } else {
                    return -priceChangePercent * (1.0 - 1.0 / multiplier); // push toward zero
                }
            }
            case SUPPLY_GLUT -> {
                // Amplifies downward movements, suppresses upward
                if (priceChangePercent <= 0) {
                    return extra; // amplify falling prices
                } else {
                    return -priceChangePercent * (1.0 - 1.0 / multiplier); // push toward zero
                }
            }
            case INFLATION_BOOST -> {
                // Always add upward drift (regardless of direction)
                return Math.abs(priceChangePercent) * (multiplier - 1.0);
            }
            case DEFLATION_DROP -> {
                // Always add downward drift
                return -Math.abs(priceChangePercent) * (multiplier - 1.0);
            }
            case GOLD_RUSH, CUSTOM -> {
                // Symmetric: amplify whatever direction
                return extra;
            }
            default -> {
                return 0.0;
            }
        }
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Component component = MiniMessage.miniMessage().deserialize(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    /**
     * Shows a boss bar to all online players for the duration of the event.
     * The bar automatically dismisses when the event ends.
     */
    private void showEventBossBar(MarketEvent event) {
        AutoTuneConfig config = configManager.getConfig();
        if (config.marketEvents() != null && !config.marketEvents().bossBar().enabled()) {
            return;
        }

        BarColor color = bossBarColor(event.type());
        String title = event.name();

        // Boss bar shows event name as title
        BossBar bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);

        // Progress bar depletes as event approaches its end time
        Duration timeUntilEnd = Duration.between(Instant.now(), event.endsAt());
        Duration totalDuration = Duration.between(event.startsAt(), event.endsAt());
        if (!totalDuration.isZero() && !timeUntilEnd.isNegative()) {
            double progress = Math.max(0.0, Math.min(1.0,
                    (double) timeUntilEnd.toMillis() / totalDuration.toMillis()));
            bar.setProgress(progress);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
            playerBossBars.put(player.getUniqueId(), bar);
        }

        // Dismiss the boss bar when the event ends
        if (!timeUntilEnd.isNegative() && !timeUntilEnd.isZero()) {
            long ticksUntilEnd = Math.max(20, timeUntilEnd.toSeconds() * 20);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                dismissAllBossBars();
            }, ticksUntilEnd);
        }
    }

    /**
     * Removes the boss bar from a specific player.
     */
    private void dismissBossBar(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * Removes all active boss bars from all players.
     */
    private void dismissAllBossBars() {
        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll();
        }
        playerBossBars.clear();
    }

    /**
     * Returns the boss bar color appropriate for an event type.
     */
    private BarColor bossBarColor(EventType type) {
        return switch (type) {
            case DEMAND_SURGE, GOLD_RUSH -> BarColor.YELLOW;
            case SUPPLY_GLUT, DEFLATION_DROP -> BarColor.BLUE;
            case INFLATION_BOOST -> BarColor.RED;
            case CUSTOM -> BarColor.GREEN;
        };
    }

    private void loadDefaultEvents() {
        defaultEvents.clear();
        AutoTuneConfig config = configManager.getConfig();
        if (config.marketEvents() != null) {
            for (AutoTuneConfig.MarketEventConfigEntry ec : config.marketEvents().defaultEvents()) {
                defaultEvents.add(ec);
            }
        }
    }

    private void seedDefaultEvents() {
        // Seed a few example events so admins can see the system working
        Instant now = Instant.now();

        List<MarketEvent> seeds = List.of(
                new MarketEvent(
                        UUID.randomUUID(), "Demand Surge", EventType.DEMAND_SURGE,
                        List.of("DIAMOND", "EMERALD", "GOLD_INGOT"),
                        1.5, now, now.plusSeconds(3600),
                        "<gold> Demand Surge! </gold> Diamond, Emerald and Gold buy prices are boosted for the next hour!",
                        "<gray> Demand Surge has ended. Prices are returning to normal.",
                        "system", Status.ACTIVE, null, 0
                )
        );

        for (MarketEvent seed : seeds) {
            eventRepository.insert(seed);
            activeEvents.add(seed);
        }
        log.info("[Auto-Tune] Seeded " + seeds.size() + " default market events");
    }
}
