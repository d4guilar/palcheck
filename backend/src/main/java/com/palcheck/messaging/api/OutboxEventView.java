package com.palcheck.messaging.api;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A published-or-pending outbox row as the Event Explorer reads it (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 7):
 * envelope metadata plus publication status, never the internal {@code messaging.domain.OutboxRecord} type — the
 * same reason {@code audit.api} exposes {@code AuditEntryView} rather than its own storage record.
 */
public record OutboxEventView(UUID id, TenantId tenantId, UUID eventId, String eventType, String schemaVersion,
        String aggregateType, String aggregateId, Instant occurredAt, String correlationId, String causationId,
        Map<String, Object> payload, String status, int attemptCount, Instant publishedAt, Instant createdAt) {
}
