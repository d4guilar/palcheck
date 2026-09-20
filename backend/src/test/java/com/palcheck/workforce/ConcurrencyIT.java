package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.support.WorkforceIntegrationTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Optimistic concurrency: two people editing the same version cannot silently overwrite each other. */
class ConcurrencyIT extends WorkforceIntegrationTest {

    @Test
    void aStaleVersionIsAConflictThatNamesTheCurrentVersionAndChangesNothing() throws Exception {
        var w = world("stale");
        var ids = hire(w, "Ana", "Vargas");
        // First user saves; the second still holds version 1.
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), Map.of("expectedVersion", 1, "firstName", "First"))
                .andExpect(status().isOk());

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), Map.of("expectedVersion", 1, "firstName", "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.detail").value(
                        "This record changed since you opened it. Review the latest version before saving."));

        api.get(w.tenant().admin(), "/api/people/" + ids.personId())
                .andExpect(jsonPath("$.firstName").value("First")).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void staleEmploymentAndAssignmentEditsAreConflictsToo() throws Exception {
        var w = world("stale-emp");
        var ids = hire(w, "Ana", "Vargas");
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                Map.of("expectedVersion", 1, "employmentType", "PART_TIME")).andExpect(status().isOk());
        api.patch(w.tenant().admin(), "/api/assignments/" + ids.assignmentId(),
                Map.of("expectedVersion", 1, "title", "Lead")).andExpect(status().isOk());

        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                Map.of("expectedVersion", 1, "employmentType", "FULL_TIME"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(2));
        api.patch(w.tenant().admin(), "/api/assignments/" + ids.assignmentId(),
                Map.of("expectedVersion", 1, "title", "Other"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void differentPartsOfARecordDoNotConflictWithEachOther() throws Exception {
        var w = world("independent");
        var ids = hire(w, "Ana", "Vargas");

        // Person, employment and assignment each start at version 1 and are edited independently.
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), Map.of("expectedVersion", 1, "firstName", "A"))
                .andExpect(status().isOk());
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                Map.of("expectedVersion", 1, "employmentType", "PART_TIME")).andExpect(status().isOk());
        api.patch(w.tenant().admin(), "/api/assignments/" + ids.assignmentId(),
                Map.of("expectedVersion", 1, "title", "Lead")).andExpect(status().isOk());
    }

    @Test
    void twoSimultaneousEditsOfTheSameVersionYieldExactlyOneSuccessAndOneConflict() throws Exception {
        for (int round = 0; round < 6; round++) {
            var w = world("race-" + round);
            var ids = hire(w, "Ana", "Vargas");
            AtomicInteger n = new AtomicInteger();

            var statuses = IdempotencyIT.runInParallel(2, () -> api.patch(w.tenant().admin(),
                    "/api/people/" + ids.personId(),
                    Map.of("expectedVersion", 1, "firstName", "Editor" + n.incrementAndGet()))
                    .andReturn().getResponse().getStatus());

            assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(200, 409);
            assertThat(count("SELECT version FROM workforce.person WHERE tenant_id = ? AND id = ?::uuid",
                    w.tenantId().value(), ids.personId())).as("version in round " + round).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'person.update'",
                    w.tenantId().value())).as("history in round " + round).isEqualTo(1);
        }
    }
    @Test
    void concurrentManagerChangesCannotCreateACycleAcrossDifferentAssignments() throws Exception {
        var w = world("manager-race");
        var a = hire(w, "Ana", "Vargas");
        var b = hire(w, "Bea", "Mora");
        // Delay writes, leaving both original requests time to inspect the old graph.
        jdbc.sql("""
                CREATE FUNCTION workforce.test_slow_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN PERFORM pg_sleep(0.7); RETURN NEW; END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER test_slow_assignment BEFORE UPDATE ON workforce.assignment
                FOR EACH ROW EXECUTE FUNCTION workforce.test_slow_assignment()
                """).update();
        try {
            AtomicInteger n = new AtomicInteger();
            var statuses = IdempotencyIT.runInParallel(2, () -> {
                boolean first = n.getAndIncrement() == 0;
                return api.patch(w.tenant().admin(), "/api/assignments/" + (first ? a.assignmentId() : b.assignmentId()),
                        Map.of("expectedVersion", 1, "managerEmploymentId", first ? b.employmentId() : a.employmentId()))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
            assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'assignment.update'",
                    w.tenantId().value())).isEqualTo(1);
        } finally {
            jdbc.sql("DROP TRIGGER test_slow_assignment ON workforce.assignment").update();
            jdbc.sql("DROP FUNCTION workforce.test_slow_assignment()").update();
        }
    }

}
