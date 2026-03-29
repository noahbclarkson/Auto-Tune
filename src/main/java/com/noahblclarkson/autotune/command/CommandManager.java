package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.SenderMapper;

@Singleton
public class CommandManager {

    private final AutoTune plugin;

    @Inject
    public CommandManager(AutoTune plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        LegacyPaperCommandManager<CommandSender> commandManager;
        try {
            // Use the standard constructor (not createNative) — this avoids
            // Brigadier registration entirely and routes commands through
            // Paper's standard command dispatch. Commands still work via
            // Cloud's annotation parser, but without native Brigadier
            // command tree support.
            commandManager = new LegacyPaperCommandManager<>(
                    plugin,
                    ExecutionCoordinator.simpleCoordinator(),
                    SenderMapper.identity()
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize command manager: " + e.getMessage());
            return;
        }

        AnnotationParser<CommandSender> parser = new AnnotationParser<>(commandManager, CommandSender.class);

        Injector injector = plugin.getInjector();
        registerSafely(parser, injector, ShopCommand.class, "shop");
        registerSafely(parser, injector, SellCommand.class, "sell");
        registerSafely(parser, injector, AutosellCommand.class, "autosell");
        registerSafely(parser, injector, LoanCommand.class, "loan");
        registerSafely(parser, injector, TransactionCommand.class, "transactions");
        registerSafely(parser, injector, AdminCommand.class, "admin");
        registerSafely(parser, injector, AuctionCommand.class, "auction");
        registerSafely(parser, injector, PriceAlertCommand.class, "alert");
        registerSafely(parser, injector, TreasuryCommand.class, "treasury");
        registerSafely(parser, injector, GuildCommand.class, "guild");
        registerSafely(parser, injector, EventCommand.class, "event");
    }

    private <T> void registerSafely(AnnotationParser<CommandSender> parser, Injector injector, Class<T> cls, String name) {
        try {
            parser.parse(injector.getInstance(cls));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register command '" + name + "': " + e.getMessage());
        }
    }
}
