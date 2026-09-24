package com.palcheck.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.palcheck.integrations.application.IntegrationOperationRepository;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.messaging.application.OutboxPublishingService;
import com.palcheck.shared.TenantId;
import com.palcheck.support.WorkforceIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * The one true end-to-end proof, with a real broker (Daniel's Phase 3 brief; docs/PHASE_3_IMPLEMENTATION_PLAN.md
 * section 10): a committed workforce change becomes a durable outbox row, gets published to a real, in-process
 * Kafka broker (real message ordering, partitioning and consumer-group semantics — {@code spring-kafka-test}'s
 * {@code EmbeddedKafkaBroker}, ADR-0026), and both {@code @KafkaListener} consumers pick it up independently and
 * complete. Every other Phase 3 consumer/operation guarantee is proven, without Kafka, in
 * {@code IntegrationOperationsIT}; only the wire itself needs proving here.
 */
@EmbeddedKafka(partitions = 1, topics = "palcheck.workforce.events.v1")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // The base test profile disables listener auto-startup so unrelated tests never dial a broker that isn't
        // running (application-test.yml); this is the one test class where a broker really is running.
        "spring.kafka.listener.auto-startup=true"
})
class EventPipelineIT extends WorkforceIntegrationTest {

    @Autowired
    private OutboxPublishingService publisher;
    @Autowired
    private IntegrationOperationRepository operations;

    @Test
    void aWorkforceChangeFlowsThroughARealBrokerToBothDownstreamConsumers() throws Exception {
        var w = world("event-pipeline");
        var ids = hire(w, "Fabiola", "Rojas");

        // Deterministic instead of waiting for the (disabled-in-tests) scheduled tick; everything after this is a
        // real, asynchronous hand-off through the broker to two independent, real @KafkaListener containers.
        publisher.publishEligible();

        awaitSucceeded(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + ids.employmentId());
        awaitSucceeded(w.tenantId(), "equipment-provisioning", "EQUIPMENT_PROVISION:" + ids.assignmentId());

        String outboxStatus = jdbc.sql("SELECT status FROM messaging.outbox_event WHERE tenant_id = :tenant AND aggregate_id = :aggId")
                .param("tenant", w.tenantId().value()).param("aggId", ids.employmentId())
                .query(String.class).single();
        assertThat(outboxStatus).isEqualTo("PUBLISHED");
    }

    private void awaitSucceeded(TenantId tenantId, String consumer, String operationKey) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            var op = operations.find(tenantId, consumer, operationKey);
            if (op.isPresent()) {
                if (op.get().status() == IntegrationOperation.Status.SUCCEEDED) {
                    return;
                }
                assertThat(op.get().status()).isIn(IntegrationOperation.Status.PENDING, IntegrationOperation.Status.SUCCEEDED);
            }
            Thread.sleep(200);
        }
        fail("Consumer " + consumer + " never reached SUCCEEDED for " + operationKey + " within the deadline.");
    }
}
