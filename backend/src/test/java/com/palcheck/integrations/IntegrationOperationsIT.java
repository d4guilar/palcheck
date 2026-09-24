package com.palcheck.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.integrations.application.EquipmentSimulator;
import com.palcheck.integrations.application.IdentitySimulator;
import com.palcheck.integrations.application.IntegrationOperationRepository;
import com.palcheck.integrations.application.OperationConsumer;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.messaging.api.IntegrationEvent;
import com.palcheck.support.WorkforceIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The consumer side of Phase 3's idempotent-processing guarantees (ADR-0027): a duplicate delivery, or the same
 * operation key arriving under two different event ids, executes the business side effect at most once; a transient
 * failure retries to success; a permanent failure needs a human, not a retry; replay re-attempts without
 * duplicating a completed action; every one of these is tenant-isolated and permission-checked. Drives
 * {@link OperationConsumer} directly (no Kafka involved) — the wire itself is proven once, with a real broker, in
 * {@code EventPipelineIT}.
 */
class IntegrationOperationsIT extends WorkforceIntegrationTest {

    @Autowired
    private List<OperationConsumer> consumers;
    @Autowired
    private IntegrationOperationRepository operations;
    @Autowired
    private IdentitySimulator identitySimulator;
    @Autowired
    private EquipmentSimulator equipmentSimulator;

    private OperationConsumer identity() {
        return consumers.stream().filter(c -> c.consumerName().equals("identity-provisioning")).findFirst().orElseThrow();
    }

    private OperationConsumer equipment() {
        return consumers.stream().filter(c -> c.consumerName().equals("equipment-provisioning")).findFirst().orElseThrow();
    }

    private IntegrationEvent identityEvent(UUID tenantId, String employmentId) {
        return new IntegrationEvent(UUID.randomUUID().toString(), "EmploymentStarted", "v1", tenantId.toString(),
                "Employment", employmentId, Instant.now(), "corr-1", "req-1", "palcheck.workforce",
                Map.of("employmentId", employmentId));
    }

    private IntegrationEvent equipmentEvent(UUID tenantId, String assignmentId) {
        return new IntegrationEvent(UUID.randomUUID().toString(), "EmploymentStarted", "v1", tenantId.toString(),
                "Employment", assignmentId, Instant.now(), "corr-1", "req-1", "palcheck.workforce",
                Map.of("assignmentId", assignmentId));
    }

    @Test
    void aDuplicateDeliveryOfTheSameEventIsAcknowledgedWithoutASecondAttempt() {
        var w = world("idempotent-duplicate");
        String employmentId = UUID.randomUUID().toString();
        var event = identityEvent(w.tenantId().value(), employmentId);

        identity().handle(event);
        var afterFirst = operations.find(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId).orElseThrow();
        assertThat(afterFirst.status()).isEqualTo(IntegrationOperation.Status.SUCCEEDED);

        identity().handle(event); // the exact same event, redelivered
        var afterSecond = operations.find(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId).orElseThrow();
        assertThat(afterSecond.completedAt()).isEqualTo(afterFirst.completedAt());
        assertThat(afterSecond.attemptCount()).isZero();
    }

    @Test
    void theSameOperationKeyFromTwoDifferentEventIdsExecutesOnce() {
        var w = world("idempotent-two-events");
        String employmentId = UUID.randomUUID().toString();
        var firstEvent = identityEvent(w.tenantId().value(), employmentId);
        var secondEvent = identityEvent(w.tenantId().value(), employmentId); // different eventId, same payload

        identity().handle(firstEvent);
        identity().handle(secondEvent);

        var op = operations.find(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId).orElseThrow();
        assertThat(op.status()).isEqualTo(IntegrationOperation.Status.SUCCEEDED);
        assertThat(op.sourceEventId()).isEqualTo(UUID.fromString(firstEvent.eventId()));
    }

    @Test
    void aTransientFailureRetriesToSuccessWithoutWaitingOnRealBackoff() {
        var w = world("transient-retry");
        String employmentId = UUID.randomUUID().toString();
        identitySimulator.forceScenario(employmentId, IdentitySimulator.Scenario.TEMPORARY_OUTAGE);

        identity().handle(identityEvent(w.tenantId().value(), employmentId));
        var afterFailure = operations.find(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId).orElseThrow();
        assertThat(afterFailure.status()).isEqualTo(IntegrationOperation.Status.FAILED_TRANSIENT);
        assertThat(afterFailure.attemptCount()).isEqualTo(1);

        makeDueNow(w.tenantId().value(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId);
        int attempted = identity().retryDue(10);

        assertThat(attempted).isGreaterThanOrEqualTo(1);
        var after = operations.find(w.tenantId(), "identity-provisioning", "IDENTITY_PROVISION:" + employmentId).orElseThrow();
        assertThat(after.status()).isEqualTo(IntegrationOperation.Status.SUCCEEDED);
    }

    @Test
    void aPermanentFailureDeadEndsWithoutAnAutomaticRetry() {
        var w = world("permanent-failure");
        String assignmentId = UUID.randomUUID().toString();
        equipmentSimulator.forceScenario(assignmentId, EquipmentSimulator.Scenario.UNSUPPORTED_EQUIPMENT_PROFILE);

        equipment().handle(equipmentEvent(w.tenantId().value(), assignmentId));

        var op = operations.find(w.tenantId(), "equipment-provisioning", "EQUIPMENT_PROVISION:" + assignmentId).orElseThrow();
        assertThat(op.status()).isEqualTo(IntegrationOperation.Status.FAILED_PERMANENT);
        assertThat(op.nextAttemptAt()).isNull();
        assertThat(op.needsAttention()).isTrue();

        int attempted = equipment().retryDue(10);
        assertThat(attempted).isZero(); // FAILED_PERMANENT is never picked up by the scheduled retry
        var stillFailed = operations.find(w.tenantId(), "equipment-provisioning", "EQUIPMENT_PROVISION:" + assignmentId).orElseThrow();
        assertThat(stillFailed.status()).isEqualTo(IntegrationOperation.Status.FAILED_PERMANENT);
    }

    @Test
    void anAuthorizedReplayAfterAFixSucceedsAndIsAudited() throws Exception {
        var w = world("replay-success");
        String assignmentId = UUID.randomUUID().toString();
        equipmentSimulator.forceScenario(assignmentId, EquipmentSimulator.Scenario.UNSUPPORTED_EQUIPMENT_PROFILE);
        equipment().handle(equipmentEvent(w.tenantId().value(), assignmentId));
        String key = "EQUIPMENT_PROVISION:" + assignmentId;
        equipmentSimulator.clearScenario(assignmentId); // the operator fixes the configuration before replaying

        api.post(w.tenant().admin(), "/api/integration-operations/equipment-provisioning/" + key + "/replay", Map.of(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        var op = operations.find(w.tenantId(), "equipment-provisioning", key).orElseThrow();
        assertThat(op.status()).isEqualTo(IntegrationOperation.Status.SUCCEEDED);
        // Replay is audited as an integration operational action (never as a workforce edit).
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'ReplayIntegrationOperation'",
                w.tenantId().value())).isEqualTo(1);
    }

    @Test
    void replayingAnAlreadySucceededOperationIsRejected() throws Exception {
        var w = world("replay-rejected");
        String employmentId = UUID.randomUUID().toString();
        identity().handle(identityEvent(w.tenantId().value(), employmentId));
        String key = "IDENTITY_PROVISION:" + employmentId;

        api.post(w.tenant().admin(), "/api/integration-operations/identity-provisioning/" + key + "/replay", Map.of(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        var op = operations.find(w.tenantId(), "identity-provisioning", key).orElseThrow();
        assertThat(op.attemptCount()).isZero(); // untouched by the rejected replay
    }

    @Test
    void oneTenantCannotSeeAnotherTenantsIntegrationOperationsOrEvents() throws Exception {
        var a = world("tenant-a-ops");
        var b = world("tenant-b-ops");
        String employmentId = UUID.randomUUID().toString();
        identity().handle(identityEvent(a.tenantId().value(), employmentId));
        String key = "IDENTITY_PROVISION:" + employmentId;

        api.get(b.tenant().admin(), "/api/integration-operations/identity-provisioning/" + key)
                .andExpect(status().isNotFound());
        api.post(b.tenant().admin(), "/api/integration-operations/identity-provisioning/" + key + "/replay", Map.of(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheThreePhase3PermissionsGrantAccessToTheirEndpoints() throws Exception {
        var w = world("permission-matrix");
        String employmentId = UUID.randomUUID().toString();
        identity().handle(identityEvent(w.tenantId().value(), employmentId));
        String key = "IDENTITY_PROVISION:" + employmentId;

        // The viewer role holds none of VIEW_EVENTS, VIEW_INTEGRATION_OPERATIONS, REPLAY_INTEGRATION_OPERATION.
        api.get(w.tenant().viewer(), "/api/integration-operations").andExpect(status().isForbidden());
        api.get(w.tenant().viewer(), "/api/integration-operations/identity-provisioning/" + key).andExpect(status().isForbidden());
        api.post(w.tenant().viewer(), "/api/integration-operations/identity-provisioning/" + key + "/replay", Map.of(), null)
                .andExpect(status().isForbidden());

        api.get(w.tenant().admin(), "/api/integration-operations").andExpect(status().isOk());
        api.get(w.tenant().admin(), "/api/integration-operations/identity-provisioning/" + key)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void correlationAndCausationPropagateFromTheOriginatingRequestToTheOutboxEventAndAreVisibleThroughTheEventsApi()
            throws Exception {
        var w = world("correlation-propagation");
        var created = api.post(w.tenant().admin(), "/api/people",
                        com.palcheck.support.Api.createBody(w, "Elena", "Castro", com.palcheck.support.Api.uniqueEmail("elena")),
                        com.palcheck.support.Api.newKey())
                .andExpect(status().isCreated()).andReturn();
        String requestId = created.getResponse().getHeader("X-Request-Id");
        String correlationId = created.getResponse().getHeader("X-Correlation-Id");
        String personId = com.jayway.jsonpath.JsonPath.parse(created.getResponse().getContentAsString()).read("$.id");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        String employmentId = employments.read("$.items[0].id");

        String eventId = jdbc.sql("SELECT event_id FROM messaging.outbox_event WHERE tenant_id = :tenant AND aggregate_id = :aggId")
                .param("tenant", w.tenantId().value()).param("aggId", employmentId)
                .query((rs, n) -> rs.getObject("event_id", UUID.class)).single().toString();

        api.get(w.tenant().admin(), "/api/events/" + eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("EmploymentStarted"))
                .andExpect(jsonPath("$.aggregateId").value(employmentId))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.causationId").value(requestId));
        api.get(w.tenant().viewer(), "/api/events/" + eventId).andExpect(status().isForbidden());
    }

    private void makeDueNow(UUID tenantId, String consumer, String operationKey) {
        jdbc.sql("""
                        UPDATE integrations.integration_operation SET next_attempt_at = now() - interval '1 second'
                         WHERE tenant_id = :tenant AND consumer = :consumer AND operation_key = :key
                        """)
                .param("tenant", tenantId).param("consumer", consumer).param("key", operationKey).update();
    }
}
