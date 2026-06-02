package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.ui.TransactionHistoryGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

@Singleton
public class TransactionCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;

    @Inject
    public TransactionCommand(AutoTune plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Command("transactions")
    @Permission("autotune.transactions")
    public void viewTransactions(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new TransactionHistoryGui(plugin, player, TransactionHistoryGui.Mode.PLAYER, player.getUniqueId()).open();
    }
}
