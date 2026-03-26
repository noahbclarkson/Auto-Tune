package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.ui.MarketHistoryGui;
import com.noahblclarkson.autotune.ui.ShopGui;
import com.noahblclarkson.autotune.ui.TrendsGui;
import com.noahblclarkson.autotune.ui.TransactionHistoryGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

@Singleton
public class ShopCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;

    @Inject
    public ShopCommand(
            AutoTune plugin,
            ConfigManager configManager,
            DatabaseManager databaseManager,
            ShopManager shopManager,
            MarketEngine marketEngine
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
    }

    @Suggestions("shop-materials")
    public List<String> shopMaterialSuggestions(CommandContext<CommandSender> context, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return shopManager.getAllItems().stream()
                .map(item -> item.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(lower))
                .toList();
    }

    @Suggestions("buyable-states")
    public List<String> buyableStateSuggestions(CommandContext<CommandSender> context, String input) {
        return List.of("true", "false", "auto");
    }

    @Suggestions("shop-sections")
    public List<String> shopSectionSuggestions(CommandContext<CommandSender> context, String input) {
        ConfigurationSection sectionConfig = plugin.getConfig().getConfigurationSection("sections");
        if (sectionConfig == null) {
            return List.of("all");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        return new ArrayList<>(sectionConfig.getKeys(false)).stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    @Command("shop")
    @Permission("autotune.shop")
    public void openShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new ShopGui(plugin, player).open();
    }

    @Command("shop search <query>")
    @Permission("autotune.shop")
    public void searchShop(CommandSender sender, @Argument("query") String query) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            player.sendMessage(configManager.getMessage("general.invalid-query"));
            return;
        }

        shopManager.searchAsync(trimmed).thenAccept(results ->
                databaseManager.runOnMain(() -> {
                    if (results == null) {
                        player.sendMessage(configManager.getMessage("error.database"));
                        return;
                    }
                    if (results.isEmpty()) {
                        player.sendMessage(configManager.getMessage("shop.search-no-results",
                                Map.of("query", trimmed)));
                    }
                    new ShopGui(plugin, player).openSearchResults(trimmed, results);
                })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to search shop", ex);
            return null;
        });
    }

    @Command("shop trends")
    @Permission("autotune.shop")
    public void showTrends(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new TrendsGui(plugin, player).open();
    }

    @Command("shop history")
    @Permission("autotune.shop")
    public void showHistoryBrowser(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new MarketHistoryGui(plugin, player).openBrowser();
    }

    @Command("shop history <material>")
    @Permission("autotune.shop")
    public void showHistory(CommandSender sender, @Argument(value = "material", suggestions = "shop-materials") Material material) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

        Optional<ShopItem> item = shopManager.getItemByMaterial(material);
        if (item.isEmpty()) {
            player.sendMessage(configManager.getMessage("general.invalid-item"));
            return;
        }

        // Open the GUI detail view directly for this item
        new MarketHistoryGui(plugin, player).openDetailView(item.get());
    }

    @Command("shop reload")
    @Permission("autotune.admin.reload")
    public void reloadCommand(CommandSender sender) {
        try {
            plugin.reload();
            sender.sendMessage(configManager.getMessage("general.reload-success"));
        } catch (Exception e) {
            sender.sendMessage(configManager.getMessage("general.reload-fail"));
            plugin.getLogger().warning("Failed to reload: " + e.getMessage());
        }
    }

    @Command("shop admin help")
    @Permission("autotune.admin")
    public void adminHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Admin Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/shop admin setprice <material> <price>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set or add item price", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop admin removeitem <material>", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove item from shop", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop admin setbuyable <material> <state>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set buyable state (true/false/auto)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop admin additem <price> <section>", NamedTextColor.YELLOW)
                .append(Component.text(" - Add held item to shop", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop admin debugprice <material>", NamedTextColor.YELLOW)
                .append(Component.text(" - View price debug info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop admin transactions", NamedTextColor.YELLOW)
                .append(Component.text(" - View all transactions", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/shop reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("shop admin setprice <material> <price>")
    @Permission("autotune.admin")
    public void setPrice(CommandSender sender, @Argument(value = "material", suggestions = "shop-materials") Material material, @Argument("price") double price) {
        Optional<ShopItem> item = shopManager.getItemByMaterial(material);
        if (item.isEmpty()) {
            databaseManager.runAsync(() ->
                            shopManager.addItem(material, BigDecimal.valueOf(price), "misc"))
                    .thenRun(() -> databaseManager.runOnMain(() ->
                            sender.sendMessage(configManager.getMessage("admin.item-added",
                                    Map.of("item", material.name())))))
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to add item", ex);
                        return null;
                    });
        } else {
            databaseManager.runAsync(() ->
                            shopManager.setPrice(item.get().id(), BigDecimal.valueOf(price)))
                    .thenRun(() -> databaseManager.runOnMain(() ->
                            sender.sendMessage(configManager.getMessage("admin.price-set", Map.of(
                                    "item", material.name(),
                                    "price", configManager.formatCurrency(price)
                            )))))
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to set price", ex);
                        return null;
                    });
        }
    }

    @Command("shop admin removeitem <material>")
    @Permission("autotune.admin.removeitem")
    public void removeItem(CommandSender sender, @Argument(value = "material", suggestions = "shop-materials") Material material) {
        Optional<ShopItem> item = shopManager.getItemByMaterial(material);
        if (item.isEmpty()) {
            sender.sendMessage(configManager.getMessage("general.invalid-item"));
            return;
        }

        databaseManager.runAsync(() -> shopManager.removeItem(item.get().id()))
                .thenRun(() -> databaseManager.runOnMain(() ->
                        sender.sendMessage(configManager.getMessage("admin.item-removed",
                                Map.of("item", material.name())))))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to remove item", ex);
                    return null;
                });
    }

    @Command("shop admin debugprice <material>")
    @Permission("autotune.admin.debug")
    public void debugPrice(CommandSender sender, @Argument(value = "material", suggestions = "shop-materials") Material material) {
        Optional<ShopItem> item = shopManager.getItemByMaterial(material);
        if (item.isEmpty()) {
            sender.sendMessage(configManager.getMessage("general.invalid-item"));
            return;
        }

        ShopItem shopItem = item.get();
        MarketEngine.SpreadResult spread = marketEngine.getSpread(shopItem.id());
        sender.sendMessage(Component.text("=== Price Debug: " + material.name() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + shopItem.id(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Price: " + configManager.formatCurrency(shopItem.price()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Buy Price: " + configManager.formatCurrency(marketEngine.getBuyPrice(shopItem)), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Sell Price: " + configManager.formatCurrency(marketEngine.getSellPrice(shopItem)), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("BPD: " + spread.bpd().multiply(BigDecimal.valueOf(100)) + "%", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("SPD: " + spread.spd().multiply(BigDecimal.valueOf(100)) + "%", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Trend: " + marketEngine.getTrendDirection(shopItem.id())
                + " (streak: " + marketEngine.getTrendStreak(shopItem.id()) + ")", NamedTextColor.GRAY));
    }

    @Command("shop admin setbuyable <material> <state>")
    @Permission("autotune.admin.buyable")
    public void setBuyable(CommandSender sender, @Argument(value = "material", suggestions = "shop-materials") Material material, @Argument(value = "state", suggestions = "buyable-states") String state) {
        Optional<ShopItem> item = shopManager.getItemByMaterial(material);
        if (item.isEmpty()) {
            sender.sendMessage(configManager.getMessage("general.invalid-item"));
            return;
        }

        Boolean buyable;
        switch (state.toLowerCase(Locale.ROOT)) {
            case "true", "yes" -> buyable = Boolean.TRUE;
            case "false", "no" -> buyable = Boolean.FALSE;
            case "null", "default", "auto" -> buyable = null;
            default -> {
                sender.sendMessage(configManager.getMessage("admin.invalid-state"));
                return;
            }
        }

        shopManager.setBuyable(item.get().id(), buyable);
        sender.sendMessage(configManager.getMessage("admin.buyable-set", Map.of(
                "item", material.name(),
                "state", buyable == null ? "auto" : buyable.toString()
        )));
    }

    @Command("shop admin additem <price> <section>")
    @Permission("autotune.admin.additem")
    public void addItem(CommandSender sender, @Argument("price") double price, @Argument(value = "section", suggestions = "shop-sections") String section) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) {
            player.sendMessage(configManager.getMessage("admin.hold-item"));
            return;
        }

        var unused = databaseManager.runAsync(() -> {
            ShopItem added = shopManager.addCustomItemWithData(itemInHand, BigDecimal.valueOf(price), section);
            databaseManager.runOnMain(() ->
                    player.sendMessage(configManager.getMessage("admin.custom-item-added", Map.of(
                            "item", added.getDisplayNameOrMaterial(),
                            "price", configManager.formatCurrency(price)
                    ))));
        });
    }

    @Command("shop admin transactions")
    @Permission("autotune.admin.transactions")
    public void adminTransactions(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new TransactionHistoryGui(plugin, player, TransactionHistoryGui.Mode.ADMIN, null).open();
    }
}
