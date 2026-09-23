package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Role;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.AssignmentPatch;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.Patch;
import com.palcheck.workforce.domain.PersonData;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds two fictional tenants for local development and demos. Every organization, person and identifier here is
 * invented: no real employer, customer, coworker, employee or identifier is used. Records are created through the real
 * application services under a real (seeded) principal, so they carry audit history like any other change.
 *
 * <p>The identity-provider subjects match the development realm in {@code infra/keycloak/palcheck-realm.json}.
 * Runs only with the {@code dev} profile and {@code palcheck.demo.seed=true}, and only for tenants that do not exist yet.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "palcheck.demo.seed", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Subjects of the synthetic development users (fixed ids in the development realm).
    private static final String ACME_ADMIN = "dfbe52ab-aad0-4488-a57d-7edba445f47a";
    private static final String ACME_VIEWER = "907a5f68-255a-47c8-930f-7c3f97f5ffe5";
    private static final String GLOBEX_ADMIN = "c68bce61-0cf4-4792-bdd3-4789b998120f";

    private final TenantProvisioning tenants;
    private final ReferenceDataRepository reference;
    private final RegisterWorkerService register;
    private final EditEmploymentService editEmployment;
    private final PeopleQueryService queries;
    private final String issuer;

    DemoDataSeeder(TenantProvisioning tenants, ReferenceDataRepository reference, RegisterWorkerService register,
            EditEmploymentService editEmployment, PeopleQueryService queries,
            @Value("${palcheck.demo.issuer}") String issuer) {
        this.tenants = tenants;
        this.reference = reference;
        this.register = register;
        this.editEmployment = editEmployment;
        this.queries = queries;
        this.issuer = issuer;
    }

    private record Staff(String key, String first, String middle, String last, String preferred, String locale,
            String entity, String unit, String title, String manager, EmploymentStatus status, EmploymentType type,
            String country, LocalDate start, LocalDate end) {

        static Staff of(String key, String first, String middle, String last, String preferred, String entity,
                String unit, String title, String manager, String start) {
            return new Staff(key, first, middle, last, preferred, "es-CR", entity, unit, title, manager,
                    EmploymentStatus.ACTIVE, EmploymentType.FULL_TIME, "CR", LocalDate.parse(start), null);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAcme();
        seedGlobex();
        for (String slug : new String[] {"acme", "globex"}) {
            var tenant = tenants.findTenantBySlug(slug).orElseThrow().id();
            for (String[] data : new String[][] {{"Northwind Costa Rica S.A.", "CR"}, {"Northwind Panama S.A.", "PA"}}) {
                if (reference.legalEntities(tenant).stream().noneMatch(e -> e.name().equals(data[0]))) entity(tenant, data[0], data[1]);
            }
            for (String name : new String[] {"Engineering", "Support"}) {
                if (reference.orgUnits(tenant).stream().noneMatch(u -> u.name().equals(name))) unit(tenant, null, name);
            }
        }
    }

    private void seedAcme() {
        if (tenants.findTenantBySlug("acme").isPresent()) {
            LOG.info("Demo tenant 'acme' already exists; skipping seed");
            return;
        }
        var tenant = tenants.createTenant("acme", "Acme Latin America");
        var admin = tenants.createPrincipal(tenant.id(), issuer, ACME_ADMIN, "Rebeca Umaña Salas", Set.of(Role.HR_ADMIN));
        tenants.createPrincipal(tenant.id(), issuer, ACME_VIEWER, "Tomás Zúñiga Rojas", Set.of(Role.HR_VIEWER));
        Actor actor = tenants.actorFor(admin.id());

        Map<String, UUID> entities = new HashMap<>();
        entities.put("cr", entity(tenant.id(), "Acme Costa Rica S.A.", "CR"));
        entities.put("pa", entity(tenant.id(), "Acme Panama S.A.", "PA"));
        Map<String, UUID> units = new HashMap<>();
        units.put("eng", unit(tenant.id(), null, "Engineering"));
        units.put("platform", unit(tenant.id(), units.get("eng"), "Platform"));
        units.put("product", unit(tenant.id(), units.get("eng"), "Product"));
        units.put("finance", unit(tenant.id(), null, "Finance"));
        units.put("people", unit(tenant.id(), null, "People"));
        units.put("ops", unit(tenant.id(), null, "Operations"));

        Map<String, String> employmentByKey = new HashMap<>();
        for (Staff s : new Staff[] {
                Staff.of("mariana", "Mariana", null, "Solís Vega", null, "cr", "eng", "VP Engineering", null, "2019-03-04"),
                Staff.of("carlos", "Carlos", "Andrés", "Jiménez Rojas", null, "cr", "platform", "Engineering Manager", "mariana", "2020-06-15"),
                Staff.of("ana", "Ana", "Lucía", "Vargas Mora", null, "cr", "platform", "Staff Software Engineer", "carlos", "2021-02-01"),
                Staff.of("jose", "José", "Pablo", "Chaves Vargas", null, "cr", "platform", "Software Engineer", "carlos", "2022-08-22"),
                Staff.of("daniela", "Daniela", null, "Rodríguez Campos", "Dani", "cr", "product", "Product Manager", "mariana", "2021-09-13"),
                Staff.of("sofia", "Sofía", null, "Araya Quesada", null, "cr", "product", "UX Designer", "daniela", "2023-01-09"),
                Staff.of("luisdiego", "Luis", "Diego", "Barrantes Mora", null, "pa", "finance", "Finance Manager", null, "2018-11-05"),
                Staff.of("natalia", "Natalia", null, "Brenes Salas", null, "pa", "finance", "Financial Analyst", "luisdiego", "2022-04-18"),
                Staff.of("esteban", "Esteban", null, "Montero Cordero", null, "cr", "people", "People Partner", null, "2020-01-13"),
                Staff.of("gabriela", "Gabriela", null, "Núñez Fallas", null, "cr", "people", "People Operations Specialist", "esteban", "2023-05-29"),
                Staff.of("marcos", "Marcos", null, "Vindas Alfaro", null, "pa", "ops", "Operations Lead", null, "2019-07-01"),
                new Staff("paola", "Paola", null, "Ulate Segura", null, "es-CR", "cr", "ops", "Office Coordinator", "marcos",
                        EmploymentStatus.ENDED, EmploymentType.PART_TIME, "CR", LocalDate.parse("2021-03-01"),
                        LocalDate.parse("2026-03-31")),
                new Staff("ricardo", "Ricardo", null, "Elizondo Picado", null, "en", "cr", "platform", "Software Engineer", "carlos",
                        EmploymentStatus.PENDING, EmploymentType.FULL_TIME, "CR", LocalDate.parse("2026-11-02"), null)}) {
            String email = (ascii(s.first()) + "." + ascii(s.last().split(" ")[0])).toLowerCase() + "@acme.example";
            var result = register.register(actor, new RegisterWorkerCommand(
                    new PersonData(s.first(), s.middle(), s.last(), s.preferred(), email, s.locale()),
                    entities.get(s.entity()),
                    new EmploymentData(s.status(), s.type(), s.entity().equals("pa") ? "PA" : s.country(), s.start(), s.end()),
                    new AssignmentData(s.title(), units.get(s.unit()),
                            s.manager() == null ? null : UUID.fromString(employmentByKey.get(s.manager()))),
                    "Development seed data"), "seed-acme-" + s.key());
            var view = queries.employments(actor, result.person().id()).get(0);
            employmentByKey.put(s.key(), view.employment().id().toString());
        }

        // A later change, so a record has visible history: a title correction with a stated reason.
        var ana = queries.list(actor, "Vargas Mora", 0, 5).items().get(0);
        var view = queries.employments(actor, ana.personId()).get(0);
        editEmployment.updateAssignment(actor, view.currentAssignment().assignment().id(), 1,
                new AssignmentPatch(Patch.set("Principal Software Engineer"), Patch.absent(), Patch.absent()),
                "Title aligned with the new engineering ladder", "seed-acme-ana-title");
        LOG.info("Seeded demo tenant 'acme'");
    }

    private void seedGlobex() {
        if (tenants.findTenantBySlug("globex").isPresent()) {
            LOG.info("Demo tenant 'globex' already exists; skipping seed");
            return;
        }
        var tenant = tenants.createTenant("globex", "Globex Andina");
        var admin = tenants.createPrincipal(tenant.id(), issuer, GLOBEX_ADMIN, "Valeria Quesada Brenes", Set.of(Role.HR_ADMIN));
        Actor actor = tenants.actorFor(admin.id());
        UUID entity = entity(tenant.id(), "Globex Andina S.A.", "CR");
        UUID sales = unit(tenant.id(), null, "Sales");
        UUID delivery = unit(tenant.id(), null, "Delivery");
        int n = 0;
        for (String[] p : new String[][] {
                {"Fernando", "Castro Solano", "Sales Director", "sales"},
                {"Lucía", "Mena Arguedas", "Account Executive", "sales"},
                {"Andrea", "Zamora Retana", "Delivery Lead", "delivery"},
                {"Mateo", "Hidalgo Barquero", "Consultant", "delivery"}}) {
            String email = ascii(p[0]).toLowerCase() + "." + ascii(p[1].split(" ")[0]).toLowerCase() + "@globex.example";
            register.register(actor, new RegisterWorkerCommand(
                    new PersonData(p[0], null, p[1], null, email, "es-CR"), entity,
                    new EmploymentData(EmploymentStatus.ACTIVE, EmploymentType.FULL_TIME, "CR",
                            LocalDate.parse("2022-01-10").plusMonths(n * 3L), null),
                    new AssignmentData(p[2], p[3].equals("sales") ? sales : delivery, null),
                    "Development seed data"), "seed-globex-" + (++n));
        }
        LOG.info("Seeded demo tenant 'globex'");
    }

    private UUID entity(TenantId tenant, String name, String country) {
        UUID id = UUID.randomUUID();
        reference.insert(new LegalEntity(id, tenant, name, country));
        return id;
    }

    private UUID unit(TenantId tenant, UUID parent, String name) {
        UUID id = UUID.randomUUID();
        reference.insert(new OrgUnit(id, tenant, parent, name));
        return id;
    }

    private static String ascii(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
