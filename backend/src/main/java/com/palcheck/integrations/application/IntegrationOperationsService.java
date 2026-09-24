package com.palcheck.integrations.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.audit.api.AuditActor;
import com.palcheck.audit.api.AuditChange;
import com.palcheck.audit.api.AuditRecord;
import com.palcheck.audit.api.AuditRecorder;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.shared.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Event Explorer's operation-side read model and replay (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 7).
 * {@code consumer} is a plain, stable string (the value each {@link OperationConsumer#consumerName()} returns), not
 * an enum: adding a third consumer never touches this class.
 */
@Service
public class IntegrationOperationsService {

    static final String ENTITY_TYPE = "INTEGRATION_OPERATION";

    private final Authorization authorization;
    private final IntegrationOperationRepository operations;
    private final AuditRecorder auditRecorder;
    private final Map<String, OperationConsumer> consumersByName;

    IntegrationOperationsService(Authorization authorization, IntegrationOperationRepository operations,
            AuditRecorder auditRecorder, List<OperationConsumer> consumers) {
        this.authorization = authorization;
        this.operations = operations;
        this.auditRecorder = auditRecorder;
        this.consumersByName = consumers.stream()
                .collect(Collectors.toMap(OperationConsumer::consumerName, c -> c));
    }

    public PageResult<IntegrationOperation> list(Actor actor, String consumer, IntegrationOperation.Status status,
            int page, int size) {
        authorization.require(actor, Permission.VIEW_INTEGRATION_OPERATIONS);
        return operations.list(actor.tenantId(), consumer, status, page, size);
    }

    public IntegrationOperation get(Actor actor, String consumer, String operationKey) {
        authorization.require(actor, Permission.VIEW_INTEGRATION_OPERATIONS);
        return find(actor, consumer, operationKey);
    }

    /**
     * Re-attempts the operation using its own stored payload (ADR-0027): it never needs the original event or the
     * broker, so replay still works after the event has aged out of the topic. Refused for a {@code SUCCEEDED}
     * operation (throws {@link com.palcheck.shared.AlreadySucceededException}); every other outcome is audited as
     * "replay requested", independent of whether this new attempt succeeds or fails again.
     */
    @Transactional
    public IntegrationOperation replay(Actor actor, String consumer, String operationKey) {
        authorization.require(actor, Permission.REPLAY_INTEGRATION_OPERATION);
        IntegrationOperation before = find(actor, consumer, operationKey);
        OperationConsumer target = consumersByName.get(consumer);
        if (target == null) {
            throw new NotFoundException("Unknown integration operation.");
        }
        target.replay(actor.tenantId(), operationKey);
        IntegrationOperation after = find(actor, consumer, operationKey);
        UUID entityId = UUID.nameUUIDFromBytes((consumer + ":" + operationKey).getBytes(StandardCharsets.UTF_8));
        auditRecorder.record(new AuditRecord(actor.tenantId(),
                AuditActor.user(actor.principalId(), actor.displayName()), "ReplayIntegrationOperation",
                UUID.randomUUID(), AuditRecord.Operation.UPDATE, ENTITY_TYPE, entityId, before.attemptCount(),
                ENTITY_TYPE, entityId, List.of(new AuditChange("status", before.status().name(), after.status().name())),
                "Integration operation replay requested"));
        return after;
    }

    private IntegrationOperation find(Actor actor, String consumer, String operationKey) {
        return operations.find(actor.tenantId(), consumer, operationKey)
                .orElseThrow(() -> new NotFoundException("Unknown integration operation."));
    }
}
