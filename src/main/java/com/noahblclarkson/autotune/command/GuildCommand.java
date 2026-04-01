package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.guild.GuildService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class GuildCommand {

    private final GuildService guildService;
    private final ConfigManager configManager;

    @Inject
    public GuildCommand(GuildService guildService, ConfigManager configManager) {
        this.guildService = guildService;
        this.configManager = configManager;
    }

    // guildHelp removed: /guild base command now handled by guildStatsForPlayer (player) and
    // guildStatsNoArgs (console) which both show contextual help when no guild is found.

    @Command("guild")
    @Permission("autotune.guild")
    public void guildBase(CommandSender sender) {
        sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY)
                .append(Component.text("/guild stats", NamedTextColor.YELLOW))
                .append(Component.text(" to view your guild's economy stats, or ", NamedTextColor.GRAY))
                .append(Component.text("/guild stats <guild>", NamedTextColor.YELLOW))
                .append(Component.text(" to view a specific guild.", NamedTextColor.GRAY)));
    }

    @Command("guild stats")
    @Permission("autotune.guild")
    public void guildStatsForPlayer(Player player) {
        Optional<GuildService.GuildStats> statsOpt = guildService.getGuildStatsForPlayer(player);

        if (statsOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED)
                    .append(Component.text(" Guild membership is set by your server's permission groups (e.g. via a guild plugin that integrates with Vault).", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("Ask your server admin to link your guild plugin to Vault permissions.", NamedTextColor.GRAY));
            return;
        }

        sendGuildStats(player, statsOpt.get());
    }

    @Command("guild stats <guildName>")
    @Permission("autotune.guild")
    public void guildStats(CommandSender sender, @Argument("guildName") String guildName) {
        String tag = guildName.toLowerCase(Locale.ROOT).trim();
        GuildService.GuildStats stats = guildService.getGuildStats(tag);

        if (stats.memberCount() == 0) {
            sender.sendMessage(Component.text("Guild '" + tag + "' not found or has no active members.", NamedTextColor.RED));
            return;
        }

        sendGuildStats(sender, stats);
    }

    private void sendGuildStats(CommandSender sender, GuildService.GuildStats stats) {
        String tag = stats.guildTag();
        Component guildName = Component.text(tag, NamedTextColor.AQUA, TextDecoration.BOLD);
        Component header = Component.text("══════ Guild: ", NamedTextColor.GOLD)
                .append(guildName)
                .append(Component.text(" ══════", NamedTextColor.GOLD));

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);

        // Rank
        int rank = guildService.getGuildRank(tag);
        sender.sendMessage(Component.text("  Rank: #", NamedTextColor.GRAY)
                .append(Component.text(rank > 0 ? String.valueOf(rank) : "—", NamedTextColor.YELLOW)));

        // Members
        NamedTextColor onlineColor = stats.onlineCount() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        sender.sendMessage(Component.text("  Members: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(stats.memberCount()), NamedTextColor.WHITE))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(stats.onlineCount()), onlineColor))
                .append(Component.text(" online)", NamedTextColor.GRAY)));

        sender.sendMessage(Component.empty());

        // Volume
        sender.sendMessage(Component.text("  Total Volume: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(stats.totalVolume()), NamedTextColor.GREEN))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(configManager.formatCurrency(stats.perMemberVolume()), NamedTextColor.WHITE))
                .append(Component.text("/member)", NamedTextColor.GRAY)));

        // Buy/Sell
        sender.sendMessage(Component.text("  Bought: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(stats.totalBought()), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("  Sold: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(stats.totalSold()), NamedTextColor.AQUA)));

        // Net position
        boolean netPositive = stats.netPosition().compareTo(java.math.BigDecimal.ZERO) >= 0;
        NamedTextColor netColor = netPositive ? NamedTextColor.AQUA : NamedTextColor.RED;
        sender.sendMessage(Component.text("  Net Position: ", NamedTextColor.GRAY)
                .append(Component.text(
                        (netPositive ? "+" : "") + configManager.formatCurrency(stats.netPosition()),
                        netColor)));

        sender.sendMessage(Component.empty());

        // Debt
        if (stats.activeLoanCount() > 0) {
            sender.sendMessage(Component.text("  Guild Debt: ", NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(stats.totalDebt()), NamedTextColor.RED))
                    .append(Component.text(" across ", NamedTextColor.GRAY))
                    .append(Component.text(stats.activeLoanCount() + " loan(s)", NamedTextColor.RED)));
            sender.sendMessage(Component.text("  Largest Loan: ", NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(stats.largestLoan()), NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("  Guild Debt: ", NamedTextColor.GRAY)
                    .append(Component.text("No active loans", NamedTextColor.GREEN)));
        }

        // Avg credit score
        if (stats.avgCreditScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            int score = stats.avgCreditScore().intValue();
            NamedTextColor scoreColor = score >= 600 ? NamedTextColor.GREEN
                    : score >= 400 ? NamedTextColor.YELLOW
                    : NamedTextColor.RED;
            sender.sendMessage(Component.text("  Avg Credit Score: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(score), scoreColor)));
        }

        sender.sendMessage(Component.empty());
    }

    @Command("guild top")
    @Permission("autotune.guild")
    public void guildTop(CommandSender sender) {
        List<GuildService.GuildStats> guilds = guildService.getAllGuildStats();

        if (guilds.isEmpty()) {
            sender.sendMessage(Component.text("No guilds found on this server.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══ Guild Leaderboard ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        int maxRank = Math.min(guilds.size(), 10);
        for (int i = 0; i < maxRank; i++) {
            GuildService.GuildStats g = guilds.get(i);
            NamedTextColor rankColor = i == 0 ? NamedTextColor.GOLD
                    : i == 1 ? NamedTextColor.GRAY
                    : i == 2 ? NamedTextColor.YELLOW
                    : NamedTextColor.WHITE;

            Component row = Component.text("  #" + (i + 1) + " ", rankColor, TextDecoration.BOLD)
                    .append(Component.text(g.guildTag(), NamedTextColor.AQUA))
                    .append(Component.text(" — " + configManager.formatCurrency(g.totalVolume()), NamedTextColor.GREEN))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(g.memberCount() + " members", NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.GRAY));

            sender.sendMessage(row);
        }

        sender.sendMessage(Component.empty());
    }
}
