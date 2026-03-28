package com.noahblclarkson.autotune.guild;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PlayerData;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;

/**
 * Provides guild-based economy statistics using Vault Permission groups as guild identifiers.
 * <p>
 * Players belong to a guild if they have a non-default primary Vault permission group.
 * Guild membership is persisted in the database (at_players.guild_tag) so it works for
 * both online and offline players.
 */
@Singleton
public class GuildService {

    private static final Logger LOGGER = Logger.getLogger(GuildService.class.getName());

    private final PlayerRepository playerRepository;
    private final LoanRepository loanRepository;
    private final Server server;

    @Inject
    public GuildService(
            PlayerRepository playerRepository,
            LoanRepository loanRepository,
            Server server
    ) {
        this.playerRepository = playerRepository;
        this.loanRepository = loanRepository;
        this.server = server;
    }

    /**
     * Gets the guild tag for a player from the database.
     */
    public Optional<String> getPlayerGuild(UUID playerId) {
        return playerRepository.findByUuid(playerId)
                .map(PlayerData::guildTag)
                .filter(tag -> tag != null && !tag.isBlank());
    }

    /**
     * Gets the guild tag for an online player.
     */
    public Optional<String> getOnlinePlayerGuild(Player player) {
        return getPlayerGuild(player.getUniqueId());
    }

    /**
     * Gets all known guild tags from the database.
     */
    public Set<String> getKnownGuilds() {
        Set<String> guilds = new HashSet<>();
        for (Player player : server.getOnlinePlayers()) {
            getPlayerGuild(player.getUniqueId()).ifPresent(guilds::add);
        }
        return guilds;
    }

    /**
     * Aggregated statistics for a guild.
     */
    public record GuildStats(
            String guildTag,
            int memberCount,
            BigDecimal totalVolume,
            BigDecimal totalBought,
            BigDecimal totalSold,
            BigDecimal netPosition,
            BigDecimal totalDebt,
            int activeLoanCount,
            BigDecimal largestLoan,
            BigDecimal avgCreditScore,
            BigDecimal perMemberVolume,
            int onlineCount
    ) {}

    /**
     * Gets guild stats for a player's guild.
     */
    public Optional<GuildStats> getGuildStatsForPlayer(Player player) {
        return getPlayerGuild(player.getUniqueId())
                .map(this::getGuildStats);
    }

    /**
     * Gets guild stats for a given guild tag.
     */
    public GuildStats getGuildStats(String guildTag) {
        List<PlayerData> members = playerRepository.findByGuildTag(guildTag);
        if (members.isEmpty()) {
            return new GuildStats(
                    guildTag,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0
            );
        }

        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalBought = BigDecimal.ZERO;
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        BigDecimal largestLoan = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int activeLoans = 0;
        int validCreditScores = 0;

        // Count online members
        Set<String> onlineNames = new HashSet<>();
        for (Player p : server.getOnlinePlayers()) {
            onlineNames.add(p.getName().toLowerCase(java.util.Locale.ROOT));
        }

        int onlineCount = 0;

        for (PlayerData p : members) {
            totalVolume = totalVolume.add(p.totalTraded());
            totalBought = totalBought.add(p.totalBought());
            totalSold = totalSold.add(p.totalSold());
            if (p.creditScore() > 0) {
                totalCredit = totalCredit.add(BigDecimal.valueOf(p.creditScore()));
                validCreditScores++;
            }
            if (p.username() != null && onlineNames.contains(p.username().toLowerCase(java.util.Locale.ROOT))) {
                onlineCount++;
            }

            // Check active loans
            Optional<Loan> loan = loanRepository.findActiveByPlayer(p.uuid());
            if (loan.isPresent()) {
                Loan l = loan.get();
                totalDebt = totalDebt.add(l.currentBalance());
                if (l.currentBalance().compareTo(largestLoan) > 0) {
                    largestLoan = l.currentBalance();
                }
                activeLoans++;
            }
        }

        BigDecimal netPosition = totalSold.subtract(totalBought);
        BigDecimal avgCredit = validCreditScores > 0
                ? totalCredit.divide(BigDecimal.valueOf(validCreditScores), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal perMemberVol = members.size() > 0
                ? totalVolume.divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new GuildStats(
                guildTag,
                members.size(),
                totalVolume,
                totalBought,
                totalSold,
                netPosition,
                totalDebt,
                activeLoans,
                largestLoan,
                avgCredit,
                perMemberVol,
                onlineCount
        );
    }

    /**
     * Returns all guilds with their stats, ranked by total trading volume.
     */
    public List<GuildStats> getAllGuildStats() {
        Set<String> guilds = getKnownGuilds();
        List<GuildStats> result = new ArrayList<>();
        for (String guild : guilds) {
            result.add(getGuildStats(guild));
        }
        result.sort((a, b) -> b.totalVolume().compareTo(a.totalVolume()));
        return result;
    }

    /**
     * Gets the rank of a specific guild by total trading volume (1-indexed).
     * Returns -1 if the guild is not found.
     */
    public int getGuildRank(String guildTag) {
        List<GuildStats> all = getAllGuildStats();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).guildTag().equalsIgnoreCase(guildTag)) {
                return i + 1;
            }
        }
        return -1;
    }
}
