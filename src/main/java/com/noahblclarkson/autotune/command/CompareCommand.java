package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.BadgeRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.database.TransactionRepository.TransactionPeriodAggregate;
import com.noahblclarkson.autotune.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Player comparison command — compares trading stats between two players.
 *
 * /compare              — show help
 * /compare <player>     — compare yourself with another player (weekly stats)
 * /compare <player> <player> — compare two named players (weekly stats)
 *
 * Shows side-by-side stats: trades, volume, net position, badges, member since.
 */
@Singleton
public class CompareCommand {

    private static final String PERMISSION = "autotune.compare";
    private static final String PERIOD = "week"; // weekly comparison window
    private static final String SUGGESTION_COMPARE_PLAYERS = "compare-players";
    private static final String SEPARATOR = "  |  ";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault());

    private final AutoTune plugin;
    private final TransactionRepository transactionRepository;
    private final PlayerRepository playerRepository;
    private final BadgeRepository badgeRepository;

    @Inject
    public CompareCommand(
            AutoTune plugin,
            TransactionRepository transactionRepository,
            PlayerRepository playerRepository,
            BadgeRepository badgeRepository
    ) {
        this.plugin = plugin;
        this.transactionRepository = transactionRepository;
        this.playerRepository = playerRepository;
        this.badgeRepository = badgeRepository;
    }

    @Command("compare")
    @Permission(PERMISSION)
    public void compareHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("─── Player Compare ───").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/compare", NamedTextColor.YELLOW)
                .append(Component.text(" — show this help", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/compare <player>", NamedTextColor.YELLOW)
                .append(Component.text(" — compare yourself vs player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/compare <player> <player>", NamedTextColor.YELLOW)
                .append(Component.text(" — compare two players", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("compare <player1>")
    @Permission(PERMISSION)
    public void compareToOther(CommandSender sender, @Argument(value = "player1", suggestions = SUGGESTION_COMPARE_PLAYERS) String targetName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is for players only.", NamedTextColor.RED));
            return;
        }

        UUID targetUuid = resolvePlayerUuid(targetName);
        if (targetUuid == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        UUID selfUuid = player.getUniqueId();

        if (selfUuid.equals(targetUuid)) {
            sender.sendMessage(Component.text("You can't compare a player to themselves.", NamedTextColor.RED));
            return;
        }

        sendComparison(sender, selfUuid, targetUuid);
    }

    @Command("compare <player1> <player2>")
    @Permission(PERMISSION)
    public void compareTwoPlayers(
            CommandSender sender,
            @Argument(value = "player1", suggestions = SUGGESTION_COMPARE_PLAYERS) String player1Name,
            @Argument(value = "player2", suggestions = SUGGESTION_COMPARE_PLAYERS) String player2Name
    ) {
        UUID uuid1 = resolvePlayerUuid(player1Name);
        if (uuid1 == null) {
            sender.sendMessage(Component.text("Player not found: " + player1Name, NamedTextColor.RED));
            return;
        }

        UUID uuid2 = resolvePlayerUuid(player2Name);
        if (uuid2 == null) {
            sender.sendMessage(Component.text("Player not found: " + player2Name, NamedTextColor.RED));
            return;
        }

        if (uuid1.equals(uuid2)) {
            sender.sendMessage(Component.text("Provide two different players to compare.", NamedTextColor.RED));
            return;
        }

        sendComparison(sender, uuid1, uuid2);
    }

    @Suggestions(SUGGESTION_COMPARE_PLAYERS)
    public List<String> suggestPlayerNames(CommandContext<CommandSender> context, String input) {
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(lower))
                .toList();
    }

    // ── Core comparison logic ───────────────────────────────────────────────

    private void sendComparison(CommandSender sender, UUID uuid1, UUID uuid2) {
        // Fetch all data in parallel via separate calls (JDBI handles this cleanly)
        TransactionPeriodAggregate stats1 = transactionRepository.findPlayerPeriodStats(PERIOD, uuid1);
        TransactionPeriodAggregate stats2 = transactionRepository.findPlayerPeriodStats(PERIOD, uuid2);
        PlayerData playerData1 = playerRepository.findByUuid(uuid1).orElse(null);
        PlayerData playerData2 = playerRepository.findByUuid(uuid2).orElse(null);
        int badgeCount1 = badgeRepository.getBadges(uuid1).size();
        int badgeCount2 = badgeRepository.getBadges(uuid2).size();

        String name1 = playerData1 != null && playerData1.username() != null
                ? playerData1.username() : uuid1.toString().substring(0, 8);
        String name2 = playerData2 != null && playerData2.username() != null
                ? playerData2.username() : uuid2.toString().substring(0, 8);

        // Compute weekly trade metrics
        long trades1 = stats1 != null ? stats1.transactionCount() : 0;
        long trades2 = stats2 != null ? stats2.transactionCount() : 0;
        BigDecimal spent1 = stats1 != null ? stats1.totalSpent() : BigDecimal.ZERO;
        BigDecimal spent2 = stats2 != null ? stats2.totalSpent() : BigDecimal.ZERO;
        BigDecimal earned1 = stats1 != null ? stats1.totalEarned() : BigDecimal.ZERO;
        BigDecimal earned2 = stats2 != null ? stats2.totalEarned() : BigDecimal.ZERO;
        BigDecimal net1 = earned1.subtract(spent1);
        BigDecimal net2 = earned2.subtract(spent2);

        // Member since
        Instant firstSeen1 = playerData1 != null ? playerData1.firstSeen() : null;
        Instant firstSeen2 = playerData2 != null ? playerData2.firstSeen() : null;

        sender.sendMessage(Component.empty());
        // Header
        Component headerLeft = Component.text(name1).color(NamedTextColor.AQUA).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Component headerRight = Component.text(name2).color(NamedTextColor.LIGHT_PURPLE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Component vs = Component.text("  vs  ").color(NamedTextColor.DARK_GRAY);
        sender.sendMessage(headerLeft.append(vs).append(headerRight));

        // ── Weekly Trades ──────────────────────────────────────────────────
        Component tradesRow = makeStatRow("Trades", trades1, trades2,
                String.valueOf(trades1), String.valueOf(trades2), true);
        sender.sendMessage(tradesRow);

        // ── Spent ─────────────────────────────────────────────────────────
        Component spentRow = makeMoneyRow("Spent", spent1, spent2);
        sender.sendMessage(spentRow);

        // ── Earned ────────────────────────────────────────────────────────
        Component earnedRow = makeMoneyRow("Earned", earned1, earned2);
        sender.sendMessage(earnedRow);

        // ── Net Position ───────────────────────────────────────────────────
        Component netRow = makeMoneyRow("Net", net1, net2);
        sender.sendMessage(netRow);

        sender.sendMessage(Component.text("─────────────────────────────────").color(NamedTextColor.DARK_GRAY));

        // ── Badges ───────────────────────────────────────────────────────
        Component badges1 = Component.text(badgeCount1).color(
                badgeCount1 >= badgeCount2 ? NamedTextColor.GOLD : NamedTextColor.GRAY);
        Component badges2 = Component.text(badgeCount2).color(
                badgeCount2 > badgeCount1 ? NamedTextColor.GOLD : NamedTextColor.GRAY);
        Component badgesLabel = Component.text("Badges").color(NamedTextColor.GRAY);
        Component badgesTrophy1 = badgeCount1 > badgeCount2
                ? Component.text(" 🏆").color(NamedTextColor.GOLD) : Component.empty();
        Component badgesTrophy2 = badgeCount2 > badgeCount1
                ? Component.text(" 🏆").color(NamedTextColor.GOLD) : Component.empty();
        sender.sendMessage(badgesLabel
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(badges1).append(badgesTrophy1)
                .append(Component.text(SEPARATOR).color(NamedTextColor.DARK_GRAY))
                .append(badges2).append(badgesTrophy2));

        // ── Member Since ──────────────────────────────────────────────────
        if (firstSeen1 != null || firstSeen2 != null) {
            String since1 = firstSeen1 != null ? DATE_FORMAT.format(firstSeen1) : "—";
            String since2 = firstSeen2 != null ? DATE_FORMAT.format(firstSeen2) : "—";
            Component sinceRow = makeLabelRow("Member Since", since1, since2);
            sender.sendMessage(sinceRow);

            // Days on server
            String days1 = firstSeen1 != null
                    ? Duration.between(firstSeen1, Instant.now()).toDays() + "d"
                    : "—";
            String days2 = firstSeen2 != null
                    ? Duration.between(firstSeen2, Instant.now()).toDays() + "d"
                    : "—";
            Component daysRow = makeLabelRow("Days on Server", days1, days2);
            sender.sendMessage(daysRow);
        }

        // ── Period footer ─────────────────────────────────────────────────
        sender.sendMessage(Component.text("─────────────────────────────────").color(NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("Last 7 days · /compare to compare longer windows").color(NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());
    }

    // ── Formatting helpers ─────────────────────────────────────────────────

    /**
     * Builds a row for numeric (count) stats.
     * winner is colored HIGHER color, loser LOWER color.
     */
    private Component makeStatRow(String label, long val1, long val2,
                                  String disp1, String disp2, boolean higherWins) {
        Component labelComponent = Component.text(label).color(NamedTextColor.GRAY);
        Component valComponent1;
        Component valComponent2;
        Component trophy1 = Component.empty();
        Component trophy2 = Component.empty();

        if (val1 == val2) {
            valComponent1 = Component.text(disp1).color(NamedTextColor.WHITE);
            valComponent2 = Component.text(disp2).color(NamedTextColor.WHITE);
        } else if (higherWins ? val1 > val2 : val1 < val2) {
            valComponent1 = Component.text(disp1).color(NamedTextColor.GREEN);
            valComponent2 = Component.text(disp2).color(NamedTextColor.RED);
            trophy1 = Component.text(" ▲").color(NamedTextColor.GREEN);
        } else {
            valComponent1 = Component.text(disp1).color(NamedTextColor.RED);
            valComponent2 = Component.text(disp2).color(NamedTextColor.GREEN);
            trophy2 = Component.text(" ▲").color(NamedTextColor.GREEN);
        }

        return labelComponent
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(valComponent1).append(trophy1)
                .append(Component.text(SEPARATOR).color(NamedTextColor.DARK_GRAY))
                .append(valComponent2).append(trophy2);
    }

    /**
     * Builds a row for money stats (spent, earned, net).
     */
    private Component makeMoneyRow(String label, BigDecimal val1, BigDecimal val2) {
        Component labelComponent = Component.text(label).color(NamedTextColor.GRAY);
        Component valComponent1 = formatMoneyComponent(val1);
        Component valComponent2 = formatMoneyComponent(val2);

        // Color winner green, loser red (higher money is better)
        if (val1.compareTo(val2) != 0) {
            if (val1.compareTo(val2) > 0) {
                valComponent1 = Component.text(formatMoneyPlain(val1)).color(NamedTextColor.GREEN);
                valComponent2 = Component.text(formatMoneyPlain(val2)).color(NamedTextColor.RED);
            } else {
                valComponent1 = Component.text(formatMoneyPlain(val1)).color(NamedTextColor.RED);
                valComponent2 = Component.text(formatMoneyPlain(val2)).color(NamedTextColor.GREEN);
            }
        }

        return labelComponent
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(valComponent1)
                .append(Component.text(SEPARATOR).color(NamedTextColor.DARK_GRAY))
                .append(valComponent2);
    }

    /**
     * Builds a row for plain string labels (no winner/loser coloring).
     */
    private Component makeLabelRow(String label, String val1, String val2) {
        Component labelComponent = Component.text(label).color(NamedTextColor.GRAY);
        Component valComponent1 = Component.text(val1).color(NamedTextColor.WHITE);
        Component valComponent2 = Component.text(val2).color(NamedTextColor.WHITE);
        return labelComponent
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(valComponent1)
                .append(Component.text(SEPARATOR).color(NamedTextColor.DARK_GRAY))
                .append(valComponent2);
    }

    private Component formatMoneyComponent(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return Component.text("$0").color(NamedTextColor.GRAY);
        }
        BigDecimal abs = amount.abs();
        String formatted;
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            formatted = String.format("$%.1fM", abs.doubleValue() / 1_000_000);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            formatted = String.format("$%.1fK", abs.doubleValue() / 1_000);
        } else {
            formatted = "$" + abs.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        NamedTextColor color = amount.compareTo(BigDecimal.ZERO) >= 0
                ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(formatted).color(color);
    }

    private String formatMoneyPlain(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "$0";
        }
        BigDecimal abs = amount.abs();
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return String.format("$%.1fM", abs.doubleValue() / 1_000_000);
        } else if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return String.format("$%.1fK", abs.doubleValue() / 1_000);
        } else {
            return "$" + abs.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }

    private UUID resolvePlayerUuid(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) return player.getUniqueId();

        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null) return offline.getUniqueId();

        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .map(Player::getUniqueId)
                .findFirst()
                .orElse(null);
    }
}
