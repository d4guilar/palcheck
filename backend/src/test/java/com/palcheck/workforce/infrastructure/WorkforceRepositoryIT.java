package com.palcheck.workforce.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import com.palcheck.support.PalCheckIntegrationTest;
import com.palcheck.workforce.application.WorkEmailAlreadyInUseException;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.Person;
import com.palcheck.workforce.domain.PersonData;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PalCheckIntegrationTest
class WorkforceRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    @Autowired
    TenantProvisioning provisioning;
    @Autowired
    JdbcPersonRepository people;
    @Autowired
    JdbcEmploymentRepository employments;
    @Autowired
    JdbcAssignmentRepository assignments;
    @Autowired
    JdbcReferenceDataRepository reference;
    @Autowired
    JdbcDirectoryReadModel directory;

    /** A tenant with one legal entity and one org unit. */
    private record World(TenantId tenant, LegalEntity entity, OrgUnit unit) {
    }

    private World world() {
        TenantId tenant = provisioning.createTenant("repo-" + UUID.randomUUID().toString().substring(0, 8), "Repo").id();
        var entity = new LegalEntity(UUID.randomUUID(), tenant, "Acme Costa Rica S.A.", "CR");
        var unit = new OrgUnit(UUID.randomUUID(), tenant, null, "Engineering");
        reference.insert(entity);
        reference.insert(unit);
        return new World(tenant, entity, unit);
    }

    private static PersonData data(String first, String last, String email) {
        return new PersonData(first, null, last, null, email, "en");
    }

    /** Creates person + employment + current assignment; returns the employment. */
    private Employment hire(World w, String first, String last, String email, UUID managerEmployment) {
        Person person = Person.create(w.tenant(), data(first, last, email), NOW);
        people.insert(person);
        Employment employment = Employment.create(w.tenant(), person.id(), w.entity().id(),
                new EmploymentData(EmploymentStatus.ACTIVE, EmploymentType.FULL_TIME, "CR", START, null), NOW);
        employments.insert(employment);
        assignments.insert(Assignment.createCurrent(w.tenant(), employment.id(), START,
                new AssignmentData("Software Engineer", w.unit().id(), managerEmployment), NOW));
        return employment;
    }

    @Test
    void findIsScopedToTheTenant() {
        World a = world();
        World b = world();
        Employment inA = hire(a, "Ana", "Vargas", "ana@a.example", null);

        assertThat(people.find(a.tenant(), inA.personId())).isPresent();
        assertThat(people.find(b.tenant(), inA.personId())).isEmpty();
        assertThat(employments.find(b.tenant(), inA.id())).isEmpty();
        assertThat(assignments.findCurrent(b.tenant(), inA.id())).isEmpty();
        assertThat(people.currentVersion(b.tenant(), inA.personId())).isEmpty();
    }

    @Test
    void updateIsGuardedByTheExpectedVersionAndIncrementsIt() {
        World w = world();
        Person person = Person.create(w.tenant(), data("Ana", "Vargas", "ana@w.example"), NOW);
        people.insert(person);

        Person renamed = new Person(person.id(), w.tenant(), data("Ana Lucia", "Vargas", "ana@w.example"),
                person.version(), person.createdAt(), NOW.plusSeconds(60));

        assertThat(people.update(renamed, 1)).isTrue();
        assertThat(people.find(w.tenant(), person.id()).orElseThrow().version()).isEqualTo(2);
        // The same expected version is now stale.
        assertThat(people.update(renamed, 1)).isFalse();
        assertThat(people.find(w.tenant(), person.id()).orElseThrow().data().firstName()).isEqualTo("Ana Lucia");
    }

    @Test
    void anUpdateFromAnotherTenantAffectsNothing() {
        World a = world();
        World b = world();
        Person person = Person.create(a.tenant(), data("Ana", "Vargas", "ana@x.example"), NOW);
        people.insert(person);

        Person forged = new Person(person.id(), b.tenant(), data("Hacked", "Name", "h@x.example"), 1, NOW, NOW);

        assertThat(people.update(forged, 1)).isFalse();
        assertThat(people.find(a.tenant(), person.id()).orElseThrow().data().firstName()).isEqualTo("Ana");
    }

    @Test
    void duplicateWorkEmailSurfacesAsADomainExceptionAndIsCheckable() {
        World w = world();
        Person first = Person.create(w.tenant(), data("Ana", "Vargas", "dup@w.example"), NOW);
        people.insert(first);

        assertThat(people.workEmailInUse(w.tenant(), "dup@w.example", null)).isTrue();
        assertThat(people.workEmailInUse(w.tenant(), "dup@w.example", first.id())).isFalse();
        assertThatThrownBy(() -> people.insert(Person.create(w.tenant(), data("Otro", "Vargas", "dup@w.example"), NOW)))
                .isInstanceOf(WorkEmailAlreadyInUseException.class);
    }

    @Test
    void reportingChainDetectionFollowsCurrentManagerLinks() {
        World w = world();
        Employment boss = hire(w, "Beatriz", "Mora", "boss@w.example", null);
        Employment lead = hire(w, "Carlos", "Rojas", "lead@w.example", boss.id());
        Employment dev = hire(w, "Diana", "Solis", "dev@w.example", lead.id());

        assertThat(assignments.reportingChainReaches(w.tenant(), dev.id(), boss.id())).isTrue();
        assertThat(assignments.reportingChainReaches(w.tenant(), boss.id(), dev.id())).isFalse();
        assertThat(assignments.reportingChainReaches(w.tenant(), lead.id(), lead.id())).isTrue();
    }

    @Test
    void directorySearchIsTenantScopedOrderedPagedAndTreatsWildcardsLiterally() {
        World a = world();
        World b = world();
        hire(a, "Zoe", "Araya", "zoe@a.example", null);
        hire(a, "Ana", "Vargas", "ana@a.example", null);
        hire(a, "Mario", "Vargas", "mario@a.example", null);
        hire(b, "Elena", "Vargas", "elena@b.example", null);

        var all = directory.search(a.tenant(), null, 0, 10);
        assertThat(all.totalItems()).isEqualTo(3);
        assertThat(all.items()).extracting(i -> i.displayName()).containsExactly("Zoe Araya", "Ana Vargas", "Mario Vargas");

        var page = directory.search(a.tenant(), null, 1, 2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);

        assertThat(directory.search(a.tenant(), "vargas", 0, 10).totalItems()).isEqualTo(2);
        assertThat(directory.search(a.tenant(), "ELENA", 0, 10).totalItems()).isZero();
        assertThat(directory.search(a.tenant(), "%", 0, 10).totalItems()).isZero();
        assertThat(directory.search(a.tenant(), "_", 0, 10).totalItems()).isZero();
        assertThat(directory.search(a.tenant(), "zoe@a", 0, 10).items()).hasSize(1);
    }

    @Test
    void directoryRowsCarryTheCurrentEmploymentSummary() {
        World w = world();
        hire(w, "Ana", "Vargas", "ana@w.example", null);

        var row = directory.search(w.tenant(), null, 0, 10).items().get(0);

        assertThat(row.title()).isEqualTo("Software Engineer");
        assertThat(row.orgUnitName()).isEqualTo("Engineering");
        assertThat(row.legalEntityName()).isEqualTo("Acme Costa Rica S.A.");
        assertThat(row.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void employmentViewJoinsEntityAssignmentUnitAndManager() {
        World w = world();
        Employment boss = hire(w, "Beatriz", "Mora", "boss@w.example", null);
        Employment dev = hire(w, "Diana", "Solis", "dev@w.example", boss.id());

        List<?> views = directory.employmentsOf(w.tenant(), dev.personId());

        assertThat(views).hasSize(1);
        var view = directory.employmentsOf(w.tenant(), dev.personId()).get(0);
        assertThat(view.legalEntity().name()).isEqualTo("Acme Costa Rica S.A.");
        assertThat(view.currentAssignment().orgUnit().name()).isEqualTo("Engineering");
        assertThat(view.currentAssignment().manager().displayName()).isEqualTo("Beatriz Mora");
        // Another tenant sees nothing for the same person id.
        assertThat(directory.employmentsOf(world().tenant(), dev.personId())).isEmpty();
    }
}
