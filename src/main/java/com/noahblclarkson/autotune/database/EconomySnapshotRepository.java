package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.EconomySnapshot;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class EconomySnapshotRepository {

    private final Jdbi jdbi;

    public EconomySnapshotRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    private static EconomySnapshot mapSnapshot(ResultSet rs) throws SQLException {
        return EconomySnapshot.builder()
                .id(rs.getLong("id"))
                .gdp(rs.getBigDecimal("gdp"))
                .totalDebt(rs.getBigDecimal("total_debt"))
                .activeLoans(rs.getInt("active_loans"))
                .playerCount(rs.getInt("player_count"))
                .averagePriceChange(rs.getBigDecimal("average_price_change"))
                .transactionVolume(rs.getBigDecimal("transaction_volume"))
                .timestamp(rs.getTimestamp("timestamp").toInstant())
                .build();
    }

    public void insert(@NotNull EconomySnapshot snapshot) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_economy_snapshots (gdp, total_debt, active_loans, player_count,
                                                                   average_price_change, transaction_volume)
                                VALUES (:gdp, :totalDebt, :activeLoans, :playerCount,
                                        :averagePriceChange, :transactionVolume)
                                """)
                        .bind("gdp", snapshot.gdp())
                        .bind("totalDebt", snapshot.totalDebt())
                        .bind("activeLoans", snapshot.activeLoans())
                        .bind("playerCount", snapshot.playerCount())
                        .bind("averagePriceChange", snapshot.averagePriceChange())
                        .bind("transactionVolume", snapshot.transactionVolume())
                        .execute());
    }

    public List<EconomySnapshot> findRecent(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, gdp, total_debt, active_loans, player_count,
                                       average_price_change, transaction_volume, timestamp
                                FROM at_economy_snapshots
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("limit", limit)
                        .map((rs, ctx) -> mapSnapshot(rs))
                        .list());
    }

    public List<EconomySnapshot> findSince(Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, gdp, total_debt, active_loans, player_count,
                                       average_price_change, transaction_volume, timestamp
                                FROM at_economy_snapshots
                                WHERE timestamp >= :since
                                ORDER BY timestamp ASC
                                """)
                        .bind("since", Timestamp.from(since))
                        .map((rs, ctx) -> mapSnapshot(rs))
                        .list());
    }

    public Optional<EconomySnapshot> findLatest() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, gdp, total_debt, active_loans, player_count,
                                       average_price_change, transaction_volume, timestamp
                                FROM at_economy_snapshots
                                ORDER BY timestamp DESC
                                LIMIT 1
                                """)
                        .map((rs, ctx) -> mapSnapshot(rs))
                        .findFirst());
    }
}
