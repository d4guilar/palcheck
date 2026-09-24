package com.palcheck.integrations.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One downstream business operation's current state (ADR-0027), keyed by {@code (tenantId, consumer, operationKey)}.
 * {@code operationKey} is derived from the workforce aggregate the operation is about, never from an event id — the
 * whole point of this row is that any number of deliveries or a human replay all land on the same one.
 */
public record IntegrationOperation(TenantId tenantId, String consumer, String operationKey, Status status,
        int attemptCount, String lastError, Instant nextAttemptAt, Instant completedAt, UUID sourceEventId,
        Map<String, Object> payload, Instant createdAt, Instant updatedAt) {

    public enum Status { PENDING, SUCCEEDED, FAILED_TRANSIENT, FAILED_PERMANENT, DEAD_LETTERED }

    public boolean needsAttention() {
        return status == Status.FAILED_PERMANENT || status == Status.DEAD_LETTERED;
    }
}
