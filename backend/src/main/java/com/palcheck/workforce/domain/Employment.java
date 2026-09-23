package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The relationship between a Person and a Legal Entity over time. A rehire is a new Employment.
 *
 * <p>{@code ownedBySystem} and {@code externalWorkerId} are null for a PalCheck-owned employment (every employment
 * created before Phase 2, and every one created by a human) and both set together for one a synchronization owns
 * (ADR-0025). {@code lastSyncedAt} is set only for the latter and refreshed on every successful sync pass.
 */
public record Employment(UUID id, TenantId tenantId, UUID personId, UUID legalEntityId, EmploymentData data,
        long version, Instant createdAt, Instant updatedAt, String ownedBySystem, String externalWorkerId,
        Instant lastSyncedAt) {

    public static Employment create(TenantId tenantId, UUID personId, UUID legalEntityId, EmploymentData data,
            Instant now) {
        return new Employment(UUID.randomUUID(), tenantId, personId, legalEntityId, data, 1L, now, now, null, null,
                null);
    }

    public static Employment createExternal(TenantId tenantId, UUID personId, UUID legalEntityId,
            EmploymentData data, String ownedBySystem, String externalWorkerId, Instant now) {
        return new Employment(UUID.randomUUID(), tenantId, personId, legalEntityId, data, 1L, now, now,
                Objects.requireNonNull(ownedBySystem, "ownedBySystem"), Objects.requireNonNull(externalWorkerId,
                        "externalWorkerId"), now);
    }

    public boolean isExternallyOwned() {
        return ownedBySystem != null;
    }
}
