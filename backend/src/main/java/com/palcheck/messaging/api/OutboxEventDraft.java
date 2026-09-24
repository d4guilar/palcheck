package com.palcheck.messaging.api;

import com.palcheck.shared.TenantId;
import java.util.Map;
import java.util.Objects;

/**
 * What a domain module supplies to durably record an integration-relevant fact, in the same transaction as the
 * business write it describes (ADR-0026). {@code payload} should carry identifiers, not a full record snapshot
 * (ADR-0028) — this type does not enforce that; the caller's judgment does, the same way {@code audit.api} trusts
 * callers to record only what is meant to be historical.
 */
public record OutboxEventDraft(TenantId tenantId, String eventType, String schemaVersion, String aggregateType,
        String aggregateId, String correlationId, String causationId, Map<String, Object> payload) {

    public OutboxEventDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }
}
