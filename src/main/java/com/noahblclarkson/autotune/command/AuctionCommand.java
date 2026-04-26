package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.noahblclarkson.autotune.AutoTune;
import net.milkbowl.vault.economy.Economy;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.auction.AuctionManager;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.ui.AuctionGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("PMD")
@Singleton
public class AuctionCommand {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AuctionManager auctionManager;
    private final AuctionRepository auctionRepo;
    private final ConfigManager configManager;
    private final Economy economy;
    private final AutoTune plugin;
    private final MarketEngine marketEngine;
    private final ShopManager shopManager;

    @Inject
    public AuctionCommand(
            AuctionManager auctionManager,
            AuctionRepository auctionRepo,
            Economy economy,
            ConfigManager configManager,
            AutoTune plugin,
            MarketEngine marketEngine,
            ShopManager shopManager
    ) {
        this.auctionManager = auctionManager;
        this.auctionRepo = auctionRepo;
        this.configManager = configManager;
        this.economy = economy;
        this.plugin = plugin;
        this.marketEngine = marketEngine;
        this.shopManager = shopManager;
    }

    @Command("auction")
    public void auctionHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auction House", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" - Player-to-player trading", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction", NamedTextColor.YELLOW)
                .append(Component.text(" - Browse active orders", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction sell <price> [qty=1]", NamedTextColor.YELLOW)
                .append(Component.text(" - List item from hand for sale", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction buy <material> <price> [qty=1]", NamedTextColor.YELLOW)
                .append(Component.text(" - Place a buy order", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction my", NamedTextColor.YELLOW)
                .append(Component.text(" - View your active orders", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction cancel <order-id>", NamedTextColor.YELLOW)
                .append(Component.text(" - Cancel an active order", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction history", NamedTextColor.YELLOW)
                .append(Component.text(" - Recent auction trades", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction reclaim", NamedTextColor.YELLOW)
                .append(Component.text(" - Reclaim items from expired sell orders", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/auction price <material>", NamedTextColor.YELLOW)
                .append(Component.text(" - Check market price before posting", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Command("auction open")
    public void auctionOpen(Player player) {
        new AuctionGui(player.getName(), auctionManager, configManager, economy).open(player);
    }

    @Command("auction browse <material>")
    public void auctionBrowse(Player player,
                              @Argument(value = "material", suggestions = "auction-materials") String material) {
        Material mat = parseMaterial(material);
        if (mat == null) {
            player.sendMessage(Component.text("Unknown material: " + material, NamedTextColor.RED));
            return;
        }
        new AuctionGui(mat.name(), player.getName(), auctionManager, configManager, economy).open(player);
    }

    @Command("auction sell <price> <quantity>")
    public void auctionSell(Player player,
                            @Argument("price") BigDecimal price,
                            @Argument("quantity") int quantity) {
        if (quantity <= 0) {
            player.sendMessage(Component.text("Quantity must be at least 1", NamedTextColor.RED));
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(Component.text("Price must be positive", NamedTextColor.RED));
            return;
        }

        var item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(Component.text("You must be holding an item to sell", NamedTextColor.RED));
            return;
        }

        int available = item.getAmount();
        if (quantity > available) {
            player.sendMessage(Component.text("You only have " + available + " of that item", NamedTextColor.RED));
            return;
        }

        var held = item.clone();
        held.setAmount(quantity);

        // Remove items from hand BEFORE async call to ensure atomicity.
        // If the async DB write fails, we restore them.
        player.getInventory().getItemInMainHand().setAmount(available - quantity);
        ItemStack toRestore = held.clone();

        player.sendMessage(Component.text("Placing sell order for " + quantity + "× " + formatMaterial(held.getType().name())
                + " at " + configManager.formatCurrency(price) + " each..."));

        auctionManager.placeSellOrderAsync(player, held.getType(), quantity, price)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("✓ " + result.message(), NamedTextColor.GREEN));

                        // Show fills if any
                        if (!result.fills().isEmpty()) {
                            for (AuctionFill fill : result.fills()) {
                                BigDecimal total = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
                                player.sendMessage(Component.text("  → " + fill.quantity() + " sold at "
                                        + configManager.formatCurrency(fill.price())
                                        + " each (" + configManager.formatCurrency(total) + " total)",
                                        NamedTextColor.YELLOW));
                            }
                        }
                    } else {
                        // DB write failed — restore items to player's hand.
                        // Must run on main thread (Inventory.addItem is not thread-safe).
                        plugin.getServer().getGlobalRegionScheduler().run(plugin, task ->
                                player.getInventory().addItem(toRestore));
                        player.sendMessage(Component.text("✗ " + result.message(), NamedTextColor.RED));
                    }
                });
    }

    @Command("auction buy <material> <price> <quantity>")
    public void auctionBuy(Player player,
                           @Argument(value = "material", suggestions = "auction-materials") String material,
                           @Argument("price") BigDecimal price,
                           @Argument("quantity") int quantity) {
        if (quantity <= 0) {
            player.sendMessage(Component.text("Quantity must be at least 1", NamedTextColor.RED));
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(Component.text("Price must be positive", NamedTextColor.RED));
            return;
        }

        Material mat = parseMaterial(material);
        if (mat == null) {
            player.sendMessage(Component.text("Unknown material: " + material, NamedTextColor.RED));
            return;
        }

        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity));
        double bal = economy.getBalance(player);

        if (bal < totalCost.doubleValue()) {
            player.sendMessage(Component.text("Insufficient funds. Need "
                    + configManager.formatCurrency(totalCost) + " (you have "
                    + configManager.formatCurrency(BigDecimal.valueOf(bal)) + ")", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Placing buy order for " + quantity + "× " + formatMaterial(mat.name())
                + " at " + configManager.formatCurrency(price) + " each (escrowing "
                + configManager.formatCurrency(totalCost) + ")..."));

        auctionManager.placeBuyOrderAsync(player, mat, quantity, price)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("✓ " + result.message(), NamedTextColor.GREEN));
                        if (!result.fills().isEmpty()) {
                            for (AuctionFill fill : result.fills()) {
                                BigDecimal total = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
                                player.sendMessage(Component.text("  → " + fill.quantity() + " purchased at "
                                        + configManager.formatCurrency(fill.price())
                                        + " each (" + configManager.formatCurrency(total) + " total)",
                                        NamedTextColor.YELLOW));
                            }
                        }
                    } else {
                        // Refund if it was a pre-auth failure
                        player.sendMessage(Component.text("✗ " + result.message(), NamedTextColor.RED));
                    }
                });
    }

    @Command("auction my")
    public void auctionMy(Player player) {
        List<AuctionOrder> orders = auctionManager.getPlayerOrders(player.getUniqueId());

        if (orders.isEmpty()) {
            player.sendMessage(Component.text("You have no active auction orders.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Your Auction Orders", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Type /auction cancel <id> to cancel an order", NamedTextColor.GRAY));
        player.sendMessage(Component.text("──".repeat(20), NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        for (AuctionOrder order : orders) {
            TextColor sideColor = order.side() == OrderSide.BUY ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
            String sideLabel = order.side() == OrderSide.BUY ? "BUY" : "SELL";

            Component line = Component.text("[" + order.id().toString().substring(0, 8) + "...]", NamedTextColor.GRAY)
                    .append(Component.text(" " + sideLabel + " ", sideColor, TextDecoration.BOLD))
                    .append(Component.text(order.remainingQuantity() + "× " + formatMaterial(order.material()), NamedTextColor.WHITE))
                    .append(Component.text(" @ " + configManager.formatCurrency(order.price()), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + order.remainingQuantity() + "/" + order.originalQuantity() + " filled)", NamedTextColor.DARK_GRAY))
                    .clickEvent(ClickEvent.suggestCommand("/auction cancel " + order.id()));
            player.sendMessage(line);
        }

        player.sendMessage(Component.empty());
    }

    @Command("auction cancel <orderId>")
    public void auctionCancel(Player player, @Argument("orderId") String orderIdStr) {
        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid order ID format", NamedTextColor.RED));
            return;
        }

        auctionManager.cancelOrderAsync(player, orderId)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("✓ " + result.message(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✗ " + result.message(), NamedTextColor.RED));
                    }
                });
    }

    @Command("auction reclaim")
    public void auctionReclaim(Player player) {
        List<AuctionOrder> expired = auctionManager.getExpiredSellOrdersForPlayer(player.getUniqueId());
        if (expired == null || expired.isEmpty()) {
            player.sendMessage(Component.text("You have no expired sell orders to reclaim.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text(
                "Reclaiming items from " + expired.size() + " expired sell order(s)...", NamedTextColor.YELLOW));

        int items = auctionManager.reclaimExpiredOrders(player);
        if (items > 0) {
            player.sendMessage(Component.text(
                    "✓ " + items + " item(s) returned to your inventory.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "No items were returned — your inventory may be full.", NamedTextColor.RED));
        }
    }

    @Command("auction history <limit>")
    public void auctionHistory(CommandSender sender,
                                @Argument("limit") int limit) {
        int clamped = Math.min(50, Math.max(1, limit));
        List<AuctionFill> fills = auctionManager.getRecentFills(clamped);

        if (fills.isEmpty()) {
            sender.sendMessage(Component.text("No recent auction trades.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Recent Auction Trades", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("──".repeat(20), NamedTextColor.DARK_GRAY));

        for (AuctionFill fill : fills) {
            BigDecimal total = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
            sender.sendMessage(Component.text(
                    fill.quantity() + " @ " + configManager.formatCurrency(fill.price())
                            + " = " + configManager.formatCurrency(total)
                            + "  (" + DATE_FORMAT.format(fill.filledAt()) + ")",
                    NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.empty());
    }

    @Command("auction list")
    @Permission("autotune.auction")
    public void auctionList(CommandSender sender) {
        List<AuctionOrder> orders = auctionManager.getAllActiveOrders();

        if (orders.isEmpty()) {
            sender.sendMessage(Component.text("No active auction orders.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Active Auction Orders (" + orders.size() + ")", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Type /auction browse <material> to see order book", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("──".repeat(20), NamedTextColor.DARK_GRAY));

        // Group by material
        Map<String, List<AuctionOrder>> byMaterial = orders.stream()
                .collect(Collectors.groupingBy(AuctionOrder::material));

        int shown = 0;
        for (Map.Entry<String, List<AuctionOrder>> entry : byMaterial.entrySet()) {
            if (shown >= 20) break;
            String mat = entry.getKey();
            List<AuctionOrder> matOrders = entry.getValue();

            BigDecimal bestBid = matOrders.stream()
                    .filter(o -> o.side() == OrderSide.BUY)
                    .map(AuctionOrder::price)
                    .max(BigDecimal::compareTo)
                    .orElse(null);
            BigDecimal bestAsk = matOrders.stream()
                    .filter(o -> o.side() == OrderSide.SELL)
                    .map(AuctionOrder::price)
                    .min(BigDecimal::compareTo)
                    .orElse(null);

            Component line = Component.text(formatMaterial(mat), NamedTextColor.WHITE)
                    .append(Component.text(" (" + matOrders.size() + " orders)", NamedTextColor.GRAY));
            if (bestBid != null) {
                line = line.append(Component.text(" BID " + configManager.formatCurrency(bestBid), NamedTextColor.AQUA));
            }
            if (bestAsk != null) {
                line = line.append(Component.text(" ASK " + configManager.formatCurrency(bestAsk), NamedTextColor.LIGHT_PURPLE));
            }
            line = line.append(Component.text(" [/auction browse " + mat + "]", NamedTextColor.YELLOW));
            sender.sendMessage(line);
            shown++;
        }

        int remaining = byMaterial.size() - shown;
        if (remaining > 0) {
            sender.sendMessage(Component.text("... and " + remaining + " more materials", NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.empty());
    }

    /**
     * Shows current auction price indicators for a material:
     * - Market reference price (shop buy/sell)
     * - Best bid and ask in the auction order book
     * - Spread
     * - Recent auction fill prices
     */
    @Command("auction price <material>")
    @Permission("autotune.auction")
    public void auctionPrice(CommandSender sender,
                             @Argument(value = "material", suggestions = "materials") String material) {
        Material mat = parseMaterial(material);
        if (mat == null) {
            sender.sendMessage(Component.text("Unknown material: " + material, NamedTextColor.RED));
            return;
        }

        // Market reference prices from the shop engine
        var shopItemOpt = shopManager.getItemByMaterial(mat);
        BigDecimal marketBuy = shopItemOpt.map(marketEngine::getBuyPrice).orElse(null);
        BigDecimal marketSell = shopItemOpt.map(marketEngine::getSellPrice).orElse(null);

        // Auction order book
        List<AuctionOrder> sellOrders = auctionRepo.findActiveByMaterial(mat.name())
                .stream().filter(o -> o.side() == OrderSide.SELL).toList();
        List<AuctionOrder> buyOrders = auctionRepo.findActiveByMaterial(mat.name())
                .stream().filter(o -> o.side() == OrderSide.BUY).toList();

        BigDecimal bestBid = buyOrders.stream()
                .map(AuctionOrder::price)
                .max(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal bestAsk = sellOrders.stream()
                .map(AuctionOrder::price)
                .min(BigDecimal::compareTo)
                .orElse(null);

        // Spread
        BigDecimal spreadPct = null;
        if (bestBid != null && bestAsk != null && bestBid.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal midpoint = bestAsk.add(bestBid)
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            spreadPct = bestAsk.subtract(bestBid)
                    .divide(midpoint, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // Recent auction fill prices for this material
        List<AuctionFill> recentFills = auctionRepo.findRecentFillsByMaterial(mat.name(), 20);
        BigDecimal avgFillPrice = null;
        if (!recentFills.isEmpty()) {
            BigDecimal sum = recentFills.stream()
                    .map(f -> f.price().multiply(BigDecimal.valueOf(f.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int totalQty = recentFills.stream()
                    .mapToInt(AuctionFill::quantity)
                    .sum();
            if (totalQty > 0) {
                avgFillPrice = sum.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
            }
        }

        String materialName = formatMaterial(mat.name());

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Auction Price — " + materialName, NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("──".repeat(20), NamedTextColor.DARK_GRAY));

        if (marketBuy != null && marketSell != null) {
            sender.sendMessage(Component.text("  Market:  ", NamedTextColor.GRAY)
                    .append(Component.text("Buy ", NamedTextColor.GREEN))
                    .append(Component.text(configManager.formatCurrency(marketBuy), NamedTextColor.WHITE))
                    .append(Component.text("   Sell ", NamedTextColor.YELLOW))
                    .append(Component.text(configManager.formatCurrency(marketSell), NamedTextColor.WHITE)));
        }

        if (bestBid != null) {
            int bidCount = buyOrders.size();
            sender.sendMessage(Component.text("  Best Bid: ", NamedTextColor.AQUA)
                    .append(Component.text(configManager.formatCurrency(bestBid), NamedTextColor.WHITE))
                    .append(Component.text(" (" + bidCount + " bid" + (bidCount != 1 ? "s" : "") + ")", NamedTextColor.DARK_GRAY)));
        } else {
            sender.sendMessage(Component.text("  Best Bid: ", NamedTextColor.AQUA)
                    .append(Component.text("none", NamedTextColor.DARK_GRAY)));
        }

        if (bestAsk != null) {
            int askCount = sellOrders.size();
            sender.sendMessage(Component.text("  Best Ask: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(configManager.formatCurrency(bestAsk), NamedTextColor.WHITE))
                    .append(Component.text(" (" + askCount + " ask" + (askCount != 1 ? "s" : "") + ")", NamedTextColor.DARK_GRAY)));
        } else {
            sender.sendMessage(Component.text("  Best Ask: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("none", NamedTextColor.DARK_GRAY)));
        }

        if (spreadPct != null) {
            sender.sendMessage(Component.text("  Spread:  ", NamedTextColor.GRAY)
                    .append(Component.text(spreadPct.setScale(1, RoundingMode.HALF_UP) + "%", NamedTextColor.AQUA)));
        }

        if (avgFillPrice != null) {
            sender.sendMessage(Component.text("  Avg Fill (" + recentFills.size() + " trades): ", NamedTextColor.GOLD)
                    .append(Component.text(configManager.formatCurrency(avgFillPrice), NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("──".repeat(20), NamedTextColor.DARK_GRAY));
        if (bestAsk != null || bestBid != null) {
            sender.sendMessage(Component.text("  Tip: Use ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("/auction sell <price> [qty]", NamedTextColor.YELLOW))
                    .append(Component.text(" or ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("/auction buy " + material + " <price> [qty]", NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("  No auction orders yet for this item.", NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("  Be the first to post! ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("/auction sell <price> [qty]", NamedTextColor.YELLOW)));
        }
        sender.sendMessage(Component.empty());
    }

    @Suggestions("auction-materials")
    public List<String> suggestAuctionMaterials(CommandContext<CommandSender> ctx) {
        return shopManager.getAllItems().stream()
                .map(item -> item.material().name())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatMaterial(String material) {
        return material.replace("_", " ").toLowerCase(Locale.ROOT)
                .substring(0, 1).toUpperCase(Locale.ROOT)
                + material.replace("_", " ").toLowerCase(Locale.ROOT).substring(1);
    }
}
