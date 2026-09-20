package com.palcheck.access.api;

import com.palcheck.shared.TenantId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The trusted identity performing an operation: who, in which tenant, allowed to do what.
 *
 * <p>Only the {@code access} module constructs an Actor, after resolving the authenticated principal through the
 * server-side mapping. Controllers and application services receive one; they never build one from request data
 * (an ArchUnit test enforces this). The tenant is therefore never taken from a URL, header or body.
 */
public record Actor(UUID principalId, TenantId tenantId, String displayName, Set<Permission> permissions) {

    public Actor {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(displayName, "displayName");
        permissions = Set.copyOf(permissions);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
