package com.palcheck.messaging.infrastructure;

import com.palcheck.messaging.api.OutboxEventReader;
import com.palcheck.messaging.api.OutboxEventView;
import com.palcheck.messaging.application.OutboxRepository;
import com.palcheck.messaging.domain.OutboxRecord;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcOutboxRepository implements OutboxRepository, OutboxEventReader {

    private static final String COLUMNS = """
            id, tenant_id, event_id, event_type, schema_version, aggregate_type, aggregate_id, occurred_at,
            correlation_id, causation_id, payload, status, attempt_count, next_attempt_at, published_at, created_at
            """;
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> PAYLOAD_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcOutboxRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(OutboxRecord r) {
        jdbc.sql("""
                        INSERT INTO messaging.outbox_event (id, tenant_id, event_id, event_type, schema_version,
                            aggregate_type, aggregate_id, occurred_at, correlation_id, causation_id, payload,
                            status, attempt_count, next_attempt_at, published_at, created_at)
                        VALUES (:id, :tenant, :eventId, :type, :version, :aggType, :aggId, :occurred, :correlation,
                            :causation, CAST(:payload AS jsonb), :status, :attempts, :next, :published, :created)
                        """)
                .param("id", r.id()).param("tenant", r.tenantId().value()).param("eventId", r.eventId())
                .param("type", r.eventType()).param("version", r.schemaVersion()).param("aggType", r.aggregateType())
                .param("aggId", r.aggregateId()).param("occurred", timestamp(r.occurredAt()))
                .param("correlation", r.correlationId()).param("causation", r.causationId())
                .param("payload", json.writeValueAsString(r.payload())).param("status", r.status().name())
                .param("attempts", r.attemptCount()).param("next", timestamp(r.nextAttemptAt()))
                .param("published", timestampOrNull(r.publishedAt())).param("created", timestamp(r.createdAt()))
                .update();
    }

    @Override
    public List<OutboxRecord> claimEligible(int limit) {
        // A single statement, so the claim is atomic without needing an explicit transaction to span the network
        // call that follows (forbidden by ADR-0018 decision 8): FOR UPDATE SKIP LOCKED picks rows no other claimer
        // currently holds, and the same statement immediately leases them (pushes next_attempt_at into the near
        // future) so a second poller's WHERE clause excludes them even after this statement's own lock is released.
        // A crash before the real outcome is recorded simply lets the lease expire and the row becomes eligible again.
        return jdbc.sql("""
                        WITH claimed AS (
                            SELECT id FROM messaging.outbox_event
                             WHERE status = 'PENDING' AND next_attempt_at <= now()
                             ORDER BY created_at
                             LIMIT :limit
                             FOR UPDATE SKIP LOCKED
                        )
                        UPDATE messaging.outbox_event
                           SET next_attempt_at = now() + interval '30 seconds'
                         WHERE id IN (SELECT id FROM claimed)
                        RETURNING
                        """ + COLUMNS)
                .param("limit", limit)
                .query(this::map).list();
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.sql("""
                        UPDATE messaging.outbox_event SET status = 'PUBLISHED', published_at = :published
                         WHERE id = :id
                        """)
                .param("published", timestamp(publishedAt)).param("id", id).update();
    }

    @Override
    public void recordFailure(UUID id, String error, Instant nextAttemptAt, int maxAttempts) {
        jdbc.sql("""
                        UPDATE messaging.outbox_event
                           SET attempt_count = attempt_count + 1,
                               next_attempt_at = :next,
                               last_error = :error,
                               status = CASE WHEN attempt_count + 1 >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END
                         WHERE id = :id
                        """)
                .param("next", timestamp(nextAttemptAt)).param("error", error).param("maxAttempts", maxAttempts)
                .param("id", id).update();
    }

    @Override
    public Optional<OutboxEventView> find(TenantId tenantId, UUID eventId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM messaging.outbox_event WHERE tenant_id = :tenant AND event_id = :eventId
                        """)
                .param("tenant", tenantId.value()).param("eventId", eventId)
                .query(this::mapView).optional();
    }

    private OutboxEventView mapView(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEventView(uuid(rs, "id"), TenantId.of(uuid(rs, "tenant_id")), uuid(rs, "event_id"),
                rs.getString("event_type"), rs.getString("schema_version"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), instant(rs, "occurred_at"), rs.getString("correlation_id"),
                rs.getString("causation_id"), json.readValue(rs.getString("payload"), PAYLOAD_TYPE),
                rs.getString("status"), rs.getInt("attempt_count"), instantOrNull(rs, "published_at"),
                instant(rs, "created_at"));
    }

    private OutboxRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRecord(uuid(rs, "id"), TenantId.of(uuid(rs, "tenant_id")), uuid(rs, "event_id"),
                rs.getString("event_type"), rs.getString("schema_version"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), instant(rs, "occurred_at"), rs.getString("correlation_id"),
                rs.getString("causation_id"), json.readValue(rs.getString("payload"), PAYLOAD_TYPE),
                OutboxRecord.Status.valueOf(rs.getString("status")), rs.getInt("attempt_count"),
                instant(rs, "next_attempt_at"), instantOrNull(rs, "published_at"), instant(rs, "created_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OffsetDateTime timestampOrNull(Instant instant) {
        return instant == null ? null : timestamp(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
