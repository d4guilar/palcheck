package com.palcheck.messaging.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The durable row behind one appended {@code OutboxEventDraft} (ADR-0026). {@code eventId} is generated here, once,
 * at append time, and stays the same across every publish attempt for this row — only a genuinely new outbox row
 * (a fresh business fact) gets a new {@code eventId}.
 */
public record OutboxRecord(UUID id, TenantId tenantId, UUID eventId, String eventType, String schemaVersion,
        String aggregateType, String aggregateId, Instant occurredAt, String correlationId, String causationId,
        Map<String, Object> payload, Status status, int attemptCount, Instant nextAttemptAt, Instant publishedAt,
        Instant createdAt) {

    public enum Status { PENDING, PUBLISHED, FAILED }
}
