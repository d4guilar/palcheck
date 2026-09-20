package com.palcheck.access.internal;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.shared.ForbiddenException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PermissionAuthorization implements Authorization {

    @Override
    public void require(Actor actor, Permission... permissions) {
        var missing = Arrays.stream(permissions).filter(p -> !actor.has(p)).toList();
        if (!missing.isEmpty()) {
            String names = missing.stream().map(Permission::name).collect(Collectors.joining(", "));
            throw new ForbiddenException("You do not have the required permission: " + names + ".");
        }
    }
}
