package com.noahblclarkson.autotune.manager;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Abstracts the plugin-hostile parts of the Paper/Bukkit plugin API
 * (specifically {@link org.bukkit.plugin.java.JavaPlugin}) so that
 * {@link MarketEngine} can be unit-tested without needing a full server
 * environment.
 *
 * Production code injects a {@link DefaultPluginAdapter}.
 * Tests inject a lightweight fake.
 */
public interface PluginAdapter {

    Logger getLogger();

    Server getServer();

    default int getOnlineCount() {
        Collection<? extends Player> players = getServer().getOnlinePlayers();
        return players != null ? players.size() : 0;
    }
}
