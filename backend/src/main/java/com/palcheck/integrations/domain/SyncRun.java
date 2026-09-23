package com.palcheck.integrations.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * One synchronization attempt. Written once at start ({@code RUNNING}) and once at completion with final counts
 * (ADR-0025 decision 6: never mid-run, and never in the same transaction as any worker's write).
 */
public record SyncRun(UUID id, TenantId tenantId, UUID systemId, Instant startedAt, Instant completedAt,
        Status status, int recordsSeen, int createdCount, int updatedCount, int unchangedCount, int failedCount,
        UUID triggeredBy) {

    public enum Status { RUNNING, SUCCEEDED, SUCCEEDED_WITH_FAILURES, FAILED }

    public static SyncRun start(TenantId tenantId, UUID systemId, UUID triggeredBy, Instant now) {
        return new SyncRun(UUID.randomUUID(), tenantId, systemId, now, null, Status.RUNNING, 0, 0, 0, 0, 0,
                triggeredBy);
    }

    public SyncRun completed(int recordsSeen, int created, int updated, int unchanged, int failed, Instant now) {
        Status finalStatus = failed == 0 ? Status.SUCCEEDED
                : (created + updated + unchanged > 0 ? Status.SUCCEEDED_WITH_FAILURES : Status.FAILED);
        return new SyncRun(id, tenantId, systemId, startedAt, now, finalStatus, recordsSeen, created, updated,
                unchanged, failed, triggeredBy);
    }
}
