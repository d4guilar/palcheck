package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.audit.api.AuditActor;
import com.palcheck.audit.api.AuditChange;
import com.palcheck.audit.api.AuditRecord;
import com.palcheck.audit.api.AuditRecorder;
import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.FieldChange;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates workforce changes into audit records. Every record belongs to the history of a Person, whatever entity
 * changed, so one screen shows the whole record. Must be called inside the business transaction.
 */
@Component
class WorkforceAuditor {

    static final String PERSON = "PERSON";
    static final String EMPLOYMENT = "EMPLOYMENT";
    static final String ASSIGNMENT = "ASSIGNMENT";

    private final AuditRecorder recorder;

    WorkforceAuditor(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    void created(Actor actor, String command, UUID changeSetId, String entityType, UUID entityId, long version,
            UUID personId, List<FieldChange> changes, String reason) {
        record(actor, command, changeSetId, AuditRecord.Operation.CREATE, entityType, entityId, version, personId,
                changes, reason);
    }

    void updated(Actor actor, String command, UUID changeSetId, String entityType, UUID entityId, long version,
            UUID personId, List<FieldChange> changes, String reason) {
        record(actor, command, changeSetId, AuditRecord.Operation.UPDATE, entityType, entityId, version, personId,
                changes, reason);
    }

    /** As {@link #created}, but attributed to a synchronization, never a human (Phase 2, ADR-0025 decision 8). */
    void createdBySystem(String systemName, String command, UUID changeSetId, String entityType, UUID entityId,
            long version, TenantId tenantId, UUID personId, List<FieldChange> changes, String reason) {
        record(tenantId, AuditActor.system(systemName), command, changeSetId, AuditRecord.Operation.CREATE,
                entityType, entityId, version, personId, changes, reason);
    }

    /** As {@link #updated}, but attributed to a synchronization, never a human (Phase 2, ADR-0025 decision 8). */
    void updatedBySystem(String systemName, String command, UUID changeSetId, String entityType, UUID entityId,
            long version, TenantId tenantId, UUID personId, List<FieldChange> changes, String reason) {
        record(tenantId, AuditActor.system(systemName), command, changeSetId, AuditRecord.Operation.UPDATE,
                entityType, entityId, version, personId, changes, reason);
    }

    private void record(Actor actor, String command, UUID changeSetId, AuditRecord.Operation operation,
            String entityType, UUID entityId, long version, UUID personId, List<FieldChange> changes, String reason) {
        record(actor.tenantId(), AuditActor.user(actor.principalId(), actor.displayName()), command, changeSetId,
                operation, entityType, entityId, version, personId, changes, reason);
    }

    private void record(TenantId tenantId, AuditActor actor, String command, UUID changeSetId,
            AuditRecord.Operation operation, String entityType, UUID entityId, long version, UUID personId,
            List<FieldChange> changes, String reason) {
        recorder.record(new AuditRecord(tenantId, actor, command, changeSetId, operation, entityType, entityId,
                version, PERSON, personId,
                changes.stream().map(c -> new AuditChange(c.field(), c.before(), c.after())).toList(), reason));
    }
}
