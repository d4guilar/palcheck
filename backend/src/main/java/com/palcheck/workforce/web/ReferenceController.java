package com.palcheck.workforce.web;

import com.palcheck.access.api.Actor;
import com.palcheck.workforce.application.PeopleQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant reference lists used by forms: legal entities and organizational units. Read-only in Phase 1. */
@RestController
@RequestMapping("/api")
class ReferenceController {

    private final PeopleQueryService queries;

    ReferenceController(PeopleQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/manager-candidates")
    Dtos.PageDto<Dtos.ManagerCandidateDto> managers(Actor actor,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.UUID excludingEmploymentId) {
        return Dtos.page(queries.managers(actor, q, page, size, excludingEmploymentId),
                c -> new Dtos.ManagerCandidateDto(c.employmentId(), c.personId(), c.displayName(), c.title(), c.legalEntityName(), c.status()));
    }

    @GetMapping("/legal-entities")
    Dtos.ItemsDto<Dtos.LegalEntityDto> legalEntities(Actor actor) {
        return new Dtos.ItemsDto<>(queries.legalEntities(actor).stream().map(Dtos::legalEntity).toList());
    }

    @GetMapping("/org-units")
    Dtos.ItemsDto<Dtos.OrgUnitDto> orgUnits(Actor actor) {
        return new Dtos.ItemsDto<>(Dtos.orgUnits(queries.orgUnits(actor)));
    }
}
