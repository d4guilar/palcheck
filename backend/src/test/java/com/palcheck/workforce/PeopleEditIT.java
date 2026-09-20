package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.support.WorkforceIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Editing person, employment and assignment: patch semantics, validation, references and per-resource versions. */
class PeopleEditIT extends WorkforceIntegrationTest {

    private static Map<String, Object> patch(long version, Object... pairs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedVersion", version);
        for (int i = 0; i < pairs.length; i += 2) {
            body.put((String) pairs[i], pairs[i + 1]);
        }
        return body;
    }

    // --- person ------------------------------------------------------------------------------------------------

    @Test
    void editingAPersonChangesOnlyTheSentFieldsAndBumpsTheVersion() throws Exception {
        var w = world("edit-person");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                patch(1, "preferredName", "Anita", "lastName", "Vargas Mora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.preferredName").value("Anita"))
                .andExpect(jsonPath("$.lastName").value("Vargas Mora"))
                .andExpect(jsonPath("$.displayName").value("Anita Vargas Mora"));
    }

    @Test
    void nullClearsAnOptionalFieldAndIsRejectedForARequiredOne() throws Exception {
        var w = world("clear");
        var ids = hire(w, "Ana", "Vargas");
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(1, "preferredName", "Anita"))
                .andExpect(status().isOk());

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(2, "preferredName", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredName").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("Ana Vargas"));

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(3, "firstName", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')].code").value("REQUIRED"));
    }

    @Test
    void aPatchThatChangesNothingDoesNotBumpTheVersionOrWriteHistory() throws Exception {
        var w = world("noop");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(1, "firstName", "Ana", "middleName", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ? AND command = 'person.update'",
                w.tenantId().value())).isZero();
    }

    @Test
    void invalidValuesAndUnknownFieldsAreRejectedTogether() throws Exception {
        var w = world("edit-invalid");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(),
                patch(1, "workEmail", "bad", "locale", "xx", "tenantId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'tenantId')].code").value("UNKNOWN_FIELD"));

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(1, "workEmail", "bad", "locale", "xx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'workEmail')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'locale')].code").value("UNSUPPORTED_VALUE"));
    }

    @Test
    void expectedVersionIsRequired() throws Exception {
        var w = world("noversion");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), Map.of("firstName", "Ana Lucía"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'expectedVersion')].code").value("REQUIRED"));
        api.patch(w.tenant().admin(), "/api/people/" + ids.personId(), patch(0, "firstName", "Ana Lucía"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingTheWorkEmailToOneInUseIsRejectedButKeepingYourOwnIsFine() throws Exception {
        var w = world("edit-email");
        var ana = hire(w, "Ana", "Vargas", "ana@example.test");
        hire(w, "Beatriz", "Mora", "beatriz@example.test");

        api.patch(w.tenant().admin(), "/api/people/" + ana.personId(), patch(1, "workEmail", "Beatriz@Example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'workEmail')].code").value("ALREADY_IN_USE"));
        api.patch(w.tenant().admin(), "/api/people/" + ana.personId(), patch(1, "workEmail", "ANA@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    // --- employment --------------------------------------------------------------------------------------------

    @Test
    void editingAnEmploymentHasItsOwnVersionAndReturnsTheRefreshedView() throws Exception {
        var w = world("edit-employment");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(),
                patch(1, "status", "ENDED", "endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endDate").value("2026-06-30"))
                .andExpect(jsonPath("$.currentAssignment.title").value("Software Engineer"));

        // The person's own version is unaffected by an employment edit.
        api.get(w.tenant().admin(), "/api/people/" + ids.personId()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void employmentRulesAreEnforced() throws Exception {
        var w = world("employment-rules");
        var ids = hire(w, "Ana", "Vargas");

        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(), patch(1, "status", "ENDED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'endDate')].code").value("REQUIRED"));
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(), patch(1, "endDate", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'endDate')].code").value("INVALID_RANGE"));
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(), patch(1, "startDate", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'startDate')].code").value("INVALID_RANGE"));
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(), patch(1, "legalEntityId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'legalEntityId')].code").value("UNKNOWN_FIELD"));
        api.patch(w.tenant().admin(), "/api/employments/" + ids.employmentId(), patch(1, "country", "cr", "employmentType", "PART_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("CR"))
                .andExpect(jsonPath("$.employmentType").value("PART_TIME"));
    }

    // --- assignment --------------------------------------------------------------------------------------------

    @Test
    void editingAnAssignmentChangesTitleUnitAndManager() throws Exception {
        var w = world("edit-assignment");
        var boss = hire(w, "Beatriz", "Mora");
        var dev = hire(w, "Diana", "Solís");

        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                patch(1, "title", "Senior Engineer", "orgUnitId", w.childUnit().id().toString(),
                        "managerEmploymentId", boss.employmentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dev.employmentId()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.currentAssignment.version").value(2))
                .andExpect(jsonPath("$.currentAssignment.title").value("Senior Engineer"))
                .andExpect(jsonPath("$.currentAssignment.orgUnit.name").value("Platform"))
                .andExpect(jsonPath("$.currentAssignment.manager.displayName").value("Beatriz Mora"));

        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(), patch(2, "managerEmploymentId", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssignment.manager").doesNotExist());
    }

    @Test
    void managerRulesRejectSelfCyclesAndUnknownPeople() throws Exception {
        var w = world("manager-rules");
        var boss = hire(w, "Beatriz", "Mora");
        var dev = hire(w, "Diana", "Solís");
        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                patch(1, "managerEmploymentId", boss.employmentId())).andExpect(status().isOk());

        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                patch(2, "managerEmploymentId", dev.employmentId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'managerEmploymentId')].code").value("INVALID_RANGE"));
        // The boss reporting to their own report would be a cycle.
        api.patch(w.tenant().admin(), "/api/assignments/" + boss.assignmentId(),
                patch(1, "managerEmploymentId", dev.employmentId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'managerEmploymentId')].code").value("CYCLE"));
        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(),
                patch(2, "managerEmploymentId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'managerEmploymentId')].code").value("NOT_FOUND"));
        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(), patch(2, "orgUnitId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'orgUnitId')].code").value("NOT_FOUND"));
        api.patch(w.tenant().admin(), "/api/assignments/" + dev.assignmentId(), patch(2, "title", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].code").value("REQUIRED"));
    }
}
