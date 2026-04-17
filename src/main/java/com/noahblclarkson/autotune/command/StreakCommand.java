package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.service.PlayerStreakService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.context.CommandContext;

/**
 * /streak command — shows the player's current trading streak, best streak,
 * and next badge milestone.
 *
 * <p>Also supports /streak top to view the server's best streaks.</p>
 */
@Singleton
@SuppressWarnings("PMD")
public class StreakCommand {

    private final AutoTune plugin;
    private final PlayerStreakService streakService;

    @Inject
    public StreakCommand(AutoTune plugin, PlayerStreakService streakService) {
        this.plugin = plugin;
        this.streakService = streakService;
    }

    @Command("streak")
    @Permission("autotune.streak")
    public void onStreak(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "This command can only be used by players.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        streakService.sendStreakInfo(player);
    }

    @Command("streak top")
    @Permission("autotune.streak")
    public void onStreakTop(CommandContext<CommandSender> ctx) {
        streakService.sendTopStreaks(ctx.sender());
    }
}
