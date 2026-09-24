package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.IntegrationOperationRepository;
import com.palcheck.integrations.application.PageResult;
import com.palcheck.integrations.domain.IntegrationOperation;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcIntegrationOperationRepository implements IntegrationOperationRepository {

    private static final String COLUMNS = """
            tenant_id, consumer, operation_key, status, attempt_count, last_error, next_attempt_at, completed_at,
            source_event_id, payload, created_at, updated_at
            """;
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> PAYLOAD_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcIntegrationOperationRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public IntegrationOperation findOrCreate(TenantId tenantId, String consumer, String operationKey,
            UUID sourceEventId, Map<String, Object> payload, Instant now) {
        // ON CONFLICT DO UPDATE with a no-op assignment is the atomic "insert, or tell me what is already there"
        // (the same fact-finding trick ExternalSystemService uses via DuplicateKeyException, here without a
        // round trip): a race between two concurrent deliveries never creates two rows for the same operation key.
        return jdbc.sql("""
                        INSERT INTO integrations.integration_operation (tenant_id, consumer, operation_key, status,
                            attempt_count, source_event_id, payload, created_at, updated_at)
                        VALUES (:tenant, :consumer, :key, 'PENDING', 0, :sourceEvent, CAST(:payload AS jsonb), :now, :now)
                        ON CONFLICT (tenant_id, consumer, operation_key) DO UPDATE
                           SET tenant_id = integrations.integration_operation.tenant_id
                        RETURNING
                        """ + COLUMNS)
                .param("tenant", tenantId.value()).param("consumer", consumer).param("key", operationKey)
                .param("sourceEvent", sourceEventId).param("payload", json.writeValueAsString(payload))
                .param("now", Sql.timestamp(now))
                .query(this::map).single();
    }

    @Override
    public Optional<IntegrationOperation> find(TenantId tenantId, String consumer, String operationKey) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM integrations.integration_operation
                        WHERE tenant_id = :tenant AND consumer = :consumer AND operation_key = :key
                        """)
                .param("tenant", tenantId.value()).param("consumer", consumer).param("key", operationKey)
                .query(this::map).optional();
    }

    @Override
    public void markSucceeded(TenantId tenantId, String consumer, String operationKey, Instant completedAt) {
        jdbc.sql("""
                        UPDATE integrations.integration_operation
                           SET status = 'SUCCEEDED', completed_at = :completed, updated_at = :completed,
                               last_error = NULL, next_attempt_at = NULL
                         WHERE tenant_id = :tenant AND consumer = :consumer AND operation_key = :key
                        """)
                .param("completed", Sql.timestamp(completedAt)).param("tenant", tenantId.value())
                .param("consumer", consumer).param("key", operationKey).update();
    }

    @Override
    public void markFailed(TenantId tenantId, String consumer, String operationKey, IntegrationOperation.Status status,
            String error, Instant nextAttemptAt) {
        jdbc.sql("""
                        UPDATE integrations.integration_operation
                           SET status = :status, attempt_count = attempt_count + 1, last_error = :error,
                               next_attempt_at = :next, updated_at = now()
                         WHERE tenant_id = :tenant AND consumer = :consumer AND operation_key = :key
                        """)
                .param("status", status.name()).param("error", error).param("next", Sql.timestampOrNull(nextAttemptAt))
                .param("tenant", tenantId.value()).param("consumer", consumer).param("key", operationKey).update();
    }

    @Override
    public List<IntegrationOperation> claimDueRetries(String consumer, int limit) {
        // Same lease pattern as the outbox publisher's claim (JdbcOutboxRepository): one atomic statement pushes
        // next_attempt_at forward so a second poller's own due-retry query excludes these rows for the lease window.
        return jdbc.sql("""
                        WITH due AS (
                            SELECT tenant_id, consumer, operation_key FROM integrations.integration_operation
                             WHERE consumer = :consumer AND status = 'FAILED_TRANSIENT' AND next_attempt_at <= now()
                             ORDER BY next_attempt_at
                             LIMIT :limit
                             FOR UPDATE SKIP LOCKED
                        )
                        UPDATE integrations.integration_operation o
                           SET next_attempt_at = now() + interval '30 seconds'
                          FROM due
                         WHERE o.tenant_id = due.tenant_id AND o.consumer = due.consumer
                           AND o.operation_key = due.operation_key
                        RETURNING
                        """ + qualified())
                .param("consumer", consumer).param("limit", limit)
                .query(this::map).list();
    }

    @Override
    public PageResult<IntegrationOperation> list(TenantId tenantId, String consumer, IntegrationOperation.Status status,
            int page, int size) {
        String statusName = status == null ? null : status.name();
        long total = jdbc.sql("""
                        SELECT count(*) FROM integrations.integration_operation
                         WHERE tenant_id = :tenant AND (:consumer::text IS NULL OR consumer = :consumer)
                           AND (:status::text IS NULL OR status = :status)
                        """)
                .param("tenant", tenantId.value()).param("consumer", consumer).param("status", statusName)
                .query(Long.class).single();
        List<IntegrationOperation> items = jdbc.sql("SELECT " + COLUMNS + """
                         FROM integrations.integration_operation
                        WHERE tenant_id = :tenant AND (:consumer::text IS NULL OR consumer = :consumer)
                          AND (:status::text IS NULL OR status = :status)
                        ORDER BY updated_at DESC, operation_key
                        LIMIT :size OFFSET :offset
                        """)
                .param("tenant", tenantId.value()).param("consumer", consumer).param("status", statusName)
                .param("size", size).param("offset", (long) page * size)
                .query(this::map).list();
        return new PageResult<>(items, page, size, total);
    }

    /** The claim's RETURNING clause reads from the alias {@code o}, not the bare table name. */
    private static String qualified() {
        return "o.tenant_id, o.consumer, o.operation_key, o.status, o.attempt_count, o.last_error, "
                + "o.next_attempt_at, o.completed_at, o.source_event_id, o.payload, o.created_at, o.updated_at";
    }

    private IntegrationOperation map(ResultSet rs, int rowNum) throws SQLException {
        String lastError = rs.getString("last_error");
        return new IntegrationOperation(TenantId.of(Sql.uuid(rs, "tenant_id")), rs.getString("consumer"),
                rs.getString("operation_key"), IntegrationOperation.Status.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"), lastError, Sql.instantOrNull(rs, "next_attempt_at"),
                Sql.instantOrNull(rs, "completed_at"), rs.getObject("source_event_id", UUID.class),
                json.readValue(rs.getString("payload"), PAYLOAD_TYPE), Sql.instant(rs, "created_at"),
                Sql.instant(rs, "updated_at"));
    }
}
