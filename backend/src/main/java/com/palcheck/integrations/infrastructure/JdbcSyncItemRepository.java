package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.PageResult;
import com.palcheck.integrations.application.SyncItemRepository;
import com.palcheck.integrations.domain.SyncFailureReason;
import com.palcheck.integrations.domain.SyncItem;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncItemRepository implements SyncItemRepository {

    private static final String COLUMNS = """
            id, sync_run_id, tenant_id, external_worker_id, status, action, failure_reason, failure_detail,
            target_person_id, target_employment_id, occurred_at
            """;

    private final JdbcClient jdbc;

    JdbcSyncItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SyncItem item) {
        jdbc.sql("""
                        INSERT INTO integrations.sync_item (id, sync_run_id, tenant_id, external_worker_id, status,
                            action, failure_reason, failure_detail, target_person_id, target_employment_id, occurred_at)
                        VALUES (:id, :run, :tenant, :externalId, :status, :action, :reason, :detail, :person,
                            :employment, :occurred)
                        """)
                .param("id", item.id()).param("run", item.syncRunId()).param("tenant", item.tenantId().value())
                .param("externalId", item.externalWorkerId()).param("status", item.status().name())
                .param("action", item.action() == null ? null : item.action().name())
                .param("reason", item.failureReason() == null ? null : item.failureReason().name())
                .param("detail", item.failureDetail()).param("person", item.targetPersonId())
                .param("employment", item.targetEmploymentId()).param("occurred", Sql.timestamp(item.occurredAt()))
                .update();
    }

    @Override
    public void markUpdated(TenantId tenantId, UUID itemId) {
        jdbc.sql("UPDATE integrations.sync_item SET action = 'UPDATED' WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", itemId).update();
    }

    @Override
    public PageResult<SyncItem> listByRun(TenantId tenantId, UUID syncRunId, int page, int size) {
        long total = jdbc.sql("SELECT count(*) FROM integrations.sync_item WHERE tenant_id = :tenant AND sync_run_id = :run")
                .param("tenant", tenantId.value()).param("run", syncRunId).query(Long.class).single();
        List<SyncItem> items = jdbc.sql("SELECT " + COLUMNS + """
                         FROM integrations.sync_item WHERE tenant_id = :tenant AND sync_run_id = :run
                         ORDER BY occurred_at, id
                         LIMIT :size OFFSET :offset
                        """)
                .param("tenant", tenantId.value()).param("run", syncRunId).param("size", size)
                .param("offset", (long) page * size)
                .query(JdbcSyncItemRepository::map).list();
        return new PageResult<>(items, page, size, total);
    }

    private static SyncItem map(ResultSet rs, int rowNum) throws SQLException {
        String action = rs.getString("action");
        String reason = rs.getString("failure_reason");
        return new SyncItem(Sql.uuid(rs, "id"), Sql.uuid(rs, "sync_run_id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                rs.getString("external_worker_id"), SyncItem.Status.valueOf(rs.getString("status")),
                action == null ? null : SyncItem.Action.valueOf(action),
                reason == null ? null : SyncFailureReason.valueOf(reason), rs.getString("failure_detail"),
                Sql.uuid(rs, "target_person_id"), Sql.uuid(rs, "target_employment_id"),
                Sql.instant(rs, "occurred_at"));
    }
}
