package com.palcheck.audit.api;

import com.palcheck.shared.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A change to record. {@code entityType}/{@code entityId} identify what changed; {@code recordType}/{@code recordId}
 * identify the history it belongs to (for example a Person, for changes to their employment). Request and correlation
 * identifiers are attached automatically from the current request context.
 */
public record AuditRecord(TenantId tenantId, AuditActor actor, String command, UUID changeSetId,
        Operation operation, String entityType, UUID entityId, long entityVersion, String recordType, UUID recordId,
        List<AuditChange> changes, String reason) {

    public enum Operation { CREATE, UPDATE }

    public AuditRecord {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(changeSetId, "changeSetId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(recordId, "recordId");
        changes = List.copyOf(changes);
    }
}
