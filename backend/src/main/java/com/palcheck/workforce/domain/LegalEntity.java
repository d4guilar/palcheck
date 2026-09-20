package com.palcheck.workforce.domain;

import com.palcheck.shared.TenantId;
import java.util.UUID;

/** An employing organization within a tenant. No legal behavior attaches to it in Phase 1. */
public record LegalEntity(UUID id, TenantId tenantId, String name, String country) {
}
