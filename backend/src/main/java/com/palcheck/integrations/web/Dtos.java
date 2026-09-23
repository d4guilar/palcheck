package com.palcheck.integrations.web;

import com.palcheck.integrations.application.PageResult;
import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.integrations.domain.FieldLineage;
import com.palcheck.integrations.domain.SyncItem;
import com.palcheck.integrations.domain.SyncRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes of the integrations API. No domain object is serialized directly. */
final class Dtos {

    private Dtos() {
    }

    record SystemDto(UUID id, String code, String displayName, String type, String mode, Instant lastSyncAt, boolean simulationScenariosEnabled) {
    }

    record SyncRunDto(UUID id, UUID systemId, Instant startedAt, Instant completedAt, String status,
            int recordsSeen, int createdCount, int updatedCount, int unchangedCount, int failedCount) {
    }

    record SyncItemDto(UUID id, String externalWorkerId, String status, String action, String failureReason,
            String failureDetail, UUID targetPersonId, UUID targetEmploymentId, Instant occurredAt) {
    }

    record FieldLineageDto(String entityType, UUID entityId, String fieldPath, String sourceSystem,
            String sourceField, String mappingVersion, UUID syncRunId, Instant importedAt, String externalWorkerId) {
    }

    record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    record ItemsDto<T>(List<T> items) {
    }

    static SystemDto system(ExternalSystem s, boolean scenariosEnabled) {
        return new SystemDto(s.id(), s.code(), s.displayName(), s.type(), s.mode(), s.lastSyncAt(), scenariosEnabled);
    }

    static SyncRunDto syncRun(SyncRun r) {
        return new SyncRunDto(r.id(), r.systemId(), r.startedAt(), r.completedAt(), r.status().name(),
                r.recordsSeen(), r.createdCount(), r.updatedCount(), r.unchangedCount(), r.failedCount());
    }

    static SyncItemDto syncItem(SyncItem i) {
        return new SyncItemDto(i.id(), i.externalWorkerId(), i.status().name(),
                i.action() == null ? null : i.action().name(), i.failureReason() == null ? null : i.failureReason().name(),
                i.failureDetail(), i.targetPersonId(), i.targetEmploymentId(), i.occurredAt());
    }

    static FieldLineageDto lineage(FieldLineage l) {
        return new FieldLineageDto(l.entityType().name(), l.entityId(), l.fieldPath(), l.sourceSystem(),
                l.sourceField(), l.mappingVersion(), l.syncRunId(), l.importedAt(), l.externalWorkerId());
    }

    static <S, T> PageDto<T> page(PageResult<S> page, java.util.function.Function<S, T> mapper) {
        return new PageDto<>(page.items().stream().map(mapper).toList(), page.page(), page.size(),
                page.totalItems(), page.totalPages());
    }
}
