package com.palcheck.workforce.web;

import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.ValidationException;
import java.util.regex.Pattern;

/** Validates the {@code Idempotency-Key} request header. */
final class IdempotencyKeys {

    static final String HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotency-Replayed";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");

    private IdempotencyKeys() {
    }

    /** @return the key, or null if absent and not required */
    static String read(String header, boolean required) {
        if (header == null || header.isBlank()) {
            if (required) {
                throw new ValidationException(new FieldViolation(HEADER, "REQUIRED",
                        "Send an Idempotency-Key header (a unique value per submission, for example a UUID)."));
            }
            return null;
        }
        String key = header.strip();
        if (!VALID.matcher(key).matches()) {
            throw new ValidationException(new FieldViolation(HEADER, "INVALID_FORMAT",
                    "Use 1 to 128 characters: letters, digits, and _ . : -"));
        }
        return key;
    }
}
