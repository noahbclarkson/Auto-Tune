package com.noahblclarkson.autotune.command;

import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import com.google.inject.Inject;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.service.EconomicNewsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class AdminRecoveryCommand {

    private final LoanManager loanManager;
    private final EconomicNewsService newsService;

    @Inject
    public AdminRecoveryCommand(LoanManager loanManager, EconomicNewsService newsService) {
        this.loanManager = loanManager;
        this.newsService = newsService;
    }

    @Command("at admin recovery start")
    @Permission("autotune.admin")
    public void startRecovery(CommandSender sender) {
        // Lock the circuit breaker manually
        loanManager.setTier3CircuitLocked(true); 
        newsService.broadcastToAll(ChatColor.RED + "⚠ " + ChatColor.BOLD + "Economy Recovery Mode Enabled" + ChatColor.RESET + ChatColor.RED + " - Loan interest frozen at 0% and issuance paused.");
        if (sender instanceof Player player) {
            player.sendMessage(ChatColor.GREEN + "Recovery mode activated. The economy is now frozen (loans paused, interest at 0%).");
        }
    }

    @Command("at admin recovery stop")
    @Permission("autotune.admin")
    public void stopRecovery(CommandSender sender) {
        loanManager.setTier3CircuitLocked(false);
        newsService.broadcastToAll(ChatColor.GREEN + "✅ Economy Recovery Mode Disabled - Markets have returned to normal operation.");
        if (sender instanceof Player player) {
            player.sendMessage(ChatColor.GREEN + "Recovery mode deactivated. The economy has returned to normal.");
        }
    }
}
