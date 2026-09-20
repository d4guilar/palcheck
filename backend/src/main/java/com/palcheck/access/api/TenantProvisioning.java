package com.palcheck.access.api;

import com.palcheck.shared.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Out-of-band provisioning of tenants and principals, used by the development seeder and by tests. Phase 1 has no
 * administration UI or API for this, deliberately: principal and role administration is a later capability.
 */
public interface TenantProvisioning {

    TenantRef createTenant(String slug, String name);

    Optional<TenantRef> findTenantBySlug(String slug);

    PrincipalRef createPrincipal(TenantId tenantId, String issuer, String subject, String displayName,
            Set<Role> roles);

    Optional<PrincipalRef> findPrincipal(String issuer, String subject);

    /**
     * The trusted {@link Actor} for an existing, active principal, for out-of-band tooling such as the development
     * seeder. It is built exactly as request-time resolution builds it (tenant and permissions from the stored
     * principal and its roles), so seeded changes are attributed to a real principal.
     */
    Actor actorFor(UUID principalId);

    /** Suspends or reactivates a tenant (a suspended tenant's principals are denied access). */
    void setTenantActive(TenantId tenantId, boolean active);

    /** Disables or enables a principal. */
    void setPrincipalActive(UUID principalId, boolean active);

    record TenantRef(TenantId id, String slug, String name) {
    }

    record PrincipalRef(UUID id, TenantId tenantId, String issuer, String subject, String displayName) {
    }
}
