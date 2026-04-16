package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.model.ItemTier;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("PMD")
public class ShopManager {

    private final DatabaseManager databaseManager;
    private final ItemRepository itemRepository;
    private final MarketEngine marketEngine;
    private final ConfigManager configManager;

    private final ConcurrentMap<String, ShopItem> hashToItemCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ShopItem> idToItemCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Boolean> buyableCache = new ConcurrentHashMap<>();

    @Inject
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public ShopManager(DatabaseManager databaseManager, ItemRepository itemRepository,
                       MarketEngine marketEngine, ConfigManager configManager) {
        this.databaseManager = databaseManager;
        this.itemRepository = itemRepository;
        this.marketEngine = marketEngine;
        this.configManager = configManager;
        loadCache();
    }

    private void loadCache() {
        List<ShopItem> items = itemRepository.findAll();
        if (items.isEmpty()) {
            loadDefaultItems();
            items = itemRepository.findAll();
        }
        for (ShopItem item : items) {
            hashToItemCache.put(item.itemHash(), item);
            idToItemCache.put(item.id(), item);
        }
    }

    private void loadDefaultItems() {
        Logger logger = java.util.logging.Logger.getLogger("Auto-Tune");
        YamlConfiguration shopsConfig = configManager.loadShopsConfig();
        List<?> itemList = shopsConfig.getList("items");
        if (itemList == null) {
            logger.warning("No default items found in shops.yml");
            return;
        }

        int loaded = 0;
        for (Object entry : itemList) {
            if (!(entry instanceof ConfigurationSection) &&
                    !(entry instanceof java.util.Map)) {
                continue;
            }

            String materialName;
            double price;
            String sectionName;

            if (entry instanceof ConfigurationSection section) {
                materialName = section.getString("material");
                price = section.getDouble("price", 10.0);
                sectionName = section.getString("section", "general");
            } else {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) entry;
                materialName = (String) map.get("material");
                Number priceNum = (Number) map.getOrDefault("price", 10.0);
                price = priceNum.doubleValue();
                sectionName = (String) map.getOrDefault("section", "general");
            }

            if (materialName == null) {
                continue;
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                logger.warning("Unknown material in shops.yml: " + materialName);
                continue;
            }

            addItem(material, BigDecimal.valueOf(price), sectionName);
            loaded++;
        }

        logger.info("Loaded " + loaded + " default shop items from shops.yml");
    }

    /**
     * Reload shop items from shops.yml, replacing the current in-memory cache and
     * repopulating the database with the current shops.yml contents.
     *
     * This is called by /at admin reload so admins can edit shops.yml and apply
     * changes without restarting the server.
     *
     * Note: If you have customized item prices via /at admin item commands, those
     * DB overrides will be overwritten by this reload. Re-edit shops.yml and
     * reload again to set new prices.
     */
    public void reload() {
        hashToItemCache.clear();
        idToItemCache.clear();
        buyableCache.clear();
        loadDefaultItems();
        loadCache();
    }

    public void refreshCache() {
        hashToItemCache.clear();
        idToItemCache.clear();
        loadCache();
    }

    public List<ShopItem> getAllItems() {
        return idToItemCache.values().stream()
                .filter(ShopItem::enabled)
                .sorted((a, b) -> {
                    int sectionCompare = a.section().compareToIgnoreCase(b.section());
                    if (sectionCompare != 0) {
                        return sectionCompare;
                    }
                    return a.getDisplayNameOrMaterial().compareToIgnoreCase(b.getDisplayNameOrMaterial());
                })
                .collect(Collectors.toList());
    }

    public Set<Integer> getAllItemIds() {
        return idToItemCache.values().stream()
                .filter(ShopItem::enabled)
                .map(ShopItem::id)
                .collect(Collectors.toSet());
    }

    public List<ShopItem> getItemsBySection(String section) {
        return idToItemCache.values().stream()
                .filter(item -> item.enabled() && item.section().equalsIgnoreCase(section))
                .sorted((a, b) -> a.getDisplayNameOrMaterial().compareToIgnoreCase(b.getDisplayNameOrMaterial()))
                .collect(Collectors.toList());
    }

    public Optional<ShopItem> getItemById(int id) {
        ShopItem cached = idToItemCache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        return itemRepository.findById(id);
    }

    public Optional<ShopItem> getItemByStack(@NotNull ItemStack itemStack) {
        String hash = ItemSerializer.getItemHash(itemStack);
        ShopItem cached = hashToItemCache.get(hash);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    /**
     * Finds the best shop item match for a player's item stack.
     * Tries exact hash match first (enchanted items with dedicated shop entries).
     * Falls back to base material match so enchanted items can still be sold
     * at the base material price (enchantment premium applied separately).
     *
     * @return the matched ShopItem, or empty if the base material isn't in the shop
     */
    public Optional<ShopItem> matchSellItem(@NotNull ItemStack itemStack) {
        // Try exact match first (item has dedicated shop entry)
        Optional<ShopItem> exact = getItemByStack(itemStack);
        if (exact.isPresent()) {
            return exact;
        }

        // Fall back to base material (handles enchanted items not in shop)
        String materialHash = ItemSerializer.getMaterialHash(itemStack.getType());
        ShopItem materialMatch = hashToItemCache.get(materialHash);
        if (materialMatch != null) {
            return Optional.of(materialMatch);
        }

        // Try DB as last resort
        return itemRepository.findByHash(materialHash);
    }

    public Optional<ShopItem> getItemByMaterial(@NotNull Material material) {
        String hash = ItemSerializer.getMaterialHash(material);
        ShopItem cached = hashToItemCache.get(hash);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public ShopItem addItem(Material material, BigDecimal price, String section) {
        String hash = ItemSerializer.getMaterialHash(material);

        Optional<ShopItem> existing = itemRepository.findByHash(hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        ShopItem newItem = ShopItem.builder()
                .material(material)
                .itemHash(hash)
                .displayName(null)
                .price(price)
                .section(section)
                .enabled(true)
                .build();

        int id = itemRepository.insert(newItem);
        ShopItem withId = newItem.toBuilder().id(id).build();

        itemRepository.recordPriceHistory(id, price, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        hashToItemCache.put(hash, withId);
        idToItemCache.put(id, withId);

        return withId;
    }

    public ShopItem addCustomItem(ItemStack itemStack, BigDecimal price, String section, String displayName) {
        String hash = ItemSerializer.getItemHash(itemStack);

        Optional<ShopItem> existing = itemRepository.findByHash(hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        ShopItem newItem = ShopItem.builder()
                .material(itemStack.getType())
                .itemHash(hash)
                .displayName(displayName)
                .price(price)
                .section(section)
                .enabled(true)
                .build();

        int id = itemRepository.insert(newItem);
        ShopItem withId = newItem.toBuilder().id(id).build();

        itemRepository.recordPriceHistory(id, price, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        hashToItemCache.put(hash, withId);
        idToItemCache.put(id, withId);

        return withId;
    }

    public ShopItem addCustomItemWithData(ItemStack itemStack, BigDecimal price, String section) {
        String hash = ItemSerializer.getItemHash(itemStack);
        String itemData = ItemSerializer.serializeItemStack(itemStack);
        String displayName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(itemStack.getItemMeta().displayName())
                : null;

        Optional<ShopItem> existing = itemRepository.findByHash(hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        ShopItem newItem = ShopItem.builder()
                .material(itemStack.getType())
                .itemHash(hash)
                .displayName(displayName)
                .price(price)
                .section(section)
                .enabled(true)
                .itemData(itemData)
                .build();

        int id = itemRepository.insert(newItem);
        ShopItem withId = newItem.toBuilder().id(id).build();

        itemRepository.recordPriceHistory(id, price, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        hashToItemCache.put(hash, withId);
        idToItemCache.put(id, withId);

        return withId;
    }

    public boolean isBuyable(@NotNull ShopItem item) {
        if (item.buyable() != null) {
            return item.buyable();
        }

        if (!configManager.getConfig().economy().requireFirstSell()) {
            return true;
        }

        return buyableCache.computeIfAbsent(item.id(), id -> itemRepository.hasAnySellTransaction(id));
    }

    public void invalidateBuyableCache(int itemId) {
        buyableCache.remove(itemId);
    }

    public void setBuyable(int itemId, @Nullable Boolean buyable) {
        itemRepository.updateBuyable(itemId, buyable);
        buyableCache.remove(itemId);

        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().buyable(buyable).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    public void removeItem(int itemId) {
        getItemById(itemId).ifPresent(item -> {
            hashToItemCache.remove(item.itemHash());
            idToItemCache.remove(item.id());
            itemRepository.delete(itemId);
        });
    }

    public void setPrice(int itemId, BigDecimal price) {
        itemRepository.findById(itemId).ifPresent(item -> {
            itemRepository.updatePrice(itemId, price);
            ShopItem updated = item.toBuilder().price(price).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    public List<ShopItem> search(String query) {
        return itemRepository.search(query);
    }

    public CompletableFuture<List<ShopItem>> searchAsync(String query) {
        return databaseManager.supplyAsync(() -> itemRepository.search(query));
    }

    public BigDecimal getBuyPrice(ShopItem item) {
        return marketEngine.getBuyPrice(item);
    }

    public BigDecimal getBuyPrice(ShopItem item, int amount) {
        return marketEngine.getBuyPrice(item, amount);
    }

    public BigDecimal getSellPrice(ShopItem item) {
        return marketEngine.getSellPrice(item);
    }

    public BigDecimal getSellPrice(ShopItem item, int amount) {
        return marketEngine.getSellPrice(item, amount);
    }

    public MarketEngine.SpreadResult getSpread(ShopItem item) {
        return marketEngine.getSpread(item.id());
    }

    /**
     * Set (or clear) a per-item base spread override.
     * Pass null to remove the override and fall back to the global config value.
     */
    public void setBaseSpreadOverride(int itemId, @Nullable Double override) {
        itemRepository.updateBaseSpreadOverride(itemId, override);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().baseSpreadOverride(override).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Set (or clear) a per-item max price change override.
     * Pass null to remove the override and fall back to the global config value.
     */
    public void setMaxPriceChangeOverride(int itemId, @Nullable Double override) {
        itemRepository.updateMaxPriceChangeOverride(itemId, override);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().maxPriceChangeOverride(override).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Set (or clear) a per-item price floor — buy/sell prices will never go below this.
     * Pass null to remove the floor and allow free market pricing.
     */
    public void setPriceFloorOverride(int itemId, @Nullable BigDecimal floor) {
        itemRepository.updatePriceFloor(itemId, floor);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().priceFloorOverride(floor).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Set (or clear) a per-item price ceiling — buy/sell prices will never exceed this.
     * Pass null to remove the ceiling and allow free market pricing.
     */
    public void setPriceCeilingOverride(int itemId, @Nullable BigDecimal ceiling) {
        itemRepository.updatePriceCeiling(itemId, ceiling);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().priceCeilingOverride(ceiling).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Freeze or unfreeze price updates for a single item.
     * When frozen, the item's price stops updating but spreads continue to compute normally.
     * Use during server events to prevent exploitation on specific high-value items.
     */
    public void setPriceFrozen(int itemId, boolean frozen) {
        itemRepository.updatePriceFrozen(itemId, frozen);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().priceFrozen(frozen).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Sets the rarity tier for an item, overriding the default classification.
     * Pass null to clear the override and revert to the default tier classification.
     */
    public void setTier(int itemId, @Nullable ItemTier tier) {
        itemRepository.updateTier(itemId, tier);
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().tier(tier).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
    }

    /**
     * Look up the base (shops.yml) price for a material.
     * Returns null if the material is not found in shops.yml.
     * This is the canonical "starting price" before any market drift.
     */
    @Nullable
    public BigDecimal getBasePriceFromShopsYaml(Material material) {
        YamlConfiguration shopsConfig = configManager.loadShopsConfig();
        List<?> itemList = shopsConfig.getList("items");
        if (itemList == null) return null;

        String targetName = material.name().toLowerCase(java.util.Locale.ROOT);
        for (Object entry : itemList) {
            String materialName = null;
            double price = -1;

            if (entry instanceof ConfigurationSection section) {
                materialName = section.getString("material");
                price = section.getDouble("price", -1);
            } else if (entry instanceof java.util.Map<?, ?> map) {
                materialName = (String) map.get("material");
                Number priceNum = (Number) map.get("price");
                if (priceNum != null) price = priceNum.doubleValue();
            }

            if (materialName != null && materialName.toLowerCase(java.util.Locale.ROOT).equals(targetName) && price >= 0) {
                return BigDecimal.valueOf(price);
            }
        }
        return null;
    }

    /**
     * Reset an item's current price to its shops.yml base price and clear its
     * price history. Useful when an item has drifted due to exploits or bugs.
     *
     * This does NOT clear admin overrides (floor/ceiling/spread etc.) — use
     * itemReset for that. This ONLY resets the floating market price.
     *
     * Returns the base price the item was reset to, or empty if base price not found.
     */
    public java.util.Optional<BigDecimal> resetPriceToBase(int itemId, Material material) {
        BigDecimal basePrice = getBasePriceFromShopsYaml(material);
        if (basePrice == null) return java.util.Optional.empty();

        // Reset the DB price
        itemRepository.updatePrice(itemId, basePrice);
        // Clear all market history so stale data doesn't bias new price discovery
        itemRepository.deleteMarketHistoryForItem(itemId);
        // Evict from MarketEngine's live price cache too
        marketEngine.resetPriceCache(itemId, basePrice);
        // Refresh the in-memory shop cache
        getItemById(itemId).ifPresent(item -> {
            ShopItem updated = item.toBuilder().price(basePrice).build();
            hashToItemCache.put(updated.itemHash(), updated);
            idToItemCache.put(updated.id(), updated);
        });
        return java.util.Optional.of(basePrice);
    }
}
