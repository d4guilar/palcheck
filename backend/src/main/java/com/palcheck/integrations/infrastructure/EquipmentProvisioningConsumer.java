package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.EquipmentSimulator;
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
 * Consumes {@code EmploymentStarted} integration events and drives the equipment-provisioning simulation (Daniel's
 * Phase 3 brief, step 9). Same shape as {@link IdentityProvisioningConsumer} deliberately: two consumers of the
 * same event, each with its own consumer group so Kafka delivers a copy to both, each idempotent on its own key.
 */
@Component
class EquipmentProvisioningConsumer extends OperationConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EquipmentProvisioningConsumer.class);

    private final EquipmentSimulator simulator;
    private final JsonMapper json;

    EquipmentProvisioningConsumer(IntegrationOperationRepository operations, EquipmentSimulator simulator,
            JsonMapper json, Clock clock,
            @Value("${palcheck.messaging.retry-delays:PT5S,PT30S,PT2M,PT10M}") String retryDelays) {
        super(operations, clock, RetryPolicy.of(Arrays.stream(retryDelays.split(",")).map(String::strip)
                .map(Duration::parse).toList()));
        this.simulator = simulator;
        this.json = json;
    }

    @Override
    public String consumerName() {
        return "equipment-provisioning";
    }

    @Override
    protected String operationKey(Map<String, Object> payload) {
        return "EQUIPMENT_PROVISION:" + payload.get("assignmentId");
    }

    @Override
    protected SimulationOutcome attempt(Map<String, Object> payload) {
        return simulator.provision((String) payload.get("assignmentId"));
    }

    @KafkaListener(topics = "${palcheck.messaging.topic:palcheck.workforce.events.v1}", groupId = "equipment-provisioning")
    void onMessage(String raw) {
        IntegrationEvent event = json.readValue(raw, IntegrationEvent.class);
        LOG.atInfo().addKeyValue("operation", "integration.receive").addKeyValue("consumer", consumerName())
                .addKeyValue("eventId", event.eventId()).addKeyValue("eventType", event.eventType())
                .addKeyValue("correlationId", event.correlationId()).addKeyValue("causationId", event.causationId())
                .log("integration event received");
        handle(event);
    }
}
