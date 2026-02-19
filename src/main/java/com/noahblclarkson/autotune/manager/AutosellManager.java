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
        databaseManager.runAsync(() -> autosellRepository.setItemEnabled(uuid, itemId, finalNewState));

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

        databaseManager.runAsync(() -> autosellRepository.enableAllItems(uuid, allItemIds));
        player.sendMessage(configManager.getMessage("autosell.all-enabled"));
    }

    public void disableAllItems(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        playerEnabledItems.put(uuid, new HashSet<>());

        databaseManager.runAsync(() -> autosellRepository.disableAllItems(uuid));
        player.sendMessage(configManager.getMessage("autosell.all-disabled"));
    }

    public Optional<ShopItem> getSellableItem(@NotNull ItemStack itemStack) {
        return shopManager.getItemByStack(itemStack);
    }

    public EconomyManager.TransactionResult sellPickup(
            @NotNull Player player,
            @NotNull ShopItem item,
            int amount
    ) {
        return economyManager.processSellImmediate(player, item, amount);
    }

    public int sellInventory(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Set<Integer> enabledItems = playerEnabledItems.get(uuid);

        if (enabledItems == null || enabledItems.isEmpty()) {
            return 0;
        }

        int totalSold = 0;
        BigDecimal totalEarned = BigDecimal.ZERO;

        // Use getStorageContents() (slots 0-35) to avoid accidentally iterating
        // over armor slots (36-39) and the off-hand slot (40) which getSize()
        // would include for a PlayerInventory.
        ItemStack[] storageContents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            ItemStack stack = storageContents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<ShopItem> shopItemOpt = shopManager.getItemByStack(stack);
            if (shopItemOpt.isEmpty()) {
                continue;
            }

            ShopItem shopItem = shopItemOpt.get();
            if (!enabledItems.contains(shopItem.id())) {
                continue;
            }

            int amount = stack.getAmount();
            EconomyManager.TransactionResult result = economyManager.processSellImmediate(player, shopItem, amount);

            if (result != null && result.success()) {
                player.getInventory().setItem(slot, null);
                totalSold += result.amount();
                totalEarned = totalEarned.add(result.totalPrice());
            }
        }

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
}
