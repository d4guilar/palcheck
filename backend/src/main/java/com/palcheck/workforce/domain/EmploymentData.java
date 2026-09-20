package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The editable fields of an Employment. The legal entity is fixed at creation and is not part of this value. */
public record EmploymentData(EmploymentStatus status, EmploymentType employmentType, String country,
        LocalDate startDate, LocalDate endDate) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public EmploymentData normalized() {
        String cleaned = Rules.clean(country);
        return new EmploymentData(status, employmentType, cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT),
                startDate, endDate);
    }

    public List<FieldViolation> validate(String prefix) {
        List<FieldViolation> out = new ArrayList<>();
        if (status == null) {
            out.add(new FieldViolation(prefix + "status", Rules.REQUIRED, "A status is required."));
        }
        if (employmentType == null) {
            out.add(new FieldViolation(prefix + "employmentType", Rules.REQUIRED, "An employment type is required."));
        }
        if (country == null) {
            out.add(new FieldViolation(prefix + "country", Rules.REQUIRED, "A country is required."));
        } else if (!ISO_COUNTRIES.contains(country)) {
            out.add(new FieldViolation(prefix + "country", Rules.UNSUPPORTED_VALUE,
                    "Use a two-letter ISO 3166-1 country code."));
        }
        Rules.requiredDate(out, prefix + "startDate", startDate);
        Rules.optionalDate(out, prefix + "endDate", endDate);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            out.add(new FieldViolation(prefix + "endDate", Rules.INVALID_RANGE,
                    "The end date cannot be before the start date."));
        }
        if (status == EmploymentStatus.ENDED && endDate == null) {
            out.add(new FieldViolation(prefix + "endDate", Rules.REQUIRED, "An ended employment needs an end date."));
        }
        return out;
    }

    public List<FieldChange> diff(EmploymentData after) {
        List<FieldChange> out = new ArrayList<>();
        FieldChange.addIfChanged(out, "status", status, after.status);
        FieldChange.addIfChanged(out, "employmentType", employmentType, after.employmentType);
        FieldChange.addIfChanged(out, "country", country, after.country);
        FieldChange.addIfChanged(out, "startDate", startDate, after.startDate);
        FieldChange.addIfChanged(out, "endDate", endDate, after.endDate);
        return out;
    }

    public List<FieldChange> asCreation() {
        return new EmploymentData(null, null, null, null, null).diff(this);
    }
}
