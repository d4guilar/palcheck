package com.palcheck.shared;

import java.util.Objects;

/**
 * One validation problem: a field path (for example {@code employment.startDate}), a stable machine code
 * (for example {@code REQUIRED}) and an English message. Clients localize from the code.
 */
public record FieldViolation(String field, String code, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
