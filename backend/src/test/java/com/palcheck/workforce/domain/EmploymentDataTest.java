package com.palcheck.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.shared.FieldViolation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Employment status and type are placeholders for software behavior, not legal classifications, so these tests assert
 * only structural rules (required values, date ordering), never anything jurisdiction-specific.
 */
class EmploymentDataTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 2);

    private static EmploymentData active(String country, LocalDate start, LocalDate end) {
        return new EmploymentData(EmploymentStatus.ACTIVE, EmploymentType.FULL_TIME, country, start, end);
    }

    private static List<String> codes(List<FieldViolation> violations) {
        return violations.stream().map(v -> v.field() + ":" + v.code()).toList();
    }

    @Test
    void aValidEmploymentHasNoViolations() {
        assertThat(active("CR", START, null).normalized().validate("employment.")).isEmpty();
    }

    @Test
    void theCountryIsNormalizedToUpperCase() {
        assertThat(active(" cr ", START, null).normalized().country()).isEqualTo("CR");
    }

    @Test
    void theCountryMustBeARealTwoLetterCode() {
        assertThat(codes(active("XX", START, null).normalized().validate("")))
                .containsExactly("country:UNSUPPORTED_VALUE");
        assertThat(codes(active("Costa Rica", START, null).normalized().validate("")))
                .containsExactly("country:UNSUPPORTED_VALUE");
    }

    @Test
    void requiredValuesAreReportedTogether() {
        var empty = new EmploymentData(null, null, null, null, null).validate("employment.");

        assertThat(codes(empty)).containsExactlyInAnyOrder("employment.status:REQUIRED",
                "employment.employmentType:REQUIRED", "employment.country:REQUIRED", "employment.startDate:REQUIRED");
    }

    @Test
    void theEndDateCannotPrecedeTheStartDateButMayEqualIt() {
        assertThat(codes(active("CR", START, START.minusDays(1)).validate("")))
                .containsExactly("endDate:INVALID_RANGE");
        assertThat(active("CR", START, START).validate("")).isEmpty();
    }

    @Test
    void anEndedEmploymentNeedsAnEndDate() {
        var ended = new EmploymentData(EmploymentStatus.ENDED, EmploymentType.FULL_TIME, "CR", START, null);

        assertThat(codes(ended.validate(""))).containsExactly("endDate:REQUIRED");
        assertThat(new EmploymentData(EmploymentStatus.ENDED, EmploymentType.FULL_TIME, "CR", START,
                START.plusDays(30)).validate("")).isEmpty();
    }

    @Test
    void datesOutsideAPlausibleRangeAreRejected() {
        assertThat(codes(active("CR", LocalDate.of(1800, 1, 1), null).validate("")))
                .containsExactly("startDate:OUT_OF_RANGE");
        assertThat(codes(active("CR", START, LocalDate.of(2200, 1, 1)).validate("")))
                .containsExactly("endDate:OUT_OF_RANGE");
    }

    @Test
    void diffNamesOnlyTheChangedFields() {
        var before = active("CR", START, null);
        var after = new EmploymentData(EmploymentStatus.ENDED, EmploymentType.FULL_TIME, "CR", START,
                START.plusDays(10));

        assertThat(before.diff(after)).containsExactlyInAnyOrder(
                new FieldChange("status", "ACTIVE", "ENDED"),
                new FieldChange("endDate", null, START.plusDays(10).toString()));
    }
}
