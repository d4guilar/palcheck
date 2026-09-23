package com.palcheck.integrations.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * The *current* source of one field on one workforce record (ADR-0025 decision 3): read-only metadata answering
 * "where did this value come from," never consulted to decide whether a write is allowed. {@code entityId} is an
 * opaque reference to a {@code workforce} record; this module never joins into {@code workforce}'s tables to read it.
 */
public record FieldLineage(TenantId tenantId, EntityType entityType, UUID entityId, String fieldPath,
        String sourceSystem, String sourceField, String mappingVersion, UUID syncRunId, UUID syncItemId,
        Instant importedAt, String externalWorkerId) {

    public enum EntityType { PERSON, EMPLOYMENT, ASSIGNMENT }
}
