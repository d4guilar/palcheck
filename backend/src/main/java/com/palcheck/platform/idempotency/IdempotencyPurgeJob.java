package com.palcheck.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes receipts past their guaranteed retry window, not business effects. Disabled in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "palcheck.idempotency.purge-enabled", havingValue = "true", matchIfMissing = true)
class IdempotencyPurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyPurgeJob.class);

    @Component
    static class Purger {

        private final IdempotencyService idempotency;

        Purger(IdempotencyService idempotency) {
            this.idempotency = idempotency;
        }

        @Scheduled(fixedDelayString = "${palcheck.idempotency.purge-interval:PT1H}",
                initialDelayString = "${palcheck.idempotency.purge-initial-delay:PT5M}")
        void run() {
            int removed = idempotency.purgeExpired();
            LOG.atInfo().addKeyValue("operation", "idempotency.purge").addKeyValue("removed", removed)
                    .addKeyValue("result", "success").log("expired idempotency receipts removed");
        }
    }
}
