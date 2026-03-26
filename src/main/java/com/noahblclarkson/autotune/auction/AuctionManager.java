package com.noahblclarkson.autotune.auction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.util.EnchantmentPricing;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
public class AuctionManager {

    private static final BigDecimal TICK_SIZE = BigDecimal.valueOf(0.01);

    private final AutoTune plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private final AuctionRepository auctionRepo;
    private final PlayerRepository playerRepo;
    private final AuctionMatchingEngine matchingEngine;
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    @Inject
    public AuctionManager(
            AutoTune plugin,
            Economy economy,
            ConfigManager configManager,
            AuctionRepository auctionRepo,
            PlayerRepository playerRepo
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.auctionRepo = auctionRepo;
        this.playerRepo = playerRepo;
        this.matchingEngine = new AuctionMatchingEngine();
    }

    /**
     * Place a sell order (player listing items for sale).
     * Items must be removed from the player's hand BEFORE calling this.
     * Matches immediately against existing buy orders.
     */
    public CompletableFuture<AuctionResult> placeSellOrderAsync(
            @NotNull Player player,
            @NotNull Material material,
            int quantity,
            @NotNull BigDecimal pricePerUnit
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (quantity <= 0) {
                return AuctionResult.error("Quantity must be positive");
            }
            if (pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
                return AuctionResult.error("Price must be positive");
            }
            if (!validateTickSize(pricePerUnit)) {
                return AuctionResult.error("Price must be a multiple of " + TICK_SIZE);
            }

            UUID playerId = player.getUniqueId();
            Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
            synchronized (lock) {
                // Create the order
                AuctionOrder order = AuctionOrder.builder()
                        .playerUuid(playerId)
                        .material(material.name())
                        .price(pricePerUnit)
                        .originalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .side(OrderSide.SELL)
                        .status(OrderStatus.OPEN)
                        .createdAt(Instant.now())
                        .build();

                // Load existing orders for matching
                List<AuctionOrder> existingOrders = auctionRepo.findActiveByMaterial(material.name());

                // Attempt matching against existing buy orders
                AuctionMatchingEngine.MatchResult result = matchingEngine.matchOrder(order, existingOrders);
                List<AuctionFill> fills = result.fills();

                // Calculate how much of the order was filled
                int totalFilled = order.originalQuantity() - result.matchedOrder().remainingQuantity();
                int remainingUnfilled = result.matchedOrder().remainingQuantity();

                if (!fills.isEmpty()) {
                    // Process fills: buyer pays, seller receives items paid
                    for (AuctionFill fill : fills) {
                        processFill(fill);
                    }
                }

                if (remainingUnfilled > 0) {
                    // Some quantity wasn't matched — persist the open order
                    AuctionOrder openOrder = order.withRemainingQuantity(remainingUnfilled);
                    auctionRepo.insert(openOrder);
                }

                // Record trade in player stats
                if (totalFilled > 0) {
                    BigDecimal totalSold = pricePerUnit.multiply(BigDecimal.valueOf(totalFilled));
                    playerRepo.addTransaction(playerId, totalSold, false);
                }

                String filledMsg = totalFilled > 0
                        ? totalFilled + " sold instantly, " + (remainingUnfilled > 0 ? remainingUnfilled + " listed" : "no remainder")
                        : "Listed " + quantity + " for " + configManager.formatCurrency(pricePerUnit) + " each";

                return AuctionResult.success(
                        totalFilled + " filled, " + remainingUnfilled + " remaining",
                        result.matchedOrder(),
                        fills
                );
            }
        });
    }

    /**
     * Place a buy order (player offering to buy items at a fixed price).
     * Holds the player's money in escrow. Matches immediately against existing sell orders.
     */
    public CompletableFuture<AuctionResult> placeBuyOrderAsync(
            @NotNull Player player,
            @NotNull Material material,
            int quantity,
            @NotNull BigDecimal pricePerUnit
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (quantity <= 0) {
                return AuctionResult.error("Quantity must be positive");
            }
            if (pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
                return AuctionResult.error("Price must be positive");
            }
            if (!validateTickSize(pricePerUnit)) {
                return AuctionResult.error("Price must be a multiple of " + TICK_SIZE);
            }

            BigDecimal totalCost = pricePerUnit.multiply(BigDecimal.valueOf(quantity));

            UUID playerId = player.getUniqueId();
            Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
            synchronized (lock) {
                // Deduct from player's balance immediately
                EconomyResponse withdrawResponse = economy.withdrawPlayer(player, totalCost.doubleValue());
                if (!withdrawResponse.transactionSuccess()) {
                    return AuctionResult.error("Insufficient funds. Need " + configManager.formatCurrency(totalCost));
                }

                AuctionOrder order = AuctionOrder.builder()
                        .playerUuid(playerId)
                        .material(material.name())
                        .price(pricePerUnit)
                        .originalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .side(OrderSide.BUY)
                        .status(OrderStatus.OPEN)
                        .createdAt(Instant.now())
                        .build();

                List<AuctionOrder> existingOrders = auctionRepo.findActiveByMaterial(material.name());
                AuctionMatchingEngine.MatchResult result = matchingEngine.matchOrder(order, existingOrders);
                List<AuctionFill> fills = result.fills();

                int totalFilled = order.originalQuantity() - result.matchedOrder().remainingQuantity();
                int remainingUnfilled = result.matchedOrder().remainingQuantity();

                if (!fills.isEmpty()) {
                    for (AuctionFill fill : fills) {
                        processFill(fill);
                    }
                }

                if (remainingUnfilled > 0) {
                    AuctionOrder openOrder = order.withRemainingQuantity(remainingUnfilled);
                    auctionRepo.insert(openOrder);
                    // The full cost was withdrawn upfront; the remaining escrowed funds
                    // stay in escrow until this buy order is filled or cancelled.
                    // Cancellation (cancelOrderAsync) handles the refund.
                }

                if (totalFilled > 0) {
                    BigDecimal totalBought = pricePerUnit.multiply(BigDecimal.valueOf(totalFilled));
                    playerRepo.addTransaction(playerId, totalBought, true);
                }

                String filledMsg = totalFilled > 0
                        ? totalFilled + " bought instantly, " + (remainingUnfilled > 0 ? remainingUnfilled + " listed" : "no remainder")
                        : "Listed buy order for " + quantity + " at " + configManager.formatCurrency(pricePerUnit) + " each";

                return AuctionResult.success(filledMsg, result.matchedOrder(), fills);
            }
        });
    }

    /**
     * Cancel an open order. Refunds remaining buy orders to the player.
     */
    public CompletableFuture<AuctionResult> cancelOrderAsync(@NotNull Player player, @NotNull UUID orderId) {
        return CompletableFuture.supplyAsync(() -> {
            UUID playerId = player.getUniqueId();
            Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
            synchronized (lock) {
                Optional<AuctionOrder> optOrder = auctionRepo.findById(orderId);
                if (optOrder.isEmpty()) {
                    return AuctionResult.error("Order not found");
                }

                AuctionOrder order = optOrder.get();
                if (!order.playerUuid().equals(playerId)) {
                    return AuctionResult.error("You don't own this order");
                }
                if (!order.isActive()) {
                    return AuctionResult.error("Order is not active");
                }

                // Refund escrowed funds for buy orders
                if (order.side() == OrderSide.BUY) {
                    BigDecimal refund = order.price().multiply(BigDecimal.valueOf(order.remainingQuantity()));
                    economy.depositPlayer(player, refund.doubleValue());
                }

                AuctionOrder cancelled = order.withStatusCancelled();
                auctionRepo.update(cancelled);

                return AuctionResult.success(
                        "Order cancelled" + (order.side() == OrderSide.BUY
                                ? ". " + configManager.formatCurrency(refund(order)) + " refunded."
                                : "."),
                        cancelled,
                        List.of()
                );
            }
        });
    }

    public List<AuctionOrder> getActiveOrdersForMaterial(String material) {
        return auctionRepo.findActiveByMaterial(material);
    }

    public List<AuctionOrder> getPlayerOrders(UUID playerUuid) {
        return auctionRepo.findActiveByPlayer(playerUuid);
    }

    public List<AuctionFill> getRecentFills(int limit) {
        return auctionRepo.findRecentFills(limit);
    }

    public List<AuctionFill> getFillsForOrder(UUID orderId) {
        return auctionRepo.findFillsByOrder(orderId);
    }

    public Optional<AuctionOrder> getOrder(UUID orderId) {
        return auctionRepo.findById(orderId);
    }

    private void processFill(AuctionFill fill) {
        // All DB work can stay async; economy + inventory ops must run on Bukkit main thread.
        auctionRepo.insertFill(fill);

        // Update remaining quantities on both orders
        auctionRepo.findById(fill.buyOrderId()).ifPresent(buy -> {
            int newRemaining = buy.remainingQuantity() - fill.quantity();
            auctionRepo.update(buy.withRemainingQuantity(Math.max(0, newRemaining)));
        });

        auctionRepo.findById(fill.sellOrderId()).ifPresent(sell -> {
            int newRemaining = sell.remainingQuantity() - fill.quantity();
            auctionRepo.update(sell.withRemainingQuantity(Math.max(0, newRemaining)));

            // Credit seller's Vault balance — this is the core fix for the escrow gap.
            // The buyer's funds were already withdrawn in placeBuyOrderAsync; now the
            // seller gets their proceeds.
            BigDecimal proceeds = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Player seller = Bukkit.getPlayer(sell.playerUuid());
                    economy.depositPlayer(seller, proceeds.doubleValue());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to credit seller " + sell.playerUuid() + " for fill " + fill.id(), e);
                }
            });
        });

        auctionRepo.findById(fill.buyOrderId()).ifPresent(buy -> {
            // Give buyer the items — run on main thread since we're in async context.
            // If player is offline the items are silently lost (same as chest shops).
            String materialName = buy.material();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Player buyer = Bukkit.getPlayer(buy.playerUuid());
                    if (buyer != null) {
                        Material mat = Material.valueOf(materialName);
                        ItemStack items = new ItemStack(mat, fill.quantity());
                        buyer.getInventory().addItem(items);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to give buyer " + buy.playerUuid() + " items for fill " + fill.id(), e);
                }
            });
        });
    }

    /**
     * Update order's remaining quantity. Called from GUI path where matching
     * engine is not involved (GUI directly fills an existing order).
     */
    public void updateOrderRemaining(@NotNull AuctionOrder order) {
        try {
            auctionRepo.update(order);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction order " + order.id(), e);
        }
    }

    private BigDecimal refund(AuctionOrder order) {
        return order.price().multiply(BigDecimal.valueOf(order.remainingQuantity()));
    }

    private boolean validateTickSize(BigDecimal price) {
        BigDecimal scaled = price.divide(TICK_SIZE, 9, RoundingMode.HALF_UP);
        return scaled.scale() == 0 || scaled.stripTrailingZeros().scale() <= 0;
    }

    public record AuctionResult(
            boolean success,
            String message,
            AuctionOrder order,
            List<AuctionFill> fills
    ) {
        public static AuctionResult success(String message, AuctionOrder order, List<AuctionFill> fills) {
            return new AuctionResult(true, message, order, fills);
        }

        public static AuctionResult error(String message) {
            return new AuctionResult(false, message, null, List.of());
        }
    }
}
