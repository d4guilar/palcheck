package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.util.UUID;

/** A node in the organizational hierarchy of a tenant (department, team). {@code parentId} is null for a root. */
public record OrgUnit(UUID id, TenantId tenantId, UUID parentId, String name) {
}
