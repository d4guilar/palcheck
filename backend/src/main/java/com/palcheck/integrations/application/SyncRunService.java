package com.palcheck.integrations.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.integrations.application.provider.WorkdayAdapter;
import com.palcheck.integrations.application.provider.WorkdayPullOptions;
import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.integrations.domain.FieldLineage;
import com.palcheck.integrations.domain.NormalizedWorker;
import com.palcheck.integrations.domain.SyncFailureReason;
import com.palcheck.integrations.domain.SyncItem;
import com.palcheck.integrations.domain.SyncRun;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.TenantId;
import com.palcheck.workforce.api.WorkforceImportPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one synchronization: pull, map (both inside {@link WorkdayAdapter}), then two passes into
 * {@code workforce} through {@link WorkforceImportPort} (ADR-0025 decision 7). One worker is one call into
 * {@code workforce}, in its own transaction there; this class and its own writes are never themselves wrapped in a
 * transaction spanning more than one statement, so one bad worker never undoes another (decision 6). Legal entity
 * and organizational unit resolution is the small, code-defined map decision 5 describes.
 */
@Service
public class SyncRunService {

    private static final Logger LOG = LoggerFactory.getLogger(SyncRunService.class);

    private final Authorization authorization;
    private final ExternalSystemRepository systemRepository;
    private final ExternalSystemService systemService;
    private final SyncRunRepository runs;
    private final SyncItemRepository items;
    private final FieldLineageRepository lineage;
    private final WorkdayAdapter adapter;
    private final WorkforceImportPort workforce;
    private final Clock clock;

    SyncRunService(Authorization authorization, ExternalSystemRepository systemRepository,
            ExternalSystemService systemService, SyncRunRepository runs, SyncItemRepository items,
            FieldLineageRepository lineage, WorkdayAdapter adapter, WorkforceImportPort workforce, Clock clock) {
        this.authorization = authorization;
        this.systemRepository = systemRepository;
        this.systemService = systemService;
        this.runs = runs;
        this.items = items;
        this.lineage = lineage;
        this.adapter = adapter;
        this.workforce = workforce;
        this.clock = clock;
    }

    public SyncRun runSync(Actor actor, String systemCode) {
        return runSync(actor, systemCode, WorkdayPullOptions.baseline());
    }

    /** The {@code options} overload exists for tests to reproduce a named scenario; the API always uses the baseline. */
    public SyncRun runSync(Actor actor, String systemCode, WorkdayPullOptions options) {
        authorization.require(actor, Permission.RUN_SYNC);
        systemService.ensureWorkdaySimulation(actor.tenantId());
        ExternalSystem system = systemRepository.findByCode(actor.tenantId(), systemCode)
                .orElseThrow(() -> new NotFoundException("Unknown system."));

        Instant startedAt = Instant.now(clock);
        SyncRun run = SyncRun.start(actor.tenantId(), system.id(), actor.principalId(), startedAt);
        runs.insert(run);
        LOG.atInfo().addKeyValue("operation", "sync.run").addKeyValue("systemCode", systemCode)
                .addKeyValue("syncRunId", run.id()).log("sync run started");

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        int recordsSeen = 0;
        try {
            WorkdayAdapter.Result pulled = adapter.pull(options);
            recordsSeen = pulled.workers().size() + pulled.failures().size();

            for (var providerFailure : pulled.failures()) {
                items.insert(SyncItem.failed(run.id(), actor.tenantId(), providerFailure.externalWorkerId(),
                        providerFailure.reason(), providerFailure.detail(), Instant.now(clock)));
                failed++;
            }

            Set<String> seen = new HashSet<>();
            Map<String, SyncItem> importedItems = new java.util.HashMap<>();
            List<NormalizedWorker> importedWorkers = new ArrayList<>();
            for (NormalizedWorker worker : pulled.workers()) {
                if (!seen.add(worker.externalWorkerId())) {
                    items.insert(SyncItem.failed(run.id(), actor.tenantId(), worker.externalWorkerId(),
                            SyncFailureReason.DUPLICATE_EXTERNAL_RECORD,
                            "This external worker id appeared more than once in the same pull.", Instant.now(clock)));
                    failed++;
                    continue;
                }
                WorkforceImportPort.ImportOutcome outcome;
                try {
                    outcome = importOne(actor, worker, system.displayName());
                } catch (RuntimeException exception) {
                    items.insert(SyncItem.failed(run.id(), actor.tenantId(), worker.externalWorkerId(),
                            SyncFailureReason.IMPORT_FAILED, "This worker could not be imported. Review the record and run synchronization again.", Instant.now(clock)));
                    failed++;
                    LOG.atWarn().addKeyValue("syncRunId", run.id()).addKeyValue("errorType", exception.getClass().getSimpleName()).log("worker transaction failed");
                    continue;
                }
                if (outcome instanceof WorkforceImportPort.ImportOutcome.Rejected rejected) {
                    items.insert(SyncItem.failed(run.id(), actor.tenantId(), worker.externalWorkerId(),
                            map(rejected.reason()), rejected.detail(), Instant.now(clock)));
                    failed++;
                    continue;
                }
                var imported = (WorkforceImportPort.ImportOutcome.Imported) outcome;
                SyncItem.Action action = switch (imported.action()) {
                    case CREATED -> SyncItem.Action.CREATED;
                    case UPDATED -> SyncItem.Action.UPDATED;
                    case UNCHANGED -> SyncItem.Action.UNCHANGED;
                };
                switch (action) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                    default -> { }
                }
                SyncItem item = SyncItem.succeeded(run.id(), actor.tenantId(), worker.externalWorkerId(), action,
                        imported.personId(), imported.employmentId(), Instant.now(clock));
                items.insert(item);
                recordLineage(actor.tenantId(), worker, imported, run.id(), item.id());
                importedWorkers.add(worker);
                importedItems.put(worker.externalWorkerId(), item);
            }

            // Pass 2: manager links, now that every worker imported in this run exists (ADR-0025 decision 7).
            for (NormalizedWorker worker : importedWorkers) {
                WorkforceImportPort.ManagerLinkOutcome linkOutcome;
                if (worker.managerExternalWorkerId() != null && !importedItems.containsKey(worker.managerExternalWorkerId())) {
                    linkOutcome = new WorkforceImportPort.ManagerLinkOutcome.Rejected(
                            WorkforceImportPort.ManagerLinkOutcome.Reason.MANAGER_NOT_FOUND,
                            "The manager was not successfully imported in this synchronization. The worker record was imported; its existing manager link is unchanged.");
                } else {
                    linkOutcome = workforce.linkManager(actor, worker.sourceSystem(), system.displayName(), worker.externalWorkerId(), worker.managerExternalWorkerId());
                }
                if (linkOutcome instanceof WorkforceImportPort.ManagerLinkOutcome.Linked) {
                    SyncItem item = importedItems.get(worker.externalWorkerId());
                    if (item.action() == SyncItem.Action.UNCHANGED) {
                        items.markUpdated(actor.tenantId(), item.id());
                        unchanged--;
                        updated++;
                    }
                }
                if (linkOutcome instanceof WorkforceImportPort.ManagerLinkOutcome.Rejected rejected) {
                    items.insert(SyncItem.failed(run.id(), actor.tenantId(), worker.externalWorkerId(),
                            SyncFailureReason.INVALID_MANAGER_REFERENCE, rejected.detail(), Instant.now(clock)));
                    failed++;
                }
                // Linked/Unchanged are not separately reported: they are a detail of the worker's own already-recorded
                // item, not a second record. Only a manager-link problem is worth its own visible failure.
            }

            SyncRun completedRun = run.completed(recordsSeen, created, updated, unchanged, failed, Instant.now(clock));
            runs.complete(completedRun);
            systemRepository.touchLastSyncAt(actor.tenantId(), systemCode, completedRun.completedAt());
            LOG.atInfo().addKeyValue("operation", "sync.run").addKeyValue("systemCode", systemCode)
                    .addKeyValue("syncRunId", run.id()).addKeyValue("result", completedRun.status().name())
                    .addKeyValue("created", created).addKeyValue("updated", updated).addKeyValue("unchanged", unchanged)
                    .addKeyValue("failed", failed).log("sync run completed");
            return completedRun;
        } catch (RuntimeException failure) {
            runs.complete(new SyncRun(run.id(), run.tenantId(), run.systemId(), run.startedAt(), Instant.now(clock),
                    SyncRun.Status.FAILED, recordsSeen, created, updated, unchanged, failed, run.triggeredBy()));
            throw failure;
        }
    }

    private WorkforceImportPort.ImportOutcome importOne(Actor actor, NormalizedWorker worker, String displayName) {
        if (!worker.mappingReport().unmappedFields().isEmpty()) {
            return new WorkforceImportPort.ImportOutcome.Rejected(WorkforceImportPort.ImportOutcome.Reason.MISSING_REQUIRED_FIELD,
                    "The source vocabulary could not be translated. Review the mapping before retrying.");
        }
        var request = new WorkforceImportPort.ImportRequest(worker.sourceSystem(), displayName, worker.externalWorkerId(),
                worker.mappingVersion(), worker.employment().legalEntityName(),
                new WorkforceImportPort.ImportRequest.PersonFields(worker.person().firstName(),
                        worker.person().middleName(), worker.person().lastName(), worker.person().workEmail(),
                        worker.person().locale()),
                new WorkforceImportPort.ImportRequest.EmploymentFields(worker.employment().status(),
                        worker.employment().employmentType(), worker.employment().country(),
                        worker.employment().startDate(), worker.employment().endDate()),
                new WorkforceImportPort.ImportRequest.AssignmentFields(worker.assignment().title(), worker.assignment().orgUnitName()));
        return workforce.importWorker(actor, request);
    }

    private void recordLineage(TenantId tenantId, NormalizedWorker worker,
            WorkforceImportPort.ImportOutcome.Imported imported, UUID syncRunId, UUID syncItemId) {
        Instant now = Instant.now(clock);
        UUID personId = imported.personId();
        UUID employmentId = imported.employmentId();
        UUID assignmentId = imported.assignmentId();
        String system = worker.sourceSystem();
        String mappingVersion = worker.mappingVersion();
        record Field(FieldLineage.EntityType type, UUID entityId, String path, String sourceField) { }
        List<Field> fields = List.of(
                new Field(FieldLineage.EntityType.PERSON, personId, "firstName", worker.sourceFields().get("firstName")),
                new Field(FieldLineage.EntityType.PERSON, personId, "lastName", worker.sourceFields().get("lastName")),
                new Field(FieldLineage.EntityType.PERSON, personId, "workEmail", worker.sourceFields().get("workEmail")),
                new Field(FieldLineage.EntityType.EMPLOYMENT, employmentId, "status", worker.sourceFields().get("status")),
                new Field(FieldLineage.EntityType.EMPLOYMENT, employmentId, "employmentType", worker.sourceFields().get("employmentType")),
                new Field(FieldLineage.EntityType.ASSIGNMENT, assignmentId, "title", worker.sourceFields().get("title")));
        for (Field f : fields) {
            if (f.type() == FieldLineage.EntityType.PERSON && imported.action() != WorkforceImportPort.ImportOutcome.Action.CREATED) continue;
            lineage.put(new FieldLineage(tenantId, f.type(), f.entityId(), f.path(), system, f.sourceField(),
                    mappingVersion, syncRunId, syncItemId, now, worker.externalWorkerId()));
        }
    }

    /** Sync history for one system, permission-checked (controllers never query a repository directly). */
    public PageResult<SyncRun> listRuns(Actor actor, String systemCode, int page, int size) {
        authorization.require(actor, Permission.VIEW_SYNCS);
        validatePage(page, size);
        systemService.ensureWorkdaySimulation(actor.tenantId());
        ExternalSystem system = systemRepository.findByCode(actor.tenantId(), systemCode)
                .orElseThrow(() -> new NotFoundException("Unknown system."));
        return runs.listBySystem(actor.tenantId(), system.id(), page, size);
    }

    /** One run's items, permission-checked. A cross-tenant run id is reported exactly like a missing one. */
    public PageResult<SyncItem> listItems(Actor actor, UUID syncRunId, int page, int size) {
        authorization.require(actor, Permission.VIEW_SYNCS);
        validatePage(page, size);
        runs.find(actor.tenantId(), syncRunId).orElseThrow(() -> new NotFoundException("Unknown sync run."));
        return items.listByRun(actor.tenantId(), syncRunId, page, size);
    }

    public SyncRun getRun(Actor actor, UUID id) {
        authorization.require(actor, Permission.VIEW_SYNCS);
        return runs.find(actor.tenantId(), id).orElseThrow(() -> new NotFoundException("Unknown sync run."));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.palcheck.shared.ValidationException(
                new com.palcheck.shared.FieldViolation("page", "INVALID", "Use a nonnegative page and a size from 1 to 100."));
    }

    private static SyncFailureReason map(WorkforceImportPort.ImportOutcome.Reason reason) {
        return switch (reason) {
            case UNKNOWN_LEGAL_ENTITY -> SyncFailureReason.UNKNOWN_LEGAL_ENTITY;
            case MISSING_REQUIRED_FIELD -> SyncFailureReason.MISSING_REQUIRED_FIELD;
            case DUPLICATE_EXTERNAL_RECORD -> SyncFailureReason.DUPLICATE_EXTERNAL_RECORD;
        };
    }
}
