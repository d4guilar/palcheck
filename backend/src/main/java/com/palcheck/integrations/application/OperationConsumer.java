package com.palcheck.integrations.application;

import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.messaging.api.RetryPolicy;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.shared.AlreadySucceededException;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The idempotent-processing skeleton every Phase 3 consumer shares (ADR-0027): derive a business operation key from
 * the event's payload, never from its {@code eventId}; attempt at most once per key unless a scheduled retry or an
 * authorized replay explicitly asks for another attempt. The concrete {@code @KafkaListener} method lives in
 * {@code integrations.infrastructure} (only that layer may know Kafka exists) and calls {@link #handle}.
 */
public abstract class OperationConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OperationConsumer.class);

    private final IntegrationOperationRepository operations;
    private final Clock clock;
    private final RetryPolicy retryPolicy;

    protected OperationConsumer(IntegrationOperationRepository operations, Clock clock, RetryPolicy retryPolicy) {
        this.operations = operations;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
    }

    /** The consumer's own name, used both as the operation-key namespace and the log/UI label. */
    public abstract String consumerName();

    /** Derives the stable business operation identity from the event's payload (never from {@code eventId}). */
    protected abstract String operationKey(Map<String, Object> payload);

    /** Performs the actual side effect. Called at most once per key per attempt, by the listener, a retry, or a replay. */
    protected abstract SimulationOutcome attempt(Map<String, Object> payload);

    /** Called by the concrete listener with the deserialized envelope. */
    public final void handle(IntegrationEvent event) {
        TenantId tenantId = TenantId.of(UUID.fromString(event.tenantId()));
        String key = operationKey(event.payload());
        IntegrationOperation operation = operations.findOrCreate(tenantId, consumerName(), key,
                UUID.fromString(event.eventId()), event.payload(), Instant.now(clock));
        if (operation.status() != IntegrationOperation.Status.PENDING) {
            // SUCCEEDED: the duplicate-delivery guard (ADR-0027). FAILED_TRANSIENT/FAILED_PERMANENT/DEAD_LETTERED:
            // recovery is the scheduled retry's or a human replay's job, never a fresh event delivery's.
            LOG.atInfo().addKeyValue("operation", "integration.consume").addKeyValue("consumer", consumerName())
                    .addKeyValue("operationKey", key).addKeyValue("eventId", event.eventId())
                    .addKeyValue("result", "skipped-" + operation.status().name().toLowerCase())
                    .log("event acknowledged without a new attempt");
            return;
        }
        processNow(operation);
    }

    /** Re-attempts every due {@code FAILED_TRANSIENT} operation for this consumer. @return how many were attempted */
    public final int retryDue(int limit) {
        var due = operations.claimDueRetries(consumerName(), limit);
        due.forEach(this::processNow);
        return due.size();
    }

    /** Re-attempts one specific operation on request. Refuses a {@code SUCCEEDED} operation so replay cannot duplicate it (ADR-0027). */
    public final void replay(TenantId tenantId, String operationKey) {
        IntegrationOperation operation = operations.find(tenantId, consumerName(), operationKey)
                .orElseThrow(() -> new NotFoundException("Unknown integration operation."));
        if (operation.status() == IntegrationOperation.Status.SUCCEEDED) {
            throw new AlreadySucceededException(
                    "This operation already succeeded; replaying it would risk repeating the business action.");
        }
        processNow(operation);
    }

    private void processNow(IntegrationOperation operation) {
        SimulationOutcome outcome = attempt(operation.payload());
        Instant now = Instant.now(clock);
        switch (outcome.result()) {
            case SUCCEEDED -> {
                operations.markSucceeded(operation.tenantId(), consumerName(), operation.operationKey(), now);
                LOG.atInfo().addKeyValue("operation", "integration.attempt").addKeyValue("consumer", consumerName())
                        .addKeyValue("operationKey", operation.operationKey()).addKeyValue("result", "succeeded")
                        .log("downstream operation succeeded");
            }
            case PERMANENT_FAILURE -> {
                operations.markFailed(operation.tenantId(), consumerName(), operation.operationKey(),
                        IntegrationOperation.Status.FAILED_PERMANENT, outcome.detail(), null);
                LOG.atWarn().addKeyValue("operation", "integration.attempt").addKeyValue("consumer", consumerName())
                        .addKeyValue("operationKey", operation.operationKey()).addKeyValue("result", "failed-permanent")
                        .log("downstream operation failed permanently");
            }
            case TRANSIENT_FAILURE -> {
                var delay = retryPolicy.delayAfter(operation.attemptCount());
                if (delay.isPresent()) {
                    operations.markFailed(operation.tenantId(), consumerName(), operation.operationKey(),
                            IntegrationOperation.Status.FAILED_TRANSIENT, outcome.detail(), now.plus(delay.get()));
                    LOG.atInfo().addKeyValue("operation", "integration.attempt").addKeyValue("consumer", consumerName())
                            .addKeyValue("operationKey", operation.operationKey()).addKeyValue("result", "retry")
                            .addKeyValue("attempt", operation.attemptCount() + 1).log("downstream operation failed transiently");
                } else {
                    operations.markFailed(operation.tenantId(), consumerName(), operation.operationKey(),
                            IntegrationOperation.Status.DEAD_LETTERED, outcome.detail(), null);
                    LOG.atWarn().addKeyValue("operation", "integration.attempt").addKeyValue("consumer", consumerName())
                            .addKeyValue("operationKey", operation.operationKey()).addKeyValue("result", "dead-lettered")
                            .log("downstream operation exhausted its retries");
                }
            }
        }
    }
}
