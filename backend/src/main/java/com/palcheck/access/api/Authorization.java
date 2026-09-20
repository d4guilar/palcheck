package com.palcheck.access.api;

/** Permission checks for application services. Fails closed with a FORBIDDEN error. */
public interface Authorization {

    /** Requires every listed permission; throws {@link com.palcheck.shared.ForbiddenException} otherwise. */
    void require(Actor actor, Permission... permissions);
}
