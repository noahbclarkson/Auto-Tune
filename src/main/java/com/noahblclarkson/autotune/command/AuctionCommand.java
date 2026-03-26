package com.noahblclarkson.autotune.command;

import com.google.inject.Inject;
import com.noahblclarkson.autotune.AutoTune;
import net.milkbowl.vault.economy.Economy;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.auction.AuctionManager;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionRepository;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Singleton
public class AuctionCommand {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AuctionManager auctionManager;
    private final AuctionRepository auctionRepo;
    private final ConfigManager configManager;
    private final Economy economy;

    @Inject
    public AuctionCommand(
            AuctionManager auctionManager,
            AuctionRepository auctionRepo,
            Economy economy,
            ConfigManager configManager
    ) {
        this.auctionManager = auctionManager;
        this.auctionRepo = auctionRepo;
        this.configManager = configManager;
        this.economy = economy;
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
        sender.sendMessage(Component.empty());
    }

    @Command("auction open")
    public void auctionOpen(Player player) {
        new AuctionGui(player.getName(), auctionManager, configManager, economy).open(player);
    }

    @Command("auction browse")
    public void auctionBrowse(Player player,
                              @Argument("material") String material) {
        Material mat = parseMaterial(material);
        if (mat == null) {
            player.sendMessage(Component.text("Unknown material: " + material, NamedTextColor.RED));
            return;
        }
        new AuctionGui(mat.name(), player.getName(), auctionManager, configManager, economy).open(player);
    }

    @Command("auction sell")
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
                        // DB write failed — restore items to player's hand
                        player.getInventory().addItem(toRestore);
                        player.sendMessage(Component.text("✗ " + result.message(), NamedTextColor.RED));
                    }
                });
    }

    @Command("auction buy")
    public void auctionBuy(Player player,
                           @Argument("material") String material,
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
                                // Give items to player
                                var item = new org.bukkit.inventory.ItemStack(mat, fill.quantity());
                                player.getWorld().dropItemNaturally(player.getLocation(), item);
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

    @Command("auction cancel")
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

    @Command("auction history")
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

    @Suggestions("materials")
    public List<String> suggestMaterials(CommandContext<CommandSender> ctx) {
        return List.of(
                "DIAMOND", "DIAMOND_SWORD", "DIAMOND_PICKAXE", "DIAMOND_HELMET",
                "GOLD_INGOT", "IRON_INGOT", "COAL", "EMERALD", "LAPIS_LAZULI",
                "NETHERITE_SWORD", "ENCHANTED_GOLDEN_APPLE", "NETHER_STAR",
                "SHULKER_BOX", "ELYTRA", "TRIDENT"
        );
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatMaterial(String material) {
        return material.replace("_", " ").toLowerCase()
                .substring(0, 1).toUpperCase()
                + material.replace("_", " ").toLowerCase().substring(1);
    }
}
