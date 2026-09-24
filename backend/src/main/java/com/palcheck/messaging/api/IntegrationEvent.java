package com.palcheck.messaging.api;

import java.time.Instant;
import java.util.Map;

/**
 * The stable envelope every published integration event uses (ADR-0028). {@code eventId} is transport identity
 * only — unique per publish attempt, never a business-operation identity (ADR-0027); a consumer that used it as one
 * would treat a harmless republish as a brand new business fact.
 */
public record IntegrationEvent(String eventId, String eventType, String schemaVersion, String tenantId,
        String aggregateType, String aggregateId, Instant occurredAt, String correlationId, String causationId,
        String source, Map<String, Object> payload) {
}
