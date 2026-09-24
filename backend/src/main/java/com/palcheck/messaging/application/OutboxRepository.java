package com.palcheck.messaging.application;

import com.palcheck.messaging.domain.OutboxRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void insert(OutboxRecord record);

    /** Up to {@code limit} rows eligible to publish now, claimed with {@code FOR UPDATE SKIP LOCKED} (ADR-0026). */
    List<OutboxRecord> claimEligible(int limit);

    void markPublished(UUID id, Instant publishedAt);

    /** Increments the attempt count and schedules the next try, or marks {@code FAILED} once {@code maxAttempts} is reached. */
    void recordFailure(UUID id, String error, Instant nextAttemptAt, int maxAttempts);
}
