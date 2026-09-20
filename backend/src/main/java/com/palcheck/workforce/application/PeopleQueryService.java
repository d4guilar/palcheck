package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.audit.api.AuditHistory;
import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.ValidationException;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read operations. Each checks the permission first, scopes every query to the actor's tenant before paging and
 * counting, and reports another tenant's ids exactly like missing ones (no existence disclosure).
 */
@Service
public class PeopleQueryService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_QUERY_LENGTH = 100;

    private final Authorization authorization;
    private final PersonRepository people;
    private final DirectoryReadModel directory;
    private final ReferenceDataRepository reference;
    private final AuditHistory audit;

    PeopleQueryService(Authorization authorization, PersonRepository people, DirectoryReadModel directory,
            ReferenceDataRepository reference, AuditHistory audit) {
        this.authorization = authorization;
        this.people = people;
        this.directory = directory;
        this.reference = reference;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<DirectoryItem> list(Actor actor, String query, int page, int size) {
        authorization.require(actor, Permission.VIEW_PEOPLE);
        validateSearch(query, page, size);
        return directory.search(actor.tenantId(), query, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<ManagerCandidate> managers(Actor actor, String query, int page, int size, UUID excludingEmploymentId) {
        authorization.require(actor, Permission.VIEW_PEOPLE);
        authorization.require(actor, Permission.VIEW_EMPLOYMENT);
        validateSearch(query, page, size);
        return directory.managers(actor.tenantId(), query, page, size, excludingEmploymentId);
    }

    private static void validateSearch(String query, int page, int size) {
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "OUT_OF_RANGE", "The page number cannot be negative."));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "OUT_OF_RANGE",
                    "The page size must be between 1 and " + MAX_PAGE_SIZE + "."));
        }
        if (query != null && query.codePointCount(0, query.length()) > MAX_QUERY_LENGTH) {
            violations.add(new FieldViolation("q", "TOO_LONG", "Use at most " + MAX_QUERY_LENGTH + " characters."));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    @Transactional(readOnly = true)
    public Person person(Actor actor, UUID personId) {
        authorization.require(actor, Permission.VIEW_PERSON);
        return people.find(actor.tenantId(), personId)
                .orElseThrow(() -> new NotFoundException("The person was not found."));
    }

    @Transactional(readOnly = true)
    public List<EmploymentView> employments(Actor actor, UUID personId) {
        authorization.require(actor, Permission.VIEW_EMPLOYMENT);
        requirePerson(actor, personId);
        return directory.employmentsOf(actor.tenantId(), personId);
    }

    /** Change history for a person, newest first. Permission and existence are checked here; audit does not check them. */
    @Transactional(readOnly = true)
    public AuditHistory.Page history(Actor actor, UUID personId, int page, int size) {
        authorization.require(actor, Permission.VIEW_AUDIT_HISTORY);
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "OUT_OF_RANGE", "The page number cannot be negative."));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "OUT_OF_RANGE",
                    "The page size must be between 1 and " + MAX_PAGE_SIZE + "."));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        requirePerson(actor, personId);
        return audit.find(actor.tenantId(), WorkforceAuditor.PERSON, personId, page, size);
    }

    @Transactional(readOnly = true)
    public List<LegalEntity> legalEntities(Actor actor) {
        authorization.require(actor, Permission.VIEW_PEOPLE);
        return reference.legalEntities(actor.tenantId());
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> orgUnits(Actor actor) {
        authorization.require(actor, Permission.VIEW_PEOPLE);
        return reference.orgUnits(actor.tenantId());
    }

    private void requirePerson(Actor actor, UUID personId) {
        if (people.currentVersion(actor.tenantId(), personId).isEmpty()) {
            throw new NotFoundException("The person was not found.");
        }
    }
}
