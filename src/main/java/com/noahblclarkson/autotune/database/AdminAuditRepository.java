package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.AdminAuditEntry;
import com.noahblclarkson.autotune.model.AdminAuditEntry.ActionType;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class AdminAuditRepository {

    private final Jdbi jdbi;

    public AdminAuditRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    private static AdminAuditEntry mapEntry(ResultSet rs, StatementContext ctx) throws SQLException {
        String uuid = rs.getString("admin_uuid");
        return new AdminAuditEntry(
                rs.getLong("id"),
                rs.getTimestamp("timestamp").toInstant(),
                uuid,
                rs.getString("admin_name"),
                ActionType.valueOf(rs.getString("action_type")),
                rs.getString("target"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("details")
        );
    }

    public void insert(AdminAuditEntry entry) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO at_admin_audit_log
                            (admin_uuid, admin_name, action_type, target, old_value, new_value, details)
                        VALUES
                            (:adminUuid, :adminName, :actionType, :target, :oldValue, :newValue, :details)
                        """)
                        .bind("adminUuid", entry.adminUuid())
                        .bind("adminName", entry.adminName())
                        .bind("actionType", entry.actionType().name())
                        .bind("target", entry.target())
                        .bind("oldValue", entry.oldValue())
                        .bind("newValue", entry.newValue())
                        .bind("details", entry.details())
                        .execute());
    }

    public List<AdminAuditEntry> findRecent(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT * FROM at_admin_audit_log
                        ORDER BY timestamp DESC
                        LIMIT :limit
                        """)
                        .bind("limit", limit)
                        .map(AdminAuditRepository::mapEntry)
                        .list());
    }

    public List<AdminAuditEntry> findByAction(ActionType type, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT * FROM at_admin_audit_log
                        WHERE action_type = :actionType
                        ORDER BY timestamp DESC
                        LIMIT :limit
                        """)
                        .bind("actionType", type.name())
                        .bind("limit", limit)
                        .map(AdminAuditRepository::mapEntry)
                        .list());
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                        DELETE FROM at_admin_audit_log
                        WHERE timestamp < :cutoff
                        """)
                        .bind("cutoff", Timestamp.from(cutoff))
                        .execute());
    }
}
