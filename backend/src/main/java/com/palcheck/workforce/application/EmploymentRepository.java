package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.Employment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface EmploymentRepository {

    void insert(Employment employment);

    Optional<Employment> find(TenantId tenantId, UUID employmentId);

    /** A person's employments, most recent start first. */
    List<Employment> findByPerson(TenantId tenantId, UUID personId);

    OptionalLong currentVersion(TenantId tenantId, UUID employmentId);

    /** Version-guarded update; see {@link PersonRepository#update}. */
    boolean update(Employment next, long expectedVersion);

    /** The employment a synchronization has already imported for this external worker, if any (Phase 2, ADR-0025). */
    Optional<Employment> findByExternalIdentity(TenantId tenantId, String ownedBySystem, String externalWorkerId);

    /** Refreshes only {@code lastSyncedAt} on an unchanged re-sync; no version bump, no audit entry (ADR-0025). */
    void touchLastSyncedAt(TenantId tenantId, UUID employmentId, Instant syncedAt);
}
