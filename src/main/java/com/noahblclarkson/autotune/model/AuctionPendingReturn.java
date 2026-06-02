package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An item that could not be delivered to a player's inventory during an
 * auction fill or order expiry and is awaiting manual reclamation.
 */
@SuppressWarnings("PMD")
public record AuctionPendingReturn(
        @NotNull UUID id,
        @NotNull UUID playerUuid,
        @Nullable UUID fillId,
        @NotNull String material,
        @Nullable String itemData,
        int quantity,
        @NotNull String reason,
        @NotNull Instant createdAt,
        @Nullable Instant returnedAt
) {

    public enum Reason {
        INVENTORY_FULL,
        OFFLINE_BUYER,
        EXPIRED_ORDER
    }

    public boolean isReturned() {
        return returnedAt != null;
    }

    public AuctionPendingReturn withReturnedAt(@NotNull Instant at) {
        return new AuctionPendingReturn(
                id, playerUuid, fillId, material, itemData, quantity, reason, createdAt, at);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID playerUuid;
        private UUID fillId;
        private String material;
        private String itemData;
        private int quantity = 1;
        private String reason = "INVENTORY_FULL";
        private Instant createdAt = Instant.now();
        private Instant returnedAt;

        public Builder id(UUID id) { this.id = id; return this; }

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder fillId(UUID fillId) {
            this.fillId = fillId;
            return this;
        }

        public Builder material(String material) {
            this.material = material;
            return this;
        }

        public Builder itemData(String itemData) {
            this.itemData = itemData;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder reason(Reason reason) {
            this.reason = reason.name();
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder returnedAt(Instant returnedAt) {
            this.returnedAt = returnedAt;
            return this;
        }

        public AuctionPendingReturn build() {
            return new AuctionPendingReturn(
                    id, playerUuid, fillId, material, itemData,
                    quantity, reason, createdAt, returnedAt);
        }
    }
}
