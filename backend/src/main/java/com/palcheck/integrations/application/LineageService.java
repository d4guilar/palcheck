package com.palcheck.integrations.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.integrations.domain.FieldLineage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Answers "where did this value come from" for a workforce record's fields (docs/PHASE_2_IMPLEMENTATION_PLAN.md
 * section 6). Entity ids are opaque references supplied by the caller (the frontend, which already has them from
 * the record it is displaying); this module never looks them up in {@code workforce} to compose the answer. A
 * cross-tenant or unknown id simply has no lineage rows — that is not sensitive, so it is not reported as an error.
 */
@Service
public class LineageService {

    private final Authorization authorization;
    private final FieldLineageRepository lineage;

    LineageService(Authorization authorization, FieldLineageRepository lineage) {
        this.authorization = authorization;
        this.lineage = lineage;
    }

    /** Combined lineage for a person and, when given, their employment and assignment. */
    public List<FieldLineage> forPerson(Actor actor, UUID personId, UUID employmentId, UUID assignmentId) {
        authorization.require(actor, Permission.VIEW_LINEAGE);
        List<FieldLineage> result = new ArrayList<>(
                lineage.listForEntity(actor.tenantId(), FieldLineage.EntityType.PERSON, personId));
        if (employmentId != null) {
            result.addAll(lineage.listForEntity(actor.tenantId(), FieldLineage.EntityType.EMPLOYMENT, employmentId));
        }
        if (assignmentId != null) {
            result.addAll(lineage.listForEntity(actor.tenantId(), FieldLineage.EntityType.ASSIGNMENT, assignmentId));
        }
        return List.copyOf(result);
    }
}
