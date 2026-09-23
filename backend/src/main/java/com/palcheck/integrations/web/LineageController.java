package com.palcheck.integrations.web;

import com.palcheck.access.api.Actor;
import com.palcheck.integrations.application.LineageService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Where did this value come from" for a workforce record (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 6). The
 * caller supplies the employment/assignment ids it already has; this module never looks them up itself.
 */
@RestController
class LineageController {

    private final LineageService lineage;

    LineageController(LineageService lineage) {
        this.lineage = lineage;
    }

    @GetMapping("/api/people/{personId}/lineage")
    Dtos.ItemsDto<Dtos.FieldLineageDto> forPerson(Actor actor, @PathVariable UUID personId,
            @RequestParam(name = "employmentId", required = false) UUID employmentId,
            @RequestParam(name = "assignmentId", required = false) UUID assignmentId) {
        var items = lineage.forPerson(actor, personId, employmentId, assignmentId).stream().map(Dtos::lineage).toList();
        return new Dtos.ItemsDto<>(items);
    }
}
