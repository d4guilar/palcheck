package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.Assignment;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface AssignmentRepository {

    /** Serializes reporting-graph changes in one tenant for the lifetime of the business transaction. */
    void lockReportingGraph(com.palcheck.shared.TenantId tenantId);

    void touchLastSyncedAt(TenantId tenantId, UUID assignmentId, java.time.Instant when);

    void insert(Assignment assignment);

    Optional<Assignment> find(TenantId tenantId, UUID assignmentId);

    Optional<Assignment> findCurrent(TenantId tenantId, UUID employmentId);

    /** The current assignment of each given employment, keyed by employment id. */
    Map<UUID, Assignment> findCurrent(TenantId tenantId, Collection<UUID> employmentIds);

    OptionalLong currentVersion(TenantId tenantId, UUID assignmentId);

    boolean update(Assignment next, long expectedVersion);

    /** Sets only the manager link, version-guarded (pass 2 of a Phase 2 sync; see {@code EditEmploymentService}). */
    boolean updateManager(TenantId tenantId, UUID assignmentId, UUID managerEmploymentId, long expectedVersion,
            java.time.Instant now);

    /**
     * True if following current manager links upward from {@code startEmploymentId} reaches {@code targetEmploymentId}
     * (or starts there). Used to reject a manager assignment that would create a reporting cycle.
     */
    boolean reportingChainReaches(TenantId tenantId, UUID startEmploymentId, UUID targetEmploymentId);

    /** The earliest {@code effective_from} among an employment's assignments, if any. */
    Optional<java.time.LocalDate> earliestEffectiveFrom(TenantId tenantId, UUID employmentId);
}
