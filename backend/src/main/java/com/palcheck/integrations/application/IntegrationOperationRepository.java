package com.palcheck.integrations.application;

import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence for one downstream business operation's idempotent state (ADR-0027). */
public interface IntegrationOperationRepository {

    /** Inserts a new {@code PENDING} row, or returns the existing one unchanged — atomic, so a race never duplicates a row. */
    IntegrationOperation findOrCreate(TenantId tenantId, String consumer, String operationKey, UUID sourceEventId,
            Map<String, Object> payload, Instant now);

    Optional<IntegrationOperation> find(TenantId tenantId, String consumer, String operationKey);

    void markSucceeded(TenantId tenantId, String consumer, String operationKey, Instant completedAt);

    /** Increments the attempt count; the caller decides {@code FAILED_TRANSIENT} vs. {@code DEAD_LETTERED} by passing the next status. */
    void markFailed(TenantId tenantId, String consumer, String operationKey, IntegrationOperation.Status status,
            String error, Instant nextAttemptAt);

    /** Rows due for a scheduled retry, leased (see {@code JdbcIntegrationOperationRepository}) so two pollers never double-attempt one. */
    List<IntegrationOperation> claimDueRetries(String consumer, int limit);

    /** Operations for the tenant, newest first, optionally narrowed to one consumer and/or one status. */
    PageResult<IntegrationOperation> list(TenantId tenantId, String consumer, IntegrationOperation.Status status,
            int page, int size);
}
