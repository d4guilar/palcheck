package com.palcheck.messaging.application;

import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.messaging.api.RetryPolicy;
import com.palcheck.messaging.domain.OutboxRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Claims eligible outbox rows and publishes them (ADR-0026). Delivery is at-least-once: a send that succeeds at the
 * broker but whose acknowledgement is lost locally looks identical to a failed send, so this retries it, which
 * can publish the same {@code eventId} twice. {@link OutboxPublisherJob} is the thin, disable-able scheduled tick
 * over this service (same shape as {@code IdempotencyPurgeJob} over {@code IdempotencyService}); tests call
 * {@link #publishEligible()} directly for determinism instead of waiting on the scheduler.
 */
@Service
public class OutboxPublishingService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublishingService.class);
    private static final int CLAIM_BATCH_SIZE = 20;

    private final OutboxRepository repository;
    private final BrokerPublisher broker;
    private final Clock clock;
    private final String topic;
    private final RetryPolicy retryPolicy;

    OutboxPublishingService(OutboxRepository repository, BrokerPublisher broker, Clock clock,
            @Value("${palcheck.messaging.topic:palcheck.workforce.events.v1}") String topic,
            @Value("${palcheck.messaging.retry-delays:PT5S,PT30S,PT2M,PT10M}") String retryDelays) {
        this.repository = repository;
        this.broker = broker;
        this.clock = clock;
        this.topic = topic;
        this.retryPolicy = RetryPolicy.of(Arrays.stream(retryDelays.split(",")).map(String::strip)
                .map(Duration::parse).toList());
    }

    /** @return how many rows were attempted, so tests and the scheduled tick share one code path */
    public int publishEligible() {
        List<OutboxRecord> claimed = repository.claimEligible(CLAIM_BATCH_SIZE);
        for (OutboxRecord record : claimed) {
            publishOne(record);
        }
        return claimed.size();
    }

    private void publishOne(OutboxRecord record) {
        IntegrationEvent event = new IntegrationEvent(record.eventId().toString(), record.eventType(),
                record.schemaVersion(), record.tenantId().value().toString(), record.aggregateType(),
                record.aggregateId(), record.occurredAt(), record.correlationId(), record.causationId(),
                "palcheck.workforce", record.payload());
        try {
            broker.publish(topic, record.tenantId().value() + ":" + record.aggregateId(), event);
            repository.markPublished(record.id(), Instant.now(clock));
            LOG.atInfo().addKeyValue("operation", "outbox.publish").addKeyValue("eventId", record.eventId())
                    .addKeyValue("eventType", record.eventType()).addKeyValue("result", "success")
                    .log("outbox event published");
        } catch (RuntimeException e) {
            Instant next = Instant.now(clock)
                    .plus(retryPolicy.delayAfter(record.attemptCount()).orElse(Duration.ZERO));
            repository.recordFailure(record.id(), e.getClass().getSimpleName(), next, retryPolicy.maxAttempts());
            LOG.atWarn().addKeyValue("operation", "outbox.publish").addKeyValue("eventId", record.eventId())
                    .addKeyValue("eventType", record.eventType()).addKeyValue("result", "retry")
                    .addKeyValue("attempt", record.attemptCount() + 1).log("outbox publish failed");
        }
    }
}
