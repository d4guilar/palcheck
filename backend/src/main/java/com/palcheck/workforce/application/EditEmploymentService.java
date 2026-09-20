package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.platform.idempotency.Fingerprint;
import com.palcheck.platform.idempotency.IdempotencyService;
import com.palcheck.shared.ConflictException;
import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.ValidationException;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentPatch;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentPatch;
import com.palcheck.workforce.domain.FieldChange;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits an Employment or its current Assignment, each with its own version so two people editing different parts of a
 * record do not conflict. The legal entity of an employment is fixed at creation: moving a person to another employer
 * is a future transfer process, not an edit.
 */
@Service
public class EditEmploymentService {

    static final String EMPLOYMENT_OPERATION = "employment.update";
    static final String ASSIGNMENT_OPERATION = "assignment.update";
    private static final Logger LOG = LoggerFactory.getLogger(EditEmploymentService.class);

    private final Authorization authorization;
    private final IdempotencyService idempotency;
    private final EmploymentRepository employments;
    private final AssignmentRepository assignments;
    private final ReferenceDataRepository reference;
    private final DirectoryReadModel directory;
    private final WorkforceAuditor auditor;
    private final AssignmentAuditLabels labels;
    private final Clock clock;

    EditEmploymentService(Authorization authorization, IdempotencyService idempotency,
            EmploymentRepository employments, AssignmentRepository assignments, ReferenceDataRepository reference,
            DirectoryReadModel directory, WorkforceAuditor auditor, AssignmentAuditLabels labels, Clock clock) {
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.employments = employments;
        this.assignments = assignments;
        this.reference = reference;
        this.directory = directory;
        this.auditor = auditor;
        this.labels = labels;
        this.clock = clock;
    }

    /** Edits employment fields. Returns the refreshed employment view (with its current assignment). */
    @Transactional
    public Result updateEmployment(Actor actor, UUID employmentId, long expectedVersion, EmploymentPatch patch,
            String reason, String idempotencyKey) {
        authorization.require(actor, Permission.EDIT_EMPLOYMENT);

        if (idempotencyKey != null) {
            var fingerprint = Fingerprint.of(EMPLOYMENT_OPERATION).part("employmentId", employmentId)
                    .part("expectedVersion", expectedVersion)
                    .part("status", EditPersonService.render(patch.status()))
                    .part("employmentType", EditPersonService.render(patch.employmentType()))
                    .part("country", EditPersonService.render(patch.country()))
                    .part("startDate", EditPersonService.render(patch.startDate()))
                    .part("endDate", EditPersonService.render(patch.endDate()))
                    .part("reason", Reasons.clean(reason));
            var reservation = idempotency.reserve(actor.tenantId(), actor.principalId(), EMPLOYMENT_OPERATION,
                    idempotencyKey, fingerprint.sha256());
            if (reservation instanceof IdempotencyService.Reservation.Replay replay) {
                return replayed(actor, replay.resultId(), EMPLOYMENT_OPERATION);
            }
        }

        Employment current = employments.find(actor.tenantId(), employmentId)
                .orElseThrow(() -> new NotFoundException("The employment was not found."));
        if (current.version() != expectedVersion) {
            throw new ConflictException(EditPersonService.STALE, current.version());
        }

        String cleanReason = Reasons.clean(reason);
        var applied = patch.applyTo(current.data());
        List<FieldViolation> violations = new ArrayList<>(applied.violations());
        if (violations.isEmpty()) {
            violations.addAll(applied.data().validate(""));
        }
        Reasons.validate(cleanReason, violations);
        List<FieldChange> changes = current.data().diff(applied.data());
        if (violations.isEmpty() && changes.stream().anyMatch(c -> c.field().equals("startDate"))) {
            assignments.earliestEffectiveFrom(actor.tenantId(), employmentId)
                    .filter(earliest -> applied.data().startDate().isAfter(earliest))
                    .ifPresent(earliest -> violations.add(new FieldViolation("startDate", "INVALID_RANGE",
                            "The start date cannot be after the start of the current assignment (" + earliest + ").")));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        if (!changes.isEmpty()) {
            Employment next = new Employment(current.id(), current.tenantId(), current.personId(),
                    current.legalEntityId(), applied.data(), current.version(), current.createdAt(),
                    Instant.now(clock));
            if (!employments.update(next, expectedVersion)) {
                throw new ConflictException(EditPersonService.STALE, employments
                        .currentVersion(actor.tenantId(), employmentId)
                        .orElseThrow(() -> new NotFoundException("The employment was not found.")));
            }
            auditor.updated(actor, EMPLOYMENT_OPERATION, UUID.randomUUID(), WorkforceAuditor.EMPLOYMENT,
                    employmentId, expectedVersion + 1, current.personId(), changes, cleanReason);
        }
        complete(actor, idempotencyKey, EMPLOYMENT_OPERATION, employmentId);
        LOG.atInfo().addKeyValue("operation", EMPLOYMENT_OPERATION)
                .addKeyValue("result", changes.isEmpty() ? "unchanged" : "success")
                .addKeyValue("employmentId", employmentId).addKeyValue("changedFields", changes.size())
                .log("workforce employment update processed");
        return new Result(viewOf(actor, current.personId(), employmentId), !changes.isEmpty(), false);
    }

    /** Edits the current assignment: title, organizational unit, manager. */
    @Transactional
    public Result updateAssignment(Actor actor, UUID assignmentId, long expectedVersion, AssignmentPatch patch,
            String reason, String idempotencyKey) {
        authorization.require(actor, Permission.EDIT_EMPLOYMENT);

        if (idempotencyKey != null) {
            var fingerprint = Fingerprint.of(ASSIGNMENT_OPERATION).part("assignmentId", assignmentId)
                    .part("expectedVersion", expectedVersion)
                    .part("title", EditPersonService.render(patch.title()))
                    .part("orgUnitId", EditPersonService.render(patch.orgUnitId()))
                    .part("managerEmploymentId", EditPersonService.render(patch.managerEmploymentId()))
                    .part("reason", Reasons.clean(reason));
            var reservation = idempotency.reserve(actor.tenantId(), actor.principalId(), ASSIGNMENT_OPERATION,
                    idempotencyKey, fingerprint.sha256());
            if (reservation instanceof IdempotencyService.Reservation.Replay replay) {
                return replayed(actor, replay.resultId(), ASSIGNMENT_OPERATION);
            }
        }

        assignments.lockReportingGraph(actor.tenantId());
        Assignment current = assignments.find(actor.tenantId(), assignmentId)
                .orElseThrow(() -> new NotFoundException("The assignment was not found."));
        if (current.version() != expectedVersion) {
            throw new ConflictException(EditPersonService.STALE, current.version());
        }
        if (current.effectiveTo() != null) {
            throw new ConflictException("This assignment is no longer current. Reload the record.", current.version());
        }
        Employment employment = employments.find(actor.tenantId(), current.employmentId())
                .orElseThrow(() -> new NotFoundException("The employment was not found."));

        String cleanReason = Reasons.clean(reason);
        var applied = patch.applyTo(current.data());
        List<FieldViolation> violations = new ArrayList<>(applied.violations());
        if (violations.isEmpty()) {
            violations.addAll(applied.data().validate(""));
        }
        Reasons.validate(cleanReason, violations);
        List<FieldChange> changes = current.data().diff(applied.data());
        if (violations.isEmpty()) {
            checkReferences(actor, current, applied.data(), changes, violations);
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        if (!changes.isEmpty()) {
            Assignment next = new Assignment(current.id(), current.tenantId(), current.employmentId(),
                    current.effectiveFrom(), current.effectiveTo(), applied.data(), current.version(),
                    current.createdAt(), Instant.now(clock));
            if (!assignments.update(next, expectedVersion)) {
                throw new ConflictException(EditPersonService.STALE, assignments
                        .currentVersion(actor.tenantId(), assignmentId)
                        .orElseThrow(() -> new NotFoundException("The assignment was not found.")));
            }
            auditor.updated(actor, ASSIGNMENT_OPERATION, UUID.randomUUID(), WorkforceAuditor.ASSIGNMENT,
                    assignmentId, expectedVersion + 1, employment.personId(), labels.label(actor.tenantId(), changes),
                    cleanReason);
        }
        complete(actor, idempotencyKey, ASSIGNMENT_OPERATION, assignmentId);
        LOG.atInfo().addKeyValue("operation", ASSIGNMENT_OPERATION)
                .addKeyValue("result", changes.isEmpty() ? "unchanged" : "success")
                .addKeyValue("assignmentId", assignmentId).addKeyValue("changedFields", changes.size())
                .log("workforce assignment update processed");
        return new Result(viewOf(actor, employment.personId(), employment.id()), !changes.isEmpty(), false);
    }

    private void checkReferences(Actor actor, Assignment current, com.palcheck.workforce.domain.AssignmentData next,
            List<FieldChange> changes, List<FieldViolation> violations) {
        boolean unitChanged = changes.stream().anyMatch(c -> c.field().equals("orgUnitId"));
        boolean managerChanged = changes.stream().anyMatch(c -> c.field().equals("managerEmploymentId"));
        if (unitChanged && reference.findOrgUnit(actor.tenantId(), next.orgUnitId()).isEmpty()) {
            violations.add(new FieldViolation("orgUnitId", "NOT_FOUND", "Unknown organizational unit."));
        }
        if (managerChanged && next.managerEmploymentId() != null) {
            if (next.managerEmploymentId().equals(current.employmentId())) {
                violations.add(new FieldViolation("managerEmploymentId", "INVALID_RANGE",
                        "A person cannot be their own manager."));
            } else if (employments.find(actor.tenantId(), next.managerEmploymentId()).isEmpty()) {
                violations.add(new FieldViolation("managerEmploymentId", "NOT_FOUND", "Unknown manager."));
            } else if (assignments.reportingChainReaches(actor.tenantId(), next.managerEmploymentId(),
                    current.employmentId())) {
                violations.add(new FieldViolation("managerEmploymentId", "CYCLE",
                        "This manager already reports to this person, directly or indirectly."));
            }
        }
    }

    private Result replayed(Actor actor, UUID resultId, String operation) {
        // The receipt stores the edited entity's id; find the person it belongs to and return the current view.
        UUID employmentId = operation.equals(ASSIGNMENT_OPERATION)
                ? assignments.find(actor.tenantId(), resultId).map(Assignment::employmentId).orElse(null)
                : resultId;
        Employment employment = employmentId == null ? null : employments.find(actor.tenantId(), employmentId).orElse(null);
        if (employment == null) {
            throw new NotFoundException("The record was not found.");
        }
        LOG.atInfo().addKeyValue("operation", operation).addKeyValue("result", "replayed")
                .log("workforce operation replayed");
        return new Result(viewOf(actor, employment.personId(), employment.id()), false, true);
    }

    private void complete(Actor actor, String idempotencyKey, String operation, UUID entityId) {
        if (idempotencyKey != null) {
            idempotency.complete(actor.tenantId(), actor.principalId(), operation, idempotencyKey, "ENTITY", entityId);
        }
    }

    private EmploymentView viewOf(Actor actor, UUID personId, UUID employmentId) {
        return directory.employmentsOf(actor.tenantId(), personId).stream()
                .filter(v -> v.employment().id().equals(employmentId)).findFirst()
                .orElseThrow(() -> new NotFoundException("The employment was not found."));
    }

    /** The refreshed employment view, whether anything changed, and whether this was an idempotent replay. */
    public record Result(EmploymentView view, boolean changed, boolean replayed) {
    }
}
