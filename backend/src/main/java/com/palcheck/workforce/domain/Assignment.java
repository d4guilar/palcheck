package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The organizational and job placement of an Employment. {@code effectiveTo == null} marks the current assignment.
 * Phase 1 edits the current assignment in place as a correction; a future job change closes it and opens a new one.
 *
 * <p>{@code ownedBySystem} follows the same rule as {@link Employment#ownedBySystem()} (ADR-0025): null for
 * PalCheck-owned, set for one a synchronization wrote or last touched.
 */
public record Assignment(UUID id, TenantId tenantId, UUID employmentId, LocalDate effectiveFrom,
        LocalDate effectiveTo, AssignmentData data, long version, Instant createdAt, Instant updatedAt,
        String ownedBySystem, Instant lastSyncedAt) {

    public static Assignment createCurrent(TenantId tenantId, UUID employmentId, LocalDate effectiveFrom,
            AssignmentData data, Instant now) {
        return new Assignment(UUID.randomUUID(), tenantId, employmentId, effectiveFrom, null, data, 1L, now, now,
                null, null);
    }

    public static Assignment createExternalCurrent(TenantId tenantId, UUID employmentId, LocalDate effectiveFrom,
            AssignmentData data, String ownedBySystem, Instant now) {
        return new Assignment(UUID.randomUUID(), tenantId, employmentId, effectiveFrom, null, data, 1L, now, now,
                Objects.requireNonNull(ownedBySystem, "ownedBySystem"), now);
    }

    public boolean isExternallyOwned() {
        return ownedBySystem != null;
    }
}
