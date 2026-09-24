package com.palcheck.messaging.application;

import com.palcheck.messaging.api.IntegrationEvent;

/** The only thing that knows how to reach the broker. Everything above this port is broker-agnostic (ADR-0026). */
public interface BrokerPublisher {

    /** @throws RuntimeException if the send did not succeed or its outcome is ambiguous; the caller retries. */
    void publish(String topic, String key, IntegrationEvent event);
}
