package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Small validation and normalization helpers shared by the workforce value objects, so create and patch apply
 * exactly the same rules. Messages are English; clients localize from the stable {@code code}.
 */
final class Rules {

    static final String REQUIRED = "REQUIRED";
    static final String TOO_LONG = "TOO_LONG";
    static final String INVALID_FORMAT = "INVALID_FORMAT";
    static final String UNSUPPORTED_VALUE = "UNSUPPORTED_VALUE";
    static final String OUT_OF_RANGE = "OUT_OF_RANGE";
    static final String INVALID_RANGE = "INVALID_RANGE";

    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private Rules() {
    }

    /** Trims, applies Unicode NFC (so accented names compare consistently), and turns blank into null. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void requiredText(List<FieldViolation> out, String field, String value, int max) {
        if (value == null) {
            out.add(new FieldViolation(field, REQUIRED, "A value is required."));
            return;
        }
        text(out, field, value, max);
    }

    static void optionalText(List<FieldViolation> out, String field, String value, int max) {
        if (value != null) {
            text(out, field, value, max);
        }
    }

    private static void text(List<FieldViolation> out, String field, String value, int max) {
        if (value.codePointCount(0, value.length()) > max) {
            out.add(new FieldViolation(field, TOO_LONG, "Use at most " + max + " characters."));
        } else if (CONTROL.matcher(value).find()) {
            out.add(new FieldViolation(field, INVALID_FORMAT, "Control characters are not allowed."));
        }
    }

    static void requiredDate(List<FieldViolation> out, String field, LocalDate value) {
        if (value == null) {
            out.add(new FieldViolation(field, REQUIRED, "A date is required."));
            return;
        }
        date(out, field, value);
    }

    static void optionalDate(List<FieldViolation> out, String field, LocalDate value) {
        if (value != null) {
            date(out, field, value);
        }
    }

    private static void date(List<FieldViolation> out, String field, LocalDate value) {
        if (value.isBefore(MIN_DATE) || value.isAfter(MAX_DATE)) {
            out.add(new FieldViolation(field, OUT_OF_RANGE, "Use a date between " + MIN_DATE + " and " + MAX_DATE + "."));
        }
    }
}
