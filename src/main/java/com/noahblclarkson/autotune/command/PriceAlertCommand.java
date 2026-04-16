package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.PriceAlertManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.PriceAlert;
import com.noahblclarkson.autotune.model.PriceAlert.AlertType;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@SuppressWarnings("PMD")
public class PriceAlertCommand {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PriceAlertManager alertManager;
    private final ShopManager shopManager;
    private final ConfigManager configManager;

    @Inject
    public PriceAlertCommand(
            PriceAlertManager alertManager,
            ShopManager shopManager,
            ConfigManager configManager
    ) {
        this.alertManager = alertManager;
        this.shopManager = shopManager;
        this.configManager = configManager;
    }

    private static Component makeTitle(String text) {
        return TextComponent.ofChildren(
                Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
        );
    }

    @Command("alert")
    public void alertHelp(Player sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(makeTitle("Price Alerts"));
        sender.sendMessage(Component.text("/alert add <item> <price> [above|below]", NamedTextColor.YELLOW)
                .append(Component.text(" - create an alert", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/alert list", NamedTextColor.YELLOW)
                .append(Component.text(" - view your alerts", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/alert remove <#|item>", NamedTextColor.YELLOW)
                .append(Component.text(" - remove an alert", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/alert rearm <#|item>", NamedTextColor.YELLOW)
                .append(Component.text(" - rearm a triggered alert", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/alert toggle <#|item>", NamedTextColor.YELLOW)
                .append(Component.text(" - pause/resume an alert", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Use 'above' to be notified when price rises above target.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Use 'below' to be notified when price falls below target.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Alerts fire once per crossing. Rearm to reuse.", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
    }

    @Suggestions("alert-item-suggestion")
    public List<String> suggestAlertItems(CommandContext<?> ctx, String input) {
        return shopManager.getAllItems().stream()
                .filter(ShopItem::enabled)
                .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                .filter(name -> name.contains(input.toLowerCase(Locale.ROOT)))
                .limit(20)
                .toList();
    }

    @Suggestions("alert-type-suggestion")
    public List<String> suggestAlertType(CommandContext<?> ctx, String input) {
        return List.of("above", "below");
    }

    @Command("alert add <material> <price> <type>")
    public void alertAdd(
            Player sender,
            @Argument("material") String materialName,
            @Argument("price") BigDecimal price,
            @Argument(value = "type", suggestions = "alert-type-suggestion") String typeStr
    ) {
        Material mat = matchMaterial(materialName);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + materialName, NamedTextColor.RED));
            return;
        }

        AlertType alertType;
        if (typeStr.equalsIgnoreCase("above") || typeStr.equalsIgnoreCase("a")) {
            alertType = AlertType.ABOVE;
        } else if (typeStr.equalsIgnoreCase("below") || typeStr.equalsIgnoreCase("b")) {
            alertType = AlertType.BELOW;
        } else {
            sender.sendMessage(Component.text("Type must be 'above' or 'below'.", NamedTextColor.RED));
            return;
        }

        ShopItem shopItem = shopManager.getItemByMaterial(mat).orElse(null);
        if (shopItem == null) {
            sender.sendMessage(Component.text("Item not in shop yet: " + mat.name() + ". Trade it first!", NamedTextColor.RED));
            return;
        }

        var result = alertManager.createAlert(sender.getUniqueId(), shopItem.id(), alertType, price);

        if (!result.success()) {
            sender.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
            return;
        }

        String direction = alertType == AlertType.ABOVE ? "above" : "below";
        sender.sendMessage(Component.text("Alert created for " + shopItem.getDisplayNameOrMaterial()
                + ": notify when price rises " + direction + " "
                + configManager.formatCurrency(price), NamedTextColor.GREEN));
    }

    @Command("alert list")
    public void alertList(Player sender) {
        List<PriceAlert> alerts = alertManager.getPlayerAlerts(sender.getUniqueId());

        sender.sendMessage(Component.empty());
        sender.sendMessage(makeTitle("Your Price Alerts"));

        if (alerts.isEmpty()) {
            sender.sendMessage(Component.text("  No alerts set. Use /alert add <item> <price> [above|below]",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.empty());
            return;
        }

        AtomicInteger idx = new AtomicInteger(1);
        for (PriceAlert alert : alerts) {
            String itemName = shopManager.getItemById(alert.itemId())
                    .map(ShopItem::getDisplayNameOrMaterial)
                    .orElse("#" + alert.itemId());

            NamedTextColor statusColor;
            String status;
            if (!alert.enabled()) {
                status = "PAUSED";
                statusColor = NamedTextColor.GRAY;
            } else if (alert.isTriggered()) {
                status = "TRIGGERED";
                statusColor = NamedTextColor.YELLOW;
            } else {
                String direction = alert.alertType() == AlertType.ABOVE ? "above" : "below";
                status = "ACTIVE (notify when price " + direction + " "
                        + configManager.formatCurrency(alert.targetPrice()) + ")";
                statusColor = NamedTextColor.GREEN;
            }

            String extra = "";
            if (alert.isTriggered() && alert.triggeredAt() != null) {
                extra = " - triggered " + DATE_FORMAT.format(alert.triggeredAt());
            }

            Component line = Component.text("  #" + idx.getAndIncrement() + " ", NamedTextColor.AQUA)
                    .append(Component.text(itemName + ": ", NamedTextColor.WHITE))
                    .append(Component.text(status, statusColor))
                    .append(Component.text(extra, NamedTextColor.GRAY));

            sender.sendMessage(line);
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Use /alert remove <#> to delete, /alert rearm <#> to reset a triggered alert.",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
    }

    @Command("alert remove <identifier>")
    public void alertRemove(Player sender, @Argument("identifier") String identifier) {
        UUID playerUuid = sender.getUniqueId();
        List<PriceAlert> alerts = alertManager.getPlayerAlerts(playerUuid);

        PriceAlert target = findAlertByIdentifier(alerts, identifier);
        if (target == null) {
            sender.sendMessage(Component.text("Alert not found: " + identifier, NamedTextColor.RED));
            return;
        }

        if (!alertManager.removeAlert(target.id(), playerUuid, false)) {
            sender.sendMessage(Component.text("Failed to remove alert.", NamedTextColor.RED));
            return;
        }

        String itemName = shopManager.getItemById(target.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("#" + target.itemId());

        sender.sendMessage(Component.text("Alert removed for " + itemName + ".", NamedTextColor.GREEN));
    }

    @Command("alert rearm <identifier>")
    public void alertRearm(Player sender, @Argument("identifier") String identifier) {
        UUID playerUuid = sender.getUniqueId();
        List<PriceAlert> alerts = alertManager.getPlayerAlerts(playerUuid);

        PriceAlert target = findAlertByIdentifier(alerts, identifier);
        if (target == null) {
            sender.sendMessage(Component.text("Alert not found: " + identifier, NamedTextColor.RED));
            return;
        }

        if (!alertManager.rearmAlert(target.id(), playerUuid)) {
            sender.sendMessage(Component.text("Cannot rearm this alert.", NamedTextColor.RED));
            return;
        }

        String itemName = shopManager.getItemById(target.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("#" + target.itemId());

        sender.sendMessage(Component.text("Alert rearmed for " + itemName + ".", NamedTextColor.GREEN));
    }

    @Command("alert toggle <identifier>")
    public void alertToggle(Player sender, @Argument("identifier") String identifier) {
        UUID playerUuid = sender.getUniqueId();
        List<PriceAlert> alerts = alertManager.getPlayerAlerts(playerUuid);

        PriceAlert target = findAlertByIdentifier(alerts, identifier);
        if (target == null) {
            sender.sendMessage(Component.text("Alert not found: " + identifier, NamedTextColor.RED));
            return;
        }

        if (!alertManager.toggleAlert(target.id(), playerUuid, false)) {
            sender.sendMessage(Component.text("Cannot toggle this alert.", NamedTextColor.RED));
            return;
        }

        String itemName = shopManager.getItemById(target.itemId())
                .map(ShopItem::getDisplayNameOrMaterial)
                .orElse("#" + target.itemId());
        String newState = !target.enabled() ? "resumed" : "paused";

        sender.sendMessage(Component.text("Alert " + newState + " for " + itemName + ".", NamedTextColor.GREEN));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Material matchMaterial(String name) {
        Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (mat == null) {
            mat = Material.matchMaterial(name);
        }
        return mat;
    }

    private Integer parseAlertIndex(String input) {
        try {
            return Integer.parseInt(input.replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PriceAlert findAlertByIdentifier(List<PriceAlert> alerts, String identifier) {
        Integer index = parseAlertIndex(identifier);
        if (index != null && index > 0 && index <= alerts.size()) {
            return alerts.get(index - 1);
        }
        String searchLower = identifier.toLowerCase(Locale.ROOT);
        for (PriceAlert alert : alerts) {
            String itemName = shopManager.getItemById(alert.itemId())
                    .map(it -> it.material().name().toLowerCase(Locale.ROOT))
                    .orElse("");
            if (itemName.contains(searchLower)) {
                return alert;
            }
        }
        return null;
    }
}
