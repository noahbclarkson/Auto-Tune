package com.noahblclarkson.autotune.service;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.BadgeRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.BadgeType;
import com.noahblclarkson.autotune.model.PlayerBadge;
import com.noahblclarkson.autotune.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service that awards achievement badges to players based on market activity
 * and tracks badge state.
 *
 * Badge awarding is idempotent — awarding an already-earned badge is a no-op.
 * Criteria checks are done on-demand at the point of relevant activity.
 */
@SuppressWarnings("PMD")
public class BadgeService {

    private static final String BADGE_METADATA_KEY = "autotune_badge_new";

    private final BadgeRepository badgeRepo;
    private final TransactionRepository transactionRepo;
    private final PlayerRepository playerRepo;
    private final AutoTune plugin;

    // Track recently awarded badges so we only notify once per event
    private final Set<String> recentlyNotified = Collections.synchronizedSet(new HashSet<>());

    public BadgeService(DatabaseManager db, AutoTune plugin) {
        this.badgeRepo = new BadgeRepository(db);
        this.transactionRepo = new TransactionRepository(db);
        this.playerRepo = new PlayerRepository(db);
        this.plugin = plugin;
    }

    /**
     * Attempts to award a badge to a player. Returns true if newly awarded,
     * false if already earned or player is offline.
     */
    public boolean award(Player player, BadgeType badge) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return award(player.getUniqueId(), badge);
    }

    /**
     * Attempts to award a badge to a player by UUID. Returns true if newly awarded.
     */
    public boolean award(UUID playerUuid, BadgeType badge) {
        if (badgeRepo.hasBadge(playerUuid, badge)) {
            return false;
        }
        PlayerBadge earned = new PlayerBadge(playerUuid, badge, Instant.now());
        badgeRepo.insert(earned);

        // Notify player in-game
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            notifyPlayer(player, badge);
        }
        return true;
    }

    /**
     * Returns all badges earned by a player.
     */
    public List<PlayerBadge> getBadges(UUID playerUuid) {
        return badgeRepo.getBadges(playerUuid);
    }

    /**
     * Returns all badge types with their earned/not-earned status for a player.
     */
    public List<BadgeWithStatus> getBadgesWithStatus(UUID playerUuid) {
        List<PlayerBadge> earned = badgeRepo.getBadges(playerUuid);
        Set<BadgeType> earnedTypes = new HashSet<>();
        for (PlayerBadge b : earned) {
            earnedTypes.add(b.badgeType());
        }
        List<BadgeWithStatus> result = new ArrayList<>();
        for (BadgeType type : BadgeType.values()) {
            PlayerBadge badge = earned.stream()
                    .filter(b -> b.badgeType() == type)
                    .findFirst()
                    .orElse(null);
            result.add(new BadgeWithStatus(type, badge != null, badge != null ? badge.earnedAt() : null));
        }
        return result;
    }

    /**
     * Returns the total badge count for a player.
     */
    public int getBadgeCount(UUID playerUuid) {
        return badgeRepo.getBadgeCount(playerUuid);
    }

    // ─── Event-driven awarding ───────────────────────────────────────────

    /**
     * Call after a sell transaction succeeds.
     */
    public void onSell(UUID playerUuid, BigDecimal totalPrice) {
        // FIRST_SALE
        award(playerUuid, BadgeType.FIRST_SALE);

        // BIG_SPENDER (threshold: 1M per transaction)
        if (totalPrice.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            award(playerUuid, BadgeType.BIG_SPENDER);
        }
    }

    /**
     * Call after a buy transaction succeeds.
     */
    public void onBuy(UUID playerUuid) {
        award(playerUuid, BadgeType.FIRST_BUYER);
    }

    /**
     * Call after a loan is fully repaid.
     * @param repaidAmount the principal amount of the loan that was repaid
     */
    public void onLoanRepaid(UUID playerUuid, BigDecimal repaidAmount) {
        // LOAN_SHARK: repaid a loan of 100K or more
        if (repaidAmount.compareTo(BigDecimal.valueOf(100_000)) >= 0) {
            award(playerUuid, BadgeType.LOAN_SHARK);
        }
    }

    /**
     * Call when a player takes out their first loan.
     */
    public void onFirstLoan(UUID playerUuid) {
        award(playerUuid, BadgeType.LOAN_TAKER);
    }

    /**
     * Call when a price alert fires.
     */
    public void onAlertFired(UUID playerUuid) {
        award(playerUuid, BadgeType.TREND_SPOTTER);
    }

    /**
     * Call periodically (e.g., every 10 minutes) to check ongoing criteria.
     * Checks STABLE_HAND and updates relevant badges.
     */
    public void checkOngoingBadges(UUID playerUuid) {
        checkStableHand(playerUuid);
        checkCenturion(playerUuid);
    }

    /**
     * Call after transaction count is updated. Checks CENTURION.
     */
    public void onTransactionCountUpdated(UUID playerUuid, int transactionCount) {
        if (transactionCount >= 100) {
            award(playerUuid, BadgeType.CENTURION);
        }
    }

    /**
     * Call after autosell inventory count changes. Checks HOARDER.
     */
    public void onAutosellInventoryUpdated(UUID playerUuid, int itemCount) {
        if (itemCount >= 50) {
            award(playerUuid, BadgeType.HOARDER);
        }
    }

    /**
     * Call to check MARKET_MAKER (distinct items traded).
     * This requires a DB query so only call opportunistically (e.g., every few minutes).
     */
    public void checkMarketMaker(UUID playerUuid) {
        int distinctItems = transactionRepo.countDistinctItemsTraded(playerUuid);
        if (distinctItems >= 10) {
            award(playerUuid, BadgeType.MARKET_MAKER);
        }
    }

    /**
     * Call to check DIVERSIFIED (5+ sections held simultaneously).
     * Requires a DB query — call opportunistically.
     */
    public void checkDiversified(UUID playerUuid) {
        int sections = transactionRepo.countDistinctSectionsTraded(playerUuid);
        if (sections >= 5) {
            award(playerUuid, BadgeType.DIVERSIFIED);
        }
    }

    // ─── Internal ───────────────────────────────────────────────────────

    private void checkStableHand(UUID playerUuid) {
        Optional<PlayerData> data = playerRepo.findByUuid(playerUuid);
        if (data.isEmpty()) return;

        PlayerData pd = data.get();
        if (pd.lastDefaultedAt() != null) return; // Has defaulted — no STABLE_HAND

        Duration active = Duration.between(pd.firstSeen(), Instant.now());
        if (active.toDays() >= 7) {
            award(playerUuid, BadgeType.STABLE_HAND);
        }
    }

    private void checkCenturion(UUID playerUuid) {
        Optional<PlayerData> data = playerRepo.findByUuid(playerUuid);
        if (data.isEmpty()) return;
        if (data.get().transactionCount() >= 100) {
            award(playerUuid, BadgeType.CENTURION);
        }
    }

    private void notifyPlayer(Player player, BadgeType badge) {
        Component message = Component.text()
                .append(Component.text("🏆 ", TextColor.fromHexString("#FFD700")))
                .append(Component.text("Badge Earned: ", NamedTextColor.GRAY))
                .append(badge.displayNameComponent())
                .append(Component.text(" — " + badge.getDescription(), NamedTextColor.DARK_GRAY))
                .build();

        Component hover = Component.text()
                .append(Component.text(badge.getDisplayName() + "\n", badge.getColor()))
                .append(Component.text(badge.getDescription(), NamedTextColor.GRAY))
                .build();

        player.sendMessage(message.hoverEvent(HoverEvent.showText(hover)));
    }

    /**
     * Result of querying a badge with its earned/not-earned status.
     */
    public record BadgeWithStatus(BadgeType type, boolean earned, Instant earnedAt) {
        public Component earnedAgo() {
            if (!earned || earnedAt == null) return Component.empty();
            String ago = formatDuration(Duration.between(earnedAt, Instant.now()));
            return Component.text(ago, NamedTextColor.DARK_GRAY);
        }

        private static String formatDuration(Duration d) {
            if (d.toDays() > 0) return d.toDays() + "d ago";
            if (d.toHours() > 0) return d.toHours() + "h ago";
            if (d.toMinutes() > 0) return d.toMinutes() + "m ago";
            return "just now";
        }
    }

    /**
     * Builds an ItemStack representing a badge icon for use in GUIs.
     * Earned badges show the item; unearned badges are a greyed-out replica.
     *
     * @param earned whether the player has earned this badge (affects coloring)
     * @param earnedAt when the badge was earned (for lore display)
     */
    public ItemStack badgeIcon(BadgeType type, boolean earned, Instant earnedAt) {
        Material mat;
        try {
            mat = Material.valueOf(type.iconMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (!earned) {
            // Greyed out for unearned badges
            meta.setDisplayName("§7" + type.getDisplayName());
            meta.setLore(List.of(
                    "§8§o" + type.getDescription(),
                    "",
                    "§8🔒 Not yet earned"
            ));
        } else {
            String earnedStr = earnedAt != null
                    ? DateTimeFormatter.ofPattern("MMM d, yyyy")
                            .withZone(ZoneId.systemDefault())
                            .format(earnedAt)
                    : "Unknown";
            meta.setDisplayName(type.getColor().toString() + type.getDisplayName());
            meta.setLore(List.of(
                    "§a§l★ EARNED",
                    "§7" + type.getDescription(),
                    "",
                    "§8Earned: " + earnedStr
            ));
        }

        // Mark earned items for potential glow effect (optional enhancement)
        if (earned) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "badge_icon"),
                    PersistentDataType.BOOLEAN,
                    true
            );
        }

        item.setItemMeta(meta);
        return item;
    }
}
