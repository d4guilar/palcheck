package com.palcheck.access.api;

import java.util.List;
import java.util.UUID;

/** What the SPA needs to bootstrap: who is signed in, in which tenant, and what they may do. */
public record SessionInfo(User user, Tenant tenant, List<Permission> permissions) {

    public record User(UUID id, String displayName) {
    }

    public record Tenant(UUID id, String slug, String name) {
    }
}
