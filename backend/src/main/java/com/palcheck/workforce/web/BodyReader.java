package com.palcheck.workforce.web;

import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.ValidationException;
import com.palcheck.workforce.domain.Patch;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads a JSON object body field by field, collecting every problem instead of stopping at the first. Unknown fields
 * are rejected, which also refuses a client-supplied {@code tenantId} or any other field the API does not define.
 * Typed values only; range and business rules are the domain's job.
 *
 * <p>For partial updates, {@code patch*} methods distinguish an absent field (leave unchanged) from JSON null
 * (clear) and from a value (set).
 */
final class BodyReader {

    static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    static final String INVALID_TYPE = "INVALID_TYPE";
    static final String INVALID_FORMAT = "INVALID_FORMAT";
    static final String UNSUPPORTED_VALUE = "UNSUPPORTED_VALUE";

    private final Map<String, Object> data;
    private final String prefix;
    private final List<FieldViolation> violations;
    private final Set<String> used = new HashSet<>();

    BodyReader(Map<String, Object> data, String prefix, List<FieldViolation> violations) {
        this.data = data == null ? Map.of() : data;
        this.prefix = prefix;
        this.violations = violations;
    }

    boolean has(String name) {
        used.add(name);
        return data.containsKey(name);
    }

    // --- plain values (absent and null are both "no value") ----------------------------------------------------

    String string(String name) {
        Object value = raw(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        typeError(name, "text");
        return null;
    }

    UUID uuid(String name) {
        String text = string(name);
        return text == null ? null : parseUuid(name, text);
    }

    LocalDate date(String name) {
        String text = string(name);
        return text == null ? null : parseDate(name, text);
    }

    <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        String text = string(name);
        return text == null ? null : parseEnum(name, text, type);
    }

    /** A required whole number such as {@code expectedVersion}. */
    Long integer(String name) {
        Object value = raw(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Long l) {
            return l;
        }
        typeError(name, "a whole number");
        return null;
    }

    // --- patch values (absent, clear, set) ---------------------------------------------------------------------

    Patch<String> patchString(String name) {
        if (!has(name)) {
            return Patch.absent();
        }
        Object value = data.get(name);
        if (value == null) {
            return Patch.clear();
        }
        if (value instanceof String s) {
            return Patch.set(s);
        }
        typeError(name, "text");
        return Patch.absent();
    }

    Patch<UUID> patchUuid(String name) {
        Patch<String> text = patchString(name);
        if (!text.isSet()) {
            return text.isClear() ? Patch.clear() : Patch.absent();
        }
        UUID parsed = parseUuid(name, text.value());
        return parsed == null ? Patch.absent() : Patch.set(parsed);
    }

    Patch<LocalDate> patchDate(String name) {
        Patch<String> text = patchString(name);
        if (!text.isSet()) {
            return text.isClear() ? Patch.clear() : Patch.absent();
        }
        LocalDate parsed = parseDate(name, text.value());
        return parsed == null ? Patch.absent() : Patch.set(parsed);
    }

    <E extends Enum<E>> Patch<E> patchEnum(String name, Class<E> type) {
        Patch<String> text = patchString(name);
        if (!text.isSet()) {
            return text.isClear() ? Patch.clear() : Patch.absent();
        }
        E parsed = parseEnum(name, text.value(), type);
        return parsed == null ? Patch.absent() : Patch.set(parsed);
    }

    // --- structure ---------------------------------------------------------------------------------------------

    /** A nested object. Absent or null yields an empty reader, so missing required fields are reported individually. */
    @SuppressWarnings("unchecked")
    BodyReader object(String name) {
        Object value = raw(name);
        if (value == null) {
            return new BodyReader(Map.of(), prefix + name + ".", violations);
        }
        if (value instanceof Map<?, ?> map) {
            return new BodyReader((Map<String, Object>) map, prefix + name + ".", violations);
        }
        typeError(name, "an object");
        return new BodyReader(Map.of(), prefix + name + ".", violations);
    }

    /** Adds an UNKNOWN_FIELD violation for every key that was never read. Call after reading everything. */
    void rejectUnknown() {
        for (String key : data.keySet()) {
            if (!used.contains(key)) {
                violations.add(new FieldViolation(prefix + key, UNKNOWN_FIELD, "This field is not recognized."));
            }
        }
    }

    /** Throws if any violation was collected. */
    static void failIfInvalid(List<FieldViolation> violations) {
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    // --- internals ---------------------------------------------------------------------------------------------

    private Object raw(String name) {
        used.add(name);
        return data.get(name);
    }

    private void typeError(String name, String expected) {
        violations.add(new FieldViolation(prefix + name, INVALID_TYPE, "Expected " + expected + "."));
    }

    private UUID parseUuid(String name, String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(prefix + name, INVALID_FORMAT, "Expected an identifier."));
            return null;
        }
    }

    private LocalDate parseDate(String name, String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            violations.add(new FieldViolation(prefix + name, INVALID_FORMAT, "Expected a date in the form YYYY-MM-DD."));
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(String name, String text, Class<E> type) {
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(prefix + name, UNSUPPORTED_VALUE,
                    "Use one of: " + String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
                            .map(Enum::name).toList()) + "."));
            return null;
        }
    }
}
