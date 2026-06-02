package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.context.CommandContext;

@Singleton
@SuppressWarnings("PMD")
public class QuickstartCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#10B981");
    private static final TextColor DIM = NamedTextColor.GRAY;
    private static final TextColor HIGHLIGHT = NamedTextColor.GOLD;
    private static final TextColor EMERALD = TextColor.fromHexString("#22C55E");
    private static final TextColor WHITE = NamedTextColor.WHITE;

    @Inject
    public QuickstartCommand() {}

    @Command("quickstart")
    @Permission("autotune.quickstart")
    public void onQuickstart(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();

        sender.sendMessage(Component.text("=======================================").color(DIM));
        sender.sendMessage(Component.text("Auto-Tune Quickstart Guide").color(HIGHLIGHT));
        sender.sendMessage(Component.text("=======================================").color(DIM));

        sender.sendMessage(blank());
        sender.sendMessage(heading("/shop - Buy Items"));
        sender.sendMessage(body("Browse all items. Each shows buy price, sell price, and a trend arrow."));
        sender.sendMessage(body("Prices update every 5 minutes. Green up = rising. Red down = falling."));

        sender.sendMessage(blank());
        sender.sendMessage(heading("/sell - Sell Items"));
        sender.sendMessage(body("Sell anything in your inventory at the current market price. Instant."));

        sender.sendMessage(blank());
        sender.sendMessage(heading("/price - Check Item Prices"));
        sender.sendMessage(body("Look up an item's buy price, sell price, spread, and trend."));
        sender.sendMessage(body("Example: "));
        sender.sendMessage(builder().content("  /price DIAMOND").color(ACCENT).build());

        sender.sendMessage(blank());
        sender.sendMessage(heading("/loan - Borrow to Buy Big"));
        sender.sendMessage(body("Take a loan for large purchases. Pay it back over time."));
        sender.sendMessage(body("Interest pauses if the economy gets unstable (circuit breaker)."));

        sender.sendMessage(blank());
        sender.sendMessage(heading("/transactions - Track Your Trades"));
        sender.sendMessage(body("See your full trading history with timestamps and prices."));

        sender.sendMessage(blank());
        sender.sendMessage(Component.text("=======================================").color(DIM));
        sender.sendMessage(tip("Prices move based on supply and demand. Buy when cheap, sell when high!"));
        sender.sendMessage(Component.text("=======================================").color(DIM));
    }

    private static Component blank() {
        return Component.text(" ");
    }

    private static Builder builder() {
        return Component.text();
    }

    private static TextComponent heading(String text) {
        return builder().content(text).color(EMERALD).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true).build();
    }

    private static TextComponent body(String text) {
        return builder().content(text).color(WHITE).build();
    }

    private static TextComponent tip(String text) {
        return builder().content("Tip: " + text).color(ACCENT).build();
    }
}
