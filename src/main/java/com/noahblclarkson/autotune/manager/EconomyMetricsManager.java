package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

@Singleton
@SuppressWarnings("PMD")
public class EconomyMetricsManager {

    private final AutoTune plugin;
    private final ItemRepository itemRepository;
    private final LoanRepository loanRepository;
    private final TransactionRepository transactionRepository;
    private final EconomySnapshotRepository snapshotRepository;

    @Inject
    public EconomyMetricsManager(
            AutoTune plugin,
            ItemRepository itemRepository,
            LoanRepository loanRepository,
            TransactionRepository transactionRepository,
            EconomySnapshotRepository snapshotRepository
    ) {
        this.plugin = plugin;
        this.itemRepository = itemRepository;
        this.loanRepository = loanRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public void captureSnapshot() {
        try {
            Instant oneDayAgo = Instant.now().minus(Duration.ofDays(1));

            BigDecimal gdp = transactionRepository.getGlobalBuyVolume(oneDayAgo);

            List<Loan> activeLoans = loanRepository.findAllActive();
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (Loan loan : activeLoans) {
                totalDebt = totalDebt.add(loan.currentBalance());
            }

            BigDecimal averagePriceChange = calculateAveragePriceChange();
            BigDecimal transactionVolume = transactionRepository.getGlobalVolume(oneDayAgo);
            int playerCount = plugin.getServer().getOnlinePlayers().size();

            EconomySnapshot snapshot = EconomySnapshot.builder()
                    .gdp(gdp)
                    .totalDebt(totalDebt)
                    .activeLoans(activeLoans.size())
                    .playerCount(playerCount)
                    .averagePriceChange(averagePriceChange)
                    .transactionVolume(transactionVolume)
                    .build();

            snapshotRepository.insert(snapshot);

            if (plugin.getConfigManager().getConfig().debug().enabled()) {
                plugin.getLogger().info("Economy snapshot captured - GDP: " + gdp + ", Debt: " + totalDebt);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to capture economy snapshot", e);
        }
    }

    private BigDecimal calculateAveragePriceChange() {
        List<ShopItem> items = itemRepository.findAll();
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalChange = BigDecimal.ZERO;
        int counted = 0;

        for (ShopItem item : items) {
            List<PriceHistory> history = itemRepository.getPriceHistory(item.id(), 2);
            if (history.size() < 2) {
                continue;
            }

            BigDecimal newest = history.get(0).price();
            BigDecimal older = history.get(1).price();

            if (older.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal change = newest.subtract(older)
                    .divide(older, 5, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            totalChange = totalChange.add(change);
            counted++;
        }

        if (counted == 0) {
            return BigDecimal.ZERO;
        }

        return totalChange.divide(BigDecimal.valueOf(counted), 5, RoundingMode.HALF_UP);
    }

    public Optional<EconomySnapshot> getLatestSnapshot() {
        return snapshotRepository.findLatest();
    }

    public BigDecimal getDebtPerCapita() {
        Optional<EconomySnapshot> latest = getLatestSnapshot();
        if (latest.isEmpty() || latest.get().playerCount() == 0) {
            return BigDecimal.ZERO;
        }
        return latest.get().totalDebt()
                .divide(BigDecimal.valueOf(latest.get().playerCount()), 2, RoundingMode.HALF_UP);
    }

    public String getInflationLabel() {
        Optional<EconomySnapshot> latest = getLatestSnapshot();
        if (latest.isEmpty()) {
            return "N/A";
        }

        BigDecimal avgChange = latest.get().averagePriceChange();
        if (avgChange.compareTo(BigDecimal.valueOf(1)) > 0) {
            return "High Inflation";
        } else if (avgChange.compareTo(BigDecimal.valueOf(-1)) < 0) {
            return "Deflation";
        }
        return "Stable";
    }

    /**
     * Returns economy snapshots within the given time window, sampled down to
     * at most `maxPoints` evenly-spaced data points for display purposes.
     */
    public List<EconomySnapshot> getSnapshotsInWindow(Instant since, int maxPoints) {
        List<EconomySnapshot> all = snapshotRepository.findSince(since);
        if (all.size() <= maxPoints) {
            return all;
        }
        // Evenly sample across the window
        double step = (double) all.size() / maxPoints;
        List<EconomySnapshot> sampled = new java.util.ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            sampled.add(all.get((int) Math.round(i * step)));
        }
        // Always include the last snapshot
        EconomySnapshot last = all.get(all.size() - 1);
        if (!sampled.get(sampled.size() - 1).equals(last)) {
            sampled.set(sampled.size() - 1, last);
        }
        return sampled;
    }
}
