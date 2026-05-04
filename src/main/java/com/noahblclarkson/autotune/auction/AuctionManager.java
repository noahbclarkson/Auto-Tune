package com.noahblclarkson.autotune.auction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.PendingNotificationRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.WatchedAuctionRepository;
import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;
import com.noahblclarkson.autotune.manager.TreasuryService;
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
import java.util.Set;
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
    private final AuctionRepository auctionRepo;
    private final PlayerRepository playerRepo;
    private final AuctionMatchingEngine matchingEngine;
    private final TreasuryService treasuryService;
    private final WatchedAuctionRepository watchedAuctionRepo;
    private final PendingNotificationRepository pendingNotificationRepo;
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final int defaultDurationHours;

    @Inject
    public AuctionManager(
            AutoTune plugin,
            Economy economy,
            ConfigManager configManager,
            AuctionRepository auctionRepo,
            PlayerRepository playerRepo,
            TreasuryService treasuryService,
            WatchedAuctionRepository watchedAuctionRepo,
            PendingNotificationRepository pendingNotificationRepo,
            AutoTuneConfig config
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.auctionRepo = auctionRepo;
        this.playerRepo = playerRepo;
        this.matchingEngine = new AuctionMatchingEngine();
        this.treasuryService = treasuryService;
        this.watchedAuctionRepo = watchedAuctionRepo;
        this.pendingNotificationRepo = pendingNotificationRepo;
        this.defaultDurationHours = config.auction().defaultDurationHours();
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
                        .expiresAt(Instant.now().plus(defaultDurationHours, ChronoUnit.HOURS))
                        .build();

                // Load existing orders for matching.
                // Filter out BUY orders from offline players — items cannot be
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
                // ── Escrow withdrawal on main thread ───────────────────────────
                // Vault Economy must run on Bukkit main thread. Use CountDownLatch
                // to wait for completion before proceeding.
                CountDownLatch escrowLatch = new CountDownLatch(1);
                AtomicReference<EconomyResponse> escrowRef = new AtomicReference<>();
                AtomicReference<Exception> escrowError = new AtomicReference<>();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        escrowRef.set(economy.withdrawPlayer(player, totalCost.doubleValue()));
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
                } catch (Exception e) {
                    // Matching or DB operation failed — refund the escrowed money on main thread
                    CountDownLatch refundLatch = new CountDownLatch(1);
                    Bukkit.getScheduler().runTask(plugin, task -> {
                        economy.depositPlayer(player, totalCost.doubleValue());
                        refundLatch.countDown();
                    });
                    try {
                        refundLatch.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    plugin.getLogger().log(Level.SEVERE,
                            "Buy order failed after escrow withdrawal for " + playerId
                                    + ", refunded " + totalCost + ": " + e.getMessage(), e);
                    return AuctionResult.error("Order failed (funds refunded): " + e.getMessage());
                }
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

                // Refund escrowed funds for buy orders — must run on main thread (Vault Economy)
                if (order.side() == OrderSide.BUY) {
                    BigDecimal refund = order.price().multiply(BigDecimal.valueOf(order.remainingQuantity()));
                    CountDownLatch refundLatch = new CountDownLatch(1);
                    Bukkit.getScheduler().runTask(plugin, task -> {
                        economy.depositPlayer(player, refund.doubleValue());
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
        });
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
     * Economy ops run FIRST on the Bukkit main thread — we wait for them to complete
     * via CountDownLatch before writing to DB. If they fail, we throw and the caller
     * refunds the buyer's escrowed money without any DB record.
     *
     * The CountDownLatch approach is safe here because:
     *   - The async thread (ForkJoinPool) blocks on latch.await() — it does NOT hold
     *     any lock that the main thread needs, so there is no deadlock risk.
     *   - The main thread runs economy ops (depositPlayer, addItem) which are fast and
     *     do NOT need the ForkJoinPool, so the blocking completes promptly.
     *
     * @throws RuntimeException if any economy operation fails on the main thread.
     *                         The caller is responsible for catching and refunding.
     */
    private void processFill(AuctionFill fill) {
        // Fetch both orders first — needed for economy ops and notifications.
        Optional<AuctionOrder> sellOpt = auctionRepo.findById(fill.sellOrderId());
        Optional<AuctionOrder> buyOpt = auctionRepo.findById(fill.buyOrderId());

        // ── DB write FIRST (source of truth) ─────────────────────────────────
        // Record fill as PENDING before running any economy operations.
        // This prevents phantom transactions: if economy ops fail, we can
        // compensate rather than having money created from nothing.
        AuctionFill pendingFill = fill.withStatus(AuctionFill.FillStatus.PENDING);
        auctionRepo.insertFill(pendingFill);

        // Shared error array so lambdas can write errors for the outer method to read.
        // Array reference is effectively final; contents are mutated by lambdas.
        Exception[] economyError = new Exception[1];

        // ── Seller credit on main thread ──────────────────────────────────────
        final CountDownLatch sellerLatch = new CountDownLatch(1);
        if (sellOpt.isPresent()) {
            AuctionOrder sell = sellOpt.get();
            BigDecimal grossProceeds = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
            BigDecimal taxAmount = treasuryService.collectAuctionTax(grossProceeds);
            BigDecimal netProceeds = grossProceeds.subtract(taxAmount);

            Bukkit.getScheduler().runTask(plugin, task -> {
                try {
                    // Use OfflinePlayer for deposit so sellers are credited even when offline.
                    org.bukkit.OfflinePlayer offlineSeller = Bukkit.getOfflinePlayer(sell.playerUuid());
                    economy.depositPlayer(offlineSeller, netProceeds.doubleValue());

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

        // ── Buyer item delivery on main thread ────────────────────────────────
        final CountDownLatch buyerLatch = new CountDownLatch(1);
        economyError[0] = null;
        if (buyOpt.isPresent()) {
            AuctionOrder buy = buyOpt.get();
            String materialName = buy.material();
            int qty = fill.quantity();

            Bukkit.getScheduler().runTask(plugin, task -> {
                try {
                    Player buyer = Bukkit.getPlayer(buy.playerUuid());
                    if (buyer == null) {
                        // Buyer offline — cannot deliver to offline inventory.
                        // This should not happen because the matching engine filters
                        // buy orders from offline players, but guard against races.
                        economyError[0] = new RuntimeException(
                                "Buyer " + buy.playerUuid() + " went offline before item delivery");
                        return;
                    }
                    Material mat = Material.valueOf(materialName);
                    ItemStack items = new ItemStack(mat, qty);
                    buyer.getInventory().addItem(items);
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
            // Mark FAILED. Buyer item delivery failed — seller has already been credited.
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

        // ── Economy ops succeeded — mark fill COMPLETED ───────────────────────
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
                "⚡ Your watched %s order for %d× %s fully filled (final fill: %d× at %s/unit)",
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
                    // Invalid UUID string — skip
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
                "⚡ Your %s order for %d× %s has been fully filled",
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
                "📦 Partial fill on your %s order: %d× %s filled at %s/unit (%d remaining)",
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
     * Record an auction fill from the GUI path (direct fill without matching engine).
     * Handles DB insert, quantity updates, seller Vault credit, and buyer item delivery.
     * Returns a CompletableFuture so callers can await DB completion before applying
     * their own economy/inventory effects (enables atomicity in the GUI layer).
     *
     * CRITICAL: Economy ops (seller credit + buyer item delivery) run FIRST on the
     * Bukkit main thread via CountDownLatch/await. Only after they succeed do we
     * write to the DB. If economy ops fail we throw a RuntimeException and the caller
     * (AuctionGui) refunds the buyer's escrowed money — no DB record is created.
     *
     * @param buyOrderId  The buy order ID (may be a player UUID in GUI direct-fill path)
     * @param sellOrderId The sell order ID (may be a player UUID in GUI direct-fill path)
     * @param quantity    Number of items in the fill
     * @param execPrice   Execution price per unit
     * @param material    Material name for the item being traded (used when order lookup fails)
     */
    public CompletableFuture<Void> recordFillAsync(@NotNull UUID buyOrderId, @NotNull UUID sellOrderId,
                                int quantity, @NotNull BigDecimal execPrice,
                                @NotNull String material) {
        UUID fillId = UUID.randomUUID();
        Instant now = Instant.now();

        // Look up orders synchronously on the ForkJoinPool thread — no main-thread
        // dependency here, just DB reads which are safe to parallelize.
        Optional<AuctionOrder> buyOpt = auctionRepo.findById(buyOrderId);
        Optional<AuctionOrder> sellOpt = auctionRepo.findById(sellOrderId);

        BigDecimal grossProceeds = execPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal taxAmount = treasuryService.collectAuctionTax(grossProceeds);
        BigDecimal netProceeds = grossProceeds.subtract(taxAmount);
        UUID sellerUuid = sellOpt.map(AuctionOrder::playerUuid).orElse(sellOrderId);
        UUID buyerUuid = buyOpt.map(AuctionOrder::playerUuid).orElse(buyOrderId);

        // ── Economy ops FIRST — then DB write ──────────────────────────────────
        // Run seller credit and buyer item delivery on the main thread, waiting
        // for both to complete before touching the DB. If either fails, throw so
        // the caller refunds escrow and no DB record is created.
        CountDownLatch economyLatch = new CountDownLatch(1);
        AtomicReference<Exception> economyError = new AtomicReference<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Credit seller (after auction tax deduction).
                // Use OfflinePlayer so sellers are credited even when not online.
                org.bukkit.OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerUuid);
                economy.depositPlayer(seller, netProceeds.doubleValue());

                // Give buyer their items.
                Player buyer = Bukkit.getPlayer(buyerUuid);
                if (buyer != null) {
                    Material mat = Material.valueOf(material);
                    buyer.getInventory().addItem(new ItemStack(mat, quantity));
                } else {
                    // Buyer went offline between GUI click and fill — log for admin.
                    // Items cannot be delivered to offline inventory; store notification
                    // so they know to use /auction reclaim when back online.
                    plugin.getLogger().warning(
                            "[Auto-Tune] Buyer " + buyerUuid + " offline during fill "
                                    + fillId + " — " + quantity + "× " + material
                                    + " not delivered. Player should /auction reclaim.");
                    pendingNotificationRepo.insert(buyerUuid,
                            "\u26a0 You were offline when your buy order was filled: "
                                    + quantity + "\u00d7 " + material
                                    + ". Use /auction reclaim to retrieve your items.",
                            "AUCTION_FILL");
                }
            } catch (Exception e) {
                economyError.set(e);
            } finally {
                economyLatch.countDown();
            }
        });

        try {
            if (!economyLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for auction economy ops for fill " + fillId);
            }
            if (economyError.get() != null) {
                throw new RuntimeException("Auction economy op failed for fill " + fillId, economyError.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while processing auction fill " + fillId, e);
        }

        // Economy ops succeeded. Now atomically write to DB — if this fails,
        // money and items are already with the right players (better outcome than
        // DB showing a fill that never delivered).
        AuctionFill fill = AuctionFill.builder()
                .id(fillId)
                .buyOrderId(buyOrderId)
                .sellOrderId(sellOrderId)
                .quantity(quantity)
                .price(execPrice)
                .filledAt(now)
                .status(AuctionFill.FillStatus.COMPLETED)
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                auctionRepo.insertFill(fill);

                buyOpt.ifPresent(buy -> {
                    int newRemaining = Math.max(0, buy.remainingQuantity() - quantity);
                    auctionRepo.update(buy.withRemainingQuantity(newRemaining));
                    if (newRemaining == 0) {
                        notifyWatchersOfFill(buy, fill);
                        notifyOwnerOfFullFill(buy);
                    } else {
                        notifyOwnerOfPartialFill(buy, fill, newRemaining);
                    }
                });

                sellOpt.ifPresent(sell -> {
                    int newRemaining = Math.max(0, sell.remainingQuantity() - quantity);
                    auctionRepo.update(sell.withRemainingQuantity(newRemaining));
                    if (newRemaining == 0) {
                        notifyWatchersOfFill(sell, fill);
                        notifyOwnerOfFullFill(sell);
                    } else {
                        notifyOwnerOfPartialFill(sell, fill, newRemaining);
                    }
                });

                return null;
            } catch (Exception e) {
                // Money + items already delivered; DB write failed — log for admin review.
                plugin.getLogger().log(Level.SEVERE,
                        "[Auto-Tune] DB write failed after auction fill " + fillId
                                + " — money/items delivered but not recorded. Manual review needed.", e);
                return null;  // Don't propagate: player already has their stuff
            }
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
                expireOrder(order);
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
     * @return the number of items returned (not orders — may be multiple items per order)
     */
    public int reclaimExpiredOrders(Player player) {
        List<AuctionOrder> expired = auctionRepo.findExpiredSellOrdersByPlayer(player.getUniqueId());
        if (expired.isEmpty()) {
            return 0;
        }

        int itemsReturned = 0;
        for (AuctionOrder order : expired) {
            try {
                int reclaimed = reclaimSingleOrder(player, order);
                itemsReturned += reclaimed;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to reclaim auction order " + order.id() + ": " + e.getMessage(), e);
            }
        }
        return itemsReturned;
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

                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "✓ Reclaimed " + returned + "× " + formatMaterialName(order.material())
                                + " from expired sell order.",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));

                // Update DB on main thread too — we already have the latch pattern here
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
     * Expire a single order: update status, refund/return as appropriate.
     */
    private void expireOrder(AuctionOrder order) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

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

                    String refundMsg = "⚠ Your buy order for " + order.remainingQuantity()
                            + "× " + formatMaterialName(order.material())
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
                        player.getInventory().addItem(items);
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "⚠️ Your sell order for " + order.remainingQuantity() + "× "
                                        + formatMaterialName(order.material())
                                        + " expired. Items returned to your inventory.",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }
                    // Note: if the player is offline, items are NOT returned automatically.
                    // They remain in the database as EXPIRED and must be manually reclaimed.
                    // Consider: add a /auction reclaim command for offline players.
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
        auctionRepo.update(order.withStatusExpired());
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
}
