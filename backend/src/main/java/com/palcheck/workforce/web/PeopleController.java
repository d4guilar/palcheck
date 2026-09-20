package com.palcheck.workforce.web;

import com.palcheck.access.api.Actor;
import com.palcheck.shared.FieldViolation;
import com.palcheck.workforce.application.EditPersonService;
import com.palcheck.workforce.application.PeopleQueryService;
import com.palcheck.workforce.application.RegisterWorkerCommand;
import com.palcheck.workforce.application.RegisterWorkerService;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import com.palcheck.workforce.domain.PersonData;
import com.palcheck.workforce.domain.PersonPatch;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * People: directory, record detail, creation, identity edits, employments and change history. Controllers translate
 * HTTP to typed commands and back; permission, tenant scoping, validation rules, concurrency and audit live below.
 */
@RestController
@RequestMapping("/api/people")
class PeopleController {

    private final PeopleQueryService queries;
    private final RegisterWorkerService register;
    private final EditPersonService editPerson;

    PeopleController(PeopleQueryService queries, RegisterWorkerService register, EditPersonService editPerson) {
        this.queries = queries;
        this.register = register;
        this.editPerson = editPerson;
    }

    @GetMapping
    Dtos.PageDto<Dtos.DirectoryItemDto> list(Actor actor,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + PeopleQueryService.DEFAULT_PAGE_SIZE) int size) {
        return Dtos.page(queries.list(actor, q, page, size), Dtos::item);
    }

    @GetMapping("/{personId}")
    Dtos.PersonDto get(Actor actor, @PathVariable UUID personId) {
        return Dtos.person(queries.person(actor, personId));
    }

    @GetMapping("/{personId}/employments")
    Dtos.ItemsDto<Dtos.EmploymentDto> employments(Actor actor, @PathVariable UUID personId) {
        return new Dtos.ItemsDto<>(queries.employments(actor, personId).stream().map(Dtos::employment).toList());
    }

    @GetMapping("/{personId}/audit")
    Dtos.PageDto<Dtos.AuditEntryDto> audit(Actor actor, @PathVariable UUID personId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return Dtos.auditPage(queries.history(actor, personId, page, size));
    }

    @PostMapping
    ResponseEntity<Dtos.PersonDto> create(Actor actor,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        String key = IdempotencyKeys.read(idempotencyKey, true);

        List<FieldViolation> violations = new ArrayList<>();
        var root = new BodyReader(body, "", violations);
        var person = root.object("person");
        var employment = root.object("employment");
        var assignment = root.object("assignment");
        String reason = root.string("reason");

        var personData = new PersonData(person.string("firstName"), person.string("middleName"),
                person.string("lastName"), person.string("preferredName"), person.string("workEmail"),
                person.string("locale"));
        UUID legalEntityId = employment.uuid("legalEntityId");
        var employmentData = new EmploymentData(employment.enumValue("status", EmploymentStatus.class),
                employment.enumValue("employmentType", EmploymentType.class), employment.string("country"),
                employment.date("startDate"), employment.date("endDate"));
        var assignmentData = new AssignmentData(assignment.string("title"), assignment.uuid("orgUnitId"),
                assignment.uuid("managerEmploymentId"));
        person.rejectUnknown();
        employment.rejectUnknown();
        assignment.rejectUnknown();
        root.rejectUnknown();
        if (!violations.isEmpty()) {
            // Report the domain rules too, so the caller sees every problem at once rather than one class at a time.
            var parseProblems = violations.stream().map(FieldViolation::field).collect(java.util.stream.Collectors.toSet());
            List<FieldViolation> domain = new ArrayList<>();
            domain.addAll(personData.normalized().validate("person."));
            domain.addAll(employmentData.normalized().validate("employment."));
            domain.addAll(assignmentData.normalized().validate("assignment."));
            domain.stream().filter(v -> !parseProblems.contains(v.field())).forEach(violations::add);
            BodyReader.failIfInvalid(violations);
        }

        var result = register.register(actor,
                new RegisterWorkerCommand(personData, legalEntityId, employmentData, assignmentData, reason), key);
        var response = ResponseEntity.created(URI.create("/api/people/" + result.person().id()));
        if (result.replayed()) {
            response.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return response.body(Dtos.person(result.person()));
    }

    @PatchMapping("/{personId}")
    ResponseEntity<Dtos.PersonDto> update(Actor actor, @PathVariable UUID personId,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        String key = IdempotencyKeys.read(idempotencyKey, false);

        List<FieldViolation> violations = new ArrayList<>();
        var root = new BodyReader(body, "", violations);
        Long expectedVersion = ExpectedVersion.read(root, violations);
        var patch = new PersonPatch(root.patchString("firstName"), root.patchString("middleName"),
                root.patchString("lastName"), root.patchString("preferredName"), root.patchString("workEmail"),
                root.patchString("locale"));
        String reason = root.string("reason");
        root.rejectUnknown();
        BodyReader.failIfInvalid(violations);

        var result = editPerson.update(actor, personId, expectedVersion, patch, reason, key);
        var response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return response.body(Dtos.person(result.person()));
    }
}
