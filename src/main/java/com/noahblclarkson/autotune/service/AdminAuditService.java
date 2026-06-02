package com.noahblclarkson.autotune.service;

import com.noahblclarkson.autotune.database.AdminAuditRepository;
import com.noahblclarkson.autotune.model.AdminAuditEntry;
import com.noahblclarkson.autotune.model.AdminAuditEntry.ActionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Records and retrieves admin actions that affect economy state. */
@Singleton
public class AdminAuditService {

    private final AdminAuditRepository repository;

    @Inject
    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Log an admin action.
     *
     * @param adminUuid  UUID of the admin (null for console)
     * @param adminName  display name (always present)
     * @param actionType type of action
     * @param target     affected entity (material name, event id, etc.)
     * @param oldValue   previous value (null for creates)
     * @param newValue   new value (null for deletes)
     * @param details    human-readable description
     */
    public void log(
            UUID adminUuid,
            String adminName,
            ActionType actionType,
            String target,
            String oldValue,
            String newValue,
            String details
    ) {
        AdminAuditEntry entry = new AdminAuditEntry(
                0,
                Instant.now(),
                adminUuid != null ? adminUuid.toString() : null,
                adminName,
                actionType,
                target,
                oldValue,
                newValue,
                details
        );
        repository.insert(entry);
    }

    /** Convenience: console action with no target. */
    public void logConsole(ActionType actionType, String details) {
        log(null, "CONSOLE", actionType, null, null, null, details);
    }

    /** Convenience: player action with a target material and old/new values. */
    public void logMaterial(
            UUID adminUuid,
            String adminName,
            ActionType actionType,
            String material,
            String oldValue,
            String newValue
    ) {
        log(adminUuid, adminName, actionType, material, oldValue, newValue, null);
    }

    /** Retrieve the most recent audit entries. */
    public List<AdminAuditEntry> getRecent(int limit) {
        return repository.findRecent(limit);
    }

    /** Prune entries older than the given cutoff. */
    public int pruneOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
