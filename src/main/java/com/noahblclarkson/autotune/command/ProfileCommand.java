package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.BadgeRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.model.PlayerBadge;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.service.PlayerImpactService;
import com.noahblclarkson.autotune.service.PlayerStreakService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * /profile command — unified player dashboard showing economy stats,
 * streak, badges, and account overview.
 *
 * <p>Usage:
 * <ul>
 *   <li>/profile — view your own profile</li>
 *   <li>/profile &lt;player&gt; — view another player's profile</li>
 * </ul>
 */
@Singleton
@SuppressWarnings("PMD")
public class ProfileCommand {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final TextColor ACCENT = TextColor.fromHexString("#10B981");
    private static final TextColor ACCENT_DIM = TextColor.fromHexString("#059669");
    private static final TextColor HEADER_BG = TextColor.fromHexString("#1E293B");

    private final AutoTune plugin;
    private final PlayerRepository playerRepository;
    private final BadgeRepository badgeRepository;
    private final PlayerStreakService streakService;
    private final PlayerImpactService impactService;

    @Inject
    public ProfileCommand(AutoTune plugin, PlayerRepository playerRepository,
                          BadgeRepository badgeRepository, PlayerStreakService streakService,
                          PlayerImpactService impactService) {
        this.plugin = plugin;
        this.playerRepository = playerRepository;
        this.badgeRepository = badgeRepository;
        this.streakService = streakService;
        this.impactService = impactService;
    }

    @Command("profile")
    @Permission("autotune.profile")
    public void onProfileSelf(CommandContext<CommandSender> ctx) {
        if (!(ctx.sender() instanceof Player player)) {
            ctx.sender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        loadAndRender(ctx.sender(), player.getUniqueId(), player.getName());
    }

    @Command("profile <target>")
    @Permission("autotune.profile")
    public void onProfileOther(CommandContext<CommandSender> ctx,
                               @Argument(value = "target", suggestions = "profile-targets") String targetName) {
        // Try online player first
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            loadAndRender(ctx.sender(), target.getUniqueId(), target.getName());
            return;
        }

        // Try offline lookup
        Player offlineMatch = Bukkit.getPlayer(targetName);
        if (offlineMatch != null) {
            loadAndRender(ctx.sender(), offlineMatch.getUniqueId(), offlineMatch.getName());
            return;
        }

        // Try by username in database
        playerRepository.findByName(targetName).ifPresentOrElse(
                pd -> loadAndRender(ctx.sender(), pd.uuid(), pd.username()),
                () -> ctx.sender().sendMessage(
                        Component.text("Player not found: " + targetName, NamedTextColor.RED))
        );
    }

    @Suggestions("profile-targets")
    public List<String> suggestTargets(CommandContext<CommandSender> ctx, String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                .limit(10)
                .toList();
    }

    private void loadAndRender(CommandSender sender, UUID uuid, String name) {
        plugin.getDatabaseManager().supplyAsync(() -> {
            PlayerData data = playerRepository.findByUuid(uuid).orElse(null);
            List<PlayerBadge> badges = badgeRepository.getBadges(uuid);
            PlayerStreakService.StreakData streak = streakService.getStreakOrDefault(uuid);
            PlayerImpactService.PlayerImpactDto impact = impactService.compute(name).orElse(null);
            return new ProfileData(data, badges, streak, name, impact);
        }).orTimeout(5, TimeUnit.SECONDS)
                .thenAcceptAsync(profile -> {
                    Bukkit.getScheduler().runTask(plugin, () -> renderProfile(sender, profile));
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    sender.sendMessage(Component.text("Could not load profile.", NamedTextColor.RED));
                    return null;
                });
    }

    private void renderProfile(CommandSender sender, ProfileData profile) {
        PlayerData data = profile.data;
        if (data == null) {
            sender.sendMessage(Component.text("No data found for " + profile.name + ".", NamedTextColor.RED));
            return;
        }

        // Header
        sender.sendMessage(Component.text("╔═══════════════════════════════════")
                .color(ACCENT));
        sender.sendMessage(Component.text("║ ")
                .color(ACCENT)
                .append(Component.text(profile.name).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text("'s Profile").color(NamedTextColor.GRAY))
                .append(Component.text(" ║").color(ACCENT)));
        sender.sendMessage(Component.text("╚═══════════════════════════════════")
                .color(ACCENT));

        // Membership duration
        long daysSince = Duration.between(data.firstSeen(), Instant.now()).toDays();
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  📅 Member for ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(daysSince + " day" + (daysSince != 1 ? "s" : ""))
                        .color(NamedTextColor.WHITE))
                .append(Component.text(" (" + data.transactionCount() + " trades)")
                        .color(NamedTextColor.DARK_GRAY)));

        // Streak
        PlayerStreakService.StreakData streak = profile.streak;
        if (streak.currentStreak() > 0) {
            String fires = "🔥".repeat(Math.min(streak.currentStreak(), 10));
            sender.sendMessage(Component.text("  " + fires + " ")
                    .color(NamedTextColor.RED)
                    .append(Component.text(streak.currentStreak() + "-day streak")
                            .color(NamedTextColor.GOLD)));
        } else {
            sender.sendMessage(Component.text("  🔥 No active streak")
                    .color(NamedTextColor.DARK_GRAY));
        }

        // Credit score
        String scoreLabel = creditScoreLabel(data.creditScore());
        sender.sendMessage(Component.text("  💳 Credit Score: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(data.creditScore() + "/1000").color(scoreColor(data.creditScore())))
                .append(Component.text(" (" + scoreLabel + ")").color(NamedTextColor.DARK_GRAY)));

        // Trading volume
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  💰 Volume Traded: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("$" + formatMoney(data.totalTraded())).color(NamedTextColor.GOLD)));

        BigDecimal net = data.totalBought().subtract(data.totalSold());
        NamedTextColor netColor = net.compareTo(BigDecimal.ZERO) >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        String netSign = net.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

        sender.sendMessage(Component.text("  📊 Net Position: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(netSign + "$" + formatMoney(net)).color(netColor)));

        sender.sendMessage(Component.text("  🛒 Bought: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("$" + formatMoney(data.totalBought())).color(NamedTextColor.GREEN))
                .append(Component.text("  │  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Sold: ").color(NamedTextColor.GRAY))
                .append(Component.text("$" + formatMoney(data.totalSold())).color(NamedTextColor.RED)));

        // Avg trade size
        if (data.transactionCount() > 0) {
            BigDecimal avgTrade = data.totalTraded().divide(
                    BigDecimal.valueOf(data.transactionCount()), 2, RoundingMode.HALF_UP);
            sender.sendMessage(Component.text("  📈 Avg Trade: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("$" + formatMoney(avgTrade)).color(NamedTextColor.WHITE)));
        }

        // Badges
        sender.sendMessage(Component.text(""));
        if (profile.badges.isEmpty()) {
            sender.sendMessage(Component.text("  🏅 No badges earned yet")
                    .color(NamedTextColor.DARK_GRAY));
        } else {
            StringBuilder badgeLine = new StringBuilder("  🏅 ");
            for (PlayerBadge badge : profile.badges) {
                badgeLine.append("[").append(badge.badgeType().getDisplayName()).append("] ");
            }
            sender.sendMessage(Component.text(badgeLine.toString() + "(" + profile.badges.size() + " badges)")
                    .color(NamedTextColor.GOLD));
        }

        // Market impact
        PlayerImpactService.PlayerImpactDto impact = profile.impact;
        if (impact != null) {
            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text("  📊 Market Impact")
                    .color(ACCENT).decorate(TextDecoration.BOLD));
            if (impact.weeklyRank() > 0) {
                sender.sendMessage(Component.text("  Weekly Rank: ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("#" + impact.weeklyRank())
                                .color(NamedTextColor.GOLD)));
            }
            if (impact.weeklyImpactPct() != 0.0) {
                String impactStr = formatPercent(impact.weeklyImpactPct());
                NamedTextColor impactColor = impact.weeklyImpactPct() > 0
                        ? NamedTextColor.GREEN : NamedTextColor.RED;
                sender.sendMessage(Component.text("  Weekly Impact: ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(impactStr).color(impactColor)));
            }
            if (!impact.topItems().isEmpty()) {
                sender.sendMessage(Component.text("  Top items by impact: ")
                        .color(NamedTextColor.GRAY));
                int count = 0;
                for (PlayerImpactService.MarketImpactItemDto item : impact.topItems()) {
                    if (count++ >= 3) break;
                    String pct = formatPercent(item.playerImpactPct());
                    NamedTextColor itemColor = item.playerImpactPct() > 0
                            ? NamedTextColor.GREEN : NamedTextColor.RED;
                    sender.sendMessage(Component.text("    • ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text(item.itemName()).color(NamedTextColor.WHITE))
                            .append(Component.text(" (" + pct + ")").color(itemColor)));
                }
            }
        }

        // Footer
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  💡 Use ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("/streak").color(ACCENT_DIM))
                .append(Component.text(" for streak details, ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("/badges").color(ACCENT_DIM))
                .append(Component.text(" for badge list").color(NamedTextColor.DARK_GRAY)));
    }

    private String creditScoreLabel(int score) {
        if (score >= 800) return "Excellent";
        if (score >= 650) return "Good";
        if (score >= 500) return "Fair";
        if (score >= 300) return "Poor";
        return "Critical";
    }

    private NamedTextColor scoreColor(int score) {
        if (score >= 800) return NamedTextColor.GREEN;
        if (score >= 650) return NamedTextColor.DARK_GREEN;
        if (score >= 500) return NamedTextColor.YELLOW;
        if (score >= 300) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    private String formatPercent(double pct) {
        if (Math.abs(pct) >= 100) {
            return String.format("%.0f%%", pct);
        } else if (Math.abs(pct) >= 10) {
            return String.format("%.1f%%", pct);
        } else {
            return String.format("%.2f%%", pct);
        }
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal abs = amount.abs();
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return abs.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP) + "M";
        }
        if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return abs.divide(BigDecimal.valueOf(1_000), 1, RoundingMode.HALF_UP) + "K";
        }
        return abs.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private record ProfileData(PlayerData data, List<PlayerBadge> badges,
                               PlayerStreakService.StreakData streak, String name,
                               PlayerImpactService.PlayerImpactDto impact) {
    }
}
