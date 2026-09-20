package com.palcheck.access.api;

import java.util.EnumSet;
import java.util.Set;

/**
 * A named bundle of permissions. The database records which roles a principal holds; what a role allows is defined
 * here, in code, so it is reviewed and versioned with the application.
 */
public enum Role {
    /** Can view and change workforce records and read their change history. */
    HR_ADMIN(EnumSet.allOf(Permission.class)),
    /** Read-only access to people and employment. No change history. */
    HR_VIEWER(EnumSet.of(Permission.VIEW_PEOPLE, Permission.VIEW_PERSON, Permission.VIEW_EMPLOYMENT));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
