package com.noahblclarkson.autotune.ui;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.listener.SellGuiListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class SellGui {

    private final AutoTune plugin;
    private final Player player;

    public SellGui(AutoTune plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        ConfigManager configManager = plugin.getConfigManager();
        String title = configManager.getConfig().gui().titles().sell();
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text(title));

        SellGuiListener listener = SellGuiListener.getInstance();
        if (listener != null) {
            listener.trackInventory(player.getUniqueId(), inventory);
        }

        player.openInventory(inventory);
        player.sendMessage(configManager.getMessage("sell.opened"));
    }
}
