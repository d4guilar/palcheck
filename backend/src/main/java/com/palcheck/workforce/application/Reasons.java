package com.palcheck.workforce.application;

import com.palcheck.shared.FieldViolation;
import java.text.Normalizer;
import java.util.List;

/** The optional free-text reason that accompanies a change and is stored in the change history. */
final class Reasons {

    static final int MAX = 500;

    private Reasons() {
    }

    static String clean(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = Normalizer.normalize(reason, Normalizer.Form.NFC).strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void validate(String reason, List<FieldViolation> out) {
        if (reason != null && reason.codePointCount(0, reason.length()) > MAX) {
            out.add(new FieldViolation("reason", "TOO_LONG", "Use at most " + MAX + " characters."));
        }
    }
}
