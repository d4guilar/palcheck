package com.palcheck.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.audit.api.AuditHistory;
import com.palcheck.support.TestPostgresInitializer;
import com.palcheck.workforce.domain.EmploymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/** The development seed: fictional tenants, mapped demo sign-ins, believable structure, real history, safe to rerun. */
@SpringBootTest
@ActiveProfiles({"test", "dev"})
@ContextConfiguration(initializers = TestPostgresInitializer.class)
@TestPropertySource(properties = {
        "palcheck.demo.issuer=http://localhost:8180/realms/palcheck",
        "palcheck.demo.seed=true"})
class DemoDataSeederIT {

    @Autowired
    TenantProvisioning tenants;
    @Autowired
    PeopleQueryService queries;
    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    AuditHistory audit;

    private static final String ISSUER = "http://localhost:8180/realms/palcheck";

    @Test
    void seedsTwoFictionalTenantsWithMappedDemoUsersAndBelievableData() {
        var acme = tenants.findTenantBySlug("acme").orElseThrow();
        var globex = tenants.findTenantBySlug("globex").orElseThrow();

        // The synthetic identity-provider users map to principals in the right tenants.
        var admin = tenants.findPrincipal(ISSUER, "dfbe52ab-aad0-4488-a57d-7edba445f47a").orElseThrow();
        assertThat(admin.tenantId()).isEqualTo(acme.id());
        assertThat(tenants.findPrincipal(ISSUER, "907a5f68-255a-47c8-930f-7c3f97f5ffe5").orElseThrow().tenantId())
                .isEqualTo(acme.id());
        assertThat(tenants.findPrincipal(ISSUER, "c68bce61-0cf4-4792-bdd3-4789b998120f").orElseThrow().tenantId())
                .isEqualTo(globex.id());
        // The fourth demo user authenticates at the identity provider but has no PalCheck principal, on purpose.
        assertThat(tenants.findPrincipal(ISSUER, "549bc81c-9da4-4461-9978-5da358d8bb84")).isEmpty();

        var actor = tenants.actorFor(admin.id());
        var directory = queries.list(actor, null, 0, 100);
        assertThat(directory.totalItems()).isEqualTo(13);
        assertThat(directory.items()).extracting(i -> i.workEmail()).allMatch(e -> e.endsWith("@acme.example"));
        assertThat(directory.items()).extracting(i -> i.employmentStatus())
                .contains(EmploymentStatus.ACTIVE, EmploymentStatus.ENDED, EmploymentStatus.PENDING);

        var globexActor = tenants.actorFor(tenants.findPrincipal(ISSUER, "c68bce61-0cf4-4792-bdd3-4789b998120f")
                .orElseThrow().id());
        assertThat(queries.list(globexActor, null, 0, 100).totalItems()).isEqualTo(4);
        assertThat(queries.legalEntities(actor)).hasSize(4);
        assertThat(queries.orgUnits(actor)).hasSize(7);
    }

    @Test
    void seededRecordsHaveManagersAndRealAuditHistoryAttributedToTheSeedAdmin() {
        var acme = tenants.findTenantBySlug("acme").orElseThrow();
        var admin = tenants.findPrincipal(ISSUER, "dfbe52ab-aad0-4488-a57d-7edba445f47a").orElseThrow();
        var actor = tenants.actorFor(admin.id());

        var ana = queries.list(actor, "Vargas Mora", 0, 10).items().get(0);
        var employment = queries.employments(actor, ana.personId()).get(0);
        assertThat(employment.currentAssignment().manager().displayName()).isEqualTo("Carlos Jiménez Rojas");
        assertThat(employment.currentAssignment().assignment().data().title()).isEqualTo("Principal Software Engineer");

        var history = audit.find(acme.id(), "PERSON", ana.personId(), 0, 10);
        assertThat(history.totalItems()).isEqualTo(4);
        assertThat(history.items().get(0).command()).isEqualTo("assignment.update");
        assertThat(history.items().get(0).reason()).isEqualTo("Title aligned with the new engineering ladder");
        assertThat(history.items()).allSatisfy(e -> assertThat(e.actor().displayName()).isEqualTo("Rebeca Umaña Salas"));
    }

    @Test
    void rerunningTheSeedDoesNothing() {
        var admin = tenants.findPrincipal(ISSUER, "dfbe52ab-aad0-4488-a57d-7edba445f47a").orElseThrow();
        var actor = tenants.actorFor(admin.id());
        long before = queries.list(actor, null, 0, 100).totalItems();

        var entitiesBefore = queries.legalEntities(actor);
        var unitsBefore = queries.orgUnits(actor);
        seeder.run(null);
        assertThat(queries.legalEntities(actor)).isEqualTo(entitiesBefore);
        assertThat(queries.orgUnits(actor)).isEqualTo(unitsBefore);

        assertThat(queries.list(actor, null, 0, 100).totalItems()).isEqualTo(before);
    }
}
