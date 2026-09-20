package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.support.Api;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Creating a workforce record and reading it back through the list, detail, employments and reference endpoints. */
class PeopleCreateAndReadIT extends WorkforceIntegrationTest {

    @Test
    void adminCreatesARecordThatAppearsInListDetailAndEmployments() throws Exception {
        var w = world("create");
        var body = Api.createBody(w, "Ana Lucía", "Vargas Mora", "Ana.Vargas@Example.Test");
        Api.section(body, "person").put("middleName", "María");
        Api.section(body, "assignment").put("title", "Staff Engineer");

        var created = api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.firstName").value("Ana Lucía"))
                .andExpect(jsonPath("$.workEmail").value("ana.vargas@example.test"))
                .andExpect(jsonPath("$.displayName").value("Ana Lucía Vargas Mora"));
        String personId = api.read(created).read("$.id");
        assertThat(created.andReturn().getResponse().getHeader("Location")).isEqualTo("/api/people/" + personId);

        api.get(w.tenant().admin(), "/api/people/" + personId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.middleName").value("María"))
                .andExpect(jsonPath("$.locale").value("en"));

        api.get(w.tenant().admin(), "/api/people/" + personId + "/employments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-01-15"))
                .andExpect(jsonPath("$.items[0].version").value(1))
                .andExpect(jsonPath("$.items[0].legalEntity.name").value("Acme Costa Rica S.A."))
                .andExpect(jsonPath("$.items[0].currentAssignment.title").value("Staff Engineer"))
                .andExpect(jsonPath("$.items[0].currentAssignment.orgUnit.name").value("Engineering"))
                .andExpect(jsonPath("$.items[0].currentAssignment.effectiveFrom").value("2026-01-15"))
                .andExpect(jsonPath("$.items[0].currentAssignment.manager").doesNotExist());

        api.get(w.tenant().admin(), "/api/people")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Ana Lucía Vargas Mora"))
                .andExpect(jsonPath("$.items[0].title").value("Staff Engineer"))
                .andExpect(jsonPath("$.items[0].orgUnitName").value("Engineering"))
                .andExpect(jsonPath("$.items[0].legalEntityName").value("Acme Costa Rica S.A."))
                .andExpect(jsonPath("$.items[0].employmentStatus").value("ACTIVE"));
    }

    @Test
    void viewerCanReadButNotCreate() throws Exception {
        var w = world("viewer");
        api.post(w.tenant().viewer(), "/api/people",
                Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana")), Api.newKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isZero();

        api.post(w.tenant().admin(), "/api/people",
                Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana")), Api.newKey()).andExpect(status().isCreated());
        api.get(w.tenant().viewer(), "/api/people").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void idempotencyKeyIsRequiredOnCreate() throws Exception {
        var w = world("nokey");

        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana")), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        api.post(w.tenant().admin(), "/api/people", Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana")),
                "bad key with spaces").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void allProblemsAreReportedTogether() throws Exception {
        var w = world("invalid");
        var body = Api.createBody(w, "  ", "Vargas", "not-an-email");
        Api.section(body, "person").put("locale", "fr");
        Api.section(body, "employment").put("country", "ZZ");
        Api.section(body, "employment").put("startDate", "2026-02-30");
        Api.section(body, "employment").put("employmentType", "CONTRACTOR");
        Api.section(body, "assignment").put("title", "");

        api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field == 'person.firstName')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'person.workEmail')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'person.locale')].code").value("UNSUPPORTED_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'employment.country')].code").value("UNSUPPORTED_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'employment.startDate')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'employment.employmentType')].code").value("UNSUPPORTED_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'assignment.title')].code").value("REQUIRED"));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isZero();
    }

    @Test
    void missingSectionsAreReportedFieldByField() throws Exception {
        var w = world("empty");

        api.post(w.tenant().admin(), "/api/people", Map.of(), Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'person.firstName')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'employment.legalEntityId')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'assignment.orgUnitId')].code").value("REQUIRED"));
    }

    @Test
    void unknownFieldsAreRejectedIncludingAClientSuppliedTenantId() throws Exception {
        var w = world("unknown");
        var body = Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana"));
        body.put("tenantId", UUID.randomUUID().toString());
        Api.section(body, "person").put("nickname", "x");

        api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'tenantId')].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[?(@.field == 'person.nickname')].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void wrongTypesAndMalformedJsonAreValidationErrors() throws Exception {
        var w = world("types");
        var body = Api.createBody(w, "Ana", "Vargas", Api.uniqueEmail("ana"));
        Api.section(body, "person").put("firstName", 42);
        api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'person.firstName')].code").value("INVALID_TYPE"));

        api.post(w.tenant().admin(), "/api/people", "{ not json", Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void referencesToAnotherTenantsRecordsAreReportedAsUnknown() throws Exception {
        var a = world("refs-a");
        var b = world("refs-b");
        var body = Api.createBody(a, "Ana", "Vargas", Api.uniqueEmail("ana"));
        Api.section(body, "employment").put("legalEntityId", b.entity().id().toString());
        Api.section(body, "assignment").put("orgUnitId", b.unit().id().toString());
        Api.section(body, "assignment").put("managerEmploymentId", UUID.randomUUID().toString());

        api.post(a.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'employment.legalEntityId')].code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[?(@.field == 'assignment.orgUnitId')].code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[?(@.field == 'assignment.managerEmploymentId')].code").value("NOT_FOUND"));
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", a.tenantId().value())).isZero();
    }

    @Test
    void duplicateWorkEmailIsRejectedPerTenantCaseInsensitivelyButAllowedInAnotherTenant() throws Exception {
        var a = world("dup-a");
        var b = world("dup-b");
        api.post(a.tenant().admin(), "/api/people", Api.createBody(a, "Ana", "Vargas", "dup@example.test"), Api.newKey())
                .andExpect(status().isCreated());

        api.post(a.tenant().admin(), "/api/people", Api.createBody(a, "Otra", "Persona", "DUP@Example.test"), Api.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'person.workEmail')].code").value("ALREADY_IN_USE"));
        api.post(b.tenant().admin(), "/api/people", Api.createBody(b, "Ana", "Vargas", "dup@example.test"), Api.newKey())
                .andExpect(status().isCreated());
    }

    @Test
    void managerCanBeSetAtCreationAndIsShownWithTheirName() throws Exception {
        var w = world("manager");
        var boss = api.read(api.post(w.tenant().admin(), "/api/people",
                Api.createBody(w, "Beatriz", "Mora", Api.uniqueEmail("boss")), Api.newKey()).andExpect(status().isCreated()));
        String bossId = boss.read("$.id");
        String bossEmployment = api.read(api.get(w.tenant().admin(), "/api/people/" + bossId + "/employments"))
                .read("$.items[0].id");

        var body = Api.createBody(w, "Diana", "Solís", Api.uniqueEmail("dev"));
        Api.section(body, "assignment").put("managerEmploymentId", bossEmployment);
        String devId = api.read(api.post(w.tenant().admin(), "/api/people", body, Api.newKey())
                .andExpect(status().isCreated())).read("$.id");

        api.get(w.tenant().admin(), "/api/people/" + devId + "/employments")
                .andExpect(jsonPath("$.items[0].currentAssignment.manager.displayName").value("Beatriz Mora"))
                .andExpect(jsonPath("$.items[0].currentAssignment.manager.employmentId").value(bossEmployment));
    }

    @Test
    void searchPagesAndOrdersTheDirectoryAndValidatesItsParameters() throws Exception {
        var w = world("search");
        for (String[] p : new String[][] {{"Zoe", "Araya"}, {"Ana", "Vargas"}, {"Mario", "Vargas"}}) {
            api.post(w.tenant().admin(), "/api/people", Api.createBody(w, p[0], p[1], Api.uniqueEmail(p[0].toLowerCase())),
                    Api.newKey()).andExpect(status().isCreated());
        }

        api.get(w.tenant().admin(), "/api/people?size=2&page=1")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Mario Vargas"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.get(w.tenant().admin(), "/api/people?q=vargas").andExpect(jsonPath("$.totalItems").value(2));
        api.get(w.tenant().admin(), "/api/people?q=%25").andExpect(jsonPath("$.totalItems").value(0));

        api.get(w.tenant().admin(), "/api/people?size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        api.get(w.tenant().admin(), "/api/people?page=-1").andExpect(status().isBadRequest());
        api.get(w.tenant().admin(), "/api/people?size=101").andExpect(status().isBadRequest());
    }

    @Test
    void referenceListsShowOnlyTheCallersTenantWithOrgUnitPaths() throws Exception {
        var a = world("ref-a");
        world("ref-b");

        api.get(a.tenant().viewer(), "/api/legal-entities")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].country").value("CR"));
        api.get(a.tenant().viewer(), "/api/org-units")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.name == 'Platform')].path").value("Engineering / Platform"));
    }

    @Test
    void unknownIdsAndMalformedIdsAreHandledCleanly() throws Exception {
        var w = world("ids");

        api.get(w.tenant().admin(), "/api/people/" + UUID.randomUUID())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        api.get(w.tenant().admin(), "/api/people/not-a-uuid")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
