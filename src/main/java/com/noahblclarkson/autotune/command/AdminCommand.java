package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.PriceOverrideRepository;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.PriceOverride;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class AdminCommand {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final EconomyMetricsManager metricsManager;
    private final PriceOverrideRepository overrideRepo;

    @Inject
    public AdminCommand(
            AutoTune plugin,
            ConfigManager configManager,
            ShopManager shopManager,
            MarketEngine marketEngine,
            EconomyMetricsManager metricsManager,
            PriceOverrideRepository overrideRepo
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.metricsManager = metricsManager;
        this.overrideRepo = overrideRepo;
    }

    @Command("autotune admin")
    @Permission("autotune.admin")
    public void adminHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune Admin", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/at admin info", NamedTextColor.YELLOW)
                .append(Component.text(" — Economy overview and health", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin stats", NamedTextColor.YELLOW)
                .append(Component.text(" — Detailed market statistics", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin market freeze", NamedTextColor.YELLOW)
                .append(Component.text(" — Freeze/unfreeze price engine", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price set <item> <price> [hours]", NamedTextColor.YELLOW)
                .append(Component.text(" — Override item price", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price remove <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Remove price override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin price list", NamedTextColor.YELLOW)
                .append(Component.text(" — List all active overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config and caches", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin info")
    @Permission("autotune.admin")
    public void adminInfo(CommandSender sender) {
        boolean frozen = marketEngine.isFrozen();
        Map<Integer, PriceOverride> overrides = marketEngine.getActiveOverrides();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auto-Tune Market Status", NamedTextColor.GOLD, TextDecoration.BOLD));

        Component frozenStatus = frozen
                ? Component.text("FROZEN", NamedTextColor.RED)
                : Component.text("Active", NamedTextColor.GREEN);
        sender.sendMessage(Component.text("  Market: ").color(NamedTextColor.GRAY)
                .append(frozenStatus));

        Component overrideStatus = overrides.isEmpty()
                ? Component.text("None", NamedTextColor.GRAY)
                : Component.text(overrides.size() + " active", NamedTextColor.YELLOW);
        sender.sendMessage(Component.text("  Overrides: ").color(NamedTextColor.GRAY)
                .append(overrideStatus));

        // GDP and debt from latest snapshot
        metricsManager.getLatestSnapshot().ifPresent(snap -> {
            sender.sendMessage(Component.text("  GDP (24h): ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.gdp()), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("  Total Debt: ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.totalDebt()),
                            snap.totalDebt().compareTo(BigDecimal.ZERO) > 0 ? NamedTextColor.RED : NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("  Active Loans: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(snap.activeLoans()), NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("  Online Players: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(snap.playerCount()), NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("  24h Volume: ").color(NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(snap.transactionVolume()), NamedTextColor.AQUA)));
        });

        if (!metricsManager.getLatestSnapshot().isPresent()) {
            sender.sendMessage(Component.text("  No economy snapshot available yet.", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin stats")
    @Permission("autotune.admin")
    public void adminStats(CommandSender sender) {
        List<ShopItem> allItems = shopManager.getAllItems();
        BigDecimal totalMarketCap = BigDecimal.ZERO;
        int updatedItems = 0;
        double avgSpread = 0;
        int spreadCount = 0;

        for (ShopItem item : allItems) {
            BigDecimal price = marketEngine.getCurrentPrice(item.id());
            MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());
            totalMarketCap = totalMarketCap.add(price);
            updatedItems++;
            avgSpread += spread.bpd().doubleValue() + spread.spd().doubleValue();
            spreadCount += 2;
        }

        double globalMult = marketEngine.getGlobalVolumeMultiplier();
        String volLabel = globalMult > 1.05 ? "High" : (globalMult < 0.95 ? "Low" : "Normal");

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Market Statistics", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  Items tracked: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(updatedItems), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Total market cap: ").color(NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(totalMarketCap), NamedTextColor.GREEN)));
        if (spreadCount > 0) {
            sender.sendMessage(Component.text("  Avg spread (BPD+SPD): ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2f%%", (avgSpread / spreadCount) * 100), NamedTextColor.YELLOW)));
        }
        sender.sendMessage(Component.text("  Volume activity: ").color(NamedTextColor.GRAY)
                .append(Component.text(volLabel + " (" + String.format("%.2fx", globalMult) + ")",
                        globalMult > 1.05 ? NamedTextColor.RED : (globalMult < 0.95 ? NamedTextColor.YELLOW : NamedTextColor.GREEN))));
        sender.sendMessage(Component.text("  Inflation: ").color(NamedTextColor.GRAY)
                .append(Component.text(metricsManager.getInflationLabel(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin reload")
    @Permission("autotune.admin")
    public void adminReload(CommandSender sender) {
        try {
            plugin.reload();
            sender.sendMessage(Component.text("Auto-Tune config and caches reloaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    // ─── Market freeze subcommand ──────────────────────────────────────────────

    @Command("autotune admin market")
    @Permission("autotune.admin")
    public void marketHelp(CommandSender sender) {
        boolean frozen = marketEngine.isFrozen();
        Component status = frozen
                ? Component.text("FROZEN", NamedTextColor.RED)
                .append(Component.text(" — prices will not update", NamedTextColor.GRAY))
                : Component.text("Active", NamedTextColor.GREEN)
                .append(Component.text(" — prices update normally", NamedTextColor.GRAY));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Market Engine: ", NamedTextColor.GOLD).append(status));
        sender.sendMessage(Component.text("Use /at admin market freeze to toggle.", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin market freeze")
    @Permission("autotune.admin")
    public void marketFreeze(CommandSender sender) {
        boolean nowFrozen = !marketEngine.isFrozen();
        marketEngine.setFrozen(nowFrozen);

        if (nowFrozen) {
            sender.sendMessage(Component.text("Market frozen. Prices will not update until unfrozen.",
                    NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Market unfrozen. Prices will resume updating.",
                    NamedTextColor.GREEN));
        }
    }

    // ─── Price override subcommands ───────────────────────────────────────────

    @Suggestions("price-override-material")
    public List<String> suggestMaterials(CommandContext<?> ctx, String input) {
        return shopManager.getAllItems().stream()
                .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.contains(input.toLowerCase(Locale.ROOT)))
                .limit(20)
                .toList();
    }

    @Command("autotune admin price set")
    @Permission("autotune.admin")
    public void priceSet(
            CommandSender sender,
            @Argument("material") String materialName,
            @Argument("price") BigDecimal price,
            @Argument("hours") Optional<Integer> hoursArg
    ) {
        Integer hours = hoursArg.orElse(null);
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("Price must be greater than 0.", NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        ShopItem item;
        if (shopItem.isEmpty()) {
            item = shopManager.addItem(mat, price, "admin");
        } else {
            item = shopItem.get();
        }

        Instant expiresAt = null;
        if (hours != null && hours > 0) {
            expiresAt = Instant.now().plusSeconds(hours * 3600L);
        }

        UUID setBy = (sender instanceof Player p) ? p.getUniqueId() : null;
        overrideRepo.setOverride(item.id(), price, expiresAt, setBy);
        marketEngine.refreshOverrideCache();

        String expiryStr = (hours != null && hours > 0) ? " for " + hours + "h" : " (permanent)";
        sender.sendMessage(Component.text("Override set for " + item.getDisplayNameOrMaterial()
                + ": " + configManager.formatCurrency(price) + expiryStr, NamedTextColor.GREEN));
    }

    @Command("autotune admin price remove")
    @Permission("autotune.admin")
    public void priceRemove(CommandSender sender, @Argument("material") String materialName) {
        org.bukkit.Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<ShopItem> shopItem = shopManager.getItemByMaterial(mat);
        if (shopItem.isEmpty()) {
            sender.sendMessage(Component.text("Material not in shop: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<PriceOverride> existing = marketEngine.getOverride(shopItem.get().id());
        if (existing.isEmpty()) {
            sender.sendMessage(Component.text("No active override for " + mat.name() + ".", NamedTextColor.YELLOW));
            return;
        }

        overrideRepo.removeOverride(shopItem.get().id());
        marketEngine.refreshOverrideCache();
        sender.sendMessage(Component.text("Override removed for " + mat.name() + ".", NamedTextColor.GREEN));
    }

    @Command("autotune admin price list")
    @Permission("autotune.admin")
    public void priceList(CommandSender sender) {
        Map<Integer, PriceOverride> all = overrideRepo.getAllOverrides();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No price overrides active.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Price Overrides", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (Map.Entry<Integer, PriceOverride> entry : all.entrySet()) {
            int itemId = entry.getKey();
            PriceOverride over = entry.getValue();

            Optional<ShopItem> item = shopManager.getItemById(itemId);
            String itemName = item.map(ShopItem::getDisplayNameOrMaterial).orElse("#" + itemId);
            String priceStr = configManager.formatCurrency(over.price());
            String expiryStr = over.formatExpiry();
            String expiredLabel = over.isExpired() ? " [EXPIRED]" : "";

            Component line = Component.text("  " + itemName + ": " + priceStr
                    + " (expires: " + expiryStr + ")" + expiredLabel,
                    over.isExpired() ? NamedTextColor.GRAY : NamedTextColor.AQUA);
            sender.sendMessage(line);
        }

        sender.sendMessage(Component.empty());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private org.bukkit.Material matchMaterial(String name) {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(name);
        }
        return mat;
    }
}
