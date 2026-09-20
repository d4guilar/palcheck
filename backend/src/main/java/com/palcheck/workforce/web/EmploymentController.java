package com.palcheck.workforce.web;

import com.palcheck.access.api.Actor;
import com.palcheck.shared.FieldViolation;
import com.palcheck.workforce.application.EditEmploymentService;
import com.palcheck.workforce.domain.AssignmentPatch;
import com.palcheck.workforce.domain.EmploymentPatch;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Edits an employment or its current assignment. Each has its own version, and each response returns the refreshed employment. */
@RestController
@RequestMapping("/api")
class EmploymentController {

    private final EditEmploymentService service;

    EmploymentController(EditEmploymentService service) {
        this.service = service;
    }

    @PatchMapping("/employments/{employmentId}")
    ResponseEntity<Dtos.EmploymentDto> updateEmployment(Actor actor, @PathVariable UUID employmentId,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        String key = IdempotencyKeys.read(idempotencyKey, false);

        List<FieldViolation> violations = new ArrayList<>();
        var root = new BodyReader(body, "", violations);
        Long expectedVersion = ExpectedVersion.read(root, violations);
        var patch = new EmploymentPatch(root.patchEnum("status", EmploymentStatus.class),
                root.patchEnum("employmentType", EmploymentType.class), root.patchString("country"),
                root.patchDate("startDate"), root.patchDate("endDate"));
        String reason = root.string("reason");
        root.rejectUnknown();
        BodyReader.failIfInvalid(violations);

        var result = service.updateEmployment(actor, employmentId, expectedVersion, patch, reason, key);
        var response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return response.body(Dtos.employment(result.view()));
    }

    @PatchMapping("/assignments/{assignmentId}")
    ResponseEntity<Dtos.EmploymentDto> updateAssignment(Actor actor, @PathVariable UUID assignmentId,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        String key = IdempotencyKeys.read(idempotencyKey, false);

        List<FieldViolation> violations = new ArrayList<>();
        var root = new BodyReader(body, "", violations);
        Long expectedVersion = ExpectedVersion.read(root, violations);
        var patch = new AssignmentPatch(root.patchString("title"), root.patchUuid("orgUnitId"),
                root.patchUuid("managerEmploymentId"));
        String reason = root.string("reason");
        root.rejectUnknown();
        BodyReader.failIfInvalid(violations);

        var result = service.updateAssignment(actor, assignmentId, expectedVersion, patch, reason, key);
        var response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return response.body(Dtos.employment(result.view()));
    }
}
