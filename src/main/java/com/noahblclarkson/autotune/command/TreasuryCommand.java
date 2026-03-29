package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.math.BigDecimal;

@Singleton
public class TreasuryCommand {

    private final TreasuryService treasuryService;
    private final ConfigManager configManager;

    @Inject
    public TreasuryCommand(TreasuryService treasuryService, ConfigManager configManager) {
        this.treasuryService = treasuryService;
        this.configManager = configManager;
    }

    @Command("treasury")
    @Permission("autotune.admin")
    public void treasuryHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Treasury", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — Server tax accumulator", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/treasury balance", NamedTextColor.YELLOW)
                .append(Component.text(" — View current treasury balance", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/treasury deposit <amount>", NamedTextColor.YELLOW)
                .append(Component.text(" — Add to treasury", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/treasury withdraw <amount>", NamedTextColor.YELLOW)
                .append(Component.text(" — Withdraw from treasury", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/treasury status", NamedTextColor.YELLOW)
                .append(Component.text(" — Tax rates and enabled state", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("treasury balance")
    @Permission("autotune.admin")
    public void treasuryBalance(CommandSender sender) {
        BigDecimal balance = treasuryService.getBalance();
        sender.sendMessage(Component.text("Treasury Balance: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(configManager.formatCurrency(balance), NamedTextColor.WHITE)));
    }

    @Command("treasury deposit <amount>")
    @Permission("autotune.admin")
    public void treasuryDeposit(CommandSender sender,
                                @Argument("amount") double amount) {
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be positive", NamedTextColor.RED));
            return;
        }

        BigDecimal amountBd = BigDecimal.valueOf(amount);
        treasuryService.deposit(amountBd);

        BigDecimal newBalance = treasuryService.getBalance();
        sender.sendMessage(Component.text("Deposited " + configManager.formatCurrency(amountBd)
                + " to treasury. New balance: " + configManager.formatCurrency(newBalance), NamedTextColor.GREEN));
    }

    @Command("treasury withdraw <amount>")
    @Permission("autotune.admin")
    public void treasuryWithdraw(CommandSender sender,
                                 @Argument("amount") double amount) {
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be positive", NamedTextColor.RED));
            return;
        }

        BigDecimal amountBd = BigDecimal.valueOf(amount);
        BigDecimal withdrawn = treasuryService.withdraw(amountBd);
        BigDecimal newBalance = treasuryService.getBalance();

        if (withdrawn.compareTo(amountBd) < 0) {
            sender.sendMessage(Component.text("Treasury had insufficient funds. Withdrew "
                    + configManager.formatCurrency(withdrawn) + " instead. New balance: "
                    + configManager.formatCurrency(newBalance), NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Withdrew " + configManager.formatCurrency(withdrawn)
                    + " from treasury. New balance: " + configManager.formatCurrency(newBalance), NamedTextColor.GREEN));
        }
    }

    @Command("treasury status")
    @Permission("autotune.admin")
    public void treasuryStatus(CommandSender sender) {
        var tax = configManager.getConfig().tax();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Treasury Status", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Tax system: ", NamedTextColor.GRAY)
                .append(tax.enabled()
                        ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED)));
        sender.sendMessage(Component.text("Current balance: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(treasuryService.getBalance()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Tax rates:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Buy tax:       ", NamedTextColor.GRAY)
                .append(Component.text(formatPercent(tax.buyTaxPercent()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Sell tax:      ", NamedTextColor.GRAY)
                .append(Component.text(formatPercent(tax.sellTaxPercent()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Auction tax:   ", NamedTextColor.GRAY)
                .append(Component.text(formatPercent(tax.auctionTaxPercent()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Loan interest: ", NamedTextColor.GRAY)
                .append(Component.text(formatPercent(tax.loanInterestTaxPercent()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Set rates in config.yml under 'tax' section.", NamedTextColor.DARK_GRAY));
    }

    private String formatPercent(double percent) {
        if (percent == 0) return "0%";
        return String.format("%.2f%%", percent);
    }
}
