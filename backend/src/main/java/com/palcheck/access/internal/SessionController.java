package com.palcheck.access.internal;

import com.palcheck.access.api.Permission;
import com.palcheck.access.api.SessionInfo;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstraps the single-page app. 401 means "sign in"; 403 TENANT_ACCESS_DENIED means the identity is valid but not
 * assigned to an active PalCheck tenant.
 */
@RestController
@RequestMapping("/api/session")
class SessionController {

    private final ActorResolver resolver;

    SessionController(ActorResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    SessionInfo current() {
        var context = resolver.resolveContext(SecurityContextHolder.getContext().getAuthentication());
        List<Permission> permissions = ActorResolver.permissionsOf(context.roles()).stream().sorted().toList();
        return new SessionInfo(
                new SessionInfo.User(context.principalId(), context.displayName()),
                new SessionInfo.Tenant(context.tenantId(), context.tenantSlug(), context.tenantName()),
                permissions);
    }
}
