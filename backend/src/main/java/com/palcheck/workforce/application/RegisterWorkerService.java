package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.platform.idempotency.IdempotencyService;
import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.ValidationException;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.Person;
import com.palcheck.workforce.domain.PersonData;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a workforce record: a Person with a first Employment and a current Assignment.
 *
 * <p>One local transaction covers the three inserts, the three audit entries and the idempotency receipt: either all
 * of it is stored or none of it is. No event is published, because Phase 1 has no asynchronous effect to trigger;
 * when the first one exists, a durable publication joins this same transaction (ADR-0018).
 */
@Service
public class RegisterWorkerService {

    static final String OPERATION = "person.create";
    static final String RESULT_TYPE = "PERSON";
    private static final Logger LOG = LoggerFactory.getLogger(RegisterWorkerService.class);

    private final Authorization authorization;
    private final IdempotencyService idempotency;
    private final PersonRepository people;
    private final EmploymentRepository employments;
    private final AssignmentRepository assignments;
    private final ReferenceDataRepository reference;
    private final WorkforceAuditor auditor;
    private final AssignmentAuditLabels labels;
    private final Clock clock;

    RegisterWorkerService(Authorization authorization, IdempotencyService idempotency, PersonRepository people,
            EmploymentRepository employments, AssignmentRepository assignments, ReferenceDataRepository reference,
            WorkforceAuditor auditor, AssignmentAuditLabels labels, Clock clock) {
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.people = people;
        this.employments = employments;
        this.assignments = assignments;
        this.reference = reference;
        this.auditor = auditor;
        this.labels = labels;
        this.clock = clock;
    }

    /** @param idempotencyKey required; same-scope retries in the guaranteed window reconstruct the original logical result */
    @Transactional
    public Result register(Actor actor, RegisterWorkerCommand command, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        authorization.require(actor, Permission.CREATE_PERSON, Permission.EDIT_EMPLOYMENT);

        var reservation = idempotency.reserve(actor.tenantId(), actor.principalId(), OPERATION, idempotencyKey,
                command.fingerprint().sha256());
        if (reservation instanceof IdempotencyService.Reservation.Replay replay) {
            Person existing = people.find(actor.tenantId(), replay.resultId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent result no longer exists"));
            LOG.atInfo().addKeyValue("operation", OPERATION).addKeyValue("result", "replayed")
                    .addKeyValue("personId", existing.id()).log("workforce operation replayed");
            return new Result(existing, true);
        }

        assignments.lockReportingGraph(actor.tenantId());
        PersonData personData = command.person().normalized();
        EmploymentData employmentData = command.employment().normalized();
        AssignmentData assignmentData = command.assignment().normalized();
        String reason = Reasons.clean(command.reason());

        List<FieldViolation> violations = new ArrayList<>();
        violations.addAll(personData.validate("person."));
        violations.addAll(employmentData.validate("employment."));
        violations.addAll(assignmentData.validate("assignment."));
        Reasons.validate(reason, violations);
        checkReferences(actor, command.legalEntityId(), assignmentData, personData, violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        Instant now = Instant.now(clock);
        Person person = Person.create(actor.tenantId(), personData, now);
        Employment employment = Employment.create(actor.tenantId(), person.id(), command.legalEntityId(),
                employmentData, now);
        Assignment assignment = Assignment.createCurrent(actor.tenantId(), employment.id(),
                employmentData.startDate(), assignmentData, now);
        try {
            people.insert(person);
        } catch (WorkEmailAlreadyInUseException e) {
            // Lost a race with a concurrent request that stored the same email after our check.
            throw new ValidationException(new FieldViolation("person.workEmail", "ALREADY_IN_USE",
                    "This work email is already used by another person."));
        }
        employments.insert(employment);
        assignments.insert(assignment);

        UUID changeSet = UUID.randomUUID();
        auditor.created(actor, OPERATION, changeSet, WorkforceAuditor.PERSON, person.id(), person.version(),
                person.id(), personData.asCreation(), reason);
        auditor.created(actor, OPERATION, changeSet, WorkforceAuditor.EMPLOYMENT, employment.id(),
                employment.version(), person.id(), employmentData.asCreation(), reason);
        auditor.created(actor, OPERATION, changeSet, WorkforceAuditor.ASSIGNMENT, assignment.id(),
                assignment.version(), person.id(), labels.label(actor.tenantId(), assignmentData.asCreation()), reason);

        idempotency.complete(actor.tenantId(), actor.principalId(), OPERATION, idempotencyKey, RESULT_TYPE,
                person.id());
        LOG.atInfo().addKeyValue("operation", OPERATION).addKeyValue("result", "success")
                .addKeyValue("personId", person.id()).addKeyValue("employmentId", employment.id())
                .log("workforce record created");
        return new Result(person, false);
    }

    /** References must exist in the caller's tenant. Another tenant's ids are reported exactly like missing ones. */
    private void checkReferences(Actor actor, UUID legalEntityId, AssignmentData assignment, PersonData person,
            List<FieldViolation> violations) {
        if (legalEntityId == null) {
            violations.add(new FieldViolation("employment.legalEntityId", "REQUIRED", "A legal entity is required."));
        } else if (reference.findLegalEntity(actor.tenantId(), legalEntityId).isEmpty()) {
            violations.add(new FieldViolation("employment.legalEntityId", "NOT_FOUND", "Unknown legal entity."));
        }
        if (assignment.orgUnitId() != null && reference.findOrgUnit(actor.tenantId(), assignment.orgUnitId()).isEmpty()) {
            violations.add(new FieldViolation("assignment.orgUnitId", "NOT_FOUND", "Unknown organizational unit."));
        }
        if (assignment.managerEmploymentId() != null
                && employments.find(actor.tenantId(), assignment.managerEmploymentId()).isEmpty()) {
            violations.add(new FieldViolation("assignment.managerEmploymentId", "NOT_FOUND", "Unknown manager."));
        }
        if (person.workEmail() != null && people.workEmailInUse(actor.tenantId(), person.workEmail(), null)) {
            violations.add(new FieldViolation("person.workEmail", "ALREADY_IN_USE",
                    "This work email is already used by another person."));
        }
    }

    /** The created (or, on replay, previously created) person, and whether this was a replay. */
    public record Result(Person person, boolean replayed) {
    }
}
