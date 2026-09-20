package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A partial update of {@link AssignmentData}. Only the manager is optional and can be cleared. */
public record AssignmentPatch(Patch<String> title, Patch<UUID> orgUnitId, Patch<UUID> managerEmploymentId) {

    public static AssignmentPatch none() {
        return new AssignmentPatch(Patch.absent(), Patch.absent(), Patch.absent());
    }

    public Result applyTo(AssignmentData current) {
        List<FieldViolation> violations = new ArrayList<>();
        AssignmentData next = new AssignmentData(
                required(title, current.title(), "title", violations),
                required(orgUnitId, current.orgUnitId(), "orgUnitId", violations),
                managerEmploymentId.applyTo(current.managerEmploymentId())).normalized();
        return new Result(next, violations);
    }

    private static <T> T required(Patch<T> patch, T current, String field, List<FieldViolation> out) {
        if (patch.isClear()) {
            out.add(new FieldViolation(field, Rules.REQUIRED, "This field cannot be cleared."));
            return current;
        }
        return patch.applyTo(current);
    }

    public record Result(AssignmentData data, List<FieldViolation> violations) {
    }
}
