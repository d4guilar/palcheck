package com.palcheck.audit.api;

/** One field's before and after value, rendered as text. Null means "no value". */
public record AuditChange(String field, String before, String after) {
}
