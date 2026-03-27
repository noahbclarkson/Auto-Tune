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
        sender.sendMessage(Component.text("Auto-Tune Admin", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
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
        sender.sendMessage(Component.text("/at admin item spread <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Per-item base spread override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item maxchange <item> <value>", NamedTextColor.YELLOW)
                .append(Component.text(" — Per-item max price change override", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item info <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Show item config & overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin item reset <item>", NamedTextColor.YELLOW)
                .append(Component.text(" — Clear all per-item overrides", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config and caches", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/at admin transactions [player]", NamedTextColor.YELLOW)
                .append(Component.text(" — View recent transaction history", NamedTextColor.GRAY)));
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

    @Command("autotune admin transactions")
    @Permission("autotune.admin")
    public void adminTransactions(CommandSender sender, @Argument(value = "player", suggestions = "minecraft-player") Optional<String> playerNameArg) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be used as a player.", NamedTextColor.RED));
            return;
        }

        UUID filterUuid = null;

        if (!playerNameArg.isEmpty() && !playerNameArg.get().isBlank()) {
            String playerName = playerNameArg.get();
            // Look up the player's UUID from their name
            org.bukkit.OfflinePlayer offlineTarget = org.bukkit.Bukkit.getOfflinePlayerIfCached(playerName);
            if (offlineTarget == null) {
                sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
                return;
            }
            filterUuid = offlineTarget.getUniqueId();
            sender.sendMessage(Component.text("Opening transaction history for " + offlineTarget.getName() + "...", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Opening full transaction history...", NamedTextColor.GRAY));
        }

        // Open ADMIN-mode transaction history GUI, optionally filtered to one player
        new com.noahblclarkson.autotune.ui.TransactionHistoryGui(
                plugin, player,
                com.noahblclarkson.autotune.ui.TransactionHistoryGui.Mode.ADMIN,
                filterUuid
        ).open();
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

    // ─── Per-item config override subcommands ──────────────────────────────────

    @Command("autotune admin item spread")
    @Permission("autotune.admin")
    public void itemSpread(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
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

        if (value <= 0 || value > 1.0) {
            sender.sendMessage(Component.text("Base spread must be between 0.01 and 1.0 (e.g. 0.20 = 20%).",
                    NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setBaseSpreadOverride(item.id(), value);
        sender.sendMessage(Component.text("Base spread override for " + item.getDisplayNameOrMaterial()
                + " set to " + String.format("%.2f%%", value * 100), NamedTextColor.GREEN));
    }

    @Command("autotune admin item maxchange")
    @Permission("autotune.admin")
    public void itemMaxChange(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName,
            @Argument("value") double value
    ) {
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

        if (value <= 0 || value > 50.0) {
            sender.sendMessage(Component.text("Max price change must be between 0.01 and 50.0 (percent).",
                    NamedTextColor.RED));
            return;
        }

        ShopItem item = shopItem.get();
        shopManager.setMaxPriceChangeOverride(item.id(), value);
        sender.sendMessage(Component.text("Max price change override for " + item.getDisplayNameOrMaterial()
                + " set to " + String.format("%.2f%%", value), NamedTextColor.GREEN));
    }

    @Command("autotune admin item info")
    @Permission("autotune.admin")
    public void itemInfo(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
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

        ShopItem item = shopItem.get();
        BigDecimal currentPrice = marketEngine.getCurrentPrice(item.id());
        MarketEngine.SpreadResult spread = marketEngine.getSpread(item.id());
        double globalMaxChange = configManager.getConfig().economy().maxPriceChangePercent();
        double globalBaseSpread = configManager.getConfig().economy().spread().baseSpread();

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(item.getDisplayNameOrMaterial(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" (" + item.section() + ")", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("  Price: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.formatCurrency(currentPrice), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  Spread: ", NamedTextColor.GRAY)
                .append(Component.text("BPD " + String.format("%.2f%%", spread.bpd().doubleValue() * 100)
                        + " / SPD " + String.format("%.2f%%", spread.spd().doubleValue() * 100), NamedTextColor.AQUA)));

        // Base spread override
        String spreadStr = item.baseSpreadOverride() != null
                ? String.format("%.2f%%", item.baseSpreadOverride() * 100) + " (override)"
                : String.format("%.2f%%", globalBaseSpread * 100) + " (global)";
        sender.sendMessage(Component.text("  Base Spread: ", NamedTextColor.GRAY)
                .append(Component.text(spreadStr,
                        item.baseSpreadOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Max price change override
        String maxChangeStr = item.maxPriceChangeOverride() != null
                ? String.format("%.2f%%", item.maxPriceChangeOverride()) + " (override)"
                : String.format("%.2f%%", globalMaxChange) + " (global)";
        sender.sendMessage(Component.text("  Max Change: ", NamedTextColor.GRAY)
                .append(Component.text(maxChangeStr,
                        item.maxPriceChangeOverride() != null ? NamedTextColor.YELLOW : NamedTextColor.WHITE)));

        // Price override
        Optional<PriceOverride> priceOverride = marketEngine.getOverride(item.id());
        if (priceOverride.isPresent()) {
            PriceOverride over = priceOverride.get();
            sender.sendMessage(Component.text("  Price Override: ", NamedTextColor.GRAY)
                    .append(Component.text(configManager.formatCurrency(over.price())
                            + " (expires: " + over.formatExpiry() + ")", NamedTextColor.YELLOW)));
        }

        sender.sendMessage(Component.text("  Buyable: ", NamedTextColor.GRAY)
                .append(Component.text(shopManager.isBuyable(item) ? "Yes" : "No",
                        shopManager.isBuyable(item) ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.empty());
    }

    @Command("autotune admin item reset")
    @Permission("autotune.admin")
    public void itemReset(
            CommandSender sender,
            @Argument(value = "material", suggestions = "price-override-material") String materialName
    ) {
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

        ShopItem item = shopItem.get();
        shopManager.setBaseSpreadOverride(item.id(), null);
        shopManager.setMaxPriceChangeOverride(item.id(), null);
        sender.sendMessage(Component.text("All per-item overrides cleared for "
                + item.getDisplayNameOrMaterial() + ". Using global config values.", NamedTextColor.GREEN));
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
