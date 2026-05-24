package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.PlayerRepository.OnboardingPlayerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player onboarding command — shows milestone progress and admin controls.
 *
 * /onboarding              — player sees their own progress
 * /onboarding <player>     — admin views a specific player's progress
 * /onboarding reload       — admin reloads onboarding config
 * /onboarding status       — admin summary of all active milestones
 */
@Singleton
@SuppressWarnings("PMD")
public class OnboardingCommand {

    private static final Logger log = Logger.getLogger(OnboardingCommand.class.getName());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault());

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PlayerRepository playerRepository;

    @Inject
    public OnboardingCommand(
            AutoTune plugin,
            ConfigManager configManager,
            DatabaseManager databaseManager,
            PlayerRepository playerRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerRepository = playerRepository;
    }

    @Command("onboarding")
    @Permission("autotune.onboarding")
    public void onOnboarding(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        showPlayerProgress(player, player.getUniqueId());
    }

    @Command("onboarding status")
    @Permission("autotune.admin")
    public void onOnboardingStatus(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        AutoTuneConfig.OnboardingConfig cfg = configManager.getConfig().onboarding();

        Component header = Component.text("═══ Onboarding System ═══")
                .color(NamedTextColor.GOLD);

        Component enabled = Component.text("Enabled: ")
                .append(cfg.enabled()
                        ? Component.text("YES").color(NamedTextColor.GREEN)
                        : Component.text("NO").color(NamedTextColor.RED));
        Component interval = Component.text("Check interval: ")
                .append(Component.text(cfg.checkIntervalHours() + " hours").color(NamedTextColor.AQUA));

        sender.sendMessage(header);
        sender.sendMessage(enabled);
        sender.sendMessage(interval);
        sender.sendMessage(Component.text("Milestones:").color(NamedTextColor.YELLOW));

        for (AutoTuneConfig.OnboardingMilestoneConfig milestone : cfg.milestones()) {
            TextComponent.Builder builder = Component.text();
            builder.append(Component.text("  • ").color(NamedTextColor.GRAY));
            builder.append(Component.text(milestone.category()).color(NamedTextColor.AQUA));
            builder.append(Component.text(" [Day ").color(NamedTextColor.GRAY));
            builder.append(Component.text(milestone.dayOffset()).color(NamedTextColor.WHITE));
            builder.append(Component.text("] ").color(NamedTextColor.GRAY));
            builder.append(milestone.enabled()
                    ? Component.text("● ENABLED").color(NamedTextColor.GREEN)
                    : Component.text("○ DISABLED").color(NamedTextColor.RED));
            sender.sendMessage(builder.build());
        }
    }

    @Command("onboarding reload")
    @Permission("autotune.admin")
    public void onOnboardingReload(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        try {
            plugin.reload();
            sender.sendMessage(Component.text("Onboarding config reloaded.", NamedTextColor.GREEN)
                    .append(Component.text(" (restart required for service check-interval to take effect)").color(NamedTextColor.GRAY)));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    @Command("onboarding player <target>")
    @Permission("autotune.admin")
    public void onOnboardingPlayer(
            CommandContext<CommandSender> ctx,
            @Argument("target") String playerName
    ) {
        CommandSender sender = ctx.sender();
        UUID uuid = resolvePlayerUuid(playerName);
        if (uuid == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
            return;
        }
        String displayName = Bukkit.getPlayer(uuid) != null
                ? Bukkit.getPlayer(uuid).getName()
                : playerName;
        showPlayerProgress(sender, uuid, displayName);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void showPlayerProgress(CommandSender sender, UUID uuid) {
        showPlayerProgress(sender, uuid, sender.getName());
    }

    private void showPlayerProgress(CommandSender sender, UUID uuid, String displayName) {
        AutoTuneConfig.OnboardingConfig cfg = configManager.getConfig().onboarding();
        if (!cfg.enabled()) {
            sender.sendMessage(Component.text("Onboarding is currently disabled on this server.", NamedTextColor.GRAY));
            return;
        }

        databaseManager.supplyAsync(() -> playerRepository.findOnboardingInfoByUuid(uuid))
                .orTimeout(5, TimeUnit.SECONDS)
                .thenAccept(optInfo -> {
                    if (optInfo == null || optInfo.isEmpty()) {
                        sender.sendMessage(Component.text("Player not found in database.", NamedTextColor.RED));
                        return;
                    }
                    OnboardingPlayerInfo info = optInfo.get();
                    Instant firstSeen = info.firstSeen();
                    if (firstSeen == null) {
                        sender.sendMessage(Component.text("No first-join record for this player.", NamedTextColor.RED));
                        return;
                    }

                    Instant now = Instant.now();
                    long daysOnServer = ChronoUnit.DAYS.between(firstSeen, now);
                    int lastMilestone = info.lastOnboardingMilestoneSent();

                    Component header = Component.text("═══ Onboarding: " + displayName + " ═══")
                            .color(NamedTextColor.GOLD);
                    Component joined = Component.text("Joined: ")
                            .append(Component.text(DATE_FORMAT.format(firstSeen)).color(NamedTextColor.AQUA))
                            .append(Component.text(" (" + daysOnServer + " days ago)").color(NamedTextColor.GRAY));
                    Component progress = Component.text("Milestones completed: ")
                            .append(Component.text(lastMilestone + " / " + cfg.milestones().size()).color(NamedTextColor.GREEN));

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(header);
                        sender.sendMessage(joined);
                        sender.sendMessage(progress);
                        sender.sendMessage(Component.text("Milestone history:").color(NamedTextColor.YELLOW));

                        for (AutoTuneConfig.OnboardingMilestoneConfig milestone : cfg.milestones()) {
                            boolean sent = milestone.dayOffset() <= lastMilestone;
                            boolean upcoming = milestone.dayOffset() > lastMilestone
                                    && milestone.dayOffset() <= daysOnServer;
                            TextComponent.Builder b = Component.text();
                            b.append(Component.text("  ").color(NamedTextColor.GRAY));
                            if (sent) {
                                b.append(Component.text("✔ ").color(NamedTextColor.GREEN));
                                b.append(Component.text(milestone.category()).color(NamedTextColor.GREEN));
                                b.append(Component.text(" [Day " + milestone.dayOffset() + "]").color(NamedTextColor.GRAY));
                            } else if (upcoming) {
                                b.append(Component.text("★ ").color(NamedTextColor.YELLOW));
                                b.append(Component.text(milestone.category()).color(NamedTextColor.YELLOW));
                                b.append(Component.text(" [Day " + milestone.dayOffset() + "] — due now!").color(NamedTextColor.GOLD));
                            } else {
                                b.append(Component.text("○ ").color(NamedTextColor.DARK_GRAY));
                                b.append(Component.text(milestone.category()).color(NamedTextColor.DARK_GRAY));
                                b.append(Component.text(" [Day " + milestone.dayOffset() + "]").color(NamedTextColor.DARK_GRAY));
                            }
                            sender.sendMessage(b.build());
                        }

                        // Next milestone info
                        AutoTuneConfig.OnboardingMilestoneConfig next = cfg.milestones().stream()
                                .filter(m -> m.dayOffset() > lastMilestone && m.enabled())
                                .findFirst()
                                .orElse(null);
                        if (next != null) {
                            long daysUntil = next.dayOffset() - daysOnServer;
                            Component nextMsg = Component.text("Next: ")
                                    .append(Component.text(next.category()).color(NamedTextColor.AQUA))
                                    .append(Component.text(" in " + daysUntil + " day" + (daysUntil == 1 ? "" : "s")).color(NamedTextColor.GRAY));
                            sender.sendMessage(nextMsg);
                        } else {
                            sender.sendMessage(Component.text("All milestones complete!", NamedTextColor.GREEN));
                        }
                    });
                })
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to load onboarding info for " + uuid, ex);
                    sender.sendMessage(Component.text("Failed to load onboarding data.", NamedTextColor.RED));
                    return null;
                });
    }

    private UUID resolvePlayerUuid(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) return player.getUniqueId();

        // Try offline player cache (players who have played on this server before)
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null) return offline.getUniqueId();

        // Last resort: scan online players by name (handles case-sensitivity)
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .map(Player::getUniqueId)
                .findFirst()
                .orElse(null);
    }
}
