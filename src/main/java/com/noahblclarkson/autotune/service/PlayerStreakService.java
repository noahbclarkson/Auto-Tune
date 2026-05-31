package com.noahblclarkson.autotune.service;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.model.BadgeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks consecutive-day trading streaks for players.
 *
 * <p>A "streak day" is any UTC day where the player completes at least one
 * transaction (buy or sell). The streak resets to 0 if a UTC day passes with
 * no trading activity. Players earn streak badges at 3, 7, 14, and 30 days.</p>
 *
 * <p>This service is idempotent — recording a trade for a player who already
 * traded today is a no-op for streak purposes.</p>
 */
@SuppressWarnings("PMD")
public class PlayerStreakService {

    private static final Logger log = Logger.getLogger(PlayerStreakService.class.getName());
    private final AutoTune plugin;
    private final DatabaseManager databaseManager;
    private final BadgeService badgeService;

    public PlayerStreakService(AutoTune plugin, DatabaseManager databaseManager,
                               BadgeService badgeService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.badgeService = badgeService;
    }

    /**
     * Called after a transaction is recorded. Updates the player's streak
     * if this is their first trade of the UTC day.
     *
     * @param playerUuid the player who traded
     */
    public void onTransaction(UUID playerUuid) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        databaseManager.runAsync(() -> {
            try {
                StreakData existing = loadStreak(playerUuid);

                if (existing == null) {
                    // First-ever streak entry
                    int newStreak = 1;
                    saveStreak(playerUuid, newStreak, Math.max(newStreak, 1), today);
                    checkAndAwardBadges(playerUuid, newStreak);
                    return;
                }

                if (existing.lastTradeDate.equals(today)) {
                    // Already traded today — no change
                    return;
                }

                LocalDate yesterday = today.minusDays(1);
                int newStreak;
                if (existing.lastTradeDate.equals(yesterday)) {
                    // Consecutive day
                    newStreak = existing.currentStreak + 1;
                } else {
                    // Gap — reset
                    newStreak = 1;
                }

                int bestStreak = Math.max(newStreak, existing.bestStreak);
                saveStreak(playerUuid, newStreak, bestStreak, today);
                checkAndAwardBadges(playerUuid, newStreak);

                // Notify player about streak progress
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    notifyStreakProgress(player, newStreak, bestStreak);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to update streak for " + playerUuid, e);
            }
        });
    }

    /**
     * Gets the current streak data for a player. Returns null if no streak exists.
     */
    public CompletableFuture<StreakData> getStreak(UUID playerUuid) {
        return databaseManager.supplyAsync(() -> loadStreak(playerUuid));
    }

    /**
     * Gets the streak data, creating a default (0-day) entry if none exists.
     */
    public StreakData getStreakOrDefault(UUID playerUuid) {
        StreakData data = loadStreak(playerUuid);
        if (data == null) {
            return new StreakData(0, 0, null);
        }
        return data;
    }

    /**
     * Sends the server's top streak leaderboard to a command sender.
     */
    public void sendTopStreaks(CommandSender sender) {
        databaseManager.supplyAsync(() ->
                databaseManager.getJdbi().withHandle(handle ->
                        handle.createQuery("""
                                SELECT s.player_uuid,
                                       COALESCE(p.username, SUBSTR(s.player_uuid, 1, 8)) AS username,
                                       s.current_streak,
                                       s.best_streak
                                FROM at_player_streaks s
                                LEFT JOIN at_players p ON s.player_uuid = p.uuid
                                ORDER BY s.current_streak DESC
                                LIMIT 10
                                """)
                                .map((rs, ctx) -> new LeaderboardEntry(
                                        rs.getString("username"),
                                        rs.getInt("current_streak"),
                                        rs.getInt("best_streak")
                                ))
                                .list()))
                .orTimeout(3, TimeUnit.SECONDS)
                .thenAcceptAsync(entries -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        renderTopStreaks(sender, entries);
                    });
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to load top streaks", ex);
                    sender.sendMessage(Component.text("Could not load streak leaderboard.",
                            NamedTextColor.RED));
                    return null;
                });
    }

    /**
     * Renders the streak info as a chat component for the player.
     */
    public void sendStreakInfo(Player player) {
        UUID uuid = player.getUniqueId();
        databaseManager.supplyAsync(() -> loadStreak(uuid))
                .orTimeout(3, TimeUnit.SECONDS)
                .thenAcceptAsync(data -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        renderStreakInfo(player, data);
                    });
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to load streak for " + player.getName(), ex);
                    player.sendMessage(Component.text("Could not load streak data.", NamedTextColor.RED));
                    return null;
                });
    }

    // ─── Badge checks ─────────────────────────────────────────────────────

    private void checkAndAwardBadges(UUID playerUuid, int currentStreak) {
        if (currentStreak >= 30) {
            badgeService.award(playerUuid, BadgeType.HOT_STREAK_30);
        }
        if (currentStreak >= 14) {
            badgeService.award(playerUuid, BadgeType.HOT_STREAK_14);
        }
        if (currentStreak >= 7) {
            badgeService.award(playerUuid, BadgeType.HOT_STREAK_7);
        }
        if (currentStreak >= 3) {
            badgeService.award(playerUuid, BadgeType.HOT_STREAK_3);
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────

    private void renderStreakInfo(Player player, StreakData data) {
        Component header = Component.text("═══ ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("🔥 ").color(NamedTextColor.RED))
                .append(Component.text("Trading Streak").color(NamedTextColor.WHITE))
                .append(Component.text(" ═══").color(NamedTextColor.GOLD));
        player.sendMessage(header);

        if (data == null || data.currentStreak == 0) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("  No active streak yet.").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("  Trade something today to start your streak!")
                    .color(NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.text(""));
            return;
        }

        // Fire bar visual
        String fires = fireBar(data.currentStreak);
        player.sendMessage(Component.text("  " + fires).color(NamedTextColor.RED));

        // Current streak
        Component streakLine = Component.text("  Current: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(data.currentStreak + " day" + (data.currentStreak != 1 ? "s" : ""))
                        .color(NamedTextColor.GOLD));
        player.sendMessage(streakLine);

        // Best streak
        if (data.bestStreak > data.currentStreak) {
            Component bestLine = Component.text("  Best: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(data.bestStreak + " days").color(NamedTextColor.YELLOW));
            player.sendMessage(bestLine);
        }

        // Next milestone
        int nextMilestone = nextMilestone(data.currentStreak);
        if (nextMilestone > 0) {
            int remaining = nextMilestone - data.currentStreak;
            Component milestoneLine = Component.text("  Next badge: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(nextMilestone + " days").color(NamedTextColor.AQUA))
                    .append(Component.text(" (" + remaining + " to go)").color(NamedTextColor.DARK_GRAY));
            player.sendMessage(milestoneLine);
        } else {
            Component maxLine = Component.text("  🏆 All streak badges earned!")
                    .color(NamedTextColor.GOLD);
            player.sendMessage(maxLine);
        }

        player.sendMessage(Component.text(""));
    }

    private String fireBar(int streak) {
        int fires = Math.min(streak, 30);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fires; i++) {
            sb.append("🔥");
        }
        return sb.toString();
    }

    private int nextMilestone(int current) {
        if (current < 3) return 3;
        if (current < 7) return 7;
        if (current < 14) return 14;
        if (current < 30) return 30;
        return 0; // all earned
    }

    private void renderTopStreaks(CommandSender sender, List<LeaderboardEntry> entries) {
        Component header = Component.text("═══ ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("🔥 ").color(NamedTextColor.RED))
                .append(Component.text("Streak Leaderboard").color(NamedTextColor.WHITE))
                .append(Component.text(" ═══").color(NamedTextColor.GOLD));
        sender.sendMessage(header);

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("  No streaks recorded yet.").color(NamedTextColor.GRAY));
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            String medal = medalIcon(i);
            Component line = Component.text("  " + medal + " ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(e.username()).color(NamedTextColor.WHITE))
                    .append(Component.text(" — " + e.currentStreak + " day" + (e.currentStreak != 1 ? "s" : ""))
                            .color(NamedTextColor.GOLD))
                    .append(Component.text(" (best: " + e.bestStreak + ")").color(NamedTextColor.DARK_GRAY));
            sender.sendMessage(line);
        }

        sender.sendMessage(Component.text(""));
    }

    private String medalIcon(int rank) {
        return switch (rank) {
            case 0 -> "🥇";
            case 1 -> "🥈";
            case 2 -> "🥉";
            default -> "  " + (rank + 1) + ".";
        };
    }

        private void notifyStreakProgress(Player player, int currentStreak, int bestStreak) {
        // Only send a notification on milestone hits
        if (currentStreak == 3 || currentStreak == 7 || currentStreak == 14 || currentStreak == 30) {
            Component msg = Component.text("🔥 ")
                    .color(NamedTextColor.RED)
                    .append(Component.text(currentStreak + "-day trading streak!").color(NamedTextColor.GOLD));
            player.sendMessage(msg);
        } else if (currentStreak > 1 && currentStreak % 5 == 0) {
            // Subtle check-in every 5 days past 5
            Component msg = Component.text("🔥 ")
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("Streak: " + currentStreak + " days").color(NamedTextColor.GRAY));
            player.sendMessage(msg);
        }
    }

    // ─── Database ─────────────────────────────────────────────────────────

    private StreakData loadStreak(UUID playerUuid) {
        return databaseManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT current_streak, best_streak, last_trade_date
                        FROM at_player_streaks
                        WHERE player_uuid = :uuid
                        """)
                        .bind("uuid", playerUuid.toString())
                        .map((rs, ctx) -> new StreakData(
                                rs.getInt("current_streak"),
                                rs.getInt("best_streak"),
                                rs.getDate("last_trade_date").toLocalDate()
                        ))
                        .findFirst()
                        .orElse(null));
    }

    private void saveStreak(UUID playerUuid, int currentStreak, int bestStreak, LocalDate lastTradeDate) {
        String sql = databaseManager.isSqlite()
                ? """
                        INSERT INTO at_player_streaks (player_uuid, current_streak, best_streak, last_trade_date)
                        VALUES (:uuid, :current, :best, :lastDate)
                        ON CONFLICT(player_uuid) DO UPDATE SET
                            current_streak = :current,
                            best_streak = :best,
                            last_trade_date = :lastDate
                        """
                : """
                        INSERT INTO at_player_streaks (player_uuid, current_streak, best_streak, last_trade_date)
                        VALUES (:uuid, :current, :best, :lastDate)
                        ON DUPLICATE KEY UPDATE
                            current_streak = VALUES(current_streak),
                            best_streak = VALUES(best_streak),
                            last_trade_date = VALUES(last_trade_date)
                        """;
        databaseManager.getJdbi().useHandle(handle ->
                handle.createUpdate(sql)
                        .bind("uuid", playerUuid.toString())
                        .bind("current", currentStreak)
                        .bind("best", bestStreak)
                        .bind("lastDate", Date.valueOf(lastTradeDate))
                        .execute());
    }

    /**
     * Streak data for a player.
     *
     * @param currentStreak consecutive days of trading (including today if traded)
     * @param bestStreak    all-time best streak
     * @param lastTradeDate the last UTC date the player traded (null if no streak)
     */
    public record StreakData(int currentStreak, int bestStreak, LocalDate lastTradeDate) {
    }

    /** Leaderboard entry for /streak top. */
    record LeaderboardEntry(String username, int currentStreak, int bestStreak) {
    }
}
