package com.palcheck.workforce.domain;

import java.util.List;
import java.util.Objects;

/** A single field that differs between two states, with values rendered as text for the change history. */
public record FieldChange(String field, String before, String after) {

    public static void addIfChanged(List<FieldChange> out, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            out.add(new FieldChange(field, before == null ? null : before.toString(),
                    after == null ? null : after.toString()));
        }
    }
}
