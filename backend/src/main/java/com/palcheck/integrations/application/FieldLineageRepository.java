package com.palcheck.integrations.application;

import com.palcheck.integrations.domain.FieldLineage;
import com.palcheck.shared.TenantId;
import java.util.List;
import java.util.UUID;

public interface FieldLineageRepository {

    /** Replaces the field's current provenance in place (one row per governed field; ADR-0025 decision 3). */
    void put(FieldLineage lineage);

    List<FieldLineage> listForEntity(TenantId tenantId, FieldLineage.EntityType entityType, UUID entityId);
}
