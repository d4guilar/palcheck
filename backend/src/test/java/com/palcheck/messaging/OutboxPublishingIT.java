package com.palcheck.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.messaging.application.BrokerPublisher;
import com.palcheck.messaging.application.OutboxPublishingService;
import com.palcheck.support.Api;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The transactional outbox's own guarantees, independent of any real broker (ADR-0026): a business write is never
 * silently missing its event, and a broker that keeps failing eventually stops being retried forever. Uses a fake
 * {@link BrokerPublisher} — a full asynchronous pipeline proof, with a real broker, is
 * {@code com.palcheck.integrations.EventPipelineIT}.
 */
@Import(OutboxPublishingIT.FakeBrokerConfig.class)
class OutboxPublishingIT extends WorkforceIntegrationTest {

    @Autowired
    private OutboxPublishingService publisher;
    @Autowired
    private FakeBrokerPublisher broker;

    @Test
    void hiringSomeoneCommitsExactlyOneOutboxRowInTheSameTransactionAsTheBusinessWrite() throws Exception {
        var w = world("outbox-atomicity");
        MvcResult created = api.post(w.tenant().admin(), "/api/people",
                        Api.createBody(w, "Ana", "Solano", Api.uniqueEmail("ana")), Api.newKey())
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = created.getResponse().getHeader("X-Request-Id");
        String correlationId = created.getResponse().getHeader("X-Correlation-Id");
        String personId = com.jayway.jsonpath.JsonPath.parse(created.getResponse().getContentAsString()).read("$.id");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        String employmentId = employments.read("$.items[0].id");

        assertThat(count("SELECT count(*) FROM messaging.outbox_event WHERE tenant_id = ? AND aggregate_id = ?",
                w.tenantId().value(), employmentId)).isEqualTo(1);
        var fields = jdbc.sql("""
                        SELECT event_type, status, aggregate_type, correlation_id, causation_id
                          FROM messaging.outbox_event WHERE tenant_id = :tenant AND aggregate_id = :employmentId
                        """)
                .param("tenant", w.tenantId().value()).param("employmentId", employmentId)
                .query((rs, n) -> new String[]{rs.getString("event_type"), rs.getString("status"),
                        rs.getString("aggregate_type"), rs.getString("correlation_id"), rs.getString("causation_id")})
                .single();
        assertThat(fields[0]).isEqualTo("EmploymentStarted");
        assertThat(fields[1]).isEqualTo("PENDING");
        assertThat(fields[2]).isEqualTo("Employment");
        assertThat(fields[3]).isEqualTo(correlationId);
        assertThat(fields[4]).isEqualTo(requestId);
    }

    @Test
    void aBrokerThatKeepsFailingIsNotRetriedForeverAfterItsAttemptsAreExhausted() throws Exception {
        var w = world("outbox-give-up");
        var ids = hire(w, "Beto", "Chaves");
        UUID rowId = jdbc.sql("SELECT id FROM messaging.outbox_event WHERE tenant_id = :tenant AND aggregate_id = :aggId")
                .param("tenant", w.tenantId().value()).param("aggId", ids.employmentId())
                .query((rs, n) -> rs.getObject("id", UUID.class)).single();

        broker.failNextTimes(1);
        int attempted = publisher.publishEligible();
        assertThat(attempted).isGreaterThanOrEqualTo(1);
        assertThat(outboxStatus(rowId)).isEqualTo("PENDING");
        assertThat(attemptCount(rowId)).isEqualTo(1);

        // Force this row to its last configured attempt and make it due again, without waiting on real backoff.
        jdbc.sql("UPDATE messaging.outbox_event SET attempt_count = 4, next_attempt_at = now() - interval '1 second' WHERE id = :id")
                .param("id", rowId).update();
        broker.failNextTimes(1);
        publisher.publishEligible();

        assertThat(outboxStatus(rowId)).isEqualTo("FAILED");
    }

    @Test
    void publishingASucceedingRowMarksItPublished() throws Exception {
        var w = world("outbox-publish-success");
        var ids = hire(w, "Carla", "Mendez");

        int attempted = publisher.publishEligible();

        assertThat(attempted).isGreaterThanOrEqualTo(1);
        String status = jdbc.sql("SELECT status FROM messaging.outbox_event WHERE tenant_id = :tenant AND aggregate_id = :aggId")
                .param("tenant", w.tenantId().value()).param("aggId", ids.employmentId())
                .query(String.class).single();
        assertThat(status).isEqualTo("PUBLISHED");
        assertThat(broker.published()).anySatisfy(e -> assertThat(e.aggregateId()).isEqualTo(ids.employmentId()));
    }

    @Test
    void aForcedOutboxInsertFailureRollsBackTheWholeHireCommand() throws Exception {
        var w = world("outbox-rollback");
        jdbc.sql("""
                        CREATE OR REPLACE FUNCTION messaging.reject_test_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                        BEGIN IF NEW.tenant_id = '""" + w.tenantId().value() + """
                        '::uuid THEN RAISE EXCEPTION 'synthetic outbox rejection'; END IF; RETURN NEW; END $$
                        """).update();
        jdbc.sql("CREATE TRIGGER reject_test_outbox BEFORE INSERT ON messaging.outbox_event FOR EACH ROW EXECUTE FUNCTION messaging.reject_test_outbox()")
                .update();
        try {
            String email = Api.uniqueEmail("diana");
            api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Diana", "Ureña", email), Api.newKey())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is5xxServerError());

            assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ? AND work_email = ?",
                    w.tenantId().value(), email.toLowerCase())).isZero();
            assertThat(count("SELECT count(*) FROM messaging.outbox_event WHERE tenant_id = ?", w.tenantId().value()))
                    .isZero();
        } finally {
            jdbc.sql("DROP TRIGGER reject_test_outbox ON messaging.outbox_event").update();
            jdbc.sql("DROP FUNCTION messaging.reject_test_outbox()").update();
        }
    }

    private String outboxStatus(UUID rowId) {
        return jdbc.sql("SELECT status FROM messaging.outbox_event WHERE id = :id").param("id", rowId)
                .query(String.class).single();
    }

    private int attemptCount(UUID rowId) {
        return jdbc.sql("SELECT attempt_count FROM messaging.outbox_event WHERE id = :id").param("id", rowId)
                .query(Integer.class).single();
    }

    @TestConfiguration
    static class FakeBrokerConfig {

        @Bean
        @Primary
        FakeBrokerPublisher fakeBrokerPublisher() {
            return new FakeBrokerPublisher();
        }
    }

    /** A test-only stand-in for the real Kafka client: never a network call, fails on demand (mirrors the simulators' {@code forceScenario} pattern). */
    static class FakeBrokerPublisher implements BrokerPublisher {

        private final List<IntegrationEvent> published = new CopyOnWriteArrayList<>();
        private final AtomicInteger failuresRemaining = new AtomicInteger(0);

        void failNextTimes(int n) {
            failuresRemaining.set(n);
        }

        @Override
        public void publish(String topic, String key, IntegrationEvent event) {
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new RuntimeException("synthetic broker failure");
            }
            published.add(event);
        }

        List<IntegrationEvent> published() {
            return published;
        }
    }
}
