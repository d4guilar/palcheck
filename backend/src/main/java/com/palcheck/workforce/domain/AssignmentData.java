package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The editable fields of an Assignment: job title, organizational unit, and reporting manager. */
public record AssignmentData(String title, UUID orgUnitId, UUID managerEmploymentId) {

    public static final int TITLE_MAX = 150;

    public AssignmentData normalized() {
        return new AssignmentData(Rules.clean(title), orgUnitId, managerEmploymentId);
    }

    public List<FieldViolation> validate(String prefix) {
        List<FieldViolation> out = new ArrayList<>();
        Rules.requiredText(out, prefix + "title", title, TITLE_MAX);
        if (orgUnitId == null) {
            out.add(new FieldViolation(prefix + "orgUnitId", Rules.REQUIRED, "An organizational unit is required."));
        }
        return out;
    }

    public List<FieldChange> diff(AssignmentData after) {
        List<FieldChange> out = new ArrayList<>();
        FieldChange.addIfChanged(out, "title", title, after.title);
        FieldChange.addIfChanged(out, "orgUnitId", orgUnitId, after.orgUnitId);
        FieldChange.addIfChanged(out, "managerEmploymentId", managerEmploymentId, after.managerEmploymentId);
        return out;
    }

    public List<FieldChange> asCreation() {
        return new AssignmentData(null, null, null).diff(this);
    }
}
