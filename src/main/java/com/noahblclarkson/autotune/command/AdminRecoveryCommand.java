package com.noahblclarkson.autotune.command;

import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import com.google.inject.Inject;
import com.noahblclarkson.autotune.economy.LoanManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class AdminRecoveryCommand {

    private final LoanManager loanManager;

    @Inject
    public AdminRecoveryCommand(LoanManager loanManager) {
        this.loanManager = loanManager;
    }

    @Command("at|autotune admin recovery start")
    @Permission("autotune.admin")
    public void startRecovery(CommandSender sender) {
        if (loanManager.isManualRecoveryMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Recovery mode is already active.");
            return;
        }
        loanManager.setManualRecoveryMode(true);
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "Economy Recovery Mode Enabled" + ChatColor.RESET + ChatColor.RED + " - Loan interest frozen at 0% and issuance paused.");
        sender.sendMessage(ChatColor.GREEN + "Recovery mode activated. New loans are paused and interest is frozen at 0% until you run /at admin recovery stop.");
    }

    @Command("at|autotune admin recovery stop")
    @Permission("autotune.admin")
    public void stopRecovery(CommandSender sender) {
        if (!loanManager.isManualRecoveryMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Recovery mode is not currently active.");
            return;
        }
        loanManager.setManualRecoveryMode(false);
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Economy Recovery Mode Disabled - Markets have returned to normal operation.");
        sender.sendMessage(ChatColor.GREEN + "Recovery mode deactivated. Loan issuance and interest processing have returned to automatic control.");
    }
}
