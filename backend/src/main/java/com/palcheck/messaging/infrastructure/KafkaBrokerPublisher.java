package com.palcheck.messaging.infrastructure;

import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.messaging.application.BrokerPublisher;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only class that knows Kafka is the broker (ADR-0026). {@code publish} blocks briefly for the broker's
 * acknowledgement so the caller can tell success from failure synchronously; a timeout or any other exception here
 * is exactly the "ambiguous outcome" the publisher retries under at-least-once semantics.
 */
@Component
class KafkaBrokerPublisher implements BrokerPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    KafkaBrokerPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(String topic, String key, IntegrationEvent event) {
        try {
            kafka.send(topic, key, json.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new BrokerPublishException("Failed to publish " + event.eventType() + " " + event.eventId(), e);
        }
    }

    static final class BrokerPublishException extends RuntimeException {
        BrokerPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
