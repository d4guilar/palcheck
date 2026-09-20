package com.palcheck.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A stored audit entry as presented to a reader. */
public record AuditEntryView(UUID id, UUID changeSetId, Instant occurredAt, AuditActor actor, String command,
        AuditRecord.Operation operation, String entityType, UUID entityId, long entityVersion,
        List<AuditChange> changes, String reason, String requestId) {
}
