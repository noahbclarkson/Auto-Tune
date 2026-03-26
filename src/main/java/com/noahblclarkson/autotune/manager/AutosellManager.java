package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AutosellRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
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
public class AutosellManager {

    private static final Logger LOGGER = Logger.getLogger(AutosellManager.class.getName());

    private final Map<UUID, Set<Integer>> playerEnabledItems = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;
    private final AutosellRepository autosellRepository;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;

    @Inject
    public AutosellManager(
            DatabaseManager databaseManager,
            AutosellRepository autosellRepository,
            ShopManager shopManager,
            EconomyManager economyManager,
            ConfigManager configManager
    ) {
        this.databaseManager = databaseManager;
        this.autosellRepository = autosellRepository;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
        this.configManager = configManager;
    }

    public void loadPlayer(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        databaseManager.supplyAsync(() -> autosellRepository.getEnabledItems(playerId))
                .thenAccept(enabledItems -> {
                    if (enabledItems != null) {
                        databaseManager.runOnMain(() -> playerEnabledItems.put(playerId, new HashSet<>(enabledItems)));
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Failed to load autosell items for player", ex);
                    return null;
                });
    }

    public void unloadPlayer(@NotNull UUID uuid) {
        playerEnabledItems.remove(uuid);
    }

    /**
     * Returns the effective minimum price for an item.
     * Returns the player's per-item override if set, otherwise the global config minimum.
     */
    public double getEffectiveMinPrice(@NotNull UUID uuid, int itemId) {
        Optional<BigDecimal> perItem = databaseManager.supplyAsync(
                () -> autosellRepository.getMinPrice(uuid, itemId)).join();
        if (perItem.isPresent() && perItem.get().doubleValue() > 0.0) {
            return perItem.get().doubleValue();
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

        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
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
            // processSellImmediate handles item removal + money deposit + DB write
            // atomically. It removes items first, deposits money, then writes to DB.
            // If DB write fails, it restores items and returns economyError.
            // In that case we stop the loop — partial completion would confuse players.
            EconomyManager.TransactionResult result = economyManager.processSellImmediate(
                    player, shopItem, amount, stack);

            if (result != null && result.success()) {
                contents[slot] = null;  // item was removed by processSellImmediate
                totalSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
            } else if (result != null && !result.success()) {
                // DB failed AND items were restored by processSellImmediate.
                // Stop the loop — don't leave player with partial sold inventory.
                LOGGER.log(Level.WARNING, "Autosell stopped for " + player.getName()
                        + " after DB failure (sold " + totalSold + " items so far).", new Exception("DB failure"));
                break;
            }
        }

        // Apply slot clears to actual inventory
        player.getInventory().setStorageContents(contents);

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
