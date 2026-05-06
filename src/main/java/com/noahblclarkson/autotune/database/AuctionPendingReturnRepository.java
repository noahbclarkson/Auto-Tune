package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.AuctionPendingReturn;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBI repository for {@code at_auction_pending_returns}.
 */
public class AuctionPendingReturnRepository {

    private final Jdbi jdbi;

    public AuctionPendingReturnRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    /**
     * Insert a new pending return record.
     */
    public void insert(@NotNull AuctionPendingReturn r) {
        jdbi.useHandle(handle -> handle
                .createUpdate("""
                        INSERT INTO at_auction_pending_returns
                            (id, player_uuid, fill_id, material, item_data, quantity, reason, created_at)
                        VALUES
                            (:id, :playerUuid, :fillId, :material, :itemData, :quantity, :reason, :createdAt)
                        """)
                .bind("id", r.id().toString())
                .bind("playerUuid", r.playerUuid().toString())
                .bind("fillId", r.fillId() != null ? r.fillId().toString() : null)
                .bind("material", r.material())
                .bind("itemData", r.itemData())
                .bind("quantity", r.quantity())
                .bind("reason", r.reason())
                .bind("createdAt", Timestamp.from(r.createdAt()))
                .execute());
    }

    /**
     * Mark a pending return as returned (sets returned_at).
     */
    public void markReturned(@NotNull UUID id) {
        jdbi.useHandle(handle -> handle
                .createUpdate("""
                        UPDATE at_auction_pending_returns
                        SET returned_at = :returnedAt
                        WHERE id = :id
                        """)
                .bind("id", id.toString())
                .bind("returnedAt", Timestamp.from(Instant.now()))
                .execute());
    }

    /**
     * Find all un-returned pending items for a player.
     */
    public List<AuctionPendingReturn> findUnreturnedForPlayer(@NotNull UUID playerUuid) {
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        SELECT id, player_uuid, fill_id, material, item_data, quantity,
                               reason, created_at, returned_at
                        FROM at_auction_pending_returns
                        WHERE player_uuid = :playerUuid AND returned_at IS NULL
                        ORDER BY created_at ASC
                        """)
                .bind("playerUuid", playerUuid.toString())
                .map((rs, ctx) -> new AuctionPendingReturn(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("fill_id") != null ? UUID.fromString(rs.getString("fill_id")) : null,
                        rs.getString("material"),
                        rs.getString("item_data"),
                        rs.getInt("quantity"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("returned_at") != null
                                ? rs.getTimestamp("returned_at").toInstant() : null))
                .list());
    }

    /**
     * Update a pending return after a partial reclaim attempt leaves some items
     * still undelivered.
     */
    public void updateUnreturnedPayload(@NotNull UUID id, int quantity, String itemData) {
        jdbi.useHandle(handle -> handle
                .createUpdate("""
                        UPDATE at_auction_pending_returns
                        SET quantity = :quantity, item_data = :itemData
                        WHERE id = :id AND returned_at IS NULL
                        """)
                .bind("id", id.toString())
                .bind("quantity", quantity)
                .bind("itemData", itemData)
                .execute());
    }

    /**
     * Count unreturned items for a player (for notification purposes).
     */
    public int countUnreturnedForPlayer(@NotNull UUID playerUuid) {
        return jdbi.withHandle(handle -> handle
                .createQuery("""
                        SELECT COUNT(*) FROM at_auction_pending_returns
                        WHERE player_uuid = :playerUuid AND returned_at IS NULL
                        """)
                .bind("playerUuid", playerUuid.toString())
                .mapTo(int.class)
                .findFirst().orElse(0));
    }
}
