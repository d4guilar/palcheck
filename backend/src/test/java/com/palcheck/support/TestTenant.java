package com.palcheck.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import com.palcheck.access.api.Role;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import java.util.Set;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A freshly provisioned tenant with an administrator and a read-only viewer, for tests. Each test creates its own
 * tenant (unique slug), so tests never share state and never need to clean up.
 */
public record TestTenant(TenantId id, String slug, Person admin, Person viewer) {

    public static final String ISSUER = "https://idp.test.invalid/realms/palcheck-test";

    /** A person who can sign in: the identity-provider subject plus the PalCheck principal it maps to. */
    public record Person(UUID principalId, String subject, String displayName) {

        /** Signs the request in as this person, as the OIDC login flow would have. */
        public RequestPostProcessor login() {
            return oidcLogin().idToken(token -> token.issuer(ISSUER).subject(subject));
        }
    }

    public static TestTenant create(TenantProvisioning provisioning, String label) {
        String slug = (label + "-" + UUID.randomUUID().toString().substring(0, 8)).toLowerCase();
        var tenant = provisioning.createTenant(slug, "Test tenant " + label);
        return new TestTenant(tenant.id(), slug,
                signInPerson(provisioning, tenant.id(), "Admin " + label, Role.HR_ADMIN),
                signInPerson(provisioning, tenant.id(), "Viewer " + label, Role.HR_VIEWER));
    }

    /** A principal with exactly the given roles (possibly none), mapped in this tenant. */
    public static Person signInPerson(TenantProvisioning provisioning, TenantId tenant, String displayName,
            Role... roles) {
        String subject = "sub-" + UUID.randomUUID();
        var principal = provisioning.createPrincipal(tenant, ISSUER, subject, displayName, Set.of(roles));
        return new Person(principal.id(), subject, displayName);
    }

    /** A valid identity-provider login that is not mapped to any PalCheck principal. */
    public static RequestPostProcessor unmappedLogin() {
        return oidcLogin().idToken(token -> token.issuer(ISSUER).subject("unmapped-" + UUID.randomUUID()));
    }
}
