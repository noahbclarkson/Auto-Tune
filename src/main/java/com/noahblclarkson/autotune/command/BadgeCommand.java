package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.model.BadgeType;
import com.noahblclarkson.autotune.service.BadgeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Player achievement badge commands.
 * /badges — open badge GUI
 * /badges list — text list
 * /badges stats — server-wide statistics
 */
@Singleton
public class BadgeCommand implements Listener {

    private static final String GUI_TITLE = "§6§l🏆 Player Badges";
    private static final int GUI_SIZE = 54; // 6 rows

    private final BadgeService badgeService;
    private final PlayerRepository playerRepo;
    private final DatabaseManager db;
    private final AutoTune plugin;

    @Inject
    public BadgeCommand(BadgeService badgeService, PlayerRepository playerRepo, DatabaseManager db, AutoTune plugin) {
        this.badgeService = badgeService;
        this.playerRepo = playerRepo;
        this.db = db;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static Component makeTitle(String text) {
        return TextComponent.ofChildren(
                Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        );
    }

    @Command("badges")
    public void badgesHelp(Player sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(makeTitle("Player Badges"));
        sender.sendMessage(Component.text("/badges", NamedTextColor.YELLOW)
                .append(Component.text(" — open your badge collection", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/badges list", NamedTextColor.YELLOW)
                .append(Component.text(" — text list of badges", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/badges stats", NamedTextColor.YELLOW)
                .append(Component.text(" — server badge statistics", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("badges list")
    public void badgesList(Player sender) {
        List<BadgeService.BadgeWithStatus> badges = badgeService.getBadgesWithStatus(sender.getUniqueId());
        int earned = (int) badges.stream().filter(BadgeService.BadgeWithStatus::earned).count();

        sender.sendMessage(Component.empty());
        sender.sendMessage(makeTitle("Badge Collection"));
        sender.sendMessage(Component.text("  " + earned + "/" + BadgeType.values().length + " badges earned", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());

        for (BadgeService.BadgeWithStatus bws : badges) {
            BadgeType type = bws.type();
            String status;
            Component hoverText;
            TextColor color;

            if (bws.earned()) {
                String earnedStr = bws.earnedAt() != null
                        ? DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(bws.earnedAt())
                        : "Unknown";
                status = "§a✔ §7" + earnedStr;
                hoverText = Component.text("Earned on " + earnedStr + "\n" + type.getDescription(), NamedTextColor.GREEN);
                color = NamedTextColor.GREEN;
            } else {
                status = "§8✗ §7Not yet earned";
                hoverText = Component.text("Locked\n" + type.getDescription(), NamedTextColor.GRAY);
                color = NamedTextColor.DARK_GRAY;
            }

            Component line = Component.text()
                    .append(Component.text("  " + getBadgeEmoji(type) + " ", TextColor.fromHexString("#FFD700")))
                    .append(Component.text(type.getDisplayName(), color))
                    .append(Component.text(" " + status, NamedTextColor.DARK_GRAY))
                    .hoverEvent(HoverEvent.showText(hoverText))
                    .build();

            sender.sendMessage(line);
        }
        sender.sendMessage(Component.empty());
    }

    @Command("badges stats")
    public void badgesStats(Player sender) {
        var badgeRepo = new com.noahblclarkson.autotune.database.BadgeRepository(db);
        List<BadgeType> allTypes = Arrays.asList(BadgeType.values());

        sender.sendMessage(Component.empty());
        sender.sendMessage(makeTitle("Server Badge Statistics"));
        sender.sendMessage(Component.empty());

        // Total badges awarded server-wide
        long total = badgeRepo.getAll().size();
        sender.sendMessage(Component.text("  Total badges awarded: ", NamedTextColor.GRAY)
                .append(Component.text(NumberFormat.getNumberInstance().format(total), NamedTextColor.GOLD)));

        sender.sendMessage(Component.empty());

        // Per-badge counts
        for (BadgeType type : allTypes) {
            int count = badgeRepo.getEarnerCount(type);
            String pct = total > 0
                    ? String.format(" (%.1f%% of all badges)", 100.0 * count / total)
                    : "";
            sender.sendMessage(Component.text()
                    .append(Component.text("  " + getBadgeEmoji(type) + " ", TextColor.fromHexString("#FFD700")))
                    .append(Component.text(type.getDisplayName() + ": ", NamedTextColor.YELLOW))
                    .append(Component.text(count + " players earned", NamedTextColor.GRAY))
                    .append(Component.text(pct, NamedTextColor.DARK_GRAY))
                    .build());
        }
        sender.sendMessage(Component.empty());
    }

    @Command("badges open")
    public void openBadgesGui(Player sender) {
        openGui(sender.getUniqueId(), sender);
    }

    @Suggestions("badges-target-suggestion")
    public List<String> badgesTargetSuggestion(CommandContext<?> ctx, String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Command("badges open [player]")
    public void openBadgesGuiOther(Player sender, @Argument("player") @Default("") String playerName) {
        UUID targetUuid;
        if (playerName.isBlank()) {
            targetUuid = sender.getUniqueId();
        } else {
            Player target = Bukkit.getPlayer(playerName);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
                return;
            }
            targetUuid = target.getUniqueId();
        }
        openGui(targetUuid, sender);
    }

    private void openGui(UUID playerUuid, Player opener) {
        List<BadgeService.BadgeWithStatus> badges = badgeService.getBadgesWithStatus(playerUuid);
        String displayName = Bukkit.getPlayer(playerUuid) != null
                ? Bukkit.getPlayer(playerUuid).getName()
                : playerUuid.toString();

        // Count earned
        int earned = (int) badges.stream().filter(BadgeService.BadgeWithStatus::earned).count();
        String title = "§6§l🏆 " + displayName + "'s Badges §7" + earned + "/" + BadgeType.values().length;

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, net.kyori.adventure.text.Component.text(title));

        // Fill first row with decorative border
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
        }

        // Place badges in remaining slots (rows 2-6)
        // Order: first row of badges starts at slot 9
        for (int i = 0; i < badges.size(); i++) {
            BadgeService.BadgeWithStatus bws = badges.get(i);
            int slot = 9 + i;
            if (slot >= GUI_SIZE) break;

            ItemStack icon = badgeService.badgeIcon(bws.type(), bws.earned(), bws.earnedAt());
            gui.setItem(slot, icon);
        }

        // Info item at bottom-right corner
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eBadge Guide");
        infoMeta.setLore(List.of(
                "§7Click a badge to see its description.",
                "§7Green = earned. Gray = locked.",
                "§7",
                "§7Earn badges by participating in the",
                "§7server economy!"
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(53, info); // Bottom right

        opener.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().title().toString();
        if (!title.contains("Badges")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        if (meta.getDisplayName().equals(" ") || meta.getDisplayName().contains("Badge Guide")) return;

        // Get lore for description
        List<String> lore = meta.getLore();
        if (lore != null && !lore.isEmpty()) {
            event.getWhoClicked().sendMessage(
                    Component.text("🏆 ", TextColor.fromHexString("#FFD700"))
                            .append(Component.text(meta.getDisplayName().replace("§", ""), NamedTextColor.GOLD))
            );
        }
    }

    private String getBadgeEmoji(BadgeType type) {
        return switch (type) {
            case FIRST_SALE -> "📜";
            case LOAN_SHARK -> "🦈";
            case MARKET_MAKER -> "💹";
            case HOARDER -> "📦";
            case TREND_SPOTTER -> "🔭";
            case STABLE_HAND -> "🤝";
            case BIG_SPENDER -> "💰";
            case DIVERSIFIED -> "🎨";
            case CENTURION -> "⭐";
            case FIRST_BUYER -> "🛒";
            case LOAN_TAKER -> "📄";
        };
    }
}
