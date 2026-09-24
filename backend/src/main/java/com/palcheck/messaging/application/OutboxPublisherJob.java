package com.palcheck.messaging.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled tick over {@link OutboxPublishingService} (ADR-0026). Disabled in tests, where
 * {@code publishEligible()} is called directly on the service for determinism (same pattern as
 * {@code IdempotencyPurgeJob} over {@code IdempotencyService}).
 */
@Configuration
@ConditionalOnProperty(name = "palcheck.messaging.publisher-enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisherJob {

    @Component
    static class Ticker {

        private final OutboxPublishingService service;

        Ticker(OutboxPublishingService service) {
            this.service = service;
        }

        @Scheduled(fixedDelayString = "${palcheck.messaging.publisher-poll-interval:PT2S}",
                initialDelayString = "${palcheck.messaging.publisher-initial-delay:PT2S}")
        void run() {
            service.publishEligible();
        }
    }
}
