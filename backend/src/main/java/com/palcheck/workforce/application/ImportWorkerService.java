package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.shared.FieldViolation;
import com.palcheck.workforce.api.WorkforceImportPort;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import com.palcheck.workforce.domain.FieldChange;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.Person;
import com.palcheck.workforce.domain.PersonData;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workforce side of a Phase 2 synchronization (ADR-0024, ADR-0025): the only implementation of
 * {@link WorkforceImportPort}, and the only code allowed to write an externally owned Employment or Assignment.
 * Person identity is initialized once and remains locally editable. Everything here goes through domain validation
 * and the same audit trail a human edit does; the
 * difference is the audit actor (a named system, never the human who triggered the run) and that the write is
 * allowed even when the target is owned by the system doing the writing.
 */
@Service
class ImportWorkerService implements WorkforceImportPort {

    private static final Logger LOG = LoggerFactory.getLogger(ImportWorkerService.class);
    private static final String IMPORT_COMMAND = "integrations.worker.import";
    private static final String UPDATE_COMMAND = "integrations.worker.update";
    private static final String MANAGER_LINK_COMMAND = "integrations.worker.manager_link";

    private final Authorization authorization;
    private final PersonRepository people;
    private final EmploymentRepository employments;
    private final AssignmentRepository assignments;
    private final ReferenceDataRepository reference;
    private final WorkforceAuditor auditor;
    private final Clock clock;
    private final AssignmentAuditLabels labels;

    ImportWorkerService(Authorization authorization, PersonRepository people, EmploymentRepository employments,
            AssignmentRepository assignments, ReferenceDataRepository reference, WorkforceAuditor auditor,
            Clock clock, AssignmentAuditLabels labels) {
        this.authorization = authorization;
        this.people = people;
        this.employments = employments;
        this.assignments = assignments;
        this.reference = reference;
        this.auditor = auditor;
        this.clock = clock;
        this.labels = labels;
    }

    @Override
    @Transactional
    public ImportOutcome importWorker(Actor actor, ImportRequest request) {
        authorization.require(actor, Permission.RUN_SYNC);
        assignments.lockReportingGraph(actor.tenantId());

        EmploymentStatus status;
        EmploymentType employmentType;
        try {
            status = EmploymentStatus.valueOf(request.employment().status());
            employmentType = EmploymentType.valueOf(request.employment().employmentType());
        } catch (IllegalArgumentException | NullPointerException e) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.MISSING_REQUIRED_FIELD,
                    "Unrecognized employment status or type from the source system.");
        }

        PersonData personData = new PersonData(request.person().firstName(), request.person().middleName(),
                request.person().lastName(), null, request.person().workEmail(), request.person().locale())
                .normalized();
        EmploymentData employmentData = new EmploymentData(status, employmentType, request.employment().country(),
                request.employment().startDate(), request.employment().endDate()).normalized();

        List<FieldViolation> violations = new ArrayList<>();
        violations.addAll(personData.validate(""));
        violations.addAll(employmentData.validate(""));
        if (request.assignment().title() == null || request.assignment().title().isBlank()) {
            violations.add(new FieldViolation("assignment.title", "REQUIRED", "A title is required."));
        }
        if (!violations.isEmpty()) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.MISSING_REQUIRED_FIELD, violations.get(0).message());
        }

        Optional<LegalEntity> legalEntity = reference.legalEntities(actor.tenantId()).stream()
                .filter(e -> e.name().equals(request.legalEntityName())).findFirst();
        if (legalEntity.isEmpty()) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.UNKNOWN_LEGAL_ENTITY,
                    "No legal entity named \"" + request.legalEntityName() + "\" in this tenant.");
        }
        Optional<OrgUnit> orgUnit = reference.orgUnits(actor.tenantId()).stream()
                .filter(u -> u.name().equals(request.assignment().orgUnitName())).findFirst();
        if (orgUnit.isEmpty()) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.MISSING_REQUIRED_FIELD,
                    "No organizational unit named \"" + request.assignment().orgUnitName() + "\" in this tenant.");
        }
        AssignmentData assignmentData = new AssignmentData(request.assignment().title(), orgUnit.get().id(), null)
                .normalized();
        violations.addAll(assignmentData.validate(""));
        if (!violations.isEmpty()) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.MISSING_REQUIRED_FIELD, violations.get(0).message());
        }

        Optional<Employment> existing = employments.findByExternalIdentity(actor.tenantId(), request.sourceSystem(),
                request.externalWorkerId());
        return existing.isPresent()
                ? update(actor, existing.get(), request, personData, legalEntity.get(), employmentData, assignmentData)
                : create(actor, request, personData, legalEntity.get(), employmentData, assignmentData);
    }

    private ImportOutcome create(Actor actor, ImportRequest request, PersonData personData, LegalEntity legalEntity,
            EmploymentData employmentData, AssignmentData assignmentData) {
        Instant now = Instant.now(clock);
        Person person = Person.create(actor.tenantId(), personData, now);
        Employment employment = Employment.createExternal(actor.tenantId(), person.id(), legalEntity.id(),
                employmentData, request.sourceSystem(), request.externalWorkerId(), now);
        Assignment assignment = Assignment.createExternalCurrent(actor.tenantId(), employment.id(),
                employmentData.startDate(), assignmentData, request.sourceSystem(), now);
        try {
            people.insert(person);
        } catch (WorkEmailAlreadyInUseException e) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.DUPLICATE_EXTERNAL_RECORD,
                    "The work email " + personData.workEmail() + " already belongs to another person in this tenant.");
        }
        employments.insert(employment);
        assignments.insert(assignment);

        UUID changeSet = UUID.randomUUID();
        String systemName = request.sourceSystem();
        auditor.createdBySystem(request.sourceDisplayName(), IMPORT_COMMAND, changeSet, WorkforceAuditor.PERSON, person.id(),
                person.version(), actor.tenantId(), person.id(), personData.asCreation(), null);
        auditor.createdBySystem(request.sourceDisplayName(), IMPORT_COMMAND, changeSet, WorkforceAuditor.EMPLOYMENT, employment.id(),
                employment.version(), actor.tenantId(), person.id(), employmentData.asCreation(), null);
        auditor.createdBySystem(request.sourceDisplayName(), IMPORT_COMMAND, changeSet, WorkforceAuditor.ASSIGNMENT, assignment.id(),
                assignment.version(), actor.tenantId(), person.id(), labels.label(actor.tenantId(), assignmentData.asCreation()), null);

        LOG.atInfo().addKeyValue("operation", IMPORT_COMMAND).addKeyValue("result", "created")
                .addKeyValue("sourceSystem", systemName).addKeyValue("externalWorkerId", request.externalWorkerId())
                .addKeyValue("personId", person.id()).log("worker imported");
        return new ImportOutcome.Imported(person.id(), employment.id(), assignment.id(), ImportOutcome.Action.CREATED,
                List.of("person", "employment", "assignment"));
    }

    private ImportOutcome update(Actor actor, Employment currentEmployment, ImportRequest request,
            PersonData personData, LegalEntity legalEntity, EmploymentData employmentData,
            AssignmentData assignmentData) {
        Person currentPerson = people.find(actor.tenantId(), currentEmployment.personId())
                .orElseThrow(() -> new IllegalStateException("Employment exists without its person"));
        Assignment currentAssignment = assignments.findCurrent(actor.tenantId(), currentEmployment.id())
                .orElseThrow(() -> new IllegalStateException("A synced employment always has a current assignment"));

        // The manager link is pass 2's job (linkManager), never this pass's: compare against the manager the
        // assignment already has, so an unrelated re-sync neither reports a false change nor erases the link.
        AssignmentData assignmentDataKeepingManager = new AssignmentData(assignmentData.title(),
                assignmentData.orgUnitId(), currentAssignment.data().managerEmploymentId());

        List<FieldChange> employmentChanges = currentEmployment.data().diff(employmentData);
        List<FieldChange> assignmentChanges = currentAssignment.data().diff(assignmentDataKeepingManager);
        boolean legalEntityChanged = !currentEmployment.legalEntityId().equals(legalEntity.id());

        if (legalEntityChanged || employmentData.startDate().isAfter(currentAssignment.effectiveFrom())) {
            return new ImportOutcome.Rejected(ImportOutcome.Reason.MISSING_REQUIRED_FIELD,
                    "A legal entity transfer or a start date after the assignment requires reconciliation.");
        }
        Instant now = Instant.now(clock);
        List<String> changedFields = new ArrayList<>();
        UUID changeSet = UUID.randomUUID();
        String systemName = request.sourceSystem();

        if (!employmentChanges.isEmpty() || legalEntityChanged) {
            Employment nextEmployment = new Employment(currentEmployment.id(), currentEmployment.tenantId(),
                    currentEmployment.personId(), legalEntity.id(), employmentData, currentEmployment.version(),
                    currentEmployment.createdAt(), now, currentEmployment.ownedBySystem(),
                    currentEmployment.externalWorkerId(), now);
            if (!employments.update(nextEmployment, currentEmployment.version())) {
                throw new IllegalStateException("Concurrent modification of a synced employment");
            }
            auditor.updatedBySystem(request.sourceDisplayName(), UPDATE_COMMAND, changeSet, WorkforceAuditor.EMPLOYMENT,
                    currentEmployment.id(), currentEmployment.version() + 1, actor.tenantId(), currentPerson.id(),
                    employmentChanges, null);
            changedFields.add("employment");
        } else {
            employments.touchLastSyncedAt(actor.tenantId(), currentEmployment.id(), now);
        }
        if (!assignmentChanges.isEmpty()) {
            Assignment nextAssignment = new Assignment(currentAssignment.id(), currentAssignment.tenantId(),
                    currentAssignment.employmentId(), currentAssignment.effectiveFrom(), currentAssignment.effectiveTo(),
                    assignmentDataKeepingManager, currentAssignment.version(), currentAssignment.createdAt(), now,
                    currentAssignment.ownedBySystem(), now);
            if (!assignments.update(nextAssignment, currentAssignment.version())) {
                throw new IllegalStateException("Concurrent modification of a synced assignment");
            }
            auditor.updatedBySystem(request.sourceDisplayName(), UPDATE_COMMAND, changeSet, WorkforceAuditor.ASSIGNMENT,
                    currentAssignment.id(), currentAssignment.version() + 1, actor.tenantId(), currentPerson.id(),
                    labels.label(actor.tenantId(), assignmentChanges), null);
            changedFields.add("assignment");
        }

        assignments.touchLastSyncedAt(actor.tenantId(), currentAssignment.id(), now);
        ImportOutcome.Action action = changedFields.isEmpty() ? ImportOutcome.Action.UNCHANGED
                : ImportOutcome.Action.UPDATED;
        LOG.atInfo().addKeyValue("operation", UPDATE_COMMAND).addKeyValue("result", action.name().toLowerCase())
                .addKeyValue("sourceSystem", systemName).addKeyValue("externalWorkerId", request.externalWorkerId())
                .addKeyValue("personId", currentPerson.id()).log("worker synchronized");
        return new ImportOutcome.Imported(currentPerson.id(), currentEmployment.id(), currentAssignment.id(), action,
                List.copyOf(changedFields));
    }

    @Override
    @Transactional
    public ManagerLinkOutcome linkManager(Actor actor, String sourceSystem, String sourceDisplayName, String externalWorkerId,
            String managerExternalWorkerId) {
        authorization.require(actor, Permission.RUN_SYNC);
        assignments.lockReportingGraph(actor.tenantId());

        Optional<Employment> worker = employments.findByExternalIdentity(actor.tenantId(), sourceSystem, externalWorkerId);
        if (worker.isEmpty()) {
            return new ManagerLinkOutcome.Rejected(ManagerLinkOutcome.Reason.WORKER_NOT_FOUND,
                    "The worker being linked was not itself imported.");
        }
        Optional<Employment> manager = employments.findByExternalIdentity(actor.tenantId(), sourceSystem,
                managerExternalWorkerId);
        if (managerExternalWorkerId != null && manager.isEmpty()) {
            return new ManagerLinkOutcome.Rejected(ManagerLinkOutcome.Reason.MANAGER_NOT_FOUND,
                    "No worker with external id \"" + managerExternalWorkerId + "\" was found in this sync.");
        }
        Assignment current = assignments.findCurrent(actor.tenantId(), worker.get().id())
                .orElseThrow(() -> new IllegalStateException("A synced employment always has a current assignment"));

        UUID managerEmploymentId = manager.map(Employment::id).orElse(null);
        if (worker.get().id().equals(managerEmploymentId)) {
            return new ManagerLinkOutcome.Rejected(ManagerLinkOutcome.Reason.CYCLE,
                    "A worker cannot be their own manager.");
        }
        if (managerEmploymentId != null && assignments.reportingChainReaches(actor.tenantId(), managerEmploymentId, worker.get().id())) {
            return new ManagerLinkOutcome.Rejected(ManagerLinkOutcome.Reason.CYCLE,
                    "This manager already reports to this worker, directly or indirectly.");
        }
        if (java.util.Objects.equals(managerEmploymentId, current.data().managerEmploymentId())) {
            return new ManagerLinkOutcome.Unchanged(current.id());
        }

        Instant now = Instant.now(clock);
        if (!assignments.updateManager(actor.tenantId(), current.id(), managerEmploymentId, current.version(), now)) {
            throw new IllegalStateException("Concurrent modification of a synced assignment");
        }
        FieldChange change = new FieldChange("managerEmploymentId",
                current.data().managerEmploymentId() == null ? null : current.data().managerEmploymentId().toString(),
                managerEmploymentId == null ? null : managerEmploymentId.toString());
        auditor.updatedBySystem(sourceDisplayName, MANAGER_LINK_COMMAND, UUID.randomUUID(), WorkforceAuditor.ASSIGNMENT,
                current.id(), current.version() + 1, actor.tenantId(), worker.get().personId(), labels.label(actor.tenantId(), List.of(change)), null);
        LOG.atInfo().addKeyValue("operation", MANAGER_LINK_COMMAND).addKeyValue("result", "linked")
                .addKeyValue("sourceSystem", sourceSystem).addKeyValue("externalWorkerId", externalWorkerId)
                .log("manager linked");
        return new ManagerLinkOutcome.Linked(current.id());
    }

}
