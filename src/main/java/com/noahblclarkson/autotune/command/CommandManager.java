package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;

@Singleton
@SuppressWarnings("PMD")
public class CommandManager {

    private final AutoTune plugin;

    @Inject
    public CommandManager(AutoTune plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        LegacyPaperCommandManager<CommandSender> commandManager;
        try {
            // Use createNative() which internally calls the constructor that nullifies
            // brigadierManagerHolder. Paper 1.21.11's Brigadier integration breaks
            // cloud-paper beta.14's @Argument parser — "Argument is not declared in syntax".
            // We additionally remove Brigadier capabilities to be safe.
            commandManager = LegacyPaperCommandManager.createNative(
                    plugin,
                    ExecutionCoordinator.simpleCoordinator()
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize command manager: " + e.getMessage());
            return;
        }

        // Remove Brigadier capabilities to prevent Paper 1.21.11 Brigadier API conflicts.
        // This forces Cloud to use its standard (non-Brigadier) argument parsing pipeline.
        removeCapabilityField(commandManager,
                org.incendo.cloud.bukkit.CloudBukkitCapabilities.BRIGADIER);
        removeCapabilityField(commandManager,
                org.incendo.cloud.bukkit.CloudBukkitCapabilities.NATIVE_BRIGADIER);

        // Register BigDecimal parser — Cloud core 2.0.0 ships double/float/int/long but not BigDecimal
        commandManager.parserRegistry().registerParser(
                ParserDescriptor.of(
                        new BigDecimalParserImpl(),
                        BigDecimal.class
                )
        );

        AnnotationParser<CommandSender> parser = new AnnotationParser<>(commandManager, CommandSender.class);

        Injector injector = plugin.getInjector();
        registerSafely(parser, injector, ShopCommand.class, "shop");
        registerSafely(parser, injector, SellCommand.class, "sell");
        registerSafely(parser, injector, AutosellCommand.class, "autosell");
        registerSafely(parser, injector, LoanCommand.class, "loan");
        registerSafely(parser, injector, TransactionCommand.class, "transactions");
        registerSafely(parser, injector, AdminCommand.class, "admin");
        registerSafely(parser, injector, AdminRecoveryCommand.class, "recovery");
        registerSafely(parser, injector, AdminTopCommand.class, "top");
        registerSafely(parser, injector, AuctionCommand.class, "auction");
        registerSafely(parser, injector, PriceAlertCommand.class, "alert");
        registerSafely(parser, injector, TreasuryCommand.class, "treasury");
        registerSafely(parser, injector, GuildCommand.class, "guild");
        registerSafely(parser, injector, EventCommand.class, "event");
        registerSafely(parser, injector, BadgeCommand.class, "badges");
        registerSafely(parser, injector, NewsCommand.class, "news");
        registerSafely(parser, injector, OnboardingCommand.class, "onboarding");
        registerSafely(parser, injector, MarketReportCommand.class, "market-report");
        registerSafely(parser, injector, CompareCommand.class, "compare");
        registerSafely(parser, injector, StreakCommand.class, "streak");
        registerSafely(parser, injector, ProfileCommand.class, "profile");
        registerSafely(parser, injector, PriceCommand.class, "price");
    }

    private void removeCapabilityField(LegacyPaperCommandManager<CommandSender> manager,
                                       org.incendo.cloud.bukkit.CloudBukkitCapabilities cap) {
        try {
            // The capabilities set is private in CommandManager. Access it via reflection.
            Class<?> clazz = manager.getClass();
            while (clazz != null) {
                try {
                    Field capField = clazz.getDeclaredField("capabilities");
                    capField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Set<org.incendo.cloud.CloudCapability> caps =
                            (Set<org.incendo.cloud.CloudCapability>) capField.get(manager);
                    boolean removed = caps.remove(cap);
                    if (removed) {
                        plugin.getLogger().info("Removed Brigadier capability: " + cap.name());
                    }
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove capability " + cap.name() + ": " + e.getMessage());
        }
    }

    private <T> void registerSafely(AnnotationParser<CommandSender> parser, Injector injector,
                                    Class<T> cls, String name) {
        try {
            parser.parse(injector.getInstance(cls));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register command '" + name + "': " + e.getMessage());
        }
    }

    private static final class BigDecimalParserImpl implements ArgumentParser<CommandSender, BigDecimal> {
        @Override
        public ArgumentParseResult<BigDecimal> parse(
                org.incendo.cloud.context.CommandContext<CommandSender> ctx,
                CommandInput input) {
            String text = input.readString();
            try {
                return ArgumentParseResult.success(new BigDecimal(text));
            } catch (NumberFormatException e) {
                return ArgumentParseResult.failure(e);
            }
        }
    }
}
