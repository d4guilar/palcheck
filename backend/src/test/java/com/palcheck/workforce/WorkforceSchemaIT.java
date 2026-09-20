package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import com.palcheck.support.PalCheckIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The database itself refuses to store a cross-tenant association or an invalid workforce state, independent of any
 * application code. These tests use raw SQL on purpose: they prove the constraints, not the repositories.
 */
@PalCheckIntegrationTest
class WorkforceSchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TenantProvisioning provisioning;

    private TenantId newTenant() {
        return provisioning.createTenant("schema-" + UUID.randomUUID().toString().substring(0, 8), "Schema test").id();
    }

    private UUID legalEntity(TenantId tenant) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO workforce.legal_entity (id, tenant_id, name, country) VALUES (:id, :t, :name, 'CR')")
                .param("id", id).param("t", tenant.value()).param("name", "Entity " + id).update();
        return id;
    }

    private UUID orgUnit(TenantId tenant, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO workforce.org_unit (id, tenant_id, parent_id, name) VALUES (:id, :t, :p, :name)")
                .param("id", id).param("t", tenant.value()).param("p", parent).param("name", "Unit " + id).update();
        return id;
    }

    private UUID person(TenantId tenant, String email) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO workforce.person (id, tenant_id, first_name, last_name, work_email, locale)
                        VALUES (:id, :t, 'Ana', 'Vargas', :email, 'en')
                        """)
                .param("id", id).param("t", tenant.value()).param("email", email).update();
        return id;
    }

    private UUID employment(TenantId tenant, UUID person, UUID entity, String status, LocalDate start, LocalDate end) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO workforce.employment (id, tenant_id, person_id, legal_entity_id, status,
                            employment_type, country, start_date, end_date)
                        VALUES (:id, :t, :p, :e, :status, 'FULL_TIME', 'CR', :start, :end)
                        """)
                .param("id", id).param("t", tenant.value()).param("p", person).param("e", entity)
                .param("status", status).param("start", start).param("end", end).update();
        return id;
    }

    private UUID assignment(TenantId tenant, UUID employment, UUID unit, UUID manager, LocalDate to) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO workforce.assignment (id, tenant_id, employment_id, org_unit_id, title,
                            manager_employment_id, effective_from, effective_to)
                        VALUES (:id, :t, :emp, :unit, 'Engineer', :mgr, DATE '2026-01-01', :to)
                        """)
                .param("id", id).param("t", tenant.value()).param("emp", employment).param("unit", unit)
                .param("mgr", manager).param("to", to).update();
        return id;
    }

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void anEmploymentCannotReferenceAPersonFromAnotherTenant() {
        TenantId a = newTenant();
        TenantId b = newTenant();
        UUID personInA = person(a, "ana@a.example");
        UUID entityInB = legalEntity(b);

        assertThatThrownBy(() -> employment(b, personInA, entityInB, "ACTIVE", START, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEmploymentCannotReferenceALegalEntityFromAnotherTenant() {
        TenantId a = newTenant();
        TenantId b = newTenant();
        UUID personInB = person(b, "ana@b.example");
        UUID entityInA = legalEntity(a);

        assertThatThrownBy(() -> employment(b, personInB, entityInA, "ACTIVE", START, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAssignmentCannotReferenceAnOrgUnitOrManagerFromAnotherTenant() {
        TenantId a = newTenant();
        TenantId b = newTenant();
        UUID entityB = legalEntity(b);
        UUID employmentB = employment(b, person(b, "b1@b.example"), entityB, "ACTIVE", START, null);
        UUID unitInA = orgUnit(a, null);
        UUID unitInB = orgUnit(b, null);
        UUID managerInA = employment(a, person(a, "m@a.example"), legalEntity(a), "ACTIVE", START, null);

        assertThatThrownBy(() -> assignment(b, employmentB, unitInA, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> assignment(b, employmentB, unitInB, managerInA, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anOrgUnitParentMustBelongToTheSameTenant() {
        TenantId a = newTenant();
        TenantId b = newTenant();
        UUID rootInA = orgUnit(a, null);

        assertThatThrownBy(() -> orgUnit(b, rootInA)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(orgUnit(a, rootInA)).isNotNull();
    }

    @Test
    void workEmailIsUniquePerTenantButReusableAcrossTenants() {
        TenantId a = newTenant();
        TenantId b = newTenant();
        person(a, "same@example.test");

        assertThatThrownBy(() -> person(a, "same@example.test")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(person(b, "same@example.test")).isNotNull();
    }

    @Test
    void workEmailMustBeStoredLowercase() {
        TenantId a = newTenant();

        assertThatThrownBy(() -> person(a, "Mixed.Case@Example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEmploymentHasAtMostOneCurrentAssignment() {
        TenantId t = newTenant();
        UUID employment = employment(t, person(t, "one@t.example"), legalEntity(t), "ACTIVE", START, null);
        UUID unit = orgUnit(t, null);
        UUID first = assignment(t, employment, unit, null, null);

        assertThatThrownBy(() -> assignment(t, employment, unit, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Closing the current assignment makes room for the next one (the shape a future job change will use).
        jdbc.sql("UPDATE workforce.assignment SET effective_to = DATE '2026-06-30' WHERE id = :id").param("id", first).update();
        assertThat(assignment(t, employment, unit, null, null)).isNotNull();
    }

    @Test
    void anEndedEmploymentNeedsAnEndDateAndDatesMustBeOrdered() {
        TenantId t = newTenant();
        UUID person = person(t, "dates@t.example");
        UUID entity = legalEntity(t);

        assertThatThrownBy(() -> employment(t, person, entity, "ENDED", START, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> employment(t, person, entity, "ACTIVE", START, START.minusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(employment(t, person, entity, "ENDED", START, START.plusDays(10))).isNotNull();
    }

    @Test
    void aManagerCannotBeTheEmployeeThemselves() {
        TenantId t = newTenant();
        UUID employment = employment(t, person(t, "self@t.example"), legalEntity(t), "ACTIVE", START, null);

        assertThatThrownBy(() -> assignment(t, employment, orgUnit(t, null), employment, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAParentDoesNotCascadeAwayHistory() {
        TenantId t = newTenant();
        UUID person = person(t, "keep@t.example");
        employment(t, person, legalEntity(t), "ACTIVE", START, null);

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM workforce.person WHERE id = :id").param("id", person).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
