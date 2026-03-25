package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.ItemRepository;
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
public final class ShopManager {

    private final DatabaseManager databaseManager;
    private final ItemRepository itemRepository;
    private final MarketEngine marketEngine;
    private final ConfigManager configManager;

    private final ConcurrentMap<String, ShopItem> hashToItemCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ShopItem> idToItemCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Boolean> buyableCache = new ConcurrentHashMap<>();

    @Inject
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

}
