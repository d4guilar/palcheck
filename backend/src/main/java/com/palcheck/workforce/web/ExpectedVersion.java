package com.palcheck.workforce.web;

import com.palcheck.shared.FieldViolation;
import java.util.List;

/** Reads the required {@code expectedVersion} of a write: the version the client read and is editing. */
final class ExpectedVersion {

    private ExpectedVersion() {
    }

    /** @return the version, or a placeholder (0) when invalid; the violation is recorded and thrown by the caller */
    static Long read(BodyReader body, List<FieldViolation> violations) {
        Long value = body.integer("expectedVersion");
        if (value == null) {
            if (violations.stream().noneMatch(v -> v.field().equals("expectedVersion"))) {
                violations.add(new FieldViolation("expectedVersion", "REQUIRED",
                        "Send the version you are editing (expectedVersion)."));
            }
            return 0L;
        }
        if (value < 1) {
            violations.add(new FieldViolation("expectedVersion", "OUT_OF_RANGE", "The version must be at least 1."));
        }
        return value;
    }
}
