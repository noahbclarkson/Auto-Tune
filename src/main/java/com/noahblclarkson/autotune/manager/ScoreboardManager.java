package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player economy scoreboard showing live market stats on the sidebar.
 *
 * Each player gets their own scoreboard (Bukkit ScoreboardManager) to avoid
 * team-name conflicts between players.
 *
 * Displayed stats (always shown, in order):
 *   GDP       — 24h trade volume (compact: $1.2M, $450K, $123)
 *   Debt      — total outstanding loan principal
 *   Loans     — active loan count
 *   Activity  — trade volume indicator (High / Norm / Low)
 *   Inflation — price change direction (▲ High / ▼ Defl / ● Stab)
 *
 * Scoreboard title and update interval are configurable via
 * config.yml → scoreboard: { enabled, title, update-interval-seconds }.
 * Default: disabled. Enable by setting scoreboard.enabled: true.
 */
@Singleton
public class ScoreboardManager {

    private static final Logger LOGGER = Logger.getLogger(ScoreboardManager.class.getName());
    private static final String OBJECTIVE_NAME = "autotune-economy";

    // Unique identifiers for each scoreboard entry
    private static final String ENTRY_GDP      = "at_gdp";
    private static final String ENTRY_DEBT     = "at_debt";
    private static final String ENTRY_LOANS    = "at_loans";
    private static final String ENTRY_ACTIVITY = "at_activity";
    private static final String ENTRY_INFLATION = "at_inflation";

    private final AutoTune plugin;
    private final EconomySnapshotRepository snapshotRepository;
    private final AutoTuneConfig.ScoreboardConfig config;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private BukkitTask updateTask;

    @Inject
    public ScoreboardManager(
            AutoTune plugin,
            EconomySnapshotRepository snapshotRepository,
            AutoTuneConfig autoTuneConfig
    ) {
        this.plugin = plugin;
        this.snapshotRepository = snapshotRepository;
        this.config = autoTuneConfig.scoreboard();
    }

    public void start() {
        if (!config.enabled()) {
            LOGGER.info("Scoreboard disabled in config.");
            return;
        }

        int ticks = (int) Math.max(10L, config.updateIntervalSeconds() * 20L);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllScoreboards, 40L, ticks);
        LOGGER.info("ScoreboardManager started (interval: " + config.updateIntervalSeconds() + "s).");
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        for (Scoreboard sb : playerScoreboards.values()) {
            for (Objective obj : sb.getObjectives()) {
                obj.unregister();
            }
        }
        playerScoreboards.clear();
        LOGGER.info("ScoreboardManager stopped.");
    }

    /**
     * Show the economy scoreboard to a player. Called on PlayerJoin.
     */
    public void showScoreboard(Player player) {
        if (!config.enabled()) {
            return;
        }

        try {
            org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) {
                LOGGER.warning("ScoreboardManager unavailable on this server.");
                return;
            }

            Scoreboard scoreboard = manager.getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), scoreboard);

            // Register the sidebar objective using Criteria.DUMMY (non-deprecated)
            Objective objective = scoreboard.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    Component.text(truncate(config.title(), 32), NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            // Register a team per entry for colored prefixes
            registerTeam(scoreboard, ENTRY_GDP,       NamedTextColor.WHITE);
            registerTeam(scoreboard, ENTRY_DEBT,      NamedTextColor.RED);
            registerTeam(scoreboard, ENTRY_LOANS,      NamedTextColor.YELLOW);
            registerTeam(scoreboard, ENTRY_ACTIVITY,   NamedTextColor.AQUA);
            registerTeam(scoreboard, ENTRY_INFLATION,  NamedTextColor.GREEN);

            // Set initial placeholder entries so they appear in the correct order
            setEntry(objective, scoreboard, ENTRY_GDP,       "Loading GDP...",     NamedTextColor.WHITE);
            setEntry(objective, scoreboard, ENTRY_DEBT,      "Loading Debt...",    NamedTextColor.RED);
            setEntry(objective, scoreboard, ENTRY_LOANS,     "Loading Loans...",   NamedTextColor.YELLOW);
            setEntry(objective, scoreboard, ENTRY_ACTIVITY,  "Loading...",        NamedTextColor.AQUA);
            setEntry(objective, scoreboard, ENTRY_INFLATION, "Loading...",        NamedTextColor.GREEN);

            player.setScoreboard(scoreboard);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to show scoreboard to " + player.getName(), e);
        }
    }

    /**
     * Remove a player's scoreboard and reset them to the main scoreboard. Called on PlayerQuit.
     */
    public void hideScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard sb = playerScoreboards.remove(uuid);
        if (sb != null) {
            for (Objective obj : sb.getObjectives()) {
                obj.unregister();
            }
        }

        if (player.isOnline()) {
            try {
                org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
                if (manager != null) {
                    player.setScoreboard(manager.getMainScoreboard());
                }
            } catch (Exception ignored) {}
        }
    }

    private void updateAllScoreboards() {
        Optional<EconomySnapshot> snap = snapshotRepository.findLatest();

        // Prune disconnected players
        Iterator<Map.Entry<UUID, Scoreboard>> it = playerScoreboards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Scoreboard> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                // Unregister this player's scoreboard
                for (Objective obj : entry.getValue().getObjectives()) {
                    obj.unregister();
                }
                it.remove();
            }
        }

        if (playerScoreboards.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Scoreboard> entry : playerScoreboards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                updatePlayerScoreboard(entry.getValue(), snap);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to update scoreboard for " + player.getName(), e);
            }
        }
    }

    private void updatePlayerScoreboard(Scoreboard scoreboard, Optional<EconomySnapshot> snap) {
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }

        if (snap.isEmpty()) {
            setEntry(objective, scoreboard, ENTRY_GDP,      "GDP: No data",   NamedTextColor.WHITE);
            setEntry(objective, scoreboard, ENTRY_DEBT,     "Debt: No data",  NamedTextColor.RED);
            setEntry(objective, scoreboard, ENTRY_LOANS,    "Loans: --",      NamedTextColor.YELLOW);
            setEntry(objective, scoreboard, ENTRY_ACTIVITY, "Activity: --",   NamedTextColor.AQUA);
            setEntry(objective, scoreboard, ENTRY_INFLATION,"Inflation: --",  NamedTextColor.GREEN);
            return;
        }

        EconomySnapshot s = snap.get();

        // GDP — 24h trade volume
        setEntry(objective, scoreboard, ENTRY_GDP,
                "GDP: " + formatCompact(s.gdp()), NamedTextColor.WHITE);

        // Debt — total outstanding principal
        boolean hasDebt = s.totalDebt().compareTo(BigDecimal.ZERO) > 0;
        String debtStr = hasDebt ? "Debt: " + formatCompact(s.totalDebt()) : "Debt: $0";
        setEntry(objective, scoreboard, ENTRY_DEBT, debtStr,
                hasDebt ? NamedTextColor.RED : NamedTextColor.GRAY);

        // Active loans
        setEntry(objective, scoreboard, ENTRY_LOANS,
                "Loans: " + s.activeLoans(), NamedTextColor.YELLOW);

        // Volume activity
        setEntry(objective, scoreboard, ENTRY_ACTIVITY,
                "Activity: " + formatActivity(s.transactionVolume()), NamedTextColor.AQUA);

        // Inflation
        int infLevel = getInflationLevel(s.averagePriceChange());
        String infLabel = formatInflation(s.averagePriceChange());
        setEntry(objective, scoreboard, ENTRY_INFLATION,
                "Inflation: " + infLabel, getInflationColor(infLevel));
    }

    // ─── Scoreboard API helpers ─────────────────────────────────────────────────

    private void registerTeam(Scoreboard scoreboard, String entry, NamedTextColor color) {
        Team team = scoreboard.registerNewTeam(entry);
        team.addEntry(entry);
        team.prefix(Component.text("", color));
    }

    private void setEntry(Objective objective, Scoreboard scoreboard,
                          String entry, String displayText, NamedTextColor color) {
        Team team = scoreboard.getTeam(entry);
        if (team != null) {
            team.prefix(Component.text(displayText, color));
        }
        objective.getScore(entry).setScore(0);
    }

    // ─── Formatting helpers ────────────────────────────────────────────────────

    private String formatCompact(BigDecimal value) {
        if (value == null) return "$0";
        double v = value.doubleValue();
        if (v >= 1_000_000_000) return String.format("$%.1fB", v / 1_000_000_000);
        if (v >= 1_000_000)     return String.format("$%.1fM", v / 1_000_000);
        if (v >= 1_000)         return String.format("$%.1fK", v / 1_000);
        return String.format("$%.0f", v);
    }

    private String formatActivity(BigDecimal volume) {
        if (volume == null) return "Low";
        double v = volume.doubleValue();
        if (v > 100_000) return "High";
        if (v > 10_000)  return "Norm";
        return "Low";
    }

    private String formatInflation(BigDecimal avgChange) {
        if (avgChange == null) return "N/A";
        double pct = avgChange.doubleValue() * 100;
        if (pct > 1.0)  return "\u25B2 High";
        if (pct < -1.0) return "\u25BC Defl";
        return "\u25CF Stab";
    }

    private int getInflationLevel(BigDecimal avgChange) {
        if (avgChange == null) return 0;
        double pct = avgChange.doubleValue() * 100;
        if (pct > 1.0)  return 1;
        if (pct < -1.0) return -1;
        return 0;
    }

    private NamedTextColor getInflationColor(int level) {
        return switch (level) {
            case 1  -> NamedTextColor.RED;
            case -1 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GREEN;
        };
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
