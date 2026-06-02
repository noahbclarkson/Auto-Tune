package com.noahblclarkson.autotune.manager;

import com.noahblclarkson.autotune.AutoTune;
import org.bukkit.Server;

/**
 * Default {@link PluginAdapter} that wraps the real {@link AutoTune} plugin.
 * Used in production; injected by Guice.
 */
public class DefaultPluginAdapter implements PluginAdapter {

    private final AutoTune plugin;

    public DefaultPluginAdapter(AutoTune plugin) {
        this.plugin = plugin;
    }

    @Override
    public java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public Server getServer() {
        return plugin.getServer();
    }
}
