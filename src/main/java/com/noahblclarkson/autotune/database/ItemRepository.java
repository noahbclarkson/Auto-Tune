package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.ItemRatio;
import com.noahblclarkson.autotune.model.ItemTier;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import org.bukkit.Material;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("PMD")
public class ItemRepository {

    private final Jdbi jdbi;

    public ItemRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    private static ShopItem mapItem(ResultSet rs) throws SQLException {
        String buyableStr = rs.getString("buyable");
        Boolean buyable = rs.wasNull() ? null : "1".equals(buyableStr) || "true".equalsIgnoreCase(buyableStr);

        double maxPriceChangeRaw = rs.getDouble("max_price_change_override");
        Double maxPriceChangeOverride = rs.wasNull() ? null : maxPriceChangeRaw;

        double baseSpreadRaw = rs.getDouble("base_spread_override");
        Double baseSpreadOverride = rs.wasNull() ? null : baseSpreadRaw;

        BigDecimal priceFloorOverride = rs.getBigDecimal("price_floor");
        if (rs.wasNull()) priceFloorOverride = null;

        BigDecimal priceCeilingOverride = rs.getBigDecimal("price_ceiling");
        if (rs.wasNull()) priceCeilingOverride = null;

        boolean priceFrozen = rs.getBoolean("price_frozen");

        String tierStr = rs.getString("tier");
        ItemTier tier = rs.wasNull() ? null : ItemTier.valueOf(tierStr);

        return ShopItem.builder()
                .id(rs.getInt("id"))
                .material(Material.valueOf(rs.getString("material")))
                .itemHash(rs.getString("item_hash"))
                .displayName(rs.getString("display_name"))
                .price(rs.getBigDecimal("price"))
                .section(rs.getString("section"))
                .enabled(rs.getBoolean("enabled"))
                .buyable(buyable)
                .itemData(rs.getString("item_data"))
                .maxPriceChangeOverride(maxPriceChangeOverride)
                .baseSpreadOverride(baseSpreadOverride)
                .priceFloorOverride(priceFloorOverride)
                .priceCeilingOverride(priceCeilingOverride)
                .priceFrozen(priceFrozen)
                .tier(tier)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    private static final String SELECT_COLUMNS =
            "id, material, item_hash, display_name, price, section, enabled, buyable, item_data, max_price_change_override, base_spread_override, price_floor, price_ceiling, price_frozen, tier, created_at, updated_at";

    public List<ShopItem> findAll() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT " + SELECT_COLUMNS + " FROM at_items WHERE enabled = TRUE ORDER BY section, display_name")
                        .map((rs, ctx) -> mapItem(rs))
                        .list());
    }

    public List<ShopItem> findBySection(String section) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT " + SELECT_COLUMNS + " FROM at_items WHERE section = :section AND enabled = TRUE ORDER BY display_name")
                        .bind("section", section)
                        .map((rs, ctx) -> mapItem(rs))
                        .list());
    }

    public Optional<ShopItem> findById(int id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT " + SELECT_COLUMNS + " FROM at_items WHERE id = :id")
                        .bind("id", id)
                        .map((rs, ctx) -> mapItem(rs))
                        .findFirst());
    }

    public Optional<ShopItem> findByHash(String itemHash) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT " + SELECT_COLUMNS + " FROM at_items WHERE item_hash = :hash")
                        .bind("hash", itemHash)
                        .map((rs, ctx) -> mapItem(rs))
                        .findFirst());
    }

    public int insert(@NotNull ShopItem item) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_items (material, item_hash, display_name, price, section, enabled, buyable, item_data, tier)
                                VALUES (:material, :itemHash, :displayName, :price, :section, :enabled, :buyable, :itemData, :tier)
                                """)
                        .bind("material", item.material().name())
                        .bind("itemHash", item.itemHash())
                        .bind("displayName", item.displayName())
                        .bind("price", item.price())
                        .bind("section", item.section())
                        .bind("enabled", item.enabled())
                        .bind("buyable", item.buyable())
                        .bind("itemData", item.itemData())
                        .bind("tier", item.tier() != null ? item.tier().name() : null)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Integer.class)
                        .one());
    }

    public void updatePrice(int itemId, BigDecimal newPrice) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET price = :price, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("price", newPrice)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updateBaseSpreadOverride(int itemId, @Nullable Double baseSpreadOverride) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET base_spread_override = :override, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("override", baseSpreadOverride)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updateMaxPriceChangeOverride(int itemId, @Nullable Double maxPriceChangeOverride) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET max_price_change_override = :override, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("override", maxPriceChangeOverride)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updatePriceFloor(int itemId, @Nullable BigDecimal priceFloor) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET price_floor = :floor, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("floor", priceFloor)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updatePriceCeiling(int itemId, @Nullable BigDecimal priceCeiling) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET price_ceiling = :ceiling, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("ceiling", priceCeiling)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updatePriceFrozen(int itemId, boolean frozen) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET price_frozen = :frozen, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("frozen", frozen)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updateTier(int itemId, @Nullable ItemTier tier) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_items SET tier = :tier, updated_at = :updatedAt
                                WHERE id = :id
                                """)
                        .bind("id", itemId)
                        .bind("tier", tier != null ? tier.name() : null)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updateBuyable(int itemId, @Nullable Boolean buyable) {
        jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE at_items SET buyable = :buyable, updated_at = :updatedAt WHERE id = :id")
                        .bind("id", itemId)
                        .bind("buyable", buyable)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void updateItemData(int itemId, @Nullable String itemData) {
        jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE at_items SET item_data = :itemData, updated_at = :updatedAt WHERE id = :id")
                        .bind("id", itemId)
                        .bind("itemData", itemData)
                        .bind("updatedAt", Timestamp.from(Instant.now()))
                        .execute());
    }

    public boolean hasAnySellTransaction(int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_transactions WHERE item_id = :itemId AND transaction_type = 'SELL'")
                        .bind("itemId", itemId)
                        .mapTo(Integer.class)
                        .one() > 0);
    }

    public void recordPriceHistory(int itemId, BigDecimal price, int buyVolume, int sellVolume,
                                   BigDecimal bpd, BigDecimal spd) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_market_history (item_id, price, buy_volume, sell_volume, bpd, spd)
                                VALUES (:itemId, :price, :buyVolume, :sellVolume, :bpd, :spd)
                                """)
                        .bind("itemId", itemId)
                        .bind("price", price)
                        .bind("buyVolume", buyVolume)
                        .bind("sellVolume", sellVolume)
                        .bind("bpd", bpd)
                        .bind("spd", spd)
                        .execute());
    }

    public List<PriceHistory> getPriceHistory(int itemId, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, item_id, price, buy_volume, sell_volume, bpd, spd, timestamp
                                FROM at_market_history
                                WHERE item_id = :itemId
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("itemId", itemId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> PriceHistory.builder()
                                .id(rs.getLong("id"))
                                .itemId(rs.getInt("item_id"))
                                .price(rs.getBigDecimal("price"))
                                .buyVolume(rs.getInt("buy_volume"))
                                .sellVolume(rs.getInt("sell_volume"))
                                .bpd(rs.getBigDecimal("bpd"))
                                .spd(rs.getBigDecimal("spd"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    /**
     * Returns price history for an item within the given time window.
     * Results are ordered newest-first and limited to the most recent N entries.
     */
    public List<PriceHistory> getPriceHistorySince(int itemId, Instant since, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, item_id, price, buy_volume, sell_volume, bpd, spd, timestamp
                                FROM at_market_history
                                WHERE item_id = :itemId AND timestamp >= :since
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("itemId", itemId)
                        .bind("since", Timestamp.from(since))
                        .bind("limit", limit)
                        .map((rs, ctx) -> PriceHistory.builder()
                                .id(rs.getLong("id"))
                                .itemId(rs.getInt("item_id"))
                                .price(rs.getBigDecimal("price"))
                                .buyVolume(rs.getInt("buy_volume"))
                                .sellVolume(rs.getInt("sell_volume"))
                                .bpd(rs.getBigDecimal("bpd"))
                                .spd(rs.getBigDecimal("spd"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    public Optional<PriceHistory> getClosestPriceBefore(int itemId, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, item_id, price, buy_volume, sell_volume, bpd, spd, timestamp
                                FROM at_market_history
                                WHERE item_id = :itemId AND timestamp >= :since
                                ORDER BY timestamp ASC
                                LIMIT 1
                                """)
                        .bind("itemId", itemId)
                        .bind("since", Timestamp.from(since))
                        .map((rs, ctx) -> PriceHistory.builder()
                                .id(rs.getLong("id"))
                                .itemId(rs.getInt("item_id"))
                                .price(rs.getBigDecimal("price"))
                                .buyVolume(rs.getInt("buy_volume"))
                                .sellVolume(rs.getInt("sell_volume"))
                                .bpd(rs.getBigDecimal("bpd"))
                                .spd(rs.getBigDecimal("spd"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .findFirst());
    }

    public List<ItemRatio> getAllRatios() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT item_a, item_b, ratio, updated_at FROM at_item_ratios")
                        .map((rs, ctx) -> ItemRatio.builder()
                                .itemA(rs.getInt("item_a"))
                                .itemB(rs.getInt("item_b"))
                                .ratio(rs.getBigDecimal("ratio"))
                                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                                .build())
                        .list());
    }

    public Optional<ItemRatio> findRatio(int itemA, int itemB) {
        int a = Math.min(itemA, itemB);
        int b = Math.max(itemA, itemB);
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT item_a, item_b, ratio, updated_at FROM at_item_ratios WHERE item_a = :itemA AND item_b = :itemB")
                        .bind("itemA", a)
                        .bind("itemB", b)
                        .map((rs, ctx) -> ItemRatio.builder()
                                .itemA(rs.getInt("item_a"))
                                .itemB(rs.getInt("item_b"))
                                .ratio(rs.getBigDecimal("ratio"))
                                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                                .build())
                        .findFirst());
    }

    public void setRatio(int itemA, int itemB, BigDecimal ratio) {
        int a = Math.min(itemA, itemB);
        int b = Math.max(itemA, itemB);
        Timestamp now = Timestamp.from(Instant.now());

        jdbi.useHandle(handle -> {
            int updated = handle.createUpdate("""
                            UPDATE at_item_ratios
                            SET ratio = :ratio, updated_at = :updatedAt
                            WHERE item_a = :itemA AND item_b = :itemB
                            """)
                    .bind("itemA", a)
                    .bind("itemB", b)
                    .bind("ratio", ratio)
                    .bind("updatedAt", now)
                    .execute();

            if (updated == 0) {
                handle.createUpdate("""
                                INSERT INTO at_item_ratios (item_a, item_b, ratio, updated_at)
                                VALUES (:itemA, :itemB, :ratio, :updatedAt)
                                """)
                        .bind("itemA", a)
                        .bind("itemB", b)
                        .bind("ratio", ratio)
                        .bind("updatedAt", now)
                        .execute();
            }
        });
    }

    public void deleteAllRatios() {
        jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM at_item_ratios")
                        .execute());
    }

    public List<ShopItem> search(String query) {
        String searchPattern = "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT %s
                                FROM at_items
                                WHERE enabled = TRUE AND (
                                    LOWER(display_name) LIKE :pattern OR
                                    LOWER(material) LIKE :pattern
                                )
                                ORDER BY display_name
                                LIMIT 50
                                """.formatted(SELECT_COLUMNS))
                        .bind("pattern", searchPattern)
                        .map((rs, ctx) -> mapItem(rs))
                        .list());
    }

    public void delete(int id) {
        jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM at_items WHERE id = :id")
                        .bind("id", id)
                        .execute());
    }

    /**
     * Delete market history rows older than the given cutoff.
     * Returns the number of rows deleted.
     */
    public int deleteMarketHistoryOlderThan(Instant cutoff) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("DELETE FROM at_market_history WHERE timestamp < :cutoff")
                        .bind("cutoff", Timestamp.from(cutoff))
                        .execute());
    }

    public long countMarketHistory() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_market_history")
                        .mapTo(Long.class)
                        .one());
    }

    /**
     * Delete all market history for a specific item.
     * Used by /at admin prices reset to clear stale history after a price reset.
     */
    public int deleteMarketHistoryForItem(int itemId) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("DELETE FROM at_market_history WHERE item_id = :itemId")
                        .bind("itemId", itemId)
                        .execute());
    }

    /**
     * Look up the base price of an item from shops.yml by material name.
     * Returns the stored initial price (the value from shops.yml at first load),
     * which is what the item's price column was set to when first inserted.
     * We surface this as a separate query so callers can compare base vs drift.
     */
    public BigDecimal getInitialPrice(int itemId) {
        // The initial price isn't explicitly stored — we return the current DB price
        // pre-drift by querying the first market_history record if available,
        // or falling back to the item's current price field.
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT price FROM at_market_history
                                WHERE item_id = :itemId
                                ORDER BY timestamp ASC
                                LIMIT 1
                                """)
                        .bind("itemId", itemId)
                        .mapTo(BigDecimal.class)
                        .findFirst()
                        .orElseGet(() ->
                                handle.createQuery("SELECT price FROM at_items WHERE id = :id")
                                        .bind("id", itemId)
                                        .mapTo(BigDecimal.class)
                                        .one()));
    }
}
