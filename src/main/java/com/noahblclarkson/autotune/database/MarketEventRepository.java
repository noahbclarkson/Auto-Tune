package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.MarketEvent;
import com.noahblclarkson.autotune.model.MarketEvent.EventType;
import com.noahblclarkson.autotune.model.MarketEvent.Status;
import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBI repository for market event persistence.
 */
public class MarketEventRepository {

    private final Jdbi jdbi;

    public MarketEventRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public void insert(MarketEvent event) {
        jdbi.useHandle(handle -> {
            String materialsStr = event.materials() != null ? String.join(",", event.materials()) : null;
            handle.createUpdate(
                    """
                    INSERT INTO at_market_events
                    (id, name, event_type, materials, price_multiplier, starts_at, ends_at,
                     start_message, end_message, created_by, status, cron_expression, tick_count)
                    VALUES
                    (:id, :name, :eventType, :materials, :priceMultiplier, :startsAt, :endsAt,
                     :startMessage, :endMessage, :createdBy, :status, :cronExpression, :tickCount)
                    """)
                    .bind("id", event.id().toString())
                    .bind("name", event.name())
                    .bind("eventType", event.type().name())
                    .bind("materials", materialsStr)
                    .bind("priceMultiplier", event.priceMultiplier())
                    .bind("startsAt", Timestamp.from(event.startsAt()))
                    .bind("endsAt", Timestamp.from(event.endsAt()))
                    .bind("startMessage", event.startMessage())
                    .bind("endMessage", event.endMessage())
                    .bind("createdBy", event.createdBy())
                    .bind("status", event.status().name())
                    .bind("cronExpression", event.cronExpression())
                    .bind("tickCount", event.tickCount())
                    .execute();
        });
    }

    public void updateStatusAndTicks(UUID id, String status, int tickCount) {
        jdbi.useHandle(handle ->
            handle.createUpdate(
                    "UPDATE at_market_events SET status = :status, tick_count = :tickCount WHERE id = :id")
                    .bind("id", id.toString())
                    .bind("status", status)
                    .bind("tickCount", tickCount)
                    .execute()
        );
    }

    public void updateStatus(UUID id, String status) {
        jdbi.useHandle(handle ->
            handle.createUpdate("UPDATE at_market_events SET status = :status WHERE id = :id")
                    .bind("id", id.toString())
                    .bind("status", status)
                    .execute()
        );
    }

    public void delete(UUID id) {
        jdbi.useHandle(handle ->
            handle.createUpdate("DELETE FROM at_market_events WHERE id = :id")
                    .bind("id", id.toString())
                    .execute()
        );
    }

    public Optional<MarketEvent> findById(UUID id) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM at_market_events WHERE id = :id")
                    .bind("id", id.toString())
                    .map((rs, ctx) -> mapEvent(rs))
                    .findFirst()
        );
    }

    public List<MarketEvent> findByStatus(String status) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM at_market_events WHERE status = :status ORDER BY starts_at ASC")
                    .bind("status", status)
                    .map((rs, ctx) -> mapEvent(rs))
                    .list()
        );
    }

    public List<MarketEvent> findActive() {
        return findByStatus(Status.ACTIVE.name());
    }

    public List<MarketEvent> findScheduledReadyToStart(Instant now) {
        return jdbi.withHandle(handle ->
            handle.createQuery(
                    "SELECT * FROM at_market_events WHERE status = :status AND starts_at <= :now ORDER BY starts_at ASC")
                    .bind("status", Status.SCHEDULED.name())
                    .bind("now", Timestamp.from(now))
                    .map((rs, ctx) -> mapEvent(rs))
                    .list()
        );
    }

    public List<MarketEvent> findActiveExpired(Instant now) {
        return jdbi.withHandle(handle ->
            handle.createQuery(
                    "SELECT * FROM at_market_events WHERE status = :status AND ends_at <= :now ORDER BY ends_at ASC")
                    .bind("status", Status.ACTIVE.name())
                    .bind("now", Timestamp.from(now))
                    .map((rs, ctx) -> mapEvent(rs))
                    .list()
        );
    }

    public List<MarketEvent> findAll() {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM at_market_events ORDER BY created_at DESC")
                    .map((rs, ctx) -> mapEvent(rs))
                    .list()
        );
    }

    private static MarketEvent mapEvent(ResultSet rs) throws SQLException {
        try {
            String id = rs.getString("id");
            String name = rs.getString("name");
            EventType type = EventType.valueOf(rs.getString("event_type"));
            String materialsStr = rs.getString("materials");
            List<String> materials = materialsStr != null && !materialsStr.isBlank()
                    ? Arrays.asList(materialsStr.split(","))
                    : List.of();
            double multiplier = rs.getDouble("price_multiplier");
            Timestamp startsTs = rs.getTimestamp("starts_at");
            Instant startsAt = startsTs != null ? startsTs.toInstant() : Instant.now();
            Timestamp endsTs = rs.getTimestamp("ends_at");
            Instant endsAt = endsTs != null ? endsTs.toInstant() : Instant.now();
            String startMsg = rs.getString("start_message");
            String endMsg = rs.getString("end_message");
            String createdBy = rs.getString("created_by");
            Status status = Status.valueOf(rs.getString("status"));
            String cronExpr = rs.getString("cron_expression");
            int tickCount = rs.getInt("tick_count");

            return new MarketEvent(
                    UUID.fromString(id),
                    name,
                    type,
                    materials,
                    multiplier,
                    startsAt,
                    endsAt,
                    startMsg,
                    endMsg,
                    createdBy,
                    status,
                    cronExpr,
                    tickCount
            );
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to map market event", e);
        }
    }
}
