package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AutosellRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.service.BadgeService;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@SuppressWarnings("PMD")
public class AutosellManager {

    private static final Logger LOGGER = Logger.getLogger(AutosellManager.class.getName());

    // Cache: player UUID → (itemId → min price, null means no per-item override / use global)
    // Loaded eagerly on player login; kept in sync with DB writes.
    private final Map<UUID, Map<Integer, BigDecimal>> playerMinPrices = new ConcurrentHashMap<>();

    private final Map<UUID, Set<Integer>> playerEnabledItems = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;
    private final AutosellRepository autosellRepository;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;
    private final BadgeService badgeService;

    @Inject
    public AutosellManager(
            DatabaseManager databaseManager,
            AutosellRepository autosellRepository,
            ShopManager shopManager,
            EconomyManager economyManager,
            ConfigManager configManager,
            BadgeService badgeService
    ) {
        this.databaseManager = databaseManager;
        this.autosellRepository = autosellRepository;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.configManager = configManager;
        this.badgeService = badgeService;
    }

    public void loadPlayer(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        // Load both enabled items and min prices in a single DB round-trip
        databaseManager.supplyAsync(() -> {
            Set<Integer> enabled = new HashSet<>(autosellRepository.getEnabledItems(playerId));
            Map<Integer, BigDecimal> minPrices = autosellRepository.getAllMinPrices(playerId);
            return new AutosellLoadResult(enabled, minPrices);
        }).thenAccept(result -> {
            if (result != null) {
                databaseManager.runOnMain(() -> {
                    playerEnabledItems.put(playerId, result.enabled());
                    playerMinPrices.put(playerId, result.minPrices());
                });
            }
        }).exceptionally(ex -> {
            LOGGER.log(Level.WARNING, "Failed to load autosell data for player", ex);
            return null;
        });
    }

    private record AutosellLoadResult(Set<Integer> enabled, Map<Integer, BigDecimal> minPrices) {}

    public void unloadPlayer(@NotNull UUID uuid) {
        playerEnabledItems.remove(uuid);
    }

    /**
     * Returns the effective minimum price for an item.
     * Uses the in-memory cache (loaded on login), falls back to global config.
     * No blocking DB calls — safe for hot paths like inventory scans.
     */
    public double getEffectiveMinPrice(@NotNull UUID uuid, int itemId) {
        Map<Integer, BigDecimal> prices = playerMinPrices.get(uuid);
        if (prices != null) {
            BigDecimal perItem = prices.get(itemId);
            if (perItem != null && perItem.doubleValue() > 0.0) {
                return perItem.doubleValue();
            }
        }
        return configManager.getConfig().autosell().minimumPrice();
    }

    public boolean isItemEnabled(@NotNull UUID uuid, int itemId) {
        Set<Integer> enabled = playerEnabledItems.get(uuid);
        return enabled != null && enabled.contains(itemId);
    }

    public boolean hasAnyItemEnabled(@NotNull UUID uuid) {
        Set<Integer> enabled = playerEnabledItems.get(uuid);
        return enabled != null && !enabled.isEmpty();
    }

    public Set<Integer> getEnabledItems(@NotNull UUID uuid) {
        return new HashSet<>(playerEnabledItems.getOrDefault(uuid, Set.of()));
    }

    public void toggleItem(@NotNull Player player, int itemId) {
        UUID uuid = player.getUniqueId();
        Set<Integer> enabled = playerEnabledItems.computeIfAbsent(uuid, k -> new HashSet<>());

        boolean newState;
        if (enabled.contains(itemId)) {
            enabled.remove(itemId);
            newState = false;
        } else {
            enabled.add(itemId);
            newState = true;
        }

        boolean finalNewState = newState;
        var unused = databaseManager.runAsync(() -> autosellRepository.setItemEnabled(uuid, itemId, finalNewState));

        // Check for HOARDER badge after enabling an item
        if (newState) {
            int count = playerEnabledItems.getOrDefault(uuid, Set.of()).size();
            badgeService.onAutosellInventoryUpdated(uuid, count);
        }

        Optional<ShopItem> item = shopManager.getItemById(itemId);
        String itemName = item.map(ShopItem::getDisplayNameOrMaterial).orElse("Unknown Item");
        player.sendMessage(configManager.getMessage(
                newState ? "autosell.item-enabled" : "autosell.item-disabled",
                Map.of("item", itemName)
        ));
    }

    public void enableAllItems(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Set<Integer> allItemIds = shopManager.getAllItemIds();

        playerEnabledItems.put(uuid, new HashSet<>(allItemIds));

        var unused = databaseManager.runAsync(() -> autosellRepository.enableAllItems(uuid, allItemIds));

        // Check for HOARDER badge after enabling all items
        badgeService.onAutosellInventoryUpdated(uuid, allItemIds.size());

        player.sendMessage(configManager.getMessage("autosell.all-enabled"));
    }

    public void disableAllItems(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        playerEnabledItems.put(uuid, new HashSet<>());

        var unused = databaseManager.runAsync(() -> autosellRepository.disableAllItems(uuid));
        player.sendMessage(configManager.getMessage("autosell.all-disabled"));
    }

    public Optional<ShopItem> getSellableItem(@NotNull ItemStack itemStack) {
        return shopManager.getItemByStack(itemStack);
    }

    public EconomyManager.TransactionResult sellPickup(
            @NotNull Player player,
            @NotNull ShopItem item,
            @NotNull ItemStack itemStack,
            int amount
    ) {
        return economyManager.processSellImmediate(player, item, amount, itemStack);
    }

    public int sellInventory(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Set<Integer> enabledItems = playerEnabledItems.get(uuid);

        if (enabledItems == null || enabledItems.isEmpty()) {
            return 0;
        }

        int totalSold = 0;
        BigDecimal totalEarned = BigDecimal.ZERO;

        // Iterate the player's ACTUAL storage contents directly.
        // processSellImmediate removes items from the player's inventory on success,
        // so we naturally skip re-selling them on subsequent loop iterations.
        // No stale snapshot needed — we read real-time from the player's inventory.
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            // Re-read from actual inventory after potential prior modification
            stack = player.getInventory().getStorageContents()[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.matchSellItem(stack);
            if (shopItemOpt.isEmpty()) {
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            if (!enabledItems.contains(shopItem.id())) {
                continue;
            }

            // Skip items below minimum price threshold (per-item override or global)
            double effectiveMinPrice = getEffectiveMinPrice(uuid, shopItem.id());
            if (effectiveMinPrice > 0.0) {
                BigDecimal sellPrice = shopManager.getSellPrice(shopItem);
                if (sellPrice.doubleValue() < effectiveMinPrice) {
                    continue;
                }
            }

            int amount = stack.getAmount();
            // processSellImmediate handles item removal + money deposit + DB write.
            // Items are removed FIRST; if DB write fails, items are restored in-place.
            // If it fails, we stop the loop so the player has a consistent inventory.
            EconomyManager.TransactionResult result = economyManager.processSellImmediate(
                    player, shopItem, amount, stack);

            if (result != null && result.success()) {
                totalSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
                // processSellImmediate already removed items from player's actual inventory.
                // Re-read the slot on next iteration — it will be null/empty if sold successfully.
            } else if (result != null && !result.success()) {
                // DB failed and processSellImmediate restored the items in-place.
                // Stop the loop — don't leave player with partially-completed autosell.
                LOGGER.log(Level.WARNING, "Autosell stopped for " + player.getName()
                        + " after DB failure (sold " + totalSold + " items so far).", new Exception("DB failure"));
                break;
            }
        }

        // NOTE: We do NOT call setStorageContents(contents) here.
        // processSellImmediate modifies the player's actual inventory directly.
        // Calling setStorageContents with our stale snapshot would overwrite any items
        // the player received (e.g., from loot, other players) between the snapshot
        // and the setStorageContents call — silently losing items.

        if (totalSold > 0) {
            player.sendMessage(configManager.getMessage("autosell.inventory-sold", Map.of(
                    "amount", String.valueOf(totalSold),
                    "price", configManager.formatCurrency(totalEarned)
            )));
        }

        return totalSold;
    }

    public void sendAutosellActionBar(@NotNull Player player, @NotNull ShopItem item, int amount,
                                      @NotNull BigDecimal totalPrice) {
        player.sendActionBar(configManager.getMessageRaw("autosell.sold", Map.of(
                "amount", String.valueOf(amount),
                "item", item.getDisplayNameOrMaterial(),
                "price", configManager.formatCurrency(totalPrice)
        )));
    }

    public void sendAutosellActionBar(@NotNull Player player, int totalAmount, @NotNull BigDecimal totalPrice) {
        player.sendActionBar(configManager.getMessageRaw("autosell.inventory-sold", Map.of(
                "amount", String.valueOf(totalAmount),
                "price", configManager.formatCurrency(totalPrice)
        )));
    }

    /**
     * Set a per-item minimum price threshold for autosell.
     * Players who don't set a per-item threshold use the global config minimum.
     */
    public void setMinPrice(@NotNull Player player, int itemId, double minPrice) {
        if (minPrice < 0) {
            player.sendMessage(configManager.getMessage("autosell.minprice-invalid"));
            return;
        }
        UUID uuid = player.getUniqueId();
        BigDecimal price = minPrice == 0 ? null : BigDecimal.valueOf(minPrice);
        autosellRepository.setMinPrice(uuid, itemId, price);

        // Keep cache in sync
        Map<Integer, BigDecimal> prices = playerMinPrices.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (price == null) {
            prices.remove(itemId);
        } else {
            prices.put(itemId, price);
        }

        Optional<ShopItem> item = shopManager.getItemById(itemId);
        String itemName = item.map(ShopItem::getDisplayNameOrMaterial).orElse("Unknown Item");

        if (price == null) {
            player.sendMessage(configManager.getMessage("autosell.minprice-reset", Map.of("item", itemName)));
        } else {
            player.sendMessage(configManager.getMessage("autosell.minprice-set", Map.of(
                    "item", itemName,
                    "price", configManager.formatCurrency(price)
            )));
        }
    }

    /**
     * Remove a per-item minimum price threshold (reverts to global config).
     */
    public void removeMinPrice(@NotNull Player player, int itemId) {
        UUID uuid = player.getUniqueId();
        autosellRepository.removeMinPrice(uuid, itemId);

        // Keep cache in sync
        Map<Integer, BigDecimal> prices = playerMinPrices.get(uuid);
        if (prices != null) {
            prices.remove(itemId);
        }

        Optional<ShopItem> item = shopManager.getItemById(itemId);
        String itemName = item.map(ShopItem::getDisplayNameOrMaterial).orElse("Unknown Item");
        player.sendMessage(configManager.getMessage("autosell.minprice-reset", Map.of("item", itemName)));
    }

    /**
     * Get the per-item minimum price for display (empty Optional if using global).
     */
    public Optional<BigDecimal> getMinPrice(@NotNull UUID uuid, int itemId) {
        return autosellRepository.getMinPrice(uuid, itemId);
    }
}
