package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.BadgeType;
import com.noahblclarkson.autotune.model.PlayerBadge;
import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBI repository for player badge persistence.
 */
@SuppressWarnings("PMD")
public class BadgeRepository {

    private final Jdbi jdbi;
    private final boolean sqlite;

    public BadgeRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
        this.sqlite = databaseManager.isSqlite();
    }

    private static PlayerBadge mapBadge(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        BadgeType badgeType = BadgeType.valueOf(rs.getString("badge_type"));
        Timestamp earnedTs = rs.getTimestamp("earned_at");
        Instant earnedAt = earnedTs != null ? earnedTs.toInstant() : Instant.now();
        return new PlayerBadge(playerUuid, badgeType, earnedAt);
    }

    /**
     * Insert a badge for a player. Idempotent — does nothing if already exists.
     */
    public void insert(PlayerBadge badge) {
        String sql = sqlite
                ? """
                        INSERT OR IGNORE INTO at_player_badges (player_uuid, badge_type, earned_at)
                        VALUES (:playerUuid, :badgeType, :earnedAt)
                        """
                : """
                        INSERT IGNORE INTO at_player_badges (player_uuid, badge_type, earned_at)
                        VALUES (:playerUuid, :badgeType, :earnedAt)
                        """;
        jdbi.useHandle(handle ->
                handle.createUpdate(sql)
                        .bind("playerUuid", badge.playerUuid().toString())
                        .bind("badgeType", badge.badgeType().name())
                        .bind("earnedAt", Timestamp.from(badge.earnedAt()))
                        .execute()
        );
    }

    /**
     * Returns true if the player has earned this specific badge.
     */
    public boolean hasBadge(UUID playerUuid, BadgeType badgeType) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT 1 FROM at_player_badges
                        WHERE player_uuid = :playerUuid AND badge_type = :badgeType
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("badgeType", badgeType.name())
                        .map((rs, ctx) -> 1)
                        .findFirst()
                        .isPresent()
        );
    }

    /**
     * Returns all badges earned by a player, in BadgeType ordinal order.
     */
    public List<PlayerBadge> getBadges(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT player_uuid, badge_type, earned_at
                        FROM at_player_badges
                        WHERE player_uuid = :playerUuid
                        ORDER BY badge_type
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> mapBadge(rs))
                        .list()
        );
    }

    /**
     * Returns all badge entries in the system (for admin purposes).
     */
    public List<PlayerBadge> getAll() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT player_uuid, badge_type, earned_at
                        FROM at_player_badges
                        ORDER BY earned_at DESC
                        """)
                        .map((rs, ctx) -> mapBadge(rs))
                        .list()
        );
    }

    /**
     * Returns the count of distinct badges earned by a player.
     */
    public int getBadgeCount(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*) FROM at_player_badges
                        WHERE player_uuid = :playerUuid
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> rs.getInt(1))
                        .findOne()
                        .orElse(0)
        );
    }

    /**
     * Returns the count of players who have earned a specific badge.
     */
    public int getEarnerCount(BadgeType badgeType) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*) FROM at_player_badges
                        WHERE badge_type = :badgeType
                        """)
                        .bind("badgeType", badgeType.name())
                        .map((rs, ctx) -> rs.getInt(1))
                        .findOne()
                        .orElse(0)
        );
    }

    /**
     * Returns the most recent N badges earned server-wide.
     */
    public List<PlayerBadge> getRecent(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT player_uuid, badge_type, earned_at
                        FROM at_player_badges
                        ORDER BY earned_at DESC
                        LIMIT :limit
                        """)
                        .bind("limit", limit)
                        .map((rs, ctx) -> mapBadge(rs))
                        .list()
        );
    }
}
