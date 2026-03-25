package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Singleton
public class AdminCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final ItemRepository itemRepository;

    @Inject
    public AdminCommand(
            AutoTune plugin,
            ConfigManager configManager,
            ShopManager shopManager,
            ItemRepository itemRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.itemRepository = itemRepository;
    }

    @Command("autotune admin")
    @Permission("autotune.admin")
    public void adminInfo(CommandSender sender) {
        sender.sendMessage(Component.text("=== Auto-Tune Admin ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/autotune price setmax <item> <price>", NamedTextColor.AQUA)
                .append(Component.text(" - Set max price ceiling", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autotune price setmin <item> <price>", NamedTextColor.AQUA)
                .append(Component.text(" - Set min price floor", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autotune price remove <item>", NamedTextColor.AQUA)
                .append(Component.text(" - Remove price limits", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autotune price info <item>", NamedTextColor.AQUA)
                .append(Component.text(" - Show price limits", NamedTextColor.GRAY)));
    }

    @Command("autotune price setmax <item> <price>")
    @Permission("autotune.admin")
    public void setMaxPrice(CommandSender sender, @NotNull String item, @NotNull BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("Max price must be positive.", NamedTextColor.RED));
            return;
        }

        shopManager.getItemByMaterial(parseMaterial(item)).ifPresentOrElse(
                shopItem -> {
                    itemRepository.updateMaxPrice(shopItem.id(), price);
                    shopManager.invalidateBuyableCache(shopItem.id());
                    sender.sendMessage(Component.text("Set max price of ", NamedTextColor.GREEN)
                            .append(Component.text(formatItem(shopItem), NamedTextColor.AQUA))
                            .append(Component.text(" to ", NamedTextColor.GREEN))
                            .append(Component.text(configManager.formatCurrency(price.doubleValue()), NamedTextColor.GOLD)));
                },
                () -> sender.sendMessage(Component.text("Item not found: " + item, NamedTextColor.RED))
        );
    }

    @Command("autotune price setmin <item> <price>")
    @Permission("autotune.admin")
    public void setMinPrice(CommandSender sender, @NotNull String item, @NotNull BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text("Min price must be positive.", NamedTextColor.RED));
            return;
        }

        shopManager.getItemByMaterial(parseMaterial(item)).ifPresentOrElse(
                shopItem -> {
                    itemRepository.updateMinPrice(shopItem.id(), price);
                    shopManager.invalidateBuyableCache(shopItem.id());
                    sender.sendMessage(Component.text("Set min price of ", NamedTextColor.GREEN)
                            .append(Component.text(formatItem(shopItem), NamedTextColor.AQUA))
                            .append(Component.text(" to ", NamedTextColor.GREEN))
                            .append(Component.text(configManager.formatCurrency(price.doubleValue()), NamedTextColor.GOLD)));
                },
                () -> sender.sendMessage(Component.text("Item not found: " + item, NamedTextColor.RED))
        );
    }

    @Command("autotune price remove <item>")
    @Permission("autotune.admin")
    public void removePriceLimits(CommandSender sender, @NotNull String item) {
        shopManager.getItemByMaterial(parseMaterial(item)).ifPresentOrElse(
                shopItem -> {
                    itemRepository.updateMaxPrice(shopItem.id(), null);
                    itemRepository.updateMinPrice(shopItem.id(), null);
                    shopManager.invalidateBuyableCache(shopItem.id());
                    sender.sendMessage(Component.text("Removed price limits for ", NamedTextColor.GREEN)
                            .append(Component.text(formatItem(shopItem), NamedTextColor.AQUA)));
                },
                () -> sender.sendMessage(Component.text("Item not found: " + item, NamedTextColor.RED))
        );
    }

    @Command("autotune price info <item>")
    @Permission("autotune.admin")
    public void priceInfo(CommandSender sender, @NotNull String item) {
        shopManager.getItemByMaterial(parseMaterial(item)).ifPresentOrElse(
                shopItem -> {
                    String maxStr = shopItem.maxPrice() != null
                            ? configManager.formatCurrency(shopItem.maxPrice().doubleValue())
                            : "none";
                    String minStr = shopItem.minPrice() != null
                            ? configManager.formatCurrency(shopItem.minPrice().doubleValue())
                            : "none";
                    sender.sendMessage(Component.text("=== Price Limits: ", NamedTextColor.GOLD)
                            .append(Component.text(formatItem(shopItem), NamedTextColor.AQUA))
                            .append(Component.text(" ===", NamedTextColor.GOLD)));
                    sender.sendMessage(Component.text("Current price: ", NamedTextColor.GREEN)
                            .append(Component.text(configManager.formatCurrency(shopItem.price().doubleValue()), NamedTextColor.GOLD)));
                    sender.sendMessage(Component.text("Max price (ceiling): ", NamedTextColor.GREEN)
                            .append(Component.text(maxStr, NamedTextColor.GOLD)));
                    sender.sendMessage(Component.text("Min price (floor): ", NamedTextColor.GREEN)
                            .append(Component.text(minStr, NamedTextColor.GOLD)));
                },
                () -> sender.sendMessage(Component.text("Item not found: " + item, NamedTextColor.RED))
        );
    }

    @Suggestions("material-suggestion")
    public List<String> suggestMaterials(CommandContext<?> ctx, String input) {
        return shopManager.getAllItems().stream()
                .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.contains(input.toLowerCase(Locale.ROOT)))
                .limit(20)
                .toList();
    }

    private org.bukkit.Material parseMaterial(@NotNull String name) {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(name);
        }
        return mat;
    }

    private String formatItem(ShopItem item) {
        return item.getDisplayNameOrMaterial();
    }
}
