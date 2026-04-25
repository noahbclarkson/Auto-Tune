package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.database.TransactionRepository.TransactionPeriodAggregate;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server economy leaderboard — top traders by volume and top loan holders by debt.
 * Permission: autotune.admin (same as other admin commands).
 *
 * /at admin top trades [day|week|month|all] [limit]  — top traders by volume
 * /at admin top loans [limit]                       — top active loan holders by balance
 */
public class AdminTopCommand {

    private static final String PERMISSION = "autotune.admin";
    private static final String PERIOD_DEFAULT = "week";
    private static final int LIMIT_DEFAULT = 10;
    private static final int LIMIT_MAX = 50;
    private static final DateTimeFormatter DURATION_FMT = DateTimeFormatter
            .ofPattern("M'd' HH'h'")
            .withZone(ZoneId.of("UTC"));

    private final TransactionRepository transactionRepository;
    private final PlayerRepository playerRepository;
    private final LoanRepository loanRepository;
    private final ItemRepository itemRepository;

    @Inject
    public AdminTopCommand(
            TransactionRepository transactionRepository,
            PlayerRepository playerRepository,
            LoanRepository loanRepository,
            ItemRepository itemRepository) {
        this.transactionRepository = transactionRepository;
        this.playerRepository = playerRepository;
        this.loanRepository = loanRepository;
        this.itemRepository = itemRepository;
    }

    @Command("autotune admin top")
    @Permission(PERMISSION)
    public Component topHelp() {
        return Component.text()
                .append(Component.text("═══ Economy Leaderboard ═══", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("  /at admin top trades [period] [limit]  — top traders by volume", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("  /at admin top loans [limit]            — top loan holders by balance", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("  Period: day | week | month | all", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("  Limit: 1–50 (default 10)", NamedTextColor.DARK_GRAY))
                .build();
    }

    @Command("autotune admin top trades")
    @Permission(PERMISSION)
    public void topTrades(org.bukkit.command.CommandSender sender,
                          @Argument(value = "period", suggestions = "leaderboard-period") @org.incendo.cloud.annotations.Default(PERIOD_DEFAULT) String period,
                          @Argument("limit") @org.incendo.cloud.annotations.Default("10") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, LIMIT_MAX));
        List<TransactionPeriodAggregate> traders = transactionRepository.findTopTradersByPeriod(period, cappedLimit);
        if (traders.isEmpty()) {
            sender.sendMessage(Component.text("No trading activity found for period: " + period, NamedTextColor.YELLOW));
            return;
        }

        String periodLabel = switch (period) {
            case "day" -> "24 hours";
            case "week" -> "7 days";
            case "month" -> "30 days";
            default -> "all time";
        };

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══ Top Traders (" + periodLabel + ") ═══", NamedTextColor.GOLD, TextDecoration.BOLD));

        int rank = 1;
        for (TransactionPeriodAggregate t : traders) {
            String medal = rank == 1 ? "🥇" : (rank == 2 ? "🥈" : (rank == 3 ? "🥉" : "  "));
            long totalVolume = t.totalBought() + t.totalSold();
            double netPosition = t.totalEarned().subtract(t.totalSpent()).doubleValue();
            String netLabel = netPosition >= 0 ? "+" : "";

            Component row = Component.text()
                    .append(Component.text(medal + " #" + rank + " ", rank == 1 ? NamedTextColor.GOLD : (rank == 2 ? NamedTextColor.GRAY : (rank == 3 ? NamedTextColor.RED : NamedTextColor.DARK_GRAY))))
                    .append(Component.text(t.username(), NamedTextColor.AQUA))
                    .append(Component.text("  vol=" + formatVol(totalVolume), NamedTextColor.GRAY))
                    .append(Component.text("  net=" + netLabel + formatMoney(netPosition), netPosition >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text("  (" + t.transactionCount() + " tx)", NamedTextColor.DARK_GRAY))
                    .build();
            sender.sendMessage(row);
            rank++;
        }
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin top loans")
    @Permission(PERMISSION)
    public void topLoans(org.bukkit.command.CommandSender sender,
                         @Argument("limit") @org.incendo.cloud.annotations.Default("10") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, LIMIT_MAX));
        List<Loan> activeLoans = loanRepository.findAllActive();
        if (activeLoans.isEmpty()) {
            sender.sendMessage(Component.text("No active loans in the economy.", NamedTextColor.GREEN));
            return;
        }

        // Aggregate loans by player (sum current balances)
        java.util.Map<UUID, BigDecimal> playerDebt = new java.util.HashMap<>();
        for (Loan loan : activeLoans) {
            playerDebt.merge(loan.playerUuid(), loan.currentBalance(), BigDecimal::add);
        }

        List<java.util.Map.Entry<UUID, BigDecimal>> sorted = new ArrayList<>(playerDebt.entrySet());
        sorted.sort(Comparator.<java.util.Map.Entry<UUID, BigDecimal>> comparingDouble(e -> -e.getValue().doubleValue()));
        int shown = Math.min(cappedLimit, sorted.size());

        BigDecimal totalActiveDebt = activeLoans.stream()
                .map(Loan::currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══ Top Loan Holders ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  Active loans: " + activeLoans.size() + "  |  Total debt: " + formatMoney(totalActiveDebt.doubleValue()), NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());

        for (int i = 0; i < shown; i++) {
            java.util.Map.Entry<UUID, BigDecimal> entry = sorted.get(i);
            String playerName = resolveName(entry.getKey());
            BigDecimal balance = entry.getValue();
            long loanCount = activeLoans.stream()
                    .filter(l -> l.playerUuid().equals(entry.getKey()))
                    .count();

            double pctOfTotal = totalActiveDebt.compareTo(BigDecimal.ZERO) > 0
                    ? balance.divide(totalActiveDebt, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0;

            String medal = i == 0 ? "🥇" : (i == 1 ? "🥈" : (i == 2 ? "🥉" : "  "));
            Component row = Component.text()
                    .append(Component.text(medal + " #" + (i + 1) + " ", i == 0 ? NamedTextColor.GOLD : (i == 1 ? NamedTextColor.GRAY : (i == 2 ? NamedTextColor.RED : NamedTextColor.DARK_GRAY))))
                    .append(Component.text(playerName, NamedTextColor.AQUA))
                    .append(Component.text("  debt=" + formatMoney(balance.doubleValue()), NamedTextColor.RED))
                    .append(Component.text("  (" + loanCount + " loan" + (loanCount == 1 ? "" : "s") + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text("  [" + String.format("%.1f%%", pctOfTotal) + " of total]", NamedTextColor.DARK_GRAY))
                    .build();
            sender.sendMessage(row);
        }
        sender.sendMessage(Component.empty());
    }

    @Suggestions("leaderboard-period")
    public List<String> suggestPeriod(CommandContext<?> ctx) {
        return List.of("day", "week", "month", "all");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final double MILLION = 1_000_000.0;
    private static final double THOUSAND = 1_000.0;

    private String resolveName(UUID uuid) {
        Optional<PlayerData> player = playerRepository.findByUuid(uuid);
        return player.map(PlayerData::username).orElse("???" + uuid.toString().substring(0, 6));
    }

    private String formatVol(long n) {
        if (n >= MILLION) return String.format("%.1fM", n / MILLION);
        if (n >= THOUSAND) return String.format("%.1fK", n / THOUSAND);
        return String.valueOf(n);
    }

    private String formatMoney(double n) {
        if (Math.abs(n) >= MILLION) return String.format("$%.1fM", n / MILLION);
        if (Math.abs(n) >= THOUSAND) return String.format("$%.1fK", n / THOUSAND);
        return String.format("$%.0f", n);
    }
}
