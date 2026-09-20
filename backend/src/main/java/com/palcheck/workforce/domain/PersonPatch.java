package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.util.ArrayList;
import java.util.List;

/** A partial update of {@link PersonData}. Required fields cannot be cleared; optional ones can. */
public record PersonPatch(Patch<String> firstName, Patch<String> middleName, Patch<String> lastName,
        Patch<String> preferredName, Patch<String> workEmail, Patch<String> locale) {

    public static PersonPatch none() {
        return new PersonPatch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent());
    }

    /** Applies the patch to {@code current}; a cleared required field is a violation, not a silent removal. */
    public Result applyTo(PersonData current) {
        List<FieldViolation> violations = new ArrayList<>();
        PersonData next = new PersonData(
                required(firstName, current.firstName(), "firstName", violations),
                middleName.applyTo(current.middleName()),
                required(lastName, current.lastName(), "lastName", violations),
                preferredName.applyTo(current.preferredName()),
                required(workEmail, current.workEmail(), "workEmail", violations),
                required(locale, current.locale(), "locale", violations)).normalized();
        return new Result(next, violations);
    }

    private static String required(Patch<String> patch, String current, String field, List<FieldViolation> out) {
        if (patch.isClear()) {
            out.add(new FieldViolation(field, Rules.REQUIRED, "This field cannot be cleared."));
            return current;
        }
        return patch.applyTo(current);
    }

    public record Result(PersonData data, List<FieldViolation> violations) {
    }
}
