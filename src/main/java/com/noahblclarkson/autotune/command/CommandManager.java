package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

@Singleton
public class CommandManager {

    private final AutoTune plugin;

    @Inject
    public CommandManager(AutoTune plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        LegacyPaperCommandManager<CommandSender> commandManager = LegacyPaperCommandManager.createNative(
                plugin,
                ExecutionCoordinator.simpleCoordinator()
        );

        if (commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            commandManager.registerBrigadier();
        } else if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            commandManager.registerAsynchronousCompletions();
        }

        AnnotationParser<CommandSender> parser = new AnnotationParser<>(commandManager, CommandSender.class);

        Injector injector = plugin.getInjector();
        registerSafely(parser, injector, ShopCommand.class, "shop");
        registerSafely(parser, injector, SellCommand.class, "sell");
        registerSafely(parser, injector, AutosellCommand.class, "autosell");
        registerSafely(parser, injector, LoanCommand.class, "loan");
        registerSafely(parser, injector, TransactionCommand.class, "transactions");
    }

    private <T> void registerSafely(AnnotationParser<CommandSender> parser, Injector injector, Class<T> cls, String name) {
        try {
            parser.parse(injector.getInstance(cls));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register command '" + name + "': " + e.getMessage());
        }
    }
}
