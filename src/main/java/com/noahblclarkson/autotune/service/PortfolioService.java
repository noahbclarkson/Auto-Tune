package com.noahblclarkson.autotune.service;

import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PnLHistoryDto;
import com.noahblclarkson.autotune.model.PlayerData;
import com.noahblclarkson.autotune.model.PortfolioDto;
import com.noahblclarkson.autotune.model.PortfolioDto.ActiveLoanDto;
import com.noahblclarkson.autotune.model.PortfolioDto.HoldingDto;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes a player's portfolio: holdings, P&L, vault balance, loans, net worth.
 */
public class PortfolioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioService.class);
    private static final int MAX_TRANSACTION_DAYS = 90;

    private final PlayerRepository playerRepository;
    private final ItemRepository itemRepository;
    private final LoanRepository loanRepository;
    private final TransactionRepository transactionRepository;
    private final EconomyManager economyManager;
    private final Server server;

    public PortfolioService(
            PlayerRepository playerRepository,
            ItemRepository itemRepository,
            LoanRepository loanRepository,
            TransactionRepository transactionRepository,
            EconomyManager economyManager,
            Server server) {
        this.playerRepository = playerRepository;
        this.itemRepository = itemRepository;
        this.loanRepository = loanRepository;
        this.transactionRepository = transactionRepository;
        this.economyManager = economyManager;
        this.server = server;
    }

    /**
     * Build a portfolio for the player identified by name.
     * Returns empty if the player has never been seen by Auto-Tune.
     */
    public Optional<PortfolioDto> buildPortfolio(String playerName) {
        // Resolve player name → PlayerData (may be null for never-seen players)
        PlayerData player = playerRepository.findByName(playerName).orElse(null);
        String uuidStr = null;
        int creditScore = 500;
        int transactionCount = 0;

        if (player != null) {
            uuidStr = player.uuid().toString();
            creditScore = player.creditScore();
            transactionCount = player.transactionCount();
        } else {
            // Try to at least get the UUID from Bukkit for Vault balance
            var offline = server.getOfflinePlayer(playerName);
            if (offline == null || !offline.hasPlayedBefore()) {
                return Optional.empty();
            }
        }

        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : server.getOfflinePlayer(playerName).getUniqueId();

        // Vault balance
        double vaultBalance;
        try {
            var offline = server.getOfflinePlayer(playerName);
            vaultBalance = economyManager.getBalance(offline);
        } catch (Exception e) {
            LOGGER.warn("Could not get vault balance for {}: {}", playerName, e.getMessage());
            vaultBalance = 0.0;
        }

        // Active loans
        List<Loan> activeLoans = loanRepository.findByPlayer(uuid).stream()
                .filter(l -> l.status() == Loan.LoanStatus.ACTIVE)
                .collect(Collectors.toList());

        double totalDebt = activeLoans.stream()
                .mapToDouble(l -> l.currentBalance().doubleValue())
                .sum();

        List<ActiveLoanDto> loanDtos = activeLoans.stream()
                .map(this::toLoanDto)
                .collect(Collectors.toList());

        // Holdings from transaction history
        Instant cutoff = Instant.now().minus(MAX_TRANSACTION_DAYS, ChronoUnit.DAYS);
        List<HoldingDto> holdings = computeHoldings(uuid, cutoff);

        double holdingsValue = holdings.stream()
                .mapToDouble(HoldingDto::currentValue)
                .sum();

        double totalRealizedPnl = holdings.stream()
                .mapToDouble(HoldingDto::realizedPnl)
                .sum();

        double netWorth = vaultBalance + holdingsValue - totalDebt;

        String displayName = player != null && player.username() != null
                ? player.username() : playerName;

        return Optional.of(new PortfolioDto(
                displayName,
                uuidStr,
                vaultBalance,
                holdingsValue,
                totalDebt,
                netWorth,
                round(totalRealizedPnl, 2),
                creditScore,
                transactionCount,
                holdings,
                loanDtos
        ));
    }

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d")
            .withZone(ZoneOffset.UTC);

    /**
     * Compute the player's P&L history over the last MAX_TRANSACTION_DAYS.
     * Uses a per-item weighted-average cost basis: each BUY updates the avg cost,
     * each SELL realizes P&L against that avg cost.
     * Returns one data point per day with cumulative net realized P&L.
     */
    public List<PnLHistoryDto> getPnlHistory(String playerName) {
        PlayerData player = playerRepository.findByName(playerName).orElse(null);
        if (player == null) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(MAX_TRANSACTION_DAYS, ChronoUnit.DAYS);
        List<Transaction> txns = transactionRepository.findByPlayerSince(player.uuid(), cutoff);
        if (txns.isEmpty()) {
            return List.of();
        }

        // Per-item weighted-average cost basis tracking
        Map<Integer, CostBasis> basis = new HashMap<>();

        // Group transactions by day (UTC midnight)
        Map<Long, Double> dailyPnl = new LinkedHashMap<>();

        for (Transaction tx : txns) {
            long dayMs = tx.timestamp().toEpochMilli() / 86_400_000L * 86_400_000L;
            double pnl = 0.0;
            if (tx.type() == TransactionType.BUY) {
                CostBasis cb = basis.computeIfAbsent(tx.itemId(), k -> new CostBasis());
                cb.addBuy(tx.amount(), tx.pricePerUnit().doubleValue());
            } else { // SELL
                CostBasis cb = basis.get(tx.itemId());
                if (cb != null && cb.qty > 0) {
                    double avgCost = cb.avgCost;
                    double sellProceeds = tx.totalPrice().doubleValue();
                    double costOfSold = Math.min(tx.amount(), cb.qty) * avgCost;
                    pnl += sellProceeds - costOfSold;
                    cb.removeQty(tx.amount());
                }
            }
            dailyPnl.merge(dayMs, pnl, Double::sum);
        }

        // Build cumulative P&L series
        List<PnLHistoryDto> result = new ArrayList<>();
        double cumulative = 0.0;
        for (Map.Entry<Long, Double> e : dailyPnl.entrySet()) {
            cumulative += e.getValue();
            result.add(new PnLHistoryDto(e.getKey(), DAY_LABEL.format(Instant.ofEpochMilli(e.getKey())), round(cumulative, 2)));
        }
        return result;
    }

    private static class CostBasis {
        int qty = 0;
        double avgCost = 0.0;

        void addBuy(int buyQty, double price) {
            if (buyQty <= 0) return;
            if (qty == 0) {
                qty = buyQty;
                avgCost = price;
            } else {
                avgCost = ((double) qty * avgCost + buyQty * price) / (qty + buyQty);
                qty += buyQty;
            }
        }

        void removeQty(int soldQty) {
            qty = Math.max(0, qty - soldQty);
        }
    }

    private @NotNull List<HoldingDto> computeHoldings(UUID playerUuid, Instant cutoff) {
        String sql = """
                WITH buy_stats AS (
                    SELECT item_id,
                           SUM(amount) AS total_bought,
                           SUM(amount * price_per_unit) AS total_spent
                    FROM at_transactions
                    WHERE player_uuid = :uuid AND transaction_type = 'BUY' AND timestamp >= :cutoff
                    GROUP BY item_id
                ),
                sell_stats AS (
                    SELECT item_id,
                           SUM(amount) AS total_sold,
                           SUM(amount * price_per_unit) AS total_received
                    FROM at_transactions
                    WHERE player_uuid = :uuid AND transaction_type = 'SELL' AND timestamp >= :cutoff
                    GROUP BY item_id
                )
                SELECT
                    i.id AS item_id,
                    i.material,
                    COALESCE(i.display_name, i.material) AS display_name,
                    i.section,
                    i.price AS current_price,
                    COALESCE(bs.total_bought, 0) AS total_bought,
                    COALESCE(ss.total_sold, 0) AS total_sold,
                    CASE WHEN COALESCE(bs.total_bought, 0) > 0
                         THEN bs.total_spent / bs.total_bought
                         ELSE 0.0 END AS avg_buy_price,
                    CASE WHEN COALESCE(ss.total_sold, 0) > 0
                         THEN ss.total_received / ss.total_sold
                         ELSE 0.0 END AS avg_sell_price
                FROM at_items i
                LEFT JOIN buy_stats bs ON bs.item_id = i.id
                LEFT JOIN sell_stats ss ON ss.item_id = i.id
                WHERE (COALESCE(bs.total_bought, 0) - COALESCE(ss.total_sold, 0)) > 0
                ORDER BY (COALESCE(bs.total_bought, 0) - COALESCE(ss.total_sold, 0)) * i.price DESC
                """;

        var jdbi = itemRepository.getJdbi(); // expose Jdbi for raw query
        List<HoldingDto> holdings = new ArrayList<>();

        jdbi.withHandle(handle -> {
            var stmt = handle.createQuery(sql)
                    .bind("uuid", playerUuid.toString())
                    .bind("cutoff", Timestamp.from(cutoff));
            var results = stmt.map((rs, ctx) -> {
                int totalBought = rs.getInt("total_bought");
                int totalSold = rs.getInt("total_sold");
                int netQty = totalBought - totalSold;
                double avgBuyPrice = rs.getDouble("avg_buy_price");
                double avgSellPrice = rs.getDouble("avg_sell_price");
                double currentPrice = rs.getDouble("current_price");
                double currentValue = netQty * currentPrice;
                double unrealizedPnl = netQty * (currentPrice - avgBuyPrice);
                double realizedPnl = totalSold > 0
                        ? totalSold * (avgSellPrice - avgBuyPrice)
                        : 0.0;
                double pnlPct = avgBuyPrice > 0.0
                        ? ((currentPrice - avgBuyPrice) / avgBuyPrice) * 100.0
                        : 0.0;

                return new HoldingDto(
                        rs.getInt("item_id"),
                        rs.getString("material"),
                        rs.getString("display_name"),
                        rs.getString("section"),
                        netQty,
                        round(avgBuyPrice, 2),
                        round(currentPrice, 2),
                        round(currentValue, 2),
                        round(unrealizedPnl, 2),
                        round(pnlPct, 2),
                        round(realizedPnl, 2)
                );
            }).list();
            holdings.addAll(results);
            return null;
        });

        return holdings;
    }

    private ActiveLoanDto toLoanDto(Loan loan) {
        return new ActiveLoanDto(
                loan.id().toString(),
                loan.principal().doubleValue(),
                loan.currentBalance().doubleValue(),
                loan.interestRate().doubleValue() * 100.0,
                loan.createdAt(),
                loan.dueDate(),
                loan.status().name()
        );
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
