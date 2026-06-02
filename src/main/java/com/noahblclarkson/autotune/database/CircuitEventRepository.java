package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.CircuitEvent;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for circuit-breaker/admin-recovery timeline events. */
public class CircuitEventRepository {

    private final Jdbi jdbi;

    public CircuitEventRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    private static CircuitEvent mapEvent(ResultSet rs) throws SQLException {
        return new CircuitEvent(
                rs.getLong("id"),
                rs.getString("previous_tier"),
                rs.getString("new_tier"),
                rs.getDouble("debt_gdp_ratio"),
                rs.getBigDecimal("gdp"),
                rs.getBigDecimal("total_debt"),
                rs.getDouble("interest_multiplier"),
                rs.getBoolean("admin_initiated"),
                rs.getString("details"),
                rs.getTimestamp("timestamp").toInstant()
        );
    }

    public void insert(@NotNull CircuitEvent event) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                        INSERT INTO at_circuit_events (
                            previous_tier, new_tier, debt_gdp_ratio, gdp, total_debt,
                            interest_multiplier, admin_initiated, details, timestamp
                        ) VALUES (
                            :previousTier, :newTier, :debtGdpRatio, :gdp, :totalDebt,
                            :interestMultiplier, :adminInitiated, :details, :timestamp
                        )
                        """)
                .bind("previousTier", event.previousTier())
                .bind("newTier", event.newTier())
                .bind("debtGdpRatio", event.debtGdpRatio())
                .bind("gdp", event.gdp())
                .bind("totalDebt", event.totalDebt())
                .bind("interestMultiplier", event.interestMultiplier())
                .bind("adminInitiated", event.adminInitiated())
                .bind("details", event.details())
                .bind("timestamp", Timestamp.from(event.timestamp()))
                .execute());
    }

    public List<CircuitEvent> findRecent(int limit) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, previous_tier, new_tier, debt_gdp_ratio, gdp, total_debt,
                               interest_multiplier, admin_initiated, details, timestamp
                        FROM at_circuit_events
                        ORDER BY timestamp DESC
                        LIMIT :limit
                        """)
                .bind("limit", limit)
                .map((rs, ctx) -> mapEvent(rs))
                .list());
    }

    public List<CircuitEvent> findSince(@NotNull Instant since) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, previous_tier, new_tier, debt_gdp_ratio, gdp, total_debt,
                               interest_multiplier, admin_initiated, details, timestamp
                        FROM at_circuit_events
                        WHERE timestamp >= :since
                        ORDER BY timestamp ASC
                        """)
                .bind("since", Timestamp.from(since))
                .map((rs, ctx) -> mapEvent(rs))
                .list());
    }

    public Optional<CircuitEvent> findLatest() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, previous_tier, new_tier, debt_gdp_ratio, gdp, total_debt,
                               interest_multiplier, admin_initiated, details, timestamp
                        FROM at_circuit_events
                        ORDER BY timestamp DESC
                        LIMIT 1
                        """)
                .map((rs, ctx) -> mapEvent(rs))
                .findFirst());
    }
}
