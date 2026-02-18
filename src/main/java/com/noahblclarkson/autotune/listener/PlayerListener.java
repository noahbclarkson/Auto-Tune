package com.noahblclarkson.autotune.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
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

    @Inject
    public PlayerListener(
            ConfigManager configManager,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository,
            LoanManager loanManager,
            AutosellManager autosellManager
    ) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerRepository = playerRepository;
        this.loanManager = loanManager;
        this.autosellManager = autosellManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        autosellManager.loadPlayer(player);
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
        autosellManager.unloadPlayer(event.getPlayer().getUniqueId());
        databaseManager.runAsync(() -> playerRepository.updateLastSeen(event.getPlayer().getUniqueId()));
    }

    private void checkLoanWarning(Player player, Optional<Loan> activeLoan) {
        if (activeLoan.isEmpty()) {
            return;
        }

        Loan loan = activeLoan.get();
        Duration timeUntilDue = Duration.between(Instant.now(), loan.dueDate());

        if (timeUntilDue.isNegative()) {
            player.sendMessage(configManager.getMessage("loan.defaulted",
                    Map.of("penalty", String.valueOf(configManager.getConfig().loans().defaultPenalty()))));
        } else if (timeUntilDue.toDays() <= 3) {
            player.sendMessage(configManager.getMessage("loan.warning-due", Map.of(
                    "amount", configManager.formatCurrency(loan.currentBalance()),
                    "days", String.valueOf(timeUntilDue.toDays())
            )));
        }
    }
}
