package com.palcheck.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.integrations.application.SyncRunService;
import com.palcheck.integrations.application.provider.WorkdayPullOptions;
import com.palcheck.integrations.domain.SyncRun;
import com.palcheck.shared.TenantId;
import com.palcheck.support.Api;
import com.palcheck.support.IntegrationsWorld;
import com.palcheck.support.PalCheckIntegrationTest;
import com.palcheck.support.TestTenant;
import com.jayway.jsonpath.DocumentContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Phase 2 vertical slice end to end: mock Workday, adapter/mapper, two-pass import, ownership enforcement,
 * audit, lineage, tenant isolation and permissions (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 10).
 */
@PalCheckIntegrationTest
class SyncIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private TenantProvisioning provisioning;
    @Autowired
    private com.palcheck.workforce.application.ReferenceDataRepository reference;
    @Autowired
    private SyncRunService syncRunService;
    @Autowired
    private JdbcClient jdbc;

    private Api api;

    @BeforeEach
    void createApi() {
        api = new Api(mvc, json);
    }

    private IntegrationsWorld world(String label) {
        return IntegrationsWorld.create(provisioning, reference, label);
    }

    /** Bypasses HTTP so a scenario (a changed value, a duplicate, a transient outage) can be reproduced exactly. */
    private SyncRun runSync(IntegrationsWorld w, WorkdayPullOptions options) {
        Actor actor = provisioning.actorFor(w.tenant().admin().principalId());
        return syncRunService.runSync(actor, "workday-simulation", options);
    }

    private long personCountByEmail(IntegrationsWorld w, String email) {
        return jdbc.sql("SELECT count(*) FROM workforce.person WHERE tenant_id = :tenant AND work_email = :email")
                .param("tenant", w.tenantId().value()).param("email", email).query(Long.class).single();
    }

    @Test
    void firstSyncCreatesFourWorkersAndFailsTwoWithoutLosingEitherOutcome() {
        SyncRun run = runSync(world("first-import"), WorkdayPullOptions.baseline());

        assertThat(run.status()).isEqualTo(SyncRun.Status.SUCCEEDED_WITH_FAILURES);
        assertThat(run.recordsSeen()).isEqualTo(6);
        assertThat(run.createdCount()).isEqualTo(4); // W-1001, 1002, 1005, 1006
        assertThat(run.updatedCount()).isZero();
        assertThat(run.unchangedCount()).isZero();
        assertThat(run.failedCount()).isEqualTo(3); // W-1003 (missing field), W-1004 (unknown legal entity), W-1005's manager link
    }

    @Test
    void aSyntheticWorkerIsFindableInThePeopleDirectoryAfterImport() throws Exception {
        var w = world("directory");
        runSync(w, WorkdayPullOptions.baseline());

        var page = api.read(api.get(w.tenant().admin(), "/api/people?q=Delgado"));
        List<String> names = page.read("$.items[*].displayName");
        assertThat(names).contains("Ana María Delgado Rojas");
    }

    @Test
    void theImportedEmploymentAndAssignmentAreMarkedAsExternallyOwned() throws Exception {
        var w = world("ownership");
        runSync(w, WorkdayPullOptions.baseline());
        String personId = findPersonId(w, "carlos.jimenez@northwind.example");

        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        assertThat(employments.read("$.items[0].ownedBySystem").toString()).isEqualTo("workday-simulation");
        assertThat(employments.read("$.items[0].currentAssignment.ownedBySystem").toString())
                .isEqualTo("workday-simulation");
    }

    @Test
    void aHumanCannotEditAnExternallyOwnedEmploymentOrAssignment() throws Exception {
        var w = world("readonly");
        runSync(w, WorkdayPullOptions.baseline());
        String personId = findPersonId(w, "carlos.jimenez@northwind.example");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        String employmentId = employments.read("$.items[0].id").toString();
        String assignmentId = employments.read("$.items[0].currentAssignment.id").toString();

        api.patch(w.tenant().admin(), "/api/employments/" + employmentId,
                        Map.of("expectedVersion", 1, "employmentType", "PART_TIME"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNALLY_OWNED"))
                .andExpect(jsonPath("$.ownedBySystem").value("workday-simulation"));

        api.patch(w.tenant().admin(), "/api/assignments/" + assignmentId,
                        Map.of("expectedVersion", 1, "title", "Overwritten"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNALLY_OWNED"));
    }

    @Test
    void repeatedUnchangedImportCreatesNothingNewAndWritesNoAudit() throws Exception {
        var w = world("idempotent");
        runSync(w, WorkdayPullOptions.baseline());
        long firstCount = personCountByEmail(w, "carlos.jimenez@northwind.example");
        String personId = findPersonId(w, "carlos.jimenez@northwind.example");
        var before = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        String updatedAtBefore = before.read("$.items[0].updatedAt").toString();
        var auditBefore = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/audit"));
        int auditCountBefore = ((List<?>) auditBefore.read("$.items")).size();

        SyncRun second = runSync(w, WorkdayPullOptions.baseline());

        assertThat(personCountByEmail(w, "carlos.jimenez@northwind.example")).isEqualTo(firstCount);
        assertThat(second.createdCount()).isZero();
        assertThat(second.unchangedCount()).isEqualTo(4);
        var after = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        assertThat(after.read("$.items[0].updatedAt").toString()).isEqualTo(updatedAtBefore);
        var auditAfter = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/audit"));
        assertThat(((List<?>) auditAfter.read("$.items")).size()).isEqualTo(auditCountBefore);
    }

    @Test
    void aChangedSourceValueUpdatesOnlyThatFieldAndIsAudited() throws Exception {
        var w = world("changed-value");
        runSync(w, WorkdayPullOptions.baseline());
        String personId = findPersonId(w, "carlos.jimenez@northwind.example");

        SyncRun second = runSync(w, new WorkdayPullOptions(true, false, false));

        assertThat(second.updatedCount()).isEqualTo(1);
        assertThat(second.unchangedCount()).isEqualTo(3);
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        assertThat(employments.read("$.items[0].currentAssignment.title").toString()).isEqualTo("Senior Support Analyst");
        assertThat(employments.read("$.items[0].status").toString()).isEqualTo("ACTIVE"); // untouched field

        var audit = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/audit"));
        assertThat(audit.read("$.items[0].actor.type").toString()).isEqualTo("SYSTEM");
        assertThat(audit.read("$.items[0].actor.displayName").toString()).isEqualTo("Workday Simulation");
        List<Map<String, Object>> changes = audit.read("$.items[0].changes");
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.get("field")).isEqualTo("title");
            assertThat(c.get("after")).isEqualTo("Senior Support Analyst");
        });
    }

    @Test
    void aMissingLegalEntityMappingFailsCleanlyAndCreatesNothing() {
        var w = world("unknown-entity");
        runSync(w, WorkdayPullOptions.baseline());

        assertThat(personCountByEmail(w, "diego.mora@northwind.example")).isZero();
        String reason = jdbc.sql("""
                        SELECT failure_reason FROM integrations.sync_item
                         WHERE tenant_id = :tenant AND external_worker_id = 'W-1004'
                        """)
                .param("tenant", w.tenantId().value()).query(String.class).single();
        assertThat(reason).isEqualTo("UNKNOWN_LEGAL_ENTITY");
    }

    @Test
    void anUnresolvedManagerReferenceFailsOnlyTheLinkNotTheWorkersOwnRecord() throws Exception {
        var w = world("bad-manager");
        runSync(w, WorkdayPullOptions.baseline());

        assertThat(personCountByEmail(w, "valeria.rojas@northwind.example")).isEqualTo(1);
        String personId = findPersonId(w, "valeria.rojas@northwind.example");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        assertThat(employments.<Object>read("$.items[0].currentAssignment.manager")).isNull();

        String reason = jdbc.sql("""
                        SELECT failure_reason FROM integrations.sync_item
                         WHERE tenant_id = :tenant AND external_worker_id = 'W-1005' AND status = 'FAILED'
                        """)
                .param("tenant", w.tenantId().value()).query(String.class).single();
        assertThat(reason).isEqualTo("INVALID_MANAGER_REFERENCE");
    }

    @Test
    void aResolvedManagerLinkAcrossTwoWorkersInTheSameRunSucceeds() throws Exception {
        var w = world("good-manager");
        runSync(w, WorkdayPullOptions.baseline());

        String managerId = findPersonId(w, "ana.delgado@northwind.example");
        String reportPersonId = findPersonId(w, "carlos.jimenez@northwind.example");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + reportPersonId + "/employments"));
        assertThat(employments.read("$.items[0].currentAssignment.manager.personId").toString()).isEqualTo(managerId);
    }

    @Test
    void fieldLineageNamesTheSourceSystemAndField() throws Exception {
        var w = world("lineage");
        runSync(w, WorkdayPullOptions.baseline());
        String personId = findPersonId(w, "carlos.jimenez@northwind.example");
        var employments = api.read(api.get(w.tenant().admin(), "/api/people/" + personId + "/employments"));
        String employmentId = employments.read("$.items[0].id").toString();
        String assignmentId = employments.read("$.items[0].currentAssignment.id").toString();

        DocumentContext lineage = api.read(api.get(w.tenant().admin(),
                "/api/people/" + personId + "/lineage?employmentId=" + employmentId + "&assignmentId=" + assignmentId));
        List<Map<String, Object>> items = lineage.read("$.items");
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("entityType")).isEqualTo("ASSIGNMENT");
            assertThat(item.get("fieldPath")).isEqualTo("title");
            assertThat(item.get("sourceSystem")).isEqualTo("workday-simulation");
            assertThat(item.get("sourceField")).isEqualTo("Business_Title");
        });
    }

    @Test
    void aSourceSideDuplicateWithinOnePullFailsWithoutCreatingASecondPerson() throws Exception {
        var w = world("duplicate");
        SyncRun run = runSync(w, new WorkdayPullOptions(false, true, false));

        assertThat(personCountByEmail(w, "carlos.jimenez@northwind.example")).isEqualTo(1);
        assertThat(run.recordsSeen()).isEqualTo(7);
        long duplicateFailures = jdbc.sql("""
                        SELECT count(*) FROM integrations.sync_item
                         WHERE tenant_id = :tenant AND external_worker_id = 'W-1002' AND failure_reason = 'DUPLICATE_EXTERNAL_RECORD'
                        """)
                .param("tenant", w.tenantId().value()).query(Long.class).single();
        assertThat(duplicateFailures).isEqualTo(1);
    }

    @Test
    void aTemporaryProviderFailureIsReportedWithoutTouchingWorkforce() {
        var w = world("temp-failure");
        SyncRun run = runSync(w, new WorkdayPullOptions(false, false, true));

        assertThat(personCountByEmail(w, "paola.vindas@northwind.example")).isZero();
        String reason = jdbc.sql("""
                        SELECT failure_reason FROM integrations.sync_item
                         WHERE tenant_id = :tenant AND external_worker_id = 'W-1006'
                        """)
                .param("tenant", w.tenantId().value()).query(String.class).single();
        assertThat(reason).isEqualTo("TEMPORARY_PROVIDER_FAILURE");
    }

    @Test
    void oneTenantCannotSeeAnotherTenantsSystemsSyncsOrLineage() throws Exception {
        var a = world("tenant-a");
        var b = world("tenant-b");
        SyncRun run = runSync(a, WorkdayPullOptions.baseline());

        // b's admin cannot read a's run through the shared /api/syncs/{id} path.
        api.get(b.tenant().admin(), "/api/syncs/" + run.id()).andExpect(status().isNotFound());
        // b's own system exists independently and has no history yet.
        var bSyncs = api.read(api.get(b.tenant().admin(), "/api/systems/workday-simulation/syncs"));
        assertThat(((List<?>) bSyncs.read("$.items")).size()).isZero();
        // b's admin sees no lineage for a's imported person.
        String personId = findPersonId(a, "carlos.jimenez@northwind.example");
        var lineage = api.read(api.get(b.tenant().admin(), "/api/people/" + personId + "/lineage"));
        assertThat(((List<?>) lineage.read("$.items")).size()).isZero();
    }

    @Test
    void onlyRunSyncCanTriggerASyncAndOnlyViewSystemsCanListThem() throws Exception {
        var w = world("permissions");
        // The viewer role holds neither VIEW_SYSTEMS nor RUN_SYNC in Phase 2 (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 7).
        api.get(w.tenant().viewer(), "/api/systems").andExpect(status().isForbidden());
        api.post(w.tenant().viewer(), "/api/systems/workday-simulation/sync", Map.of(), null)
                .andExpect(status().isForbidden());
        api.get(w.tenant().admin(), "/api/systems").andExpect(status().isOk());
    }

    @Test
    void runningSyncOverHttpEndToEndWorks() throws Exception {
        var w = world("http");
        api.post(w.tenant().admin(), "/api/systems/workday-simulation/sync", Map.of(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(4))
                .andExpect(jsonPath("$.failedCount").value(3));

        var syncs = api.read(api.get(w.tenant().admin(), "/api/systems/workday-simulation/syncs"));
        assertThat(((List<?>) syncs.read("$.items")).size()).isEqualTo(1);
        String runId = syncs.read("$.items[0].id").toString();
        var items = api.read(api.get(w.tenant().admin(), "/api/syncs/" + runId));
        assertThat(((List<?>) items.read("$.items")).size()).isEqualTo(7); // 6 workers + the duplicate-manager failure item
    }

    @Autowired
    private com.palcheck.workforce.api.WorkforceImportPort importPort;

    @Test
    void localIdentitySurvivesResyncAndRetainsItsInitialImportLineage() throws Exception {
        var w = world("local-identity");
        runSync(w, WorkdayPullOptions.baseline());
        String id = findPersonId(w, "carlos.jimenez@northwind.example");
        var before = api.read(api.get(w.tenant().admin(), "/api/people/" + id + "/lineage"));
        api.patch(w.tenant().admin(), "/api/people/" + id,
                Map.of("expectedVersion", 1, "firstName", "Carlos Local", "preferredName", "Carlitos"))
                .andExpect(status().isOk());
        var run = runSync(w, WorkdayPullOptions.baseline());
        assertThat(run.unchangedCount()).isEqualTo(4);
        api.get(w.tenant().admin(), "/api/people/" + id).andExpect(jsonPath("$.firstName").value("Carlos Local"))
                .andExpect(jsonPath("$.preferredName").value("Carlitos"));
        var after = api.read(api.get(w.tenant().admin(), "/api/people/" + id + "/lineage"));
        assertThat(after.<String>read("$.items[0].importedAt")).isEqualTo(before.read("$.items[0].importedAt"));
    }

    @Test
    void managerCanBeClearedAndAResyncCountsRestoringItAsAnUpdate() {
        var w = world("manager-clear");
        runSync(w, WorkdayPullOptions.baseline());
        var actor = provisioning.actorFor(w.tenant().admin().principalId());
        assertThat(importPort.linkManager(actor, "workday-simulation", "Workday Simulation", "W-1002", null))
                .isInstanceOf(com.palcheck.workforce.api.WorkforceImportPort.ManagerLinkOutcome.Linked.class);
        assertThat(importPort.linkManager(actor, "workday-simulation", "Workday Simulation", "W-1002", null))
                .isInstanceOf(com.palcheck.workforce.api.WorkforceImportPort.ManagerLinkOutcome.Unchanged.class);
        var run = runSync(w, WorkdayPullOptions.baseline());
        assertThat(run.updatedCount()).isEqualTo(1);
        assertThat(run.unchangedCount()).isEqualTo(3);
        String action = jdbc.sql("SELECT action FROM integrations.sync_item WHERE tenant_id = :tenant AND sync_run_id = :run AND external_worker_id = 'W-1002'")
                .param("tenant", w.tenantId().value()).param("run", run.id()).query(String.class).single();
        assertThat(action).isEqualTo("UPDATED");
    }

    @Test
    void summaryPaginationScenarioAndEveryReadPermissionAreEnforced() throws Exception {
        var w = world("read-contract");
        var run = runSync(w, WorkdayPullOptions.baseline());
        api.get(w.tenant().admin(), "/api/syncs/" + run.id() + "/summary")
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsSeen").value(6));
        api.get(w.tenant().viewer(), "/api/syncs/" + run.id() + "/summary").andExpect(status().isForbidden());
        api.get(w.tenant().viewer(), "/api/syncs/" + run.id()).andExpect(status().isForbidden());
        api.get(w.tenant().viewer(), "/api/systems/workday-simulation").andExpect(status().isForbidden());
        api.get(w.tenant().viewer(), "/api/systems/workday-simulation/syncs").andExpect(status().isForbidden());
        api.get(w.tenant().viewer(), "/api/people/" + java.util.UUID.randomUUID() + "/lineage").andExpect(status().isForbidden());
        api.get(w.tenant().admin(), "/api/systems/workday-simulation/syncs?size=0").andExpect(status().isBadRequest());
        api.get(w.tenant().admin(), "/api/syncs/" + run.id() + "?page=-1").andExpect(status().isBadRequest());
        api.get(w.tenant().admin(), "/api/syncs/" + run.id() + "?size=101").andExpect(status().isBadRequest());
        api.post(w.tenant().admin(), "/api/systems/workday-simulation/sync?scenario=TITLE_CHANGE", Map.of(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(1));
        api.post(w.tenant().admin(), "/api/systems/workday-simulation/sync?scenario=UNKNOWN", Map.of(), null)
                .andExpect(status().isBadRequest());
        var other = world("summary-other");
        api.get(other.tenant().admin(), "/api/syncs/" + run.id() + "/summary").andExpect(status().isNotFound());
    }

    @Test
    void concurrentRepeatedSyncsCreateOneResourcePerExternalIdentity() throws Exception {
        var w = world("concurrent-sync");
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> runSync(w, WorkdayPullOptions.baseline()));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> runSync(w, WorkdayPullOptions.baseline()));
        var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
        var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(a.createdCount() + b.createdCount()).isEqualTo(4);
        assertThat(a.failedCount()).isEqualTo(3);
        assertThat(b.failedCount()).isEqualTo(3);
        assertThat(personCountByEmail(w, "carlos.jimenez@northwind.example")).isEqualTo(1);
    }

    @Test
    void aWorkerTransactionFailureIsRecordedAndDoesNotLoseOtherWorkers() {
        var w = world("transaction-failure");
        // Real PostgreSQL error inside one worker's transaction, with successful workers on either side.
        jdbc.sql("CREATE OR REPLACE FUNCTION workforce.reject_test_import() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id = '" + w.tenantId().value() + "'::uuid AND NEW.work_email = 'carlos.jimenez@northwind.example' THEN RAISE EXCEPTION 'synthetic rejection'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER reject_test_import BEFORE INSERT ON workforce.person FOR EACH ROW EXECUTE FUNCTION workforce.reject_test_import()").update();
        try {
            var run = runSync(w, WorkdayPullOptions.baseline());
            assertThat(run.createdCount()).isEqualTo(3);
            assertThat(personCountByEmail(w, "carlos.jimenez@northwind.example")).isZero();
            String reason = jdbc.sql("SELECT failure_reason FROM integrations.sync_item WHERE tenant_id = :tenant AND external_worker_id = 'W-1002'")
                    .param("tenant", w.tenantId().value()).query(String.class).single();
            assertThat(reason).isEqualTo("IMPORT_FAILED");
        } finally {
            jdbc.sql("DROP TRIGGER reject_test_import ON workforce.person").update();
            jdbc.sql("DROP FUNCTION workforce.reject_test_import()").update();
        }
    }

    private String findPersonId(IntegrationsWorld w, String email) {
        return jdbc.sql("SELECT id FROM workforce.person WHERE work_email = :email AND tenant_id = :tenant")
                .param("email", email).param("tenant", w.tenantId().value())
                .query((rs, n) -> rs.getObject("id", java.util.UUID.class).toString()).single();
    }
}
