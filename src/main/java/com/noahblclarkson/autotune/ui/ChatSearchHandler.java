package com.noahblclarkson.autotune.ui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class ChatSearchHandler implements Listener {

    private final Plugin plugin;
    private final Player player;
    private final Consumer<String> onInput;
    private final Runnable onCancel;
    private final BukkitTask timeoutTask;

    public ChatSearchHandler(Plugin plugin, Player player, long timeoutTicks, Consumer<String> onInput, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.onInput = onInput;
        this.onCancel = onCancel;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            unregister();
            plugin.getServer().getScheduler().runTask(plugin, onCancel);
        }, timeoutTicks);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        unregister();

        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getServer().getScheduler().runTask(plugin, onCancel);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> onInput.accept(message));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            unregister();
        }
    }

    private void unregister() {
        timeoutTask.cancel();
        HandlerList.unregisterAll(this);
    }
}
