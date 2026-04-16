package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.service.EconomicNewsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.context.CommandContext;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * /news — browse recent economic news history.
 *
 * Shows a scrollable chest GUI of recent news items broadcast by EconomicNewsService.
 * Items are shown newest-first with timestamp, message text, and a click action.
 */
@Singleton
@SuppressWarnings("PMD")
public class NewsCommand implements org.bukkit.event.Listener {

    private static final int GUI_SIZE = 54; // 6 rows
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault());
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final EconomicNewsService newsService;
    private final AutoTune plugin;

    @Inject
    public NewsCommand(EconomicNewsService newsService, AutoTune plugin) {
        this.newsService = newsService;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Command("news")
    public void onNews(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        openGui(player);
    }

    private void openGui(Player player) {
        List<EconomicNewsService.RecentNewsItem> items = newsService.getRecentNews();
        int rows = Math.max(1, (items.size() / 9) + 1);
        int size = Math.min(GUI_SIZE, Math.max(9, rows * 9));

        Inventory gui = Bukkit.createInventory(null, size,
                Component.text("📰 Economic News", NamedTextColor.GOLD));

        if (items.isEmpty()) {
            gui.setItem(size / 2, makeEmptyItem());
        } else {
            for (int i = 0; i < items.size() && i < size; i++) {
                gui.setItem(i, makeNewsItem(items.get(i)));
            }
        }

        player.openInventory(gui);
    }

    private ItemStack makeNewsItem(EconomicNewsService.RecentNewsItem item) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();

        String timeStr = TIME_FORMAT.format(item.timestamp());
        String plainText = item.text();
        if (plainText.length() > 50) {
            plainText = plainText.substring(0, 47) + "...";
        }

        // Name: [time] — [message text, color preserved via MiniMessage]
        Component coloredMsg;
        try {
            coloredMsg = MINI.deserialize(item.text()).colorIfAbsent(item.color());
        } catch (Exception e) {
            coloredMsg = Component.text(item.text(), item.color());
        }

        Component name = Component.text("📋 ", NamedTextColor.WHITE)
                .append(Component.text(timeStr, NamedTextColor.DARK_GRAY))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(coloredMsg);
        meta.displayName(name);

        Component clickHint = Component.text("Click to run: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(item.clickCommand(), NamedTextColor.AQUA));

        meta.lore(List.of(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<dark_gray>Click to run: <aqua>" + item.clickCommand()),
                Component.text(item.hoverText())
        ));

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack makeEmptyItem() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("No Recent News", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.text("No economic news has been broadcast yet.", NamedTextColor.DARK_GRAY),
                Component.text("Check back after some market activity!", NamedTextColor.DARK_GRAY)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().title().toString();
        if (!title.contains("Economic News")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            player.closeInventory();
            return;
        }

        List<String> lore = meta.getLore();
        if (lore != null && !lore.isEmpty()) {
            // First lore line: "Click to run: /command"
            String clickLine = lore.get(0);
            if (clickLine.startsWith("Click to run: ")) {
                String command = clickLine.substring("Click to run: ".length()).trim();
                if (!command.isEmpty()) {
                    Bukkit.getServer().dispatchCommand(player, command);
                }
            }
        }

        player.closeInventory();
    }
}
