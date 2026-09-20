package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A partial update of {@link EmploymentData}. Only the end date is optional and can be cleared. */
public record EmploymentPatch(Patch<EmploymentStatus> status, Patch<EmploymentType> employmentType,
        Patch<String> country, Patch<LocalDate> startDate, Patch<LocalDate> endDate) {

    public static EmploymentPatch none() {
        return new EmploymentPatch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent());
    }

    public Result applyTo(EmploymentData current) {
        List<FieldViolation> violations = new ArrayList<>();
        EmploymentData next = new EmploymentData(
                required(status, current.status(), "status", violations),
                required(employmentType, current.employmentType(), "employmentType", violations),
                required(country, current.country(), "country", violations),
                required(startDate, current.startDate(), "startDate", violations),
                endDate.applyTo(current.endDate())).normalized();
        return new Result(next, violations);
    }

    private static <T> T required(Patch<T> patch, T current, String field, List<FieldViolation> out) {
        if (patch.isClear()) {
            out.add(new FieldViolation(field, Rules.REQUIRED, "This field cannot be cleared."));
            return current;
        }
        return patch.applyTo(current);
    }

    public record Result(EmploymentData data, List<FieldViolation> violations) {
    }
}
