package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.support.Api;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tenant B, fully authorized within its own tenant, must not be able to read, count, search, change or learn about
 * tenant A's data through any endpoint, including with guessed ids. Cross-tenant ids are indistinguishable from
 * nonexistent ones.
 */
class TenantIsolationIT extends WorkforceIntegrationTest {

    @Test
    void anotherTenantsAdministratorCannotReachAnyOfTheseRecordsThroughAnyEndpoint() throws Exception {
        var a = world("iso-a");
        var b = world("iso-b");
        var ana = hire(a, "Ana", "Vargas");
        var intruder = b.tenant().admin();
        String person = "/api/people/" + ana.personId();

        // Reads: not found, exactly like an id that never existed.
        api.get(intruder, person).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        api.get(intruder, person + "/employments").andExpect(status().isNotFound());
        api.get(intruder, person + "/audit").andExpect(status().isNotFound());
        api.get(intruder, "/api/people/" + UUID.randomUUID()).andExpect(status().isNotFound());

        // Writes: not found, and nothing changed.
        api.patch(intruder, person, Map.of("expectedVersion", 1, "firstName", "Hacked")).andExpect(status().isNotFound());
        api.patch(intruder, "/api/employments/" + ana.employmentId(), Map.of("expectedVersion", 1, "status", "ENDED",
                "endDate", "2026-06-30")).andExpect(status().isNotFound());
        api.patch(intruder, "/api/assignments/" + ana.assignmentId(), Map.of("expectedVersion", 1, "title", "Hacked"))
                .andExpect(status().isNotFound());

        // Lists, counts, searches and reference data only ever show the caller's own tenant.
        api.get(intruder, "/api/people").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        api.get(intruder, "/api/people?q=Ana").andExpect(jsonPath("$.totalItems").value(0));
        api.get(intruder, "/api/people?q=vargas&size=100").andExpect(jsonPath("$.items.length()").value(0));
        api.get(intruder, "/api/legal-entities").andExpect(jsonPath("$.items[0].id").value(b.entity().id().toString()))
                .andExpect(jsonPath("$.items.length()").value(1));
        api.get(intruder, "/api/org-units").andExpect(jsonPath("$.items.length()").value(2));
        api.get(intruder, "/api/session").andExpect(jsonPath("$.tenant.slug").value(b.tenant().slug()));

        // Tenant A's record is untouched.
        api.get(a.tenant().admin(), person).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.firstName").value("Ana"));
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command <> 'person.create'",
                a.tenantId().value())).isZero();
    }

    @Test
    void aCrossTenantIdGivesTheSameAnswerAsAnIdThatDoesNotExist() throws Exception {
        var a = world("same-a");
        var b = world("same-b");
        var ana = hire(a, "Ana", "Vargas");

        var real = api.read(api.get(b.tenant().admin(), "/api/people/" + ana.personId()));
        var missing = api.read(api.get(b.tenant().admin(), "/api/people/" + UUID.randomUUID()));

        assertThat((Object) real.read("$.code")).isEqualTo(missing.read("$.code"));
        assertThat((Object) real.read("$.status")).isEqualTo(missing.read("$.status"));
        assertThat((Object) real.read("$.detail")).isEqualTo(missing.read("$.detail"));
    }

    @Test
    void aManagerFromAnotherTenantCannotBeAssignedAndDisclosesNothing() throws Exception {
        var a = world("mgr-a");
        var b = world("mgr-b");
        var outsider = hire(a, "Olga", "Rojas");
        var dev = hire(b, "Diana", "Solís");

        api.patch(b.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                Map.of("expectedVersion", 1, "managerEmploymentId", outsider.employmentId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'managerEmploymentId')].code").value("NOT_FOUND"));
    }

    @Test
    void identicalIdempotencyKeysInDifferentTenantsAreIndependent() throws Exception {
        var a = world("key-a");
        var b = world("key-b");
        String key = Api.newKey();

        api.post(a.tenant().admin(), "/api/people", Api.createBody(a, "Ana", "Vargas", "same@example.test"), key)
                .andExpect(status().isCreated());
        api.post(b.tenant().admin(), "/api/people", Api.createBody(b, "Ana", "Vargas", "same@example.test"), key)
                .andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", a.tenantId().value())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", b.tenantId().value())).isEqualTo(1);
    }
}
