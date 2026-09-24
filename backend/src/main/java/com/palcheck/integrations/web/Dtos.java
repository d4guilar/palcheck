package com.palcheck.integrations.web;

import com.palcheck.integrations.application.PageResult;
import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.integrations.domain.FieldLineage;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.integrations.domain.SyncItem;
import com.palcheck.integrations.domain.SyncRun;
import com.palcheck.messaging.api.OutboxEventView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    record IntegrationOperationDto(String consumer, String operationKey, String status, int attemptCount,
            String lastError, Instant nextAttemptAt, Instant completedAt, UUID sourceEventId,
            Map<String, Object> payload, Instant createdAt, Instant updatedAt) {
    }

    record OutboxEventDto(UUID eventId, String eventType, String schemaVersion, String aggregateType,
            String aggregateId, Instant occurredAt, String correlationId, String causationId,
            Map<String, Object> payload, String status, int attemptCount, Instant publishedAt, Instant createdAt) {
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

    static IntegrationOperationDto integrationOperation(IntegrationOperation o) {
        return new IntegrationOperationDto(o.consumer(), o.operationKey(), o.status().name(), o.attemptCount(),
                o.lastError(), o.nextAttemptAt(), o.completedAt(), o.sourceEventId(), o.payload(), o.createdAt(),
                o.updatedAt());
    }

    static OutboxEventDto outboxEvent(OutboxEventView e) {
        return new OutboxEventDto(e.eventId(), e.eventType(), e.schemaVersion(), e.aggregateType(), e.aggregateId(),
                e.occurredAt(), e.correlationId(), e.causationId(), e.payload(), e.status(), e.attemptCount(),
                e.publishedAt(), e.createdAt());
    }
}
