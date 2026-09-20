package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/** A human being known to a tenant. Identity only; jobs live in Employment and Assignment. */
public record Person(UUID id, TenantId tenantId, PersonData data, long version, Instant createdAt,
        Instant updatedAt) {

    public static Person create(TenantId tenantId, PersonData data, Instant now) {
        return new Person(UUID.randomUUID(), tenantId, data, 1L, now, now);
    }
}
