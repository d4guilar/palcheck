package com.palcheck.access.internal;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Permission;
import com.palcheck.access.api.Role;
import com.palcheck.platform.logging.LogKeys;
import com.palcheck.shared.TenantAccessDeniedException;
import com.palcheck.shared.TenantId;
import com.palcheck.shared.UnauthenticatedException;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Turns the authenticated OIDC identity into a trusted {@link Actor}. The tenant and roles come only from the
 * server-side principal mapping on (issuer, subject); nothing in the request can influence them. Roles are read on
 * every resolution, so revoking a role or disabling a principal takes effect on the next request.
 */
@Component
class ActorResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ActorResolver.class);

    private final JdbcAccess access;

    ActorResolver(JdbcAccess access) {
        this.access = access;
    }

    /** Resolves the actor for the current security context. */
    Actor resolveCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return resolve(authentication);
    }

    JdbcAccess.PrincipalContext resolveContext(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OidcUser oidc)) {
            throw new UnauthenticatedException("Sign in to continue.");
        }
        String issuer = oidc.getIssuer() == null ? null : oidc.getIssuer().toString();
        String subject = oidc.getSubject();
        if (issuer == null || subject == null) {
            throw new UnauthenticatedException("Sign in to continue.");
        }
        var context = access.findContext(issuer, subject).orElse(null);
        if (context == null || !context.principalActive() || !context.tenantActive()) {
            LOG.atInfo()
                    .addKeyValue(LogKeys.OPERATION, "access.resolve")
                    .addKeyValue(LogKeys.RESULT, "denied")
                    .log("Authenticated identity has no active PalCheck principal or tenant");
            throw new TenantAccessDeniedException(
                    "Your account is not assigned to an active PalCheck tenant. Contact your administrator.");
        }
        MDC.put(LogKeys.TENANT_ID, context.tenantId().toString());
        MDC.put(LogKeys.USER_ID, context.principalId().toString());
        return context;
    }

    Actor resolve(Authentication authentication) {
        var context = resolveContext(authentication);
        return new Actor(context.principalId(), TenantId.of(context.tenantId()), context.displayName(),
                permissionsOf(context.roles()));
    }

    static Set<Permission> permissionsOf(Set<Role> roles) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> permissions.addAll(role.permissions()));
        return permissions;
    }
}
