package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * /price command — quick in-game price lookup for any tradeable item.
 *
 * <p>Usage:
 * <ul>
 *   <li>/price &lt;material&gt; — show buy/sell price, spread, trend, 24h change</li>
 * </ul>
 *
 * <p>Shows: current price, buy price (per unit), sell price (per unit), spread,
 * 24h change, and trend direction.</p>
 */
@Singleton
@SuppressWarnings("PMD")
public class PriceCommand {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final TextColor ACCENT = TextColor.fromHexString("#10B981");
    private static final TextColor UP_COLOR = TextColor.fromHexString("#EF4444");
    private static final TextColor DOWN_COLOR = TextColor.fromHexString("#22C55E");

    private final AutoTune plugin;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final ItemRepository itemRepository;

    @Inject
    public PriceCommand(AutoTune plugin, ShopManager shopManager,
                        MarketEngine marketEngine, ItemRepository itemRepository) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.marketEngine = marketEngine;
        this.itemRepository = itemRepository;
    }

    @Command("price <material>")
    @Permission("autotune.price")
    public void onPrice(CommandContext<CommandSender> ctx,
                        @Argument("material") String material) {
        String materialName = material.toUpperCase(java.util.Locale.ROOT);

        // Try exact material match
        Material mat = Material.matchMaterial(materialName);
        Optional<ShopItem> itemOpt = Optional.empty();

        if (mat != null) {
            itemOpt = shopManager.getItemByMaterial(mat);
        }

        // Fallback: search by name
        if (itemOpt.isEmpty()) {
            List<ShopItem> results = itemRepository.search(materialName);
            if (!results.isEmpty()) {
                itemOpt = Optional.of(results.getFirst());
            }
        }

        if (itemOpt.isEmpty()) {
            ctx.sender().sendMessage(Component.text("Item not found: " + materialName, NamedTextColor.RED));
            return;
        }

        ShopItem item = itemOpt.get();
        renderPriceInfo(ctx.sender(), item);
    }

    private void renderPriceInfo(CommandSender sender, ShopItem item) {
        BigDecimal midPrice = marketEngine.getCurrentPrice(item.id());
        BigDecimal buyPrice = marketEngine.getBuyPrice(item);
        BigDecimal sellPrice = marketEngine.getSellPrice(item);

        MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(item.id());

        // Header
        String displayName = item.getDisplayNameOrMaterial();
        sender.sendMessage(Component.text("═══ ")
                .color(ACCENT)
                .append(Component.text(displayName).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(" ═══").color(ACCENT)));

        sender.sendMessage(Component.text(""));

        // Mid price
        sender.sendMessage(Component.text("  Price: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("$" + formatPrice(midPrice)).color(NamedTextColor.GOLD)));

        // Buy / Sell
        sender.sendMessage(Component.text("  Buy: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("$" + formatPrice(buyPrice)).color(NamedTextColor.GREEN))
                .append(Component.text("  │  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Sell: ").color(NamedTextColor.GRAY))
                .append(Component.text("$" + formatPrice(sellPrice)).color(UP_COLOR)));

        // Spread
        BigDecimal spreadAmount = buyPrice.subtract(sellPrice);
        if (spreadAmount.compareTo(BigDecimal.ZERO) > 0 && midPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal spreadPct = spreadAmount.divide(midPrice, 4, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
            sender.sendMessage(Component.text("  Spread: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("$" + formatPrice(spreadAmount) + " (" + spreadPct + "%)")
                            .color(NamedTextColor.DARK_GRAY)));
        }

        // 24h change
        if (trend != null && trend.percentChange() != null) {
            BigDecimal change = trend.percentChange();
            boolean up = change.compareTo(BigDecimal.ZERO) > 0;
            TextColor changeColor = up ? UP_COLOR : DOWN_COLOR;
            String arrow = up ? "▲" : "▼";

            sender.sendMessage(Component.text("  24h: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(arrow + " " + change.abs().setScale(1, RoundingMode.HALF_UP) + "%")
                            .color(changeColor))
                    .append(Component.text(" (" + trend.label() + ")").color(NamedTextColor.DARK_GRAY)));
        }

        // Trend direction
        if (trend != null) {
            String trendEmoji = trendDirectionEmoji(trend.direction());
            sender.sendMessage(Component.text("  Trend: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(trendEmoji + " " + trend.direction().name().toLowerCase())
                            .color(trendDirectionColor(trend.direction()))));
        }

        // 24h projected
        if (trend != null && trend.projected24h() != null && midPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projected = trend.projected24h();
            boolean projUp = projected.compareTo(midPrice) > 0;
            sender.sendMessage(Component.text("  Projected: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("$" + formatPrice(projected))
                            .color(projUp ? UP_COLOR : DOWN_COLOR)));
        }

        // Section and tier
        sender.sendMessage(Component.text(""));
        String sectionInfo = item.section() != null ? item.section() : "misc";
        String tierInfo = item.tier() != null ? item.tier().name() : "-";
        sender.sendMessage(Component.text("  Section: ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(sectionInfo).color(NamedTextColor.GRAY))
                .append(Component.text("  │  Tier: ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(tierInfo).color(NamedTextColor.GRAY)));

        sender.sendMessage(Component.text(""));
    }

    private String trendDirectionEmoji(MarketEngine.PriceTrend.Direction dir) {
        return switch (dir) {
            case UP -> "📈";
            case DOWN -> "📉";
            case STABLE -> "➡️";
        };
    }

    private TextColor trendDirectionColor(MarketEngine.PriceTrend.Direction dir) {
        return switch (dir) {
            case UP -> UP_COLOR;
            case DOWN -> DOWN_COLOR;
            case STABLE -> NamedTextColor.GRAY;
        };
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "—";
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
