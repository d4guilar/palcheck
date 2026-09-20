package com.palcheck.audit.api;

import java.util.Objects;
import java.util.UUID;

/** Who performed a change. A snapshot of the display name is kept so history stays readable if the person is renamed. */
public record AuditActor(Type type, UUID id, String displayName) {

    public enum Type { USER, SYSTEM }

    public AuditActor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static AuditActor user(UUID principalId, String displayName) {
        return new AuditActor(Type.USER, Objects.requireNonNull(principalId, "principalId"), displayName);
    }

    /** A non-interactive actor such as the development data seeder. */
    public static AuditActor system(String name) {
        return new AuditActor(Type.SYSTEM, null, name);
    }
}
