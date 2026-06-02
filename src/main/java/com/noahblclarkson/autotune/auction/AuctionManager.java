package com.noahblclarkson.autotune.auction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionPendingReturnRepository;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PendingNotificationRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.WatchedAuctionRepository;
import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionPendingReturn;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.util.ItemSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("PMD")
public class AuctionManager {

    private static final BigDecimal TICK_SIZE = BigDecimal.valueOf(0.01);

    private final AutoTune plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final AuctionRepository auctionRepo;
    private final PlayerRepository playerRepo;
    private final AuctionMatchingEngine matchingEngine;
    private final TreasuryService treasuryService;
    private final WatchedAuctionRepository watchedAuctionRepo;
    private final PendingNotificationRepository pendingNotificationRepo;
    private final AuctionPendingReturnRepository pendingReturnRepo;
    private final ConcurrentHashMap<String, Object> bookLocks = new ConcurrentHashMap<>();
    private final int defaultDurationHours;

    @Inject
    public AuctionManager(
            AutoTune plugin,
            Economy economy,
            ConfigManager configManager,
            DatabaseManager databaseManager,
            AuctionRepository auctionRepo,
            PlayerRepository playerRepo,
            TreasuryService treasuryService,
            WatchedAuctionRepository watchedAuctionRepo,
            PendingNotificationRepository pendingNotificationRepo,
            AuctionPendingReturnRepository pendingReturnRepo,
            AutoTuneConfig config
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.auctionRepo = auctionRepo;
        this.playerRepo = playerRepo;
        this.matchingEngine = new AuctionMatchingEngine();
        this.treasuryService = treasuryService;
        this.watchedAuctionRepo = watchedAuctionRepo;
        this.pendingNotificationRepo = pendingNotificationRepo;
        this.pendingReturnRepo = pendingReturnRepo;
        this.defaultDurationHours = config.auction().defaultDurationHours();
    }

    /**
     * Place a sell order (player listing items for sale).
     * Items must be removed from the player's hand BEFORE calling this.
     * Matches immediately against existing buy orders.
     */
    public CompletableFuture<AuctionResult> placeSellOrderAsync(
            @NotNull Player player,
            @NotNull ItemStack listedItem,
            int quantity,
            @NotNull BigDecimal pricePerUnit
    ) {
        Material material = listedItem.getType();
        ItemStack orderItem = listedItem.clone();
        orderItem.setAmount(quantity);
        // Snapshot online player UUIDs on the calling thread (main thread) so
        // the async block can safely filter buy orders without cross-thread
        // Bukkit API calls.
        Set<UUID> onlineUuids = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());

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
            Object lock = bookLocks.computeIfAbsent(material.name(), k -> new Object());
            synchronized (lock) {
                // Create the order
                AuctionOrder order = AuctionOrder.builder()
                        .playerUuid(playerId)
                        .material(material.name())
                        .itemData(ItemSerializer.serializeItemStack(orderItem))
                        .price(pricePerUnit)
                        .originalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .side(OrderSide.SELL)
                        .status(OrderStatus.OPEN)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plus(defaultDurationHours, ChronoUnit.HOURS))
                        .build();

                // Persist the taker order before matching so every fill references
                // real order IDs under both SQLite foreign keys and MariaDB.
                auctionRepo.insert(order);

                try {
                // Load existing orders for matching.
                // Filter out BUY orders from offline players - items cannot be
                // delivered to offline inventories, so those orders must wait
                // until their owner is online to participate in matching.
                List<AuctionOrder> existingOrders = auctionRepo.findActiveByMaterial(material.name())
                        .stream()
                        .filter(o -> o.side() != OrderSide.BUY || onlineUuids.contains(o.playerUuid()))
                        .toList();

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
                    // Some quantity wasn't matched - persist the open order
                    AuctionOrder openOrder = order.withRemainingQuantity(remainingUnfilled);
                    auctionRepo.update(openOrder);
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
                } catch (RuntimeException e) {
                    AuctionOrder latest = auctionRepo.findById(order.id()).orElse(order);
                    if (latest.remainingQuantity() > 0) {
                        returnOrderItems(player, latest, AuctionPendingReturn.Reason.EXPIRED_ORDER);
                    }
                    auctionRepo.update(latest.withStatusCancelled());
                    plugin.getLogger().log(Level.WARNING,
                            "Sell order failed after persistence; cancelled remainder for " + playerId, e);
                    throw new ItemsAlreadyHandledException(
                            "Sell order failed after persistence; remaining items were returned", e);
                }
            }
        }, databaseManager.getExecutor());
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
        // Snapshot online player UUIDs on the calling thread (main thread) so
        // the async block can safely filter sell orders without cross-thread
        // Bukkit API calls.
        Set<UUID> onlineUuids = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());

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
            Object lock = bookLocks.computeIfAbsent(material.name(), k -> new Object());
            synchronized (lock) {
                // -- Escrow withdrawal on main thread -
                // Vault Economy must run on Bukkit main thread. Use CountDownLatch
                // to wait for completion before proceeding.
                CountDownLatch escrowLatch = new CountDownLatch(1);
                AtomicReference<EconomyResponse> escrowRef = new AtomicReference<>();
                AtomicReference<Exception> escrowError = new AtomicReference<>();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        // Use OfflinePlayer so the withdrawal works even if player disconnects
                        // before this task runs on the main thread.
                        escrowRef.set(economy.withdrawPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()),
                                totalCost.doubleValue()));
                    } catch (Exception e) {
                        escrowError.set(e);
                    } finally {
                        escrowLatch.countDown();
                    }
                });

                try {
                    if (!escrowLatch.await(10, TimeUnit.SECONDS)) {
                        throw new RuntimeException("Timed out waiting for escrow withdrawal for " + playerId);
                    }
                    if (escrowError.get() != null) {
                        throw new RuntimeException("Escrow withdrawal failed for " + playerId,
                                escrowError.get());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while processing buy order for " + playerId, e);
                }

                EconomyResponse withdrawResponse = escrowRef.get();
                if (!withdrawResponse.transactionSuccess()) {
                    return AuctionResult.error("Insufficient funds. Need " + configManager.formatCurrency(totalCost));
                }

                AtomicReference<AuctionOrder> placedOrder = new AtomicReference<>();
                try {
                    AuctionOrder order = AuctionOrder.builder()
                            .playerUuid(playerId)
                            .material(material.name())
                            .price(pricePerUnit)
                            .originalQuantity(quantity)
                            .remainingQuantity(quantity)
                            .side(OrderSide.BUY)
                            .status(OrderStatus.OPEN)
                            .createdAt(Instant.now())
                            .expiresAt(Instant.now().plus(defaultDurationHours, ChronoUnit.HOURS))
                            .build();
                    placedOrder.set(order);

                    // Persist the taker buy order before matching so fills never
                    // reference transient order IDs.
                    auctionRepo.insert(order);

                    // Load existing orders for matching.
                    // Filter out SELL orders from offline players - items cannot be
                    // retrieved from offline inventories, and they cannot be delisted
                    // mid-transaction once a match begins, so exclude them from matching
                    // until the seller is back online.
                    List<AuctionOrder> existingOrders = auctionRepo.findActiveByMaterial(material.name())
                            .stream()
                            .filter(o -> o.side() != OrderSide.SELL || onlineUuids.contains(o.playerUuid()))
                            .toList();
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
                        auctionRepo.update(openOrder);
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
                } catch (Exception e) {
                    AuctionOrder placed = placedOrder.get();
                    AuctionOrder latest = placed == null ? null : auctionRepo.findById(placed.id()).orElse(placed);
                    BigDecimal refundAmount = latest == null
                            ? totalCost
                            : latest.price().multiply(BigDecimal.valueOf(latest.remainingQuantity()));
                    if (latest != null && latest.isActive()) {
                        auctionRepo.update(latest.withStatusCancelled());
                    }
                    // Matching or DB operation failed; refund only escrow that has not already filled.
                    // Use OfflinePlayer so the refund succeeds even if player disconnects
                    // before this task runs on the main thread.
                    CountDownLatch refundLatch = new CountDownLatch(1);
                    Bukkit.getScheduler().runTask(plugin, task -> {
                        economy.depositPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()),
                                refundAmount.doubleValue());
                        refundLatch.countDown();
                    });
                    try {
                        refundLatch.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    plugin.getLogger().log(Level.SEVERE,
                            "Buy order failed after escrow withdrawal for " + playerId
                                    + ", refunded " + refundAmount + ": " + e.getMessage(), e);
                    return AuctionResult.error("Order failed (funds refunded): " + e.getMessage());
                }
            }
        }, databaseManager.getExecutor());
    }

    /**
     * Cancel an open order. Refunds remaining buy orders to the player.
     */
    public CompletableFuture<AuctionResult> cancelOrderAsync(@NotNull Player player, @NotNull UUID orderId) {
        return CompletableFuture.supplyAsync(() -> {
            UUID playerId = player.getUniqueId();
            Optional<AuctionOrder> firstRead = auctionRepo.findById(orderId);
            if (firstRead.isEmpty()) {
                return AuctionResult.error("Order not found");
            }
            Object lock = bookLocks.computeIfAbsent(firstRead.get().material(), k -> new Object());
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

                // Refund escrowed funds for buy orders - must run on main thread (Vault Economy).
                // Use OfflinePlayer so the refund succeeds even if player disconnects
                // before this task runs on the main thread.
                if (order.side() == OrderSide.BUY) {
                    BigDecimal refund = order.price().multiply(BigDecimal.valueOf(order.remainingQuantity()));
                    CountDownLatch refundLatch = new CountDownLatch(1);
                    Bukkit.getScheduler().runTask(plugin, task -> {
                        economy.depositPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()),
                                refund.doubleValue());
                        refundLatch.countDown();
                    });
                    try {
                        if (!refundLatch.await(10, TimeUnit.SECONDS)) {
                            return AuctionResult.error("Order cancellation timed out during refund");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return AuctionResult.error("Order cancellation interrupted");
                    }
                } else {
                    returnOrderItems(player, order, AuctionPendingReturn.Reason.EXPIRED_ORDER);
                }

                AuctionOrder cancelled = order.withStatusCancelled();
                auctionRepo.update(cancelled);
                // Remove all watch entries for this order.
                watchedAuctionRepo.clearAllForOrder(order.id());

                return AuctionResult.success(
                        "Order cancelled" + (order.side() == OrderSide.BUY
                                ? ". " + configManager.formatCurrency(refund(order)) + " refunded."
                                : "."),
                        cancelled,
                        List.of()
                );
            }
        }, databaseManager.getExecutor());
    }

    public List<AuctionOrder> getActiveOrdersForMaterial(String material) {
        return auctionRepo.findActiveByMaterial(material);
    }

    public List<AuctionOrder> getPlayerOrders(UUID playerUuid) {
        return auctionRepo.findActiveByPlayer(playerUuid);
    }

    public List<AuctionOrder> getExpiredSellOrdersForPlayer(UUID playerUuid) {
        return auctionRepo.findExpiredSellOrdersByPlayer(playerUuid);
    }

    public int getPendingReturnCount(UUID playerUuid) {
        return pendingReturnRepo.countUnreturnedForPlayer(playerUuid);
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

    public List<AuctionOrder> getAllActiveOrders() {
        return auctionRepo.findAllActive();
    }

    /**
     * Process an auction fill: credit seller (after auction tax), deliver items to buyer,
     * then record to DB.
     *
     * Economy ops run FIRST on the Bukkit main thread - we wait for them to complete
     * via CountDownLatch before writing to DB. If they fail, we throw and the caller
     * refunds the buyer's escrowed money without any DB record.
     *
     * The CountDownLatch approach is safe here because:
     *   - The async thread blocks on latch.await() - it does NOT hold
     *     any lock that the main thread needs, so there is no deadlock risk.
     *   - The main thread runs economy ops (depositPlayer, addItem) which are fast and
     *     do NOT need the ForkJoinPool, so the blocking completes promptly.
     *
     * @throws RuntimeException if any economy operation fails on the main thread.
     *                         The caller is responsible for catching and refunding.
     */
    private void processFill(AuctionFill fill) {
        // Fetch both orders first - needed for economy ops and notifications.
        Optional<AuctionOrder> sellOpt = auctionRepo.findById(fill.sellOrderId());
        Optional<AuctionOrder> buyOpt = auctionRepo.findById(fill.buyOrderId());
        if (sellOpt.isEmpty() || buyOpt.isEmpty()) {
            throw new IllegalStateException("Auction fill references missing order(s): buy="
                    + fill.buyOrderId() + ", sell=" + fill.sellOrderId());
        }

        // -- DB write FIRST (source of truth) -
        // Record fill as PENDING before running any economy operations.
        // This prevents phantom transactions: if economy ops fail, we can
        // compensate rather than having money created from nothing.
        AuctionFill pendingFill = fill.withStatus(AuctionFill.FillStatus.PENDING);
        auctionRepo.insertFill(pendingFill);

        // Shared error array so lambdas can write errors for the outer method to read.
        // Array reference is effectively final; contents are mutated by lambdas.
        Exception[] economyError = new Exception[1];

        // -- Seller credit on main thread -
        final CountDownLatch sellerLatch = new CountDownLatch(1);
        if (sellOpt.isPresent()) {
            AuctionOrder sell = sellOpt.get();
            BigDecimal grossProceeds = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
            BigDecimal taxAmount = treasuryService.calculateAuctionTax(grossProceeds);
            BigDecimal netProceeds = grossProceeds.subtract(taxAmount);

            Bukkit.getScheduler().runTask(plugin, task -> {
                try {
                    // Use OfflinePlayer for deposit so sellers are credited even when offline.
                    org.bukkit.OfflinePlayer offlineSeller = Bukkit.getOfflinePlayer(sell.playerUuid());
                    EconomyResponse depositResponse = economy.depositPlayer(offlineSeller, netProceeds.doubleValue());
                    if (!depositResponse.transactionSuccess()) {
                        throw new IllegalStateException("Seller credit failed: " + depositResponse.errorMessage);
                    }
                    if (buyOpt.isPresent() && buyOpt.get().price().compareTo(fill.price()) > 0) {
                        BigDecimal improvementRefund = buyOpt.get().price()
                                .subtract(fill.price())
                                .multiply(BigDecimal.valueOf(fill.quantity()));
                        EconomyResponse refundResponse = economy.depositPlayer(
                                Bukkit.getOfflinePlayer(buyOpt.get().playerUuid()),
                                improvementRefund.doubleValue());
                        if (!refundResponse.transactionSuccess()) {
                            throw new IllegalStateException("Buyer price improvement refund failed: "
                                    + refundResponse.errorMessage);
                        }
                    }
                    treasuryService.collectTaxAmount(taxAmount);

                    // Notify seller if online; queue for offline delivery otherwise.
                    String itemName = sell.material().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', ' ');
                    itemName = itemName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                            + itemName.substring(1);
                    String saleMsg = "\u26a1 Your auction listing sold: " + fill.quantity()
                            + "\u00d7 " + itemName + " for "
                            + configManager.formatCurrency(grossProceeds) + " total";
                    Player seller = Bukkit.getPlayer(sell.playerUuid());
                    if (seller != null) {
                        seller.sendMessage(net.kyori.adventure.text.Component.text(
                                saleMsg, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    } else {
                        pendingNotificationRepo.insert(sell.playerUuid(), saleMsg, "AUCTION_FILL");
                    }
                } catch (Exception e) {
                    economyError[0] = e;
                } finally {
                    sellerLatch.countDown();
                }
            });
        } else {
            sellerLatch.countDown();
        }

        // Wait for seller credit before proceeding to buyer.
        try {
            sellerLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for seller credit", e);
        }
        if (economyError[0] != null) {
            // Mark FAILED and let the caller handle compensation for the seller.
            auctionRepo.updateFillStatus(fill.id(), AuctionFill.FillStatus.FAILED);
            throw new RuntimeException("Seller credit failed for fill " + fill.id()
                    + ": " + economyError[0].getMessage(), economyError[0]);
        }

        // -- Buyer item delivery on main thread -
        final CountDownLatch buyerLatch = new CountDownLatch(1);
        economyError[0] = null;
        if (buyOpt.isPresent()) {
            AuctionOrder buy = buyOpt.get();
            AuctionOrder sell = sellOpt.orElseThrow(() -> new IllegalStateException(
                    "Missing sell order " + fill.sellOrderId()));
            String materialName = sell.material();
            int qty = fill.quantity();

            Bukkit.getScheduler().runTask(plugin, task -> {
                try {
                    Player buyer = Bukkit.getPlayer(buy.playerUuid());
                    if (buyer == null) {
                        // Buyer offline - cannot deliver to offline inventory.
                        // This should not happen because the matching engine filters
                        // buy orders from offline players, but guard against races.
                        economyError[0] = new RuntimeException(
                                "Buyer " + buy.playerUuid() + " went offline before item delivery");
                        return;
                    }
                    ItemStack items = createOrderItemStack(sell, qty);
                    // addItem returns overflow - save undelivered items so player can reclaim.
                    Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(items);
                    if (!overflow.isEmpty()) {
                        saveOverflowReturns(buyer.getUniqueId(), fill.id(), materialName, overflow);
                    }
                } catch (Exception e) {
                    economyError[0] = e;
                } finally {
                    buyerLatch.countDown();
                }
            });
        } else {
            buyerLatch.countDown();
        }

        try {
            buyerLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for buyer item delivery", e);
        }
        if (economyError[0] != null) {
            // Mark FAILED. Buyer item delivery failed - seller has already been credited.
            // The caller should use FAILED fill status to decide compensation:
            // the seller was paid (correct), but the buyer's items were not delivered.
            // Admin intervention required to reconcile the seller credit vs missing delivery.
            auctionRepo.updateFillStatus(fill.id(), AuctionFill.FillStatus.FAILED);
            plugin.getLogger().warning(
                    "[Auto-Tune] Buyer item delivery failed for fill " + fill.id()
                            + ": " + economyError[0].getMessage()
                            + ". Seller credit of " + fill.price().multiply(BigDecimal.valueOf(fill.quantity()))
                            + " may need manual review.");
            throw new RuntimeException("Buyer item delivery failed for fill " + fill.id()
                    + ": " + economyError[0].getMessage(), economyError[0]);
        }

        // -- Economy ops succeeded - mark fill COMPLETED -
        auctionRepo.updateFillStatus(fill.id(), AuctionFill.FillStatus.COMPLETED);

        // Update remaining quantities on both orders and send notifications
        buyOpt.ifPresent(buy -> {
            int newRemaining = Math.max(0, buy.remainingQuantity() - fill.quantity());
            auctionRepo.update(buy.withRemainingQuantity(newRemaining));
            if (newRemaining == 0) {
                notifyWatchersOfFill(buy, fill);
                notifyOwnerOfFullFill(buy);
            } else {
                notifyOwnerOfPartialFill(buy, fill, newRemaining);
            }
        });
        sellOpt.ifPresent(sell -> {
            int newRemaining = Math.max(0, sell.remainingQuantity() - fill.quantity());
            auctionRepo.update(sell.withRemainingQuantity(newRemaining));
            if (newRemaining == 0) {
                notifyWatchersOfFill(sell, fill);
                notifyOwnerOfFullFill(sell);
            } else {
                notifyOwnerOfPartialFill(sell, fill, newRemaining);
            }
        });
    }

    /**
     * Notify players watching an order that it has been fully filled.
     * Offline players receive a pending notification delivered on next login.
     */
    private void notifyWatchersOfFill(AuctionOrder order, AuctionFill fill) {
        List<String> watcherUuids = watchedAuctionRepo.fetchAndClearByOrder(order.id());
        if (watcherUuids.isEmpty()) {
            return;
        }
        String itemName = order.material().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ');
        itemName = itemName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + itemName.substring(1);
        String msg = String.format(
                "Your watched %s order for %dx %s fully filled (final fill: %dx at %s/unit)",
                order.side().name().toLowerCase(),
                order.originalQuantity(),
                itemName,
                fill.quantity(),
                configManager.formatCurrency(fill.price()));
        Bukkit.getScheduler().runTask(plugin, task -> {
            for (String uuidStr : watcherUuids) {
                try {
                    UUID watcherUuid = UUID.fromString(uuidStr);
                    Player watcher = Bukkit.getPlayer(watcherUuid);
                    if (watcher != null) {
                        watcher.sendMessage(net.kyori.adventure.text.Component.text(
                                msg, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    } else {
                        pendingNotificationRepo.insert(watcherUuid, msg, "AUCTION_FILL");
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID string - skip
                }
            }
        });
    }

    /**
     * Notify the order owner that their order has been fully filled.
     * Sends one message for the complete fill.
     */
    private void notifyOwnerOfFullFill(AuctionOrder order) {
        String itemName = order.material().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ');
        itemName = itemName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + itemName.substring(1);
        String msg = String.format(
                "Your %s order for %dx %s has been fully filled",
                order.side().name().toLowerCase(),
                order.originalQuantity(),
                itemName);
        sendOwnerMessage(order.playerUuid(), msg);
    }

    /**
     * Notify the order owner that their order received a partial fill.
     * Shows the fill quantity and remaining amount.
     *
     * @param order        the original order (pre-fill snapshot)
     * @param fill         the fill that just occurred
     * @param newRemaining the remaining quantity AFTER this fill
     */
    private void notifyOwnerOfPartialFill(AuctionOrder order, AuctionFill fill, int newRemaining) {
        String itemName = order.material().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ');
        itemName = itemName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + itemName.substring(1);
        String msg = String.format(
                "Partial fill on your %s order: %dx %s filled at %s/unit (%d remaining)",
                order.side().name().toLowerCase(),
                fill.quantity(),
                itemName,
                configManager.formatCurrency(fill.price()),
                newRemaining);
        sendOwnerMessage(order.playerUuid(), msg);
    }

    private void sendOwnerMessage(UUID playerUuid, String msg) {
        Bukkit.getScheduler().runTask(plugin, task -> {
            Player owner = Bukkit.getPlayer(playerUuid);
            if (owner != null) {
                owner.sendMessage(net.kyori.adventure.text.Component.text(
                        msg, net.kyori.adventure.text.format.NamedTextColor.GREEN));
            } else {
                pendingNotificationRepo.insert(playerUuid, msg, "AUCTION_FILL");
            }
        });
    }

    /**
     * Fill an existing buy order by selling items from the player inventory.
     * The method creates a real short-lived sell order so the fill table never
     * stores player UUIDs in order-id columns.
     */
    public CompletableFuture<AuctionResult> fillBuyOrderAsync(
            @NotNull Player seller,
            @NotNull UUID buyOrderId,
            int requestedQuantity
    ) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionOrder initialBuyOrder = auctionRepo.findById(buyOrderId)
                    .orElse(null);
            if (initialBuyOrder == null || !initialBuyOrder.isActive()
                    || initialBuyOrder.side() != OrderSide.BUY) {
                return AuctionResult.error("Buy order is no longer available");
            }
            Object lock = bookLocks.computeIfAbsent(initialBuyOrder.material(), k -> new Object());
            synchronized (lock) {
            AuctionOrder buyOrder = auctionRepo.findById(buyOrderId)
                    .orElse(null);
            if (buyOrder == null || !buyOrder.isActive() || buyOrder.side() != OrderSide.BUY) {
                return AuctionResult.error("Buy order is no longer available");
            }
            if (buyOrder.playerUuid().equals(seller.getUniqueId())) {
                return AuctionResult.error("You cannot fill your own order");
            }

            int fillQty = Math.min(Math.max(1, requestedQuantity), buyOrder.remainingQuantity());
            Material material = Material.valueOf(buyOrder.material());
            if (!takeItemsFromPlayer(seller, material, fillQty)) {
                return AuctionResult.error("You do not have enough " + formatMaterialName(buyOrder.material()));
            }

            AuctionOrder sellOrder = AuctionOrder.builder()
                    .playerUuid(seller.getUniqueId())
                    .material(buyOrder.material())
                    .price(buyOrder.price())
                    .originalQuantity(fillQty)
                    .remainingQuantity(fillQty)
                    .side(OrderSide.SELL)
                    .status(OrderStatus.OPEN)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                    .build();
            try {
                auctionRepo.insert(sellOrder);
                AuctionFill fill = AuctionFill.builder()
                        .buyOrderId(buyOrder.id())
                        .sellOrderId(sellOrder.id())
                        .quantity(fillQty)
                        .price(buyOrder.price())
                        .filledAt(Instant.now())
                        .build();
                processFill(fill);
                return AuctionResult.success("Sold " + fillQty + "x "
                        + formatMaterialName(buyOrder.material()) + " for "
                        + configManager.formatCurrency(buyOrder.price().multiply(BigDecimal.valueOf(fillQty))),
                        sellOrder.withRemainingQuantity(0),
                        List.of(fill));
            } catch (RuntimeException e) {
                auctionRepo.update(sellOrder.withStatusCancelled());
                giveItemsToPlayer(seller, new ItemStack(material, fillQty), null,
                        AuctionPendingReturn.Reason.INVENTORY_FULL);
                throw e;
            }
            }
        }, databaseManager.getExecutor());
    }

    /**
     * Fill an existing sell order by buying its remaining items immediately.
     * The method creates a real short-lived buy order so normal fill settlement
     * and history code can be reused.
     */
    public CompletableFuture<AuctionResult> fillSellOrderAsync(
            @NotNull Player buyer,
            @NotNull UUID sellOrderId,
            int requestedQuantity
    ) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionOrder initialSellOrder = auctionRepo.findById(sellOrderId)
                    .orElse(null);
            if (initialSellOrder == null || !initialSellOrder.isActive()
                    || initialSellOrder.side() != OrderSide.SELL) {
                return AuctionResult.error("Sell order is no longer available");
            }
            Object lock = bookLocks.computeIfAbsent(initialSellOrder.material(), k -> new Object());
            synchronized (lock) {
            AuctionOrder sellOrder = auctionRepo.findById(sellOrderId)
                    .orElse(null);
            if (sellOrder == null || !sellOrder.isActive() || sellOrder.side() != OrderSide.SELL) {
                return AuctionResult.error("Sell order is no longer available");
            }
            if (sellOrder.playerUuid().equals(buyer.getUniqueId())) {
                return AuctionResult.error("You cannot fill your own order");
            }

            int fillQty = Math.min(Math.max(1, requestedQuantity), sellOrder.remainingQuantity());
            BigDecimal totalCost = sellOrder.price().multiply(BigDecimal.valueOf(fillQty));
            if (!withdrawPlayer(buyer.getUniqueId(), totalCost)) {
                return AuctionResult.error("Insufficient funds. Need " + configManager.formatCurrency(totalCost));
            }

            AuctionOrder buyOrder = AuctionOrder.builder()
                    .playerUuid(buyer.getUniqueId())
                    .material(sellOrder.material())
                    .price(sellOrder.price())
                    .originalQuantity(fillQty)
                    .remainingQuantity(fillQty)
                    .side(OrderSide.BUY)
                    .status(OrderStatus.OPEN)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                    .build();
            try {
                auctionRepo.insert(buyOrder);
                AuctionFill fill = AuctionFill.builder()
                        .buyOrderId(buyOrder.id())
                        .sellOrderId(sellOrder.id())
                        .quantity(fillQty)
                        .price(sellOrder.price())
                        .filledAt(Instant.now())
                        .build();
                processFill(fill);
                return AuctionResult.success("Bought " + fillQty + "x "
                        + formatMaterialName(sellOrder.material()) + " for "
                        + configManager.formatCurrency(totalCost),
                        buyOrder.withRemainingQuantity(0),
                        List.of(fill));
            } catch (RuntimeException e) {
                auctionRepo.update(buyOrder.withStatusCancelled());
                depositPlayer(buyer.getUniqueId(), totalCost, "auction purchase refund");
                throw e;
            }
            }
        }, databaseManager.getExecutor());
    }

    /**
     * Compatibility wrapper for direct fill recording. Both IDs must be real
     * auction order IDs; GUI/player-UUID fill paths now use fillBuyOrderAsync or
     * fillSellOrderAsync so settlement remains manager-owned and FK-safe.
     */
    public CompletableFuture<Void> recordFillAsync(@NotNull UUID buyOrderId, @NotNull UUID sellOrderId,
                                int quantity, @NotNull BigDecimal execPrice,
                                @NotNull String material) {
        return CompletableFuture.runAsync(() -> {
            AuctionOrder buy = auctionRepo.findById(buyOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("Buy order not found: " + buyOrderId));
            AuctionOrder sell = auctionRepo.findById(sellOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("Sell order not found: " + sellOrderId));
            if (buy.side() != OrderSide.BUY || sell.side() != OrderSide.SELL) {
                throw new IllegalArgumentException("Fill order sides are invalid");
            }
            if (!buy.material().equalsIgnoreCase(sell.material())
                    || !buy.material().equalsIgnoreCase(material)) {
                throw new IllegalArgumentException("Fill material does not match both orders");
            }
            if (quantity <= 0 || quantity > buy.remainingQuantity() || quantity > sell.remainingQuantity()) {
                throw new IllegalArgumentException("Invalid fill quantity: " + quantity);
            }
            AuctionFill fill = AuctionFill.builder()
                    .buyOrderId(buyOrderId)
                    .sellOrderId(sellOrderId)
                    .quantity(quantity)
                    .price(execPrice)
                    .filledAt(Instant.now())
                    .build();
            processFill(fill);
        }, databaseManager.getExecutor());
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

    /**
     * Process all expired auction orders.
     * BUY orders: escrowed funds are refunded to the player.
     * SELL orders: items are returned to the player's inventory (if online).
     * Players are notified via message when their orders expire.
     *
     * This method runs on the calling thread (typically the async scheduler).
     * Economy operations (refunds, item returns) are dispatched to the main thread
     * via Bukkit.getScheduler().runTask() and awaited via CountDownLatch.
     *
     * @return number of orders that were expired
     */
    public int processExpiredOrders() {
        List<AuctionOrder> expired = auctionRepo.findExpiredOrders();
        if (expired.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (AuctionOrder order : expired) {
            try {
                expireOrderSafe(order);
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to expire auction order " + order.id() + ": " + e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * Reclaim expired sell orders for a player: finds EXPIRED sell orders with
     * remaining items and gives them back to the player.
     *
     * Called when a player runs /auction reclaim.
     *
     * @return the number of items returned (not orders - may be multiple items per order)
     */
    public int reclaimExpiredOrders(Player player) {
        List<AuctionOrder> expired = auctionRepo.findExpiredSellOrdersByPlayer(player.getUniqueId());
        int itemsReturned = 0;
        for (AuctionOrder order : expired) {
            try {
                int reclaimed = reclaimSingleOrderSafe(player, order);
                itemsReturned += reclaimed;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to reclaim auction order " + order.id() + ": " + e.getMessage(), e);
            }
        }

        for (AuctionPendingReturn pending : pendingReturnRepo.findUnreturnedForPlayer(player.getUniqueId())) {
            try {
                int reclaimed = reclaimPendingReturnSafe(player, pending);
                itemsReturned += reclaimed;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to reclaim pending auction return " + pending.id() + ": " + e.getMessage(), e);
            }
        }
        return itemsReturned;
    }

    private int reclaimSingleOrderSafe(Player player, AuctionOrder order) {
        try {
            int returned = callOnMain(() -> {
                ItemStack stack = createOrderItemStack(order, order.remainingQuantity());
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                int overflowCount = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                int returnedCount = Math.max(0, order.remainingQuantity() - overflowCount);
                if (!overflow.isEmpty()) {
                    saveOverflowReturns(player.getUniqueId(), null, order.material(), overflow);
                }
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "Reclaimed " + returnedCount + "x " + formatMaterialName(order.material())
                                + " from expired sell order.",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));
                return returnedCount;
            }, "reclaim expired auction order");
            auctionRepo.update(order.withStatusReclaimed());
            return returned;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Error reclaiming order " + order.id(), e);
            return 0;
        }
    }

    private int reclaimPendingReturnSafe(Player player, AuctionPendingReturn pending) {
        try {
            return callOnMain(() -> {
                ItemStack stack = pending.itemData() != null
                        ? ItemSerializer.deserializeItemStack(pending.itemData())
                        : new ItemStack(Material.valueOf(pending.material()), pending.quantity());
                stack.setAmount(pending.quantity());

                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                int overflowCount = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                int returned = Math.max(0, pending.quantity() - overflowCount);

                if (overflow.isEmpty()) {
                    pendingReturnRepo.markReturned(pending.id());
                } else {
                    ItemStack remaining = overflow.values().iterator().next().clone();
                    remaining.setAmount(overflowCount);
                    pendingReturnRepo.updateUnreturnedPayload(
                            pending.id(), overflowCount, ItemSerializer.serializeItemStack(remaining));
                }

                if (returned > 0) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "Reclaimed " + returned + "x " + formatMaterialName(pending.material())
                                    + " from pending auction delivery.",
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } else {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "No inventory space for pending auction delivery. Free space and run /auction reclaim again.",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                }
                return returned;
            }, "reclaim pending auction return");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Error reclaiming pending return " + pending.id(), e);
            return 0;
        }
    }

    /**
     * Reclaim a single expired sell order: give items to the player and mark RECLAIMED.
     * Runs item-return on the main thread, then updates the DB.
     */
    private int reclaimSingleOrder(Player player, AuctionOrder order) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        AtomicReference<Integer> itemsRef = new AtomicReference<>(0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Material mat = Material.valueOf(order.material());
                ItemStack stack = new ItemStack(mat, order.remainingQuantity());
                var overflow = player.getInventory().addItem(stack);
                int returned = order.remainingQuantity() - (overflow.isEmpty() ? 0 :
                        overflow.values().stream().mapToInt(ItemStack::getAmount).sum());
                itemsRef.set(returned);
                if (!overflow.isEmpty()) {
                    saveOverflowReturns(player.getUniqueId(), null, order.material(), overflow);
                }

                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "Reclaimed " + returned + "x " + formatMaterialName(order.material())
                                + " from expired sell order.",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));

                // Update DB on main thread too - this legacy path already uses the latch pattern.
                auctionRepo.update(order.withStatusReclaimed());
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while reclaiming order " + order.id());
            return 0;
        }

        if (error.get() != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Error reclaiming order " + order.id() + ": " + error.get().getMessage());
            return 0;
        }

        return itemsRef.get();
    }

    /**
     * Reclaim a pending item return that was saved because the original
     * delivery could not fit in the player's inventory.
     */
    private int reclaimPendingReturn(Player player, AuctionPendingReturn pending) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        AtomicReference<Integer> itemsRef = new AtomicReference<>(0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ItemStack stack = pending.itemData() != null
                        ? ItemSerializer.deserializeItemStack(pending.itemData())
                        : new ItemStack(Material.valueOf(pending.material()), pending.quantity());
                stack.setAmount(pending.quantity());

                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                int overflowCount = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                int returned = pending.quantity() - overflowCount;
                itemsRef.set(Math.max(0, returned));

                if (overflow.isEmpty()) {
                    pendingReturnRepo.markReturned(pending.id());
                } else {
                    ItemStack remaining = overflow.values().iterator().next().clone();
                    remaining.setAmount(overflowCount);
                    pendingReturnRepo.updateUnreturnedPayload(
                            pending.id(), overflowCount, ItemSerializer.serializeItemStack(remaining));
                }

                if (returned > 0) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "Reclaimed " + returned + "x " + formatMaterialName(pending.material())
                                    + " from pending auction delivery.",
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } else {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "No inventory space for pending auction delivery. Free space and run /auction reclaim again.",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                }
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while reclaiming pending return " + pending.id());
            return 0;
        }

        if (error.get() != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Error reclaiming pending return " + pending.id() + ": " + error.get().getMessage());
            return 0;
        }

        return itemsRef.get();
    }

    private void expireOrderSafe(AuctionOrder order) {
        try {
            AuctionOrder updatedOrder = callOnMain(() -> {
                if (order.side() == OrderSide.BUY) {
                    BigDecimal refund = order.price()
                            .multiply(BigDecimal.valueOf(order.remainingQuantity()));
                    EconomyResponse response = economy.depositPlayer(
                            Bukkit.getOfflinePlayer(order.playerUuid()),
                            refund.doubleValue());
                    if (!response.transactionSuccess()) {
                        throw new IllegalStateException("Auction escrow refund failed: " + response.errorMessage);
                    }

                    String refundMsg = "Your buy order for " + order.remainingQuantity()
                            + "x " + formatMaterialName(order.material())
                            + " expired. " + configManager.formatCurrency(refund)
                            + " was refunded to your balance.";
                    Player player = Bukkit.getPlayer(order.playerUuid());
                    if (player != null) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                refundMsg,
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    } else {
                        pendingNotificationRepo.insert(order.playerUuid(), refundMsg, "AUCTION_EXPIRY");
                    }
                    return order.withStatusExpired();
                }

                Player player = Bukkit.getPlayer(order.playerUuid());
                if (player == null) {
                    pendingNotificationRepo.insert(order.playerUuid(),
                            "Your sell order for " + order.remainingQuantity()
                                    + "x " + formatMaterialName(order.material())
                                    + " expired while you were offline. Use /auction reclaim when you return.",
                            "AUCTION_EXPIRY");
                    return order.withStatusExpired();
                }

                ItemStack items = createOrderItemStack(order, order.remainingQuantity());
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(items);
                if (!overflow.isEmpty()) {
                    saveOverflowReturns(order.playerUuid(), null, order.material(), overflow);
                }
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "Your sell order for " + order.remainingQuantity() + "x "
                                + formatMaterialName(order.material())
                                + " expired. Items were returned to your inventory.",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                return order.withStatusReclaimed();
            }, "expire auction order");
            auctionRepo.update(updatedOrder);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error expiring order " + order.id() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Expire a single order: update status, refund/return as appropriate.
     */
    private void expireOrder(AuctionOrder order) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        AtomicReference<AuctionOrder> updatedOrder = new AtomicReference<>(order.withStatusExpired());

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (order.side() == OrderSide.BUY) {
                    // Refund escrowed funds to the player.
                    // Use OfflinePlayer so refunds are always credited, even when
                    // the player is offline when their order expires.
                    BigDecimal refund = order.price()
                            .multiply(BigDecimal.valueOf(order.remainingQuantity()));
                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(order.playerUuid());
                    economy.depositPlayer(offlinePlayer, refund.doubleValue());

                    String refundMsg = "Your buy order for " + order.remainingQuantity()
                            + "x " + formatMaterialName(order.material())
                            + " expired. " + configManager.formatCurrency(refund)
                            + " refunded to your balance.";
                    Player player = Bukkit.getPlayer(order.playerUuid());
                    if (player != null) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                refundMsg,
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    } else {
                        pendingNotificationRepo.insert(order.playerUuid(), refundMsg, "AUCTION_EXPIRY");
                    }
                } else {
                    // Return items to the seller's inventory (if online)
                    Player player = Bukkit.getPlayer(order.playerUuid());
                    if (player != null) {
                        Material mat = Material.valueOf(order.material());
                        ItemStack items = new ItemStack(mat, order.remainingQuantity());
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items);
                        if (!overflow.isEmpty()) {
                            saveOverflowReturns(order.playerUuid(), null, order.material(), overflow);
                        }
                        updatedOrder.set(order.withStatusReclaimed());
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "Your sell order for " + order.remainingQuantity() + "x "
                                        + formatMaterialName(order.material())
                                        + " expired. Items returned to your inventory.",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }
                    // Player is offline - items remain in the DB as EXPIRED.
                    // They will be auto-reclaimed on next login (PlayerListener).
                    // Send a pending notification so the player knows.
                    pendingNotificationRepo.insert(order.playerUuid(),
                            "\u26a0 Your sell order for " + order.remainingQuantity()
                                    + "\u00d7 " + formatMaterialName(order.material())
                                    + " expired while you were offline. Items will be returned to your inventory on next login.",
                            "AUCTION_EXPIRY");
                }
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while expiring order " + order.id());
            return;
        }

        if (error.get() != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Error expiring order " + order.id() + ": " + error.get().getMessage());
            return;
        }

        // Update order status in DB
        auctionRepo.update(updatedOrder.get());
    }

    /**
     * Persist items Bukkit could not add to a player's inventory. Bukkit's
     * addItem API reports undelivered stacks in its return map; ignoring that
     * map silently destroys items when an inventory is full.
     */
    private void returnOrderItems(Player player, AuctionOrder order, AuctionPendingReturn.Reason reason) {
        if (order.remainingQuantity() <= 0) {
            return;
        }
        ItemStack stack = createOrderItemStack(order, order.remainingQuantity());
        giveItemsToPlayer(player, stack, null, reason);
    }

    private void giveItemsToPlayer(Player player, ItemStack stack, UUID fillId, AuctionPendingReturn.Reason reason) {
        callOnMain(() -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                saveOverflowReturns(player.getUniqueId(), fillId, stack.getType().name(), overflow);
            }
            return null;
        }, "return auction items");
    }

    private boolean takeItemsFromPlayer(Player player, Material material, int amount) {
        return callOnMain(() -> {
            int available = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == material) {
                    available += stack.getAmount();
                }
            }
            if (available < amount) {
                return false;
            }

            int remaining = amount;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType() != material) {
                    continue;
                }
                int taken = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) {
                    player.getInventory().clear(i);
                }
            }
            return true;
        }, "take auction items");
    }

    private boolean withdrawPlayer(UUID playerUuid, BigDecimal amount) {
        return callOnMain(() -> economy.withdrawPlayer(
                Bukkit.getOfflinePlayer(playerUuid),
                amount.doubleValue()).transactionSuccess(), "withdraw auction funds");
    }

    private void depositPlayer(UUID playerUuid, BigDecimal amount, String operation) {
        callOnMain(() -> {
            EconomyResponse response = economy.depositPlayer(
                    Bukkit.getOfflinePlayer(playerUuid),
                    amount.doubleValue());
            if (!response.transactionSuccess()) {
                throw new IllegalStateException(operation + " failed: " + response.errorMessage);
            }
            return null;
        }, operation);
    }

    private ItemStack createOrderItemStack(AuctionOrder order, int quantity) {
        ItemStack stack = ItemSerializer.tryDeserializeItemStack(order.itemData());
        if (stack == null) {
            stack = new ItemStack(Material.valueOf(order.material()));
        } else {
            stack = stack.clone();
        }
        stack.setAmount(quantity);
        return stack;
    }

    private <T> T callOnMain(Callable<T> callable, String operation) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(operation + " failed", e);
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                result.set(callable.call());
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException(operation + " timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(operation + " interrupted", e);
        }

        if (error.get() != null) {
            throw new RuntimeException(operation + " failed", error.get());
        }
        return result.get();
    }

    private void saveOverflowReturns(UUID playerUuid, UUID fillId, String materialName, Map<Integer, ItemStack> overflow) {
        int total = 0;
        for (ItemStack item : overflow.values()) {
            ItemStack copy = item.clone();
            total += copy.getAmount();
            AuctionPendingReturn pending = AuctionPendingReturn.builder()
                    .playerUuid(playerUuid)
                    .fillId(fillId)
                    .material(copy.getType().name())
                    .itemData(ItemSerializer.serializeItemStack(copy))
                    .quantity(copy.getAmount())
                    .reason(AuctionPendingReturn.Reason.INVENTORY_FULL)
                    .build();
            pendingReturnRepo.insert(pending);
        }

        String message = total + "x " + formatMaterialName(materialName)
                + " could not fit in your inventory and was saved. Use /auction reclaim to retrieve it.";
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    message,
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        } else {
            pendingNotificationRepo.insert(playerUuid, message, "AUCTION_RETURN");
        }
    }

    private String formatMaterialName(String material) {
        String name = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
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

    public static class ItemsAlreadyHandledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ItemsAlreadyHandledException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
