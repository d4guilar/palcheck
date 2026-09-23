package com.palcheck.integrations.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/** One worker's outcome within a {@link SyncRun}; never the worker's payload, only enough to explain the outcome. */
public record SyncItem(UUID id, UUID syncRunId, TenantId tenantId, String externalWorkerId, Status status,
        Action action, SyncFailureReason failureReason, String failureDetail, UUID targetPersonId,
        UUID targetEmploymentId, Instant occurredAt) {

    public enum Status { SUCCEEDED, FAILED }

    public enum Action { CREATED, UPDATED, UNCHANGED, MANAGER_LINKED }

    public static SyncItem succeeded(UUID syncRunId, TenantId tenantId, String externalWorkerId, Action action,
            UUID targetPersonId, UUID targetEmploymentId, Instant now) {
        return new SyncItem(UUID.randomUUID(), syncRunId, tenantId, externalWorkerId, Status.SUCCEEDED, action, null,
                null, targetPersonId, targetEmploymentId, now);
    }

    public static SyncItem failed(UUID syncRunId, TenantId tenantId, String externalWorkerId,
            SyncFailureReason reason, String detail, Instant now) {
        return new SyncItem(UUID.randomUUID(), syncRunId, tenantId, externalWorkerId, Status.FAILED, null, reason,
                detail, null, null, now);
    }
}
