package com.palcheck.messaging.application;

import com.palcheck.messaging.api.OutboxEventDraft;
import com.palcheck.messaging.api.OutboxWriter;
import com.palcheck.messaging.domain.OutboxRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The only implementation of {@link OutboxWriter}; requires the caller's own transaction (ADR-0026). */
@Service
class OutboxWriterService implements OutboxWriter {

    private final OutboxRepository repository;
    private final Clock clock;

    OutboxWriterService(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxEventDraft draft) {
        Instant now = Instant.now(clock);
        repository.insert(new OutboxRecord(UUID.randomUUID(), draft.tenantId(), UUID.randomUUID(), draft.eventType(),
                draft.schemaVersion(), draft.aggregateType(), draft.aggregateId(), now, draft.correlationId(),
                draft.causationId(), draft.payload(), OutboxRecord.Status.PENDING, 0, now, null, now));
    }
}
