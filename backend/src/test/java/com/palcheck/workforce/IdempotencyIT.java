package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.platform.idempotency.IdempotencyService;
import com.palcheck.support.Api;
import com.palcheck.support.TestTenant;
import com.palcheck.support.WorkforceIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Same-scope write retries have one effect during the guaranteed retry window; receipt cleanup is not resend safety. */
class IdempotencyIT extends WorkforceIntegrationTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-21T12:00:00Z");
    @MockitoBean Clock clock;
    @Autowired IdempotencyService idempotency;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void resetClock() {
        when(clock.instant()).thenReturn(ACCEPTED_AT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(ZoneOffset.UTC)).thenReturn(clock);
    }

    @Test
    void theFullWindowPreservesReplayConflictAndCurrentResourceIncludingTheBoundary() throws Exception {
        var w = world("idem-window");
        String key = Api.newKey();
        var body = Api.createBody(w, "Ana", "Vargas", "window@example.test");
        String personId = api.read(api.post(w.tenant().admin(), "/api/people", body, key)
                .andExpect(status().isCreated())).read("$.id");
        api.patch(w.tenant().admin(), "/api/people/" + personId,
                Map.of("expectedVersion", 1, "firstName", "Updated"))
                .andExpect(status().isOk());

        for (long seconds : new long[] {24 * 3600 - 1, 24 * 3600}) {
            when(clock.instant()).thenReturn(ACCEPTED_AT.plusSeconds(seconds));
            idempotency.purgeExpired();
            api.post(w.tenant().admin(), "/api/people", body, key)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(header().string("Location", "/api/people/" + personId))
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.firstName").value("Updated"))
                    .andExpect(jsonPath("$.version").value(2));
            api.post(w.tenant().admin(), "/api/people",
                    Api.createBody(w, "Other", "Vargas", "different@example.test"), key)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        }
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(4);
        assertThat(jdbc.sql("SELECT expires_at FROM platform.idempotency_record WHERE tenant_id = :tenant")
                .param("tenant", w.tenantId().value()).query(OffsetDateTime.class).single().toInstant())
                .as("retries do not reset the original window").isEqualTo(ACCEPTED_AT.plus(Duration.ofHours(24)));
    }

    @Test
    void timeSpentExecutingTheOriginalWriteDoesNotShortenItsGuaranteedWindow() {
        var w = world("idem-completion");
        UUID principal = UUID.randomUUID();
        String key = Api.newKey();
        var completedAt = ACCEPTED_AT.plus(Duration.ofHours(2));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(idempotency.reserve(w.tenantId(), principal, "test.write", key, "hash"))
                    .isInstanceOf(IdempotencyService.Reservation.Fresh.class);
            when(clock.instant()).thenReturn(completedAt);
            idempotency.complete(w.tenantId(), principal, "test.write", key, "TEST", UUID.randomUUID());
        });
        when(clock.instant()).thenReturn(completedAt.plus(Duration.ofHours(24)));
        idempotency.purgeExpired();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThat(idempotency.reserve(w.tenantId(), principal, "test.write", key, "hash"))
                        .isInstanceOf(IdempotencyService.Reservation.Replay.class));
    }

    @Test
    void afterTheWindowTheOldBusinessEffectRemainsEvenWhenItsReceiptIsGone() throws Exception {
        var w = world("idem-after-window");
        String key = Api.newKey();
        var body = Api.createBody(w, "Ana", "Vargas", "original@example.test");
        String originalId = api.read(api.post(w.tenant().admin(), "/api/people", body, key)
                .andExpect(status().isCreated())).read("$.id");
        // Free the original email through a legitimate later edit: uniqueness is not permanent deduplication.
        api.patch(w.tenant().admin(), "/api/people/" + originalId,
                Map.of("expectedVersion", 1, "workEmail", "updated@example.test"))
                .andExpect(status().isOk());
        when(clock.instant()).thenReturn(ACCEPTED_AT.plus(Duration.ofHours(24)).plusSeconds(1));
        idempotency.purgeExpired();
        assertThat(count("SELECT count(*) FROM platform.idempotency_record WHERE tenant_id = ?", w.tenantId().value()))
                .isZero();
        String nextId = api.read(api.post(w.tenant().admin(), "/api/people", body, key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed")))
                .read("$.id");
        assertThat(nextId).isNotEqualTo(originalId);
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(2);
    }

    @Test
    void tenantAndOperationNamespacesRemainIndependent() throws Exception {
        var a = world("idem-scope-a");
        var b = world("idem-scope-b");
        String key = Api.newKey();
        String personId = api.read(api.post(a.tenant().admin(), "/api/people",
                Api.createBody(a, "Ana", "Vargas", "scope@example.test"), key)
                .andExpect(status().isCreated())).read("$.id");
        api.post(b.tenant().admin(), "/api/people", Api.createBody(b, "Ana", "Vargas", "scope@example.test"), key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed"));
        api.patch(a.tenant().admin(), "/api/people/" + personId,
                Map.of("expectedVersion", 1, "firstName", "Updated"), key)
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Idempotency-Replayed"));
    }

    @Test
    void simultaneousDifferentPayloadsHaveOneWinnerAndOneDeterministicConflict() throws Exception {
        var w = world("idem-collision");
        String key = Api.newKey();
        var counter = new AtomicInteger();
        List<Integer> statuses = runInParallel(2, () -> api.post(w.tenant().admin(), "/api/people",
                Api.createBody(w, "Ana", "Vargas", "collision" + counter.incrementAndGet() + "@example.test"), key)
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(3);
    }


    @Test
    void retryingACreateWithTheSameKeyReturnsTheOriginalResultAndCreatesNothingNew() throws Exception {
        var w = world("idem-create");
        var body = Api.createBody(w, "Ana", "Vargas", "idem@example.test");
        String key = Api.newKey();

        var first = api.post(w.tenant().admin(), "/api/people", body, key).andExpect(status().isCreated());
        String personId = api.read(first).read("$.id");

        api.post(w.tenant().admin(), "/api/people", body, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("Location", "/api/people/" + personId))
                .andExpect(jsonPath("$.id").value(personId));

        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(3);
    }

    @Test
    void theSameRequestWithCosmeticDifferencesStillMatches() throws Exception {
        var w = world("idem-cosmetic");
        String key = Api.newKey();
        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", "cosmetic@example.test"), key)
                .andExpect(status().isCreated());

        // Different case and surrounding whitespace normalize to the same content.
        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "  Ana ", "Vargas", "COSMETIC@example.test "), key)
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "true"));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejectedAndCreatesNothing() throws Exception {
        var w = world("idem-conflict");
        String key = Api.newKey();
        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", "first@example.test"), key)
                .andExpect(status().isCreated());

        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Beatriz", "Mora", "second@example.test"), key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
    }

    @Test
    void aFailedRequestDoesNotConsumeItsKey() throws Exception {
        var w = world("idem-failed");
        String key = Api.newKey();
        var invalid = Api.createBody(w, "Ana", "Vargas", "not-an-email");
        api.post(w.tenant().admin(), "/api/people", invalid, key).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM platform.idempotency_record WHERE tenant_id = ?", w.tenantId().value())).isZero();

        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", "fixed@example.test"), key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed"));
    }

    @Test
    void theKeyIsScopedToThePrincipalWithinATenant() throws Exception {
        var w = world("idem-principal");
        var otherAdmin = TestTenant.signInPerson(provisioning, w.tenantId(), "Second admin",
                com.palcheck.access.api.Role.HR_ADMIN);
        String key = Api.newKey();

        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", "one@example.test"), key)
                .andExpect(status().isCreated());
        // A different person using the same key value is a different scope: no replay, no conflict.
        api.post(otherAdmin, "/api/people", Api.createBody(w, "Beatriz", "Mora", "two@example.test"), key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed"));
    }

    @Test
    void afterTheWindowSameKeyDifferentPayloadIsNotGuaranteedToConflict() throws Exception {
        var w = world("idem-expired");
        String key = Api.newKey();
        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", "old@example.test"), key)
                .andExpect(status().isCreated());
        when(clock.instant()).thenReturn(ACCEPTED_AT.plus(Duration.ofHours(24)).plusSeconds(1));

        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Beatriz", "Mora", "new@example.test"), key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed"));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(2);
    }

    @Test
    void retryingAnEditWithTheSameKeyDoesNotConflictWithItself() throws Exception {
        var w = world("idem-edit");
        var ids = hire(w, "Ana", "Vargas");
        String key = Api.newKey();
        var edit = Map.<String, Object>of("expectedVersion", 1, "firstName", "Ana Lucía");

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), edit, key)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        // Without a key the retry would be a 409 (the version moved on). With the key it is the same success.
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), edit, key)
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.version").value(2));
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), edit)
                .andExpect(status().isConflict());

        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'person.update'",
                w.tenantId().value())).isEqualTo(1);
    }

    @Test
    void anEditKeyReusedWithADifferentBodyIsRejected() throws Exception {
        var w = world("idem-edit-conflict");
        var ids = hire(w, "Ana", "Vargas");
        String key = Api.newKey();
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                Map.of("expectedVersion", 1, "firstName", "One"), key).andExpect(status().isOk());

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                Map.of("expectedVersion", 1, "firstName", "Two"), key)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void employmentAndAssignmentEditsAreIdempotentToo() throws Exception {
        var w = world("idem-emp");
        var ids = hire(w, "Ana", "Vargas");
        String employmentKey = Api.newKey();
        String assignmentKey = Api.newKey();

        for (int i = 0; i < 2; i++) {
            api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                    Map.of("expectedVersion", 1, "employmentType", "PART_TIME"), employmentKey)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
            api.patch(w.tenant().admin(), "/api/assignments/" + ids.assignmentId(),
                    Map.of("expectedVersion", 1, "title", "Lead"), assignmentKey)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.currentAssignment.version").value(2));
        }
    }

    // --- concurrency -------------------------------------------------------------------------------------------

    @Test
    void concurrentRequestsWithTheSameKeyConvergeOnOneRecord() throws Exception {
        for (int round = 0; round < 4; round++) {
            var w = world("idem-race-" + round);
            String key = Api.newKey();
            var body = Api.createBody(w, "Ana", "Vargas", "race@example.test");

            List<Integer> statuses = runInParallel(4, () ->
                    api.post(w.tenant().admin(), "/api/people", body, key).andReturn().getResponse().getStatus());

            assertThat(statuses).as("round " + round).containsOnly(201);
            assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value()))
                    .as("people in round " + round).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(3);
        }
    }

    @Test
    void concurrentRequestsWithDifferentKeysAndTheSameEmailCreateOneAndRejectTheRestCleanly() throws Exception {
        for (int round = 0; round < 3; round++) {
            var w = world("email-race-" + round);
            List<Integer> statuses = runInParallel(3, () -> api.post(w.tenant().admin(), "/api/people",
                    Api.createBody(w, "Ana", "Vargas", "same@example.test"), Api.newKey())
                    .andReturn().getResponse().getStatus());

            assertThat(statuses.stream().filter(s -> s == 201).count()).as("created in round " + round).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 400).count()).as("rejected in round " + round).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
        }
    }

    static List<Integer> runInParallel(int threads, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
