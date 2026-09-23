package com.palcheck.workforce.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Small JDBC conversion helpers. The PostgreSQL driver does not bind {@link Instant} directly. */
final class Sql {

    private Sql() {
    }

    static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** As {@link #timestamp(Instant)}, but null-safe for optional columns (Phase 2 ownership metadata). */
    static OffsetDateTime timestampOrNull(Instant instant) {
        return instant == null ? null : timestamp(instant);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    /** As {@link #instant(ResultSet, String)}, but null-safe for optional columns (Phase 2 ownership metadata). */
    static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    /** Escapes LIKE metacharacters so user input is matched literally. */
    static String likeContains(String raw) {
        String escaped = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
