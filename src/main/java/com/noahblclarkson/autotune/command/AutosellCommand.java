package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.ui.AutosellGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class AutosellCommand {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final AutosellManager autosellManager;
    private final ShopManager shopManager;

    @Inject
    public AutosellCommand(AutoTune plugin, ConfigManager configManager,
                          AutosellManager autosellManager, ShopManager shopManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.autosellManager = autosellManager;
        this.shopManager = shopManager;
    }

    @Command("autosell")
    @Permission("autotune.autosell")
    public void openAutosellGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        new AutosellGui(plugin, player).open();
    }

    @Command("autosell sell")
    @Permission("autotune.autosell")
    public void sellInventory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }
        autosellManager.sellInventory(player);
    }

    @Command("autosell minprice")
    @Permission("autotune.autosell")
    public void minpriceHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Autosell Min Price", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — Per-item sell price floor", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autosell minprice <material> [price]", NamedTextColor.YELLOW)
                .append(Component.text(" — Set minimum sell price for an item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autosell minprice <material>", NamedTextColor.YELLOW)
                .append(Component.text(" — View current minimum for an item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/autosell minprice <material> remove", NamedTextColor.YELLOW)
                .append(Component.text(" — Reset to global default", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Global minimum price: ", NamedTextColor.GRAY)
                .append(Component.text(
                        configManager.formatCurrency(
                                BigDecimal.valueOf(configManager.getConfig().autosell().minimumPrice())),
                        NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());
    }

    @Command("autosell minprice")
    @Permission("autotune.autosell")
    public void minpriceCommand(CommandSender sender,
                               @Argument("material") String materialName,
                               @Argument("price") Optional<Double> priceArg) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

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

        if (priceArg.isPresent()) {
            double price = priceArg.get();
            if (price < 0) {
                sender.sendMessage(configManager.getMessage("autosell.minprice-invalid"));
                return;
            }
            autosellManager.setMinPrice(player, shopItem.get().id(), price);
        } else {
            // No price given — show current minimum
            Optional<BigDecimal> perItemMin = autosellManager.getMinPrice(player.getUniqueId(), shopItem.get().id());
            BigDecimal globalMin = BigDecimal.valueOf(configManager.getConfig().autosell().minimumPrice());
            BigDecimal effectiveMin = perItemMin.orElse(globalMin);

            sender.sendMessage(Component.text("Minimum sell price for ", NamedTextColor.GRAY)
                    .append(Component.text(shopItem.get().getDisplayNameOrMaterial(), NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(configManager.formatCurrency(effectiveMin),
                            perItemMin.isPresent() ? NamedTextColor.GREEN : NamedTextColor.WHITE)));
            if (perItemMin.isPresent()) {
                sender.sendMessage(Component.text("(Per-item override — global default is "
                        + configManager.formatCurrency(globalMin) + ")", NamedTextColor.DARK_GRAY));
            }
        }
    }

    @Command("autosell minprice remove")
    @Permission("autotune.autosell")
    public void minpriceRemove(CommandSender sender,
                               @Argument("material") String materialName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("general.player-only"));
            return;
        }

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

        autosellManager.removeMinPrice(player, shopItem.get().id());
    }

    private org.bukkit.Material matchMaterial(String name) {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = org.bukkit.Material.matchMaterial(name);
        }
        return mat;
    }
}
