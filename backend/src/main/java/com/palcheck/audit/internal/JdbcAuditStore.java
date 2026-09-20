package com.palcheck.audit.internal;

import com.palcheck.audit.api.AuditActor;
import com.palcheck.audit.api.AuditChange;
import com.palcheck.audit.api.AuditEntryView;
import com.palcheck.audit.api.AuditHistory;
import com.palcheck.audit.api.AuditRecord;
import com.palcheck.audit.api.AuditRecorder;
import com.palcheck.platform.logging.LogKeys;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Owns the {@code audit} schema. Recording joins the caller's transaction; reading is a plain tenant-scoped query. */
@Repository
class JdbcAuditStore implements AuditRecorder, AuditHistory {

    private static final TypeReference<List<AuditChange>> CHANGES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    JdbcAuditStore(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditRecord r) {
        jdbc.sql("""
                        INSERT INTO audit.audit_entry (id, tenant_id, occurred_at, actor_type, actor_id, actor_display,
                            command, change_set_id, operation, entity_type, entity_id, entity_version,
                            record_type, record_id, changes, reason, request_id, correlation_id)
                        VALUES (:id, :tenant, :at, :actorType, :actorId, :actorDisplay,
                            :command, :changeSet, :operation, :entityType, :entityId, :entityVersion,
                            :recordType, :recordId, CAST(:changes AS jsonb), :reason, :requestId, :correlationId)
                        """)
                .param("id", UUID.randomUUID()).param("tenant", r.tenantId().value())
                .param("at", OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)))
                .param("actorType", r.actor().type().name()).param("actorId", r.actor().id())
                .param("actorDisplay", r.actor().displayName())
                .param("command", r.command()).param("changeSet", r.changeSetId())
                .param("operation", r.operation().name()).param("entityType", r.entityType())
                .param("entityId", r.entityId()).param("entityVersion", r.entityVersion())
                .param("recordType", r.recordType()).param("recordId", r.recordId())
                .param("changes", json.writeValueAsString(r.changes()))
                .param("reason", r.reason())
                .param("requestId", MDC.get(LogKeys.REQUEST_ID))
                .param("correlationId", MDC.get(LogKeys.CORRELATION_ID))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Page find(TenantId tenantId, String recordType, UUID recordId, int page, int size) {
        long total = jdbc.sql("""
                        SELECT count(*) FROM audit.audit_entry
                         WHERE tenant_id = :tenant AND record_type = :type AND record_id = :id
                        """)
                .param("tenant", tenantId.value()).param("type", recordType).param("id", recordId)
                .query(Long.class).single();
        List<AuditEntryView> items = jdbc.sql("""
                        SELECT id, change_set_id, occurred_at, actor_type, actor_id, actor_display, command, operation,
                               entity_type, entity_id, entity_version, changes, reason, request_id
                          FROM audit.audit_entry
                         WHERE tenant_id = :tenant AND record_type = :type AND record_id = :id
                         ORDER BY occurred_at DESC, id DESC
                         LIMIT :limit OFFSET :offset
                        """)
                .param("tenant", tenantId.value()).param("type", recordType).param("id", recordId)
                .param("limit", size).param("offset", (long) page * size)
                .query(this::map).list();
        return new Page(items, page, size, total);
    }

    private AuditEntryView map(ResultSet rs, int rowNum) throws SQLException {
        var actor = new AuditActor(AuditActor.Type.valueOf(rs.getString("actor_type")),
                rs.getObject("actor_id", UUID.class), rs.getString("actor_display"));
        return new AuditEntryView(rs.getObject("id", UUID.class), rs.getObject("change_set_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), actor, rs.getString("command"),
                AuditRecord.Operation.valueOf(rs.getString("operation")), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getLong("entity_version"),
                json.readValue(rs.getString("changes"), CHANGES), rs.getString("reason"), rs.getString("request_id"));
    }
}
