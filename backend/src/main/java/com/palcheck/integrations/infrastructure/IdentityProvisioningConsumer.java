package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.IdentitySimulator;
import com.palcheck.integrations.application.IntegrationOperationRepository;
import com.palcheck.integrations.application.OperationConsumer;
import com.palcheck.integrations.application.SimulationOutcome;
import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.messaging.api.RetryPolicy;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code EmploymentStarted} integration events and drives the identity-provisioning simulation (Daniel's
 * Phase 3 brief, step 8). Only this class knows Kafka exists on the consuming side — {@link OperationConsumer}
 * carries every idempotent-processing rule, so a listener method here is a thin adapter, not another place the
 * rules could drift.
 */
@Component
class IdentityProvisioningConsumer extends OperationConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityProvisioningConsumer.class);

    private final IdentitySimulator simulator;
    private final JsonMapper json;

    IdentityProvisioningConsumer(IntegrationOperationRepository operations, IdentitySimulator simulator,
            JsonMapper json, Clock clock,
            @Value("${palcheck.messaging.retry-delays:PT5S,PT30S,PT2M,PT10M}") String retryDelays) {
        super(operations, clock, RetryPolicy.of(Arrays.stream(retryDelays.split(",")).map(String::strip)
                .map(Duration::parse).toList()));
        this.simulator = simulator;
        this.json = json;
    }

    @Override
    public String consumerName() {
        return "identity-provisioning";
    }

    @Override
    protected String operationKey(Map<String, Object> payload) {
        return "IDENTITY_PROVISION:" + payload.get("employmentId");
    }

    @Override
    protected SimulationOutcome attempt(Map<String, Object> payload) {
        return simulator.provision((String) payload.get("employmentId"));
    }

    @KafkaListener(topics = "${palcheck.messaging.topic:palcheck.workforce.events.v1}", groupId = "identity-provisioning")
    void onMessage(String raw) {
        IntegrationEvent event = json.readValue(raw, IntegrationEvent.class);
        LOG.atInfo().addKeyValue("operation", "integration.receive").addKeyValue("consumer", consumerName())
                .addKeyValue("eventId", event.eventId()).addKeyValue("eventType", event.eventType())
                .addKeyValue("correlationId", event.correlationId()).addKeyValue("causationId", event.causationId())
                .log("integration event received");
        handle(event);
    }
}
