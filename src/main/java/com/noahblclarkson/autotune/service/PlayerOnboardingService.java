package com.noahblclarkson.autotune.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PendingNotificationRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Progressive player onboarding — sends a one-time in-game message series
 * to new players as they hit key milestones in their first 30 days.
 *
 * Messages are sent while the player is offline (via PendingNotificationRepository)
 * and delivered on their next login, so they never get spammed mid-session.
 *
 * Milestones (days since first_join):
 *   0  — Welcome message (Day 1: economy intro + key commands)
 *   3  — Loans tip (/loan guide, why borrowing can be smart)
 *   7  — Comparison tip (/compare to find underpriced items)
 *   14 — Diversification (/shop browse for low-correlated items)
 *   30 — Loyalty check (send if player is still active)
 *
 * Admin can configure which milestones are enabled, the day offset, and the message template.
 */
@Singleton
public class PlayerOnboardingService {

    private static final Logger log = Logger.getLogger(PlayerOnboardingService.class.getName());

    /**
     * Onboarding milestone — a day offset and the message category.
     * Sent once when player's days-on-server crosses the milestone threshold.
     */
    public record Milestone(int dayOffset, String category) {}

    /** All milestones in ascending day-offset order (defaults, used when config is absent). */
    public static final List<Milestone> DEFAULT_MILESTONES = List.of(
            new Milestone(0,  "WELCOME"),
            new Milestone(3,  "LOANS_TIP"),
            new Milestone(7,  "COMPARE_TIP"),
            new Milestone(14, "DIVERSIFY_TIP"),
            new Milestone(30, "LOYALTY")
    );

    private final AutoTune plugin;
    private final DatabaseManager databaseManager;
    private final PlayerRepository playerRepository;
    private final PendingNotificationRepository notificationRepository;
    private final ConfigManager configManager;

    /** Task handle for the periodic check — null when disabled */
    private ScheduledTask checkTask;

    /** Tracks players we've already checked this session to avoid duplicate work */
    private final CopyOnWriteArrayList<UUID> checkedThisCycle = new CopyOnWriteArrayList<>();

    @Inject
    public PlayerOnboardingService(
            AutoTune plugin,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository,
            PendingNotificationRepository notificationRepository,
            ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.playerRepository = playerRepository;
        this.notificationRepository = notificationRepository;
        this.configManager = configManager;
    }

    /**
     * Returns the active milestones from config, sorted by day offset ascending.
     * Falls back to DEFAULT_MILESTONES if config is absent or disabled.
     */
    private List<Milestone> activeMilestones() {
        AutoTuneConfig.OnboardingConfig cfg = configManager.getConfig().onboarding();
        if (!cfg.enabled()) {
            return List.of();
        }
        return cfg.milestones().stream()
                .filter(AutoTuneConfig.OnboardingMilestoneConfig::enabled)
                .sorted(java.util.Comparator.comparingInt(AutoTuneConfig.OnboardingMilestoneConfig::dayOffset))
                .map(m -> new Milestone(m.dayOffset(), m.category()))
                .toList();
    }

    /**
     * Starts the periodic onboarding check task.
     * Runs once every 6 hours — milestone tracking doesn't need real-time resolution.
     */
    public void start() {
        if (!configManager.getConfig().onboarding().enabled()) {
            log.info("[Auto-Tune] Player onboarding service disabled (onboarding.enabled=false).");
            return;
        }
        int intervalHours = configManager.getConfig().onboarding().checkIntervalHours();
        // Schedule first check 30 seconds after startup (let economy settle)
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            runMilestoneCheck();
            // Then run at configured interval
            checkTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    ignored -> runMilestoneCheck(),
                    intervalHours * 60L * 60L * 1000L,
                    intervalHours * 60L * 60L * 1000L,
                    java.util.concurrent.TimeUnit.MILLISECONDS
            );
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
        log.info("[Auto-Tune] Player onboarding service started (check every " + intervalHours + "h).");
    }

    /** Cancels the periodic task. Called on shutdown and reload. */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Restarts the periodic check with the current config.
     * Call this after a config reload to pick up new check-interval or milestone settings.
     */
    public void restart() {
        shutdown();
        start();
    }

    /**
     * Called by the scheduled task. Scans all players and sends milestone messages
     * to those who have crossed a threshold since the last check.
     */
    private void runMilestoneCheck() {
        try {
            databaseManager.supplyAsync(() -> playerRepository.findAllForOnboarding())
                    .thenAccept(players -> {
                        Instant now = Instant.now();
                        for (java.util.Map.Entry<UUID, PlayerRepository.OnboardingPlayerInfo> entry : players.entrySet()) {
                            PlayerRepository.OnboardingPlayerInfo info = entry.getValue();
                            evaluateMilestones(entry.getKey(), info.firstSeen(), info.lastOnboardingMilestoneSent(), now);
                        }
                        checkedThisCycle.clear();
                    })
                    .exceptionally(ex -> {
                        log.log(Level.WARNING, "Failed to run onboarding milestone check", ex);
                        return null;
                    });
        } catch (Exception e) {
            log.log(Level.WARNING, "Error in onboarding milestone check", e);
        }
    }

    /**
     * Evaluates all milestones for a player and queues any that are newly due.
     * Messages are queued as pending notifications (delivered on next login).
     *
     * @param uuid        player's UUID
     * @param firstSeen   when the player first joined the server
     * @param lastSent    the highest milestone day-offset already sent (0 = none sent yet)
     * @param now         current timestamp
     */
    void evaluateMilestones(UUID uuid, Instant firstSeen, int lastSent, Instant now) {
        if (!configManager.getConfig().onboarding().enabled()) return;
        long daysSinceFirstSeen = ChronoUnit.DAYS.between(firstSeen, now);
        if (daysSinceFirstSeen < 0) return; // clock drift guard

        for (Milestone milestone : activeMilestones()) {
            if (milestone.dayOffset() < lastSent) continue; // already sent
            if (daysSinceFirstSeen < milestone.dayOffset()) break; // not yet due

            String message = buildMilestoneMessage(milestone, daysSinceFirstSeen);
            if (message != null) {
                notificationRepository.insert(uuid, message, milestone.category());
                // Mark this milestone as sent
                markMilestoneSent(uuid, milestone.dayOffset());
            }
        }
    }

    /**
     * Builds the formatted message for a milestone.
     * Uses MiniMessage format for colors and formatting.
     *
     * @param milestone        the milestone being triggered
     * @param daysOnServer     player's current days-on-server (for personalization)
     * @return the formatted message string, or null if disabled
     */
    private String buildMilestoneMessage(Milestone milestone, long daysOnServer) {
        // Look up the message template from config
        AutoTuneConfig.OnboardingConfig cfg = configManager.getConfig().onboarding();
        return cfg.milestones().stream()
                .filter(m -> m.category().equals(milestone.category()))
                .findFirst()
                .map(m -> {
                    String template = m.message();
                    if (template == null || template.isBlank()) return null;
                    // Replace %d with days, %s with plural suffix
                    String s = daysOnServer == 1 ? "" : "s";
                    return template
                            .replace("%d", String.valueOf(daysOnServer))
                            .replace("%s", s);
                })
                .orElse(null);
    }

    /**
     * Persist the highest milestone sent so we never re-send it.
     *
     * @param uuid           player's UUID
     * @param milestoneDay   the day-offset of the milestone that was just sent
     */
    private void markMilestoneSent(UUID uuid, int milestoneDay) {
        databaseManager.runAsync(() -> playerRepository.updateOnboardingMilestone(uuid, milestoneDay))
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to persist onboarding milestone " + milestoneDay + " for " + uuid, ex);
                    return null;
                });
    }
}