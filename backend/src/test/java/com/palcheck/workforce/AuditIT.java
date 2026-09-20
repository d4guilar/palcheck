package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.audit.api.AuditActor;
import com.palcheck.audit.api.AuditRecord;
import com.palcheck.audit.api.AuditRecorder;
import com.palcheck.support.Api;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;

/** Change history: what is recorded, who can read it, that it is append-only, and that it commits atomically. */
class AuditIT extends WorkforceIntegrationTest {

    @Autowired
    AuditRecorder recorder;

    @Test
    void creatingARecordWritesOneGroupedEntryPerEntityWithActorAndValues() throws Exception {
        var w = world("audit-create");
        var body = Api.createBody(w, "Ana", "Vargas", "ana.audit@example.test");
        Api.section(body, "person").put("middleName", "María");
        body.put("reason", "Onboarding batch 7");
        String personId = api.read(api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isCreated())).read("$.id");

        var history = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/audit")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3)));

        List<String> types = history.read("$.items[*].entityType");
        assertThat(types).containsExactlyInAnyOrder("PERSON", "EMPLOYMENT", "ASSIGNMENT");
        List<String> changeSets = history.read("$.items[*].changeSetId");
        assertThat(changeSets).hasSize(3).containsOnly(changeSets.get(0));
        List<String> commands = history.read("$.items[*].command");
        assertThat(commands).containsOnly("person.create");
        List<String> reasons = history.read("$.items[*].reason");
        assertThat(reasons).containsOnly("Onboarding batch 7");

        List<Map<String, Object>> personEntries = history.read("$.items[?(@.entityType == 'PERSON')]");
        var entry = personEntries.get(0);
        assertThat(entry.get("operation")).isEqualTo("CREATE");
        assertThat(entry.get("entityVersion")).isEqualTo(1);
        assertThat(entry.get("requestId")).isNotNull();
        @SuppressWarnings("unchecked")
        var actor = (Map<String, Object>) entry.get("actor");
        assertThat(actor.get("type")).isEqualTo("USER");
        assertThat(actor.get("id")).isEqualTo(w.tenant().admin().principalId().toString());
        assertThat(actor.get("displayName")).isEqualTo(w.tenant().admin().displayName());
        @SuppressWarnings("unchecked")
        var changes = (List<Map<String, Object>>) entry.get("changes");
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.get("field")).isEqualTo("middleName");
            assertThat(c.get("before")).isNull();
            assertThat(c.get("after")).isEqualTo("María");
        });
    }

    @Test
    void editsRecordBeforeAndAfterValuesNewestFirstAcrossPersonEmploymentAndAssignment() throws Exception {
        var w = world("audit-edit");
        var ids = hire(w, "Ana", "Vargas");
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                Map.of("expectedVersion", 1, "firstName", "Ana Lucía", "reason", "Corrected spelling"))
                .andExpect(status().isOk());
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                Map.of("expectedVersion", 1, "status", "ENDED", "endDate", "2026-06-30")).andExpect(status().isOk());
        api.patch(w.tenant().admin(), "/api/assignments/" + ids.assignmentId(),
                Map.of("expectedVersion", 1, "title", "Staff Engineer")).andExpect(status().isOk());

        var history = api.read(api.get(w.tenant().admin(), "/api/people/" + ids.personId() + "/audit?size=3"));

        assertThat((List<String>) history.read("$.items[*].command"))
                .containsExactly("assignment.update", "employment.update", "person.update");
        assertThat((Object) history.read("$.totalItems")).isEqualTo(6);
        assertThat((Object) history.read("$.totalPages")).isEqualTo(2);
        assertThat((Object) history.read("$.items[2].reason")).isEqualTo("Corrected spelling");
        assertThat((Object) history.read("$.items[2].changes[0].field")).isEqualTo("firstName");
        assertThat((Object) history.read("$.items[2].changes[0].before")).isEqualTo("Ana");
        assertThat((Object) history.read("$.items[2].changes[0].after")).isEqualTo("Ana Lucía");
        assertThat((Object) history.read("$.items[2].entityVersion")).isEqualTo(2);
        assertThat((Object) history.read("$.items[1].changes.length()")).isEqualTo(2);
    }

    @Test
    void assignmentChangesAreStoredAsReadableNamesNotIds() throws Exception {
        var w = world("audit-names");
        var boss = hire(w, "Beatriz", "Mora");
        var dev = hire(w, "Diana", "Solís");
        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                Map.of("expectedVersion", 1, "orgUnitId", w.childUnit().id().toString(),
                        "managerEmploymentId", boss.employmentId())).andExpect(status().isOk());

        var history = api.read(api.get(w.tenant().admin(), "/api/people/" + dev.personId() + "/audit?size=1"));

        assertThat((Object) history.read("$.items[0].command")).isEqualTo("assignment.update");
        List<Map<String, Object>> changes = history.read("$.items[0].changes");
        assertThat(changes).extracting(c -> c.get("field")).containsExactlyInAnyOrder("orgUnit", "manager");
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.get("field")).isEqualTo("orgUnit");
            assertThat(c.get("before")).isEqualTo("Engineering");
            assertThat(c.get("after")).isEqualTo("Platform");
        });
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.get("field")).isEqualTo("manager");
            assertThat(c.get("before")).isNull();
            assertThat(c.get("after")).isEqualTo("Beatriz Mora");
        });
    }

    @Test
    void onlyPermittedCallersInTheSameTenantCanReadTheHistory() throws Exception {
        var a = world("audit-perm-a");
        var b = world("audit-perm-b");
        var ids = hire(a, "Ana", "Vargas");

        api.get(a.tenant().viewer(), "/api/people/" + ids.personId() + "/audit").andExpect(status().isForbidden());
        api.get(b.tenant().admin(), "/api/people/" + ids.personId() + "/audit").andExpect(status().isNotFound());
        api.get(a.tenant().admin(), "/api/people/" + ids.personId() + "/audit?size=0").andExpect(status().isBadRequest());
        api.get(a.tenant().admin(), "/api/people/" + ids.personId() + "/audit").andExpect(status().isOk());
    }

    @Test
    void auditEntriesCannotBeUpdatedDeletedOrTruncated() throws Exception {
        var w = world("audit-append-only");
        hire(w, "Ana", "Vargas");

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit.audit_entry SET reason = 'x' WHERE tenant_id = :t")
                .param("t", w.tenantId().value()).update()).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit.audit_entry WHERE tenant_id = :t")
                .param("t", w.tenantId().value()).update()).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit.audit_entry").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(3);
    }

    @Test
    void anAuditEntryCannotBeRecordedOutsideABusinessTransaction() {
        var record = new AuditRecord(provisioning.createTenant("audit-tx-" + UUID.randomUUID().toString().substring(0, 8), "T").id(),
                AuditActor.system("test"), "test.command", UUID.randomUUID(), AuditRecord.Operation.CREATE, "PERSON",
                UUID.randomUUID(), 1, "PERSON", UUID.randomUUID(), List.of(), null);

        assertThatThrownBy(() -> recorder.record(record)).isInstanceOf(IllegalTransactionStateException.class);
    }

    // --- atomicity: if the audit write fails, the business change must not be stored --------------------------

    private void failAuditWritesWithReason(String reason) {
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION audit.test_force_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.reason = '%s' THEN
                        RAISE EXCEPTION 'forced audit failure for test';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(reason)).update();
        jdbc.sql("""
                CREATE TRIGGER zz_test_force_failure BEFORE INSERT ON audit.audit_entry
                FOR EACH ROW EXECUTE FUNCTION audit.test_force_failure()
                """).update();
    }

    private void restoreAuditWrites() {
        jdbc.sql("DROP TRIGGER IF EXISTS zz_test_force_failure ON audit.audit_entry").update();
        jdbc.sql("DROP FUNCTION IF EXISTS audit.test_force_failure()").update();
    }

    @Test
    void ifTheAuditWriteFailsTheCreateLeavesNoPersonEmploymentAssignmentAuditOrReceipt() throws Exception {
        var w = world("audit-atomic-create");
        failAuditWritesWithReason("force-audit-failure");
        try {
            var body = Api.createBody(w, "Ana", "Vargas", "atomic@example.test");
            body.put("reason", "force-audit-failure");

            api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
        } finally {
            restoreAuditWrites();
        }

        long tenant = 0;
        for (String table : List.of("workforce.person", "workforce.employment", "workforce.assignment",
                "audit.audit_entry", "platform.idempotency_record")) {
            assertThat(count("SELECT count(*) FROM " + table + " WHERE tenant_id = ?", w.tenantId().value()))
                    .as(table).isEqualTo(tenant);
        }
    }

    @Test
    void ifTheAuditWriteFailsAnEditLeavesTheRecordAndItsVersionUnchanged() throws Exception {
        var w = world("audit-atomic-edit");
        var ids = hire(w, "Ana", "Vargas");
        failAuditWritesWithReason("force-audit-failure");
        try {
            api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                    Map.of("expectedVersion", 1, "firstName", "Changed", "reason", "force-audit-failure"))
                    .andExpect(status().isInternalServerError());
        } finally {
            restoreAuditWrites();
        }

        api.get(w.tenant().admin(), "/api/people/" + ids.personId())
                .andExpect(jsonPath("$.firstName").value("Ana")).andExpect(jsonPath("$.version").value(1));
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'person.update'",
                w.tenantId().value())).isZero();
    }
}
