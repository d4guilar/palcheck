package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.PageResult;
import com.palcheck.integrations.application.SyncRunRepository;
import com.palcheck.integrations.domain.SyncRun;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncRunRepository implements SyncRunRepository {

    private static final String COLUMNS = """
            id, tenant_id, system_id, started_at, completed_at, status, records_seen, created_count, updated_count,
            unchanged_count, failed_count, triggered_by
            """;

    private final JdbcClient jdbc;

    JdbcSyncRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SyncRun run) {
        jdbc.sql("""
                        INSERT INTO integrations.sync_run (id, tenant_id, system_id, started_at, status, triggered_by)
                        VALUES (:id, :tenant, :system, :started, :status, :triggeredBy)
                        """)
                .param("id", run.id()).param("tenant", run.tenantId().value()).param("system", run.systemId())
                .param("started", Sql.timestamp(run.startedAt())).param("status", run.status().name())
                .param("triggeredBy", run.triggeredBy())
                .update();
    }

    @Override
    public void complete(SyncRun run) {
        jdbc.sql("""
                        UPDATE integrations.sync_run
                           SET completed_at = :completed, status = :status, records_seen = :seen,
                               created_count = :created, updated_count = :updated, unchanged_count = :unchanged,
                               failed_count = :failed
                         WHERE tenant_id = :tenant AND id = :id
                        """)
                .param("completed", Sql.timestamp(run.completedAt())).param("status", run.status().name())
                .param("seen", run.recordsSeen()).param("created", run.createdCount())
                .param("updated", run.updatedCount()).param("unchanged", run.unchangedCount())
                .param("failed", run.failedCount()).param("tenant", run.tenantId().value()).param("id", run.id())
                .update();
    }

    @Override
    public Optional<SyncRun> find(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM integrations.sync_run WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", id)
                .query(JdbcSyncRunRepository::map).optional();
    }

    @Override
    public PageResult<SyncRun> listBySystem(TenantId tenantId, UUID systemId, int page, int size) {
        long total = jdbc.sql("SELECT count(*) FROM integrations.sync_run WHERE tenant_id = :tenant AND system_id = :system")
                .param("tenant", tenantId.value()).param("system", systemId).query(Long.class).single();
        List<SyncRun> items = jdbc.sql("SELECT " + COLUMNS + """
                         FROM integrations.sync_run WHERE tenant_id = :tenant AND system_id = :system
                         ORDER BY started_at DESC, id
                         LIMIT :size OFFSET :offset
                        """)
                .param("tenant", tenantId.value()).param("system", systemId).param("size", size)
                .param("offset", (long) page * size)
                .query(JdbcSyncRunRepository::map).list();
        return new PageResult<>(items, page, size, total);
    }

    private static SyncRun map(ResultSet rs, int rowNum) throws SQLException {
        return new SyncRun(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")), Sql.uuid(rs, "system_id"),
                Sql.instant(rs, "started_at"), Sql.instantOrNull(rs, "completed_at"),
                SyncRun.Status.valueOf(rs.getString("status")), rs.getInt("records_seen"),
                rs.getInt("created_count"), rs.getInt("updated_count"), rs.getInt("unchanged_count"),
                rs.getInt("failed_count"), Sql.uuid(rs, "triggered_by"));
    }
}
