package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.PendingNotificationRepository;
import com.noahblclarkson.autotune.database.PendingNotificationRepository.PendingNotification;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ScoreboardManager;
import com.noahblclarkson.autotune.model.Loan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@SuppressWarnings("PMD")
public class PlayerListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PlayerListener.class.getName());
    private static final String DEFAULT_GROUP = "default";

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerRepository playerRepository;
    private final LoanManager loanManager;
    private final AutosellManager autosellManager;
    private final ScoreboardManager scoreboardManager;
    private final Permission vaultPerms;
    private final PendingNotificationRepository pendingNotificationRepository;
    private final AutoTune plugin;

    @Inject
    public PlayerListener(
            AutoTune plugin,
            ConfigManager configManager,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository,
            LoanManager loanManager,
            AutosellManager autosellManager,
            ScoreboardManager scoreboardManager,
            Permission vaultPerms,
            PendingNotificationRepository pendingNotificationRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerRepository = playerRepository;
        this.loanManager = loanManager;
        this.autosellManager = autosellManager;
        this.scoreboardManager = scoreboardManager;
        this.vaultPerms = vaultPerms;
        this.pendingNotificationRepository = pendingNotificationRepository;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        autosellManager.loadPlayer(player);
        scoreboardManager.showScoreboard(player);
        java.util.UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        databaseManager.supplyAsync(() -> {
            playerRepository.getOrCreate(playerId, playerName);
            updateGuildTag(player);
            // Fetch and clear pending notifications before returning loan
            List<PendingNotification> pending = pendingNotificationRepository.fetchAndClear(playerId);
            Optional<Loan> activeLoan = loanManager.getActiveLoan(playerId);
            return new PlayerJoinData(activeLoan, pending);
        }).thenAccept(data -> databaseManager.runOnMain(() -> {
            checkLoanWarning(player, data.activeLoan());
            deliverPendingNotifications(player, data.pending());
        })).exceptionally(ex -> {
            LOGGER.log(Level.WARNING, "Failed to process player join", ex);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        autosellManager.unloadPlayer(player.getUniqueId());
        scoreboardManager.hideScoreboard(player);
        var unused = databaseManager.runAsync(() -> playerRepository.updateLastSeen(player.getUniqueId()));
    }

    private void updateGuildTag(Player player) {
        try {
            String[] groups = vaultPerms.getPlayerGroups(player);
            if (groups != null && groups.length > 0) {
                for (String group : groups) {
                    if (group != null && !group.equalsIgnoreCase(DEFAULT_GROUP)) {
                        playerRepository.updateGuildTag(player.getUniqueId(), group);
                        return;
                    }
                }
            }
            // No non-default group found — clear any existing guild tag
            playerRepository.updateGuildTag(player.getUniqueId(), null);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not update guild tag for " + player.getName(), ex);
        }
    }

    /**
     * Delivers pending (queued-while-offline) notifications to a player on login.
     * Sends them with a slight delay so they appear after the login spam clears.
     */
    private void deliverPendingNotifications(Player player, List<PendingNotification> pending) {
        if (pending.isEmpty()) return;

        // Delay delivery by 3 seconds so the message appears after login messages
        long delayTicks = 60L;
        for (int i = 0; i < pending.size(); i++) {
            final PendingNotification notif = pending.get(i);
            final long delay = delayTicks + (i * 10L); // stagger by 0.5s each
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(
                        Component.text("[Auto-Tune] ", NamedTextColor.DARK_AQUA)
                                .append(Component.text(notif.message(), NamedTextColor.YELLOW))
                );
            }, delay);
        }
    }

    /** Bundles async join data for delivery on main thread. */
    private record PlayerJoinData(Optional<Loan> activeLoan, List<PendingNotification> pending) {}

    private void checkLoanWarning(Player player, Optional<Loan> activeLoan) {
        if (activeLoan.isEmpty()) {
            return;
        }

        Loan loan = activeLoan.get();
        Duration timeUntilDue = Duration.between(Instant.now(), loan.dueDate());

        if (timeUntilDue.isNegative()) {
            // Loan is past due but not yet processed by the scheduled job (still ACTIVE).
            // Don't send the "defaulted" message (no credit penalty has been applied yet) —
            // instead warn that the loan is overdue and will be processed on next tick.
            player.sendMessage(configManager.getMessage("loan.overdue",
                    Map.of("amount", configManager.formatCurrency(loan.currentBalance()))));
        } else if (timeUntilDue.toDays() <= 3) {
            // Format time remaining the same way LoanManager.processWarnings() does,
            // using the "time_left" key that messages.yml expects.
            long hoursRemaining = timeUntilDue.toHours();
            String timeLeft = hoursRemaining >= 24
                    ? (hoursRemaining / 24) + " day" + (hoursRemaining / 24 != 1 ? "s" : "")
                    : hoursRemaining + " hour" + (hoursRemaining != 1 ? "s" : "");
            player.sendMessage(configManager.getMessage("loan.warning-due", Map.of(
                    "amount", configManager.formatCurrency(loan.currentBalance()),
                    "time_left", timeLeft
            )));
        }
    }
}
