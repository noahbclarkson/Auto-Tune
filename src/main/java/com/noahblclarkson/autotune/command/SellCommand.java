package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.ui.SellGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

@Singleton
public class SellCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;

    @Inject
    public SellCommand(AutoTune plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Command("sell")
    @Permission("autotune.sell")
    public void openSellGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new SellGui(plugin, player).open();
    }
}
