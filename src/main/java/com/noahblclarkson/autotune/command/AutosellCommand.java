package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.ui.AutosellGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

@Singleton
public class AutosellCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final AutosellManager autosellManager;

    @Inject
    public AutosellCommand(AutoTune plugin, ConfigManager configManager, AutosellManager autosellManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.autosellManager = autosellManager;
    }

    @Command("autosell")
    @Permission("autotune.autosell")
    public void openAutosellGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new AutosellGui(plugin, player).open();
    }

    @Command("autosell sell")
    @Permission("autotune.autosell")
    public void sellInventory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        autosellManager.sellInventory(player);
    }
}
