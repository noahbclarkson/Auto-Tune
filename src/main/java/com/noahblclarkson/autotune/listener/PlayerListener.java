package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ScoreboardManager;
import com.noahblclarkson.autotune.model.Loan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class PlayerListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PlayerListener.class.getName());

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerRepository playerRepository;
    private final LoanManager loanManager;
    private final AutosellManager autosellManager;
    private final ScoreboardManager scoreboardManager;

    @Inject
    public PlayerListener(
            ConfigManager configManager,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository,
            LoanManager loanManager,
            AutosellManager autosellManager,
            ScoreboardManager scoreboardManager
    ) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerRepository = playerRepository;
        this.loanManager = loanManager;
        this.autosellManager = autosellManager;
        this.scoreboardManager = scoreboardManager;
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
            return loanManager.getActiveLoan(playerId);
        }).thenAccept(activeLoan -> databaseManager.runOnMain(() ->
                checkLoanWarning(player, activeLoan)))
                .exceptionally(ex -> {
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
