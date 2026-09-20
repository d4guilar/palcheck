package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/** The relationship between a Person and a Legal Entity over time. A rehire is a new Employment. */
public record Employment(UUID id, TenantId tenantId, UUID personId, UUID legalEntityId, EmploymentData data,
        long version, Instant createdAt, Instant updatedAt) {

    public static Employment create(TenantId tenantId, UUID personId, UUID legalEntityId, EmploymentData data,
            Instant now) {
        return new Employment(UUID.randomUUID(), tenantId, personId, legalEntityId, data, 1L, now, now);
    }
}
