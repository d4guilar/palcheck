package com.palcheck.integrations.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks both Phase 3 consumers' scheduled retries (ADR-0027 decision 4: retry timing is decoupled from Kafka
 * redelivery). Lives apart from {@link IdentityProvisioningConsumer}/{@link EquipmentProvisioningConsumer} so it —
 * and only it — can be disabled in tests the same way {@code OutboxPublisherJob} is, without also disabling their
 * {@code @KafkaListener} beans. Tests call {@code retryDue(...)} on a consumer directly instead, for determinism.
 */
@Configuration
@ConditionalOnProperty(name = "palcheck.integrations.retry-enabled", havingValue = "true", matchIfMissing = true)
class IntegrationRetryJob {

    @Component
    static class Retrier {

        private static final Logger LOG = LoggerFactory.getLogger(Retrier.class);
        private static final int RETRY_BATCH_SIZE = 20;

        private final IdentityProvisioningConsumer identity;
        private final EquipmentProvisioningConsumer equipment;

        Retrier(IdentityProvisioningConsumer identity, EquipmentProvisioningConsumer equipment) {
            this.identity = identity;
            this.equipment = equipment;
        }

        @Scheduled(fixedDelayString = "${palcheck.integrations.retry-poll-interval:PT5S}",
                initialDelayString = "${palcheck.integrations.retry-poll-initial-delay:PT5S}")
        void run() {
            int identityAttempted = identity.retryDue(RETRY_BATCH_SIZE);
            int equipmentAttempted = equipment.retryDue(RETRY_BATCH_SIZE);
            if (identityAttempted > 0 || equipmentAttempted > 0) {
                LOG.atInfo().addKeyValue("operation", "integration.retry")
                        .addKeyValue("identityAttempted", identityAttempted)
                        .addKeyValue("equipmentAttempted", equipmentAttempted).addKeyValue("result", "success")
                        .log("scheduled integration retries attempted");
            }
        }
    }
}
