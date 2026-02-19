package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.economy.LoanManager;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class LoanCommand {

    private static final Logger LOGGER = Logger.getLogger(LoanCommand.class.getName());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final LoanManager loanManager;

    @Inject
    public LoanCommand(
            ConfigManager configManager,
            DatabaseManager databaseManager,
            EconomyManager economyManager,
            LoanManager loanManager
    ) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
        this.loanManager = loanManager;
    }

    @Command("loan")
    @Permission("autotune.loan")
    public void loanHelp(CommandSender sender) {
        AutoTuneConfig.LoanConfig config = configManager.getConfig().loans();
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Loan Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/loan take <amount> [days]", NamedTextColor.YELLOW)
                .append(Component.text(" - Take out a loan (" + config.minTermDays() + "-" + config.maxTermDays() + " day terms)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/loan repay <amount>", NamedTextColor.YELLOW)
                .append(Component.text(" - Repay your active loan", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/loan info", NamedTextColor.YELLOW)
                .append(Component.text(" - View your loan and credit info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("loan take")
    @Permission("autotune.loan")
    public void takeLoanNoArgs(CommandSender sender) {
        AutoTuneConfig.LoanConfig config = configManager.getConfig().loans();
        sender.sendMessage(Component.text("Usage: /loan take <amount> [days]", NamedTextColor.YELLOW)
                .append(Component.text(" (default: " + config.defaultDurationDays() + " days)", NamedTextColor.GRAY)));
    }

    @Command("loan take <amount>")
    @Permission("autotune.loan")
    public void takeLoan(CommandSender sender, @Argument("amount") double amount) {
        int defaultDays = configManager.getConfig().loans().defaultDurationDays();
        takeLoanWithTerm(sender, amount, defaultDays);
    }

    @Command("loan take <amount> <days>")
    @Permission("autotune.loan")
    public void takeLoanWithDays(CommandSender sender, @Argument("amount") double amount, @Argument("days") int days) {
        takeLoanWithTerm(sender, amount, days);
    }

    private void takeLoanWithTerm(CommandSender sender, double amount, int days) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(configManager.getMessage("general.invalid-amount"));
            return;
        }

        AutoTuneConfig.LoanConfig config = configManager.getConfig().loans();
        int clampedDays = Math.max(config.minTermDays(), Math.min(days, config.maxTermDays()));

        loanManager.requestLoanAsync(player, BigDecimal.valueOf(amount), clampedDays).thenAccept(result ->
                databaseManager.runOnMain(() -> {
                    if (result == null) {
                        player.sendMessage(configManager.getMessage("error.database"));
                        return;
                    }
                    if (result.success()) {
                        Loan loan = result.loan();
                        player.sendMessage(configManager.getMessage("loan.taken", Map.of(
                                "amount", configManager.formatCurrency(loan.principal()),
                                "rate", loan.interestRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%",
                                "term", String.valueOf(clampedDays),
                                "due_date", formatDate(loan.dueDate())
                        )));
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                    }
                })).exceptionally(ex -> {
            LOGGER.log(Level.WARNING, "Failed to process loan request", ex);
            return null;
        });
    }

    @Command("loan repay")
    @Permission("autotune.loan")
    public void repayLoanNoArgs(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /loan repay <amount>", NamedTextColor.YELLOW));
    }

    @Command("loan repay <amount>")
    @Permission("autotune.loan")
    public void repayLoan(CommandSender sender, @Argument("amount") double amount) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(configManager.getMessage("general.invalid-amount"));
            return;
        }

        loanManager.repayLoanAsync(player, BigDecimal.valueOf(amount)).thenAccept(result ->
                databaseManager.runOnMain(() -> {
                    if (result == null) {
                        player.sendMessage(configManager.getMessage("error.database"));
                        return;
                    }
                    if (result.success()) {
                        if (result.loan().status() == Loan.LoanStatus.PAID) {
                            player.sendMessage(configManager.getMessage("loan.repaid"));
                        } else {
                            player.sendMessage(configManager.getMessage("loan.partial-repay", Map.of(
                                    "amount", configManager.formatCurrency(result.amount()),
                                    "remaining", configManager.formatCurrency(result.loan().currentBalance())
                            )));
                        }
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                    }
                })).exceptionally(ex -> {
            LOGGER.log(Level.WARNING, "Failed to process loan repayment", ex);
            return null;
        });
    }

    @Command("loan info")
    @Permission("autotune.loan")
    public void loanInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        CompletableFuture<Optional<Loan>> loanFuture = loanManager.getActiveLoanAsync(player.getUniqueId());
        CompletableFuture<PlayerData> playerDataFuture = economyManager.getPlayerDataAsync(player);
        CompletableFuture<BigDecimal> maxLoanFuture = loanManager.getMaxLoanAmountAsync(player.getUniqueId());

        CompletableFuture.allOf(loanFuture, playerDataFuture, maxLoanFuture).thenRun(() ->
                databaseManager.runOnMain(() -> {
                    Optional<Loan> activeLoan = loanFuture.join();
                    PlayerData playerData = playerDataFuture.join();
                    BigDecimal maxLoan = maxLoanFuture.join();

                    if (playerData == null || maxLoan == null || activeLoan == null) {
                        player.sendMessage(configManager.getMessage("error.database"));
                        return;
                    }

                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("Loan Info", NamedTextColor.GOLD, TextDecoration.BOLD));
                    player.sendMessage(configManager.getMessage("loan.credit-score",
                            Map.of("score", String.valueOf(playerData.creditScore()))));

                    if (activeLoan.isPresent()) {
                        Loan loan = activeLoan.get();
                        BigDecimal interestAccrued = loan.currentBalance().subtract(loan.principal()).max(BigDecimal.ZERO);
                        BigDecimal amortizationPayment = loanManager.getAmortizationPayment(loan);

                        long totalTermDays = Duration.between(loan.createdAt(), loan.dueDate()).toDays();
                        long daysRemaining = Math.max(0, Duration.between(Instant.now(), loan.dueDate()).toDays());

                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Active Loan", NamedTextColor.GOLD));
                        player.sendMessage(Component.text("  Principal: ", NamedTextColor.GRAY)
                                .append(Component.text(configManager.formatCurrency(loan.principal()), NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("  Balance: ", NamedTextColor.GRAY)
                                .append(Component.text(configManager.formatCurrency(loan.currentBalance()), NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("  Interest Accrued: ", NamedTextColor.GRAY)
                                .append(Component.text(configManager.formatCurrency(interestAccrued), NamedTextColor.YELLOW)));
                        player.sendMessage(Component.text("  Interest Rate: ", NamedTextColor.GRAY)
                                .append(Component.text(loan.interestRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%", NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("  Term: ", NamedTextColor.GRAY)
                                .append(Component.text(totalTermDays + " days (" + daysRemaining + " remaining)", NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("  Suggested Payment: ", NamedTextColor.GRAY)
                                .append(Component.text(configManager.formatCurrency(amortizationPayment) + "/period", NamedTextColor.AQUA)));
                        player.sendMessage(Component.text("  Due: ", NamedTextColor.GRAY)
                                .append(Component.text(formatDate(loan.dueDate()), loan.isOverdue() ? NamedTextColor.RED : NamedTextColor.WHITE)));
                        if (loan.isOverdue()) {
                            player.sendMessage(Component.text("  OVERDUE!", NamedTextColor.RED, TextDecoration.BOLD));
                        }
                    } else {
                        AutoTuneConfig.LoanConfig config = configManager.getConfig().loans();
                        player.sendMessage(Component.text("  No active loan", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("  Max loan: ", NamedTextColor.GRAY)
                                .append(Component.text(configManager.formatCurrency(maxLoan), NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("  Terms available: ", NamedTextColor.GRAY)
                                .append(Component.text(config.minTermDays() + "-" + config.maxTermDays() + " days", NamedTextColor.WHITE)));

                        BigDecimal rateMin = loanManager.getInterestRate(player.getUniqueId(), config.minTermDays());
                        BigDecimal rateMax = loanManager.getInterestRate(player.getUniqueId(), config.maxTermDays());
                        player.sendMessage(Component.text("  Your rates: ", NamedTextColor.GRAY)
                                .append(Component.text(
                                        rateMin.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "% (" + config.minTermDays() + "d) - "
                                                + rateMax.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "% (" + config.maxTermDays() + "d)",
                                        NamedTextColor.WHITE)));
                    }
                    player.sendMessage(Component.empty());
                })).exceptionally(ex -> {
            LOGGER.log(Level.WARNING, "Failed to retrieve loan info", ex);
            return null;
        });
    }

    private static String formatDate(Instant instant) {
        return DATE_FORMAT.format(instant);
    }
}
