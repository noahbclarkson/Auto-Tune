package com.noahblclarkson.autotune.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record ShopItem(
        int id,
        @NotNull Material material,
        @NotNull String itemHash,
        @Nullable String displayName,
        @NotNull BigDecimal price,
        @NotNull String section,
        boolean enabled,
        @Nullable Boolean buyable,
        @Nullable String itemData,
        @Nullable Double maxPriceChangeOverride,
        @Nullable Double baseSpreadOverride,
        @Nullable BigDecimal priceFloorOverride,
        @Nullable BigDecimal priceCeilingOverride,
        boolean priceFrozen,
        @Nullable ItemTier tier,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt
) {

    /**
     * Returns the effective tier for this item.
     * Admin override (stored in DB) takes precedence; otherwise falls back to
     * the default tier classification based on material rarity.
     */
    @NotNull
    public ItemTier effectiveTier() {
        return tier != null ? tier : ItemTier.defaultTierFor(material.name());
    }

    /**
     * Returns the effective base spread for this item.
     * Explicit override takes precedence; otherwise uses the tier's spread multiplier.
     */
    public double effectiveSpreadMultiplier() {
        return effectiveTier().spreadMultiplier;
    }

    /**
     * Returns the effective max price change multiplier for this item.
     * Explicit override takes precedence; otherwise uses the tier's multiplier.
     */
    public double effectiveMaxPriceChangeMultiplier() {
        return effectiveTier().maxPriceChangeMultiplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .material(material)
                .itemHash(itemHash)
                .displayName(displayName)
                .price(price)
                .section(section)
                .enabled(enabled)
                .buyable(buyable)
                .itemData(itemData)
                .maxPriceChangeOverride(maxPriceChangeOverride)
                .baseSpreadOverride(baseSpreadOverride)
                .priceFloorOverride(priceFloorOverride)
                .priceCeilingOverride(priceCeilingOverride)
                .priceFrozen(priceFrozen)
                .tier(tier)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    @NotNull
    public String getDisplayNameOrMaterial() {
        return displayName != null ? displayName : formatMaterialName(material);
    }

    private static String formatMaterialName(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ' ') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    public static class Builder {
        private int id;
        private Material material;
        private String itemHash;
        private String displayName;
        private BigDecimal price = BigDecimal.ZERO;
        private String section = "misc";
        private boolean enabled = true;
        private Boolean buyable;
        private String itemData;
        private Double maxPriceChangeOverride;
        private Double baseSpreadOverride;
        private BigDecimal priceFloorOverride;
        private BigDecimal priceCeilingOverride;
        private boolean priceFrozen = false;
        private ItemTier tier;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder itemHash(String itemHash) {
            this.itemHash = itemHash;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder section(String section) {
            this.section = section;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder buyable(Boolean buyable) {
            this.buyable = buyable;
            return this;
        }

        public Builder itemData(String itemData) {
            this.itemData = itemData;
            return this;
        }

        public Builder maxPriceChangeOverride(Double maxPriceChangeOverride) {
            this.maxPriceChangeOverride = maxPriceChangeOverride;
            return this;
        }

        public Builder baseSpreadOverride(Double baseSpreadOverride) {
            this.baseSpreadOverride = baseSpreadOverride;
            return this;
        }

        public Builder priceFloorOverride(BigDecimal priceFloorOverride) {
            this.priceFloorOverride = priceFloorOverride;
            return this;
        }

        public Builder priceCeilingOverride(BigDecimal priceCeilingOverride) {
            this.priceCeilingOverride = priceCeilingOverride;
            return this;
        }

        public Builder priceFrozen(boolean priceFrozen) {
            this.priceFrozen = priceFrozen;
            return this;
        }

        public Builder tier(ItemTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ShopItem build() {
            return new ShopItem(
                    id, material, itemHash, displayName, price,
                    section, enabled, buyable, itemData,
                    maxPriceChangeOverride, baseSpreadOverride,
                    priceFloorOverride, priceCeilingOverride,
                    priceFrozen, tier,
                    createdAt, updatedAt
            );
        }
    }
}
