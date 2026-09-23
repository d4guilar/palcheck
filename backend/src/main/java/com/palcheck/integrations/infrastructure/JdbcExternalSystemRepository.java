package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.ExternalSystemRepository;
import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcExternalSystemRepository implements ExternalSystemRepository {

    private static final String COLUMNS = "id, tenant_id, code, display_name, type, mode, last_sync_at, created_at";

    private final JdbcClient jdbc;

    JdbcExternalSystemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ExternalSystem system) {
        jdbc.sql("""
                        INSERT INTO integrations.external_system (id, tenant_id, code, display_name, type, mode,
                            last_sync_at, created_at)
                        VALUES (:id, :tenant, :code, :name, :type, :mode, :syncedAt, :created)
                        """)
                .param("id", system.id()).param("tenant", system.tenantId().value()).param("code", system.code())
                .param("name", system.displayName()).param("type", system.type()).param("mode", system.mode())
                .param("syncedAt", Sql.timestampOrNull(system.lastSyncAt()))
                .param("created", Sql.timestamp(system.createdAt()))
                .update();
    }

    @Override
    public Optional<ExternalSystem> findByCode(TenantId tenantId, String code) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM integrations.external_system WHERE tenant_id = :tenant AND code = :code")
                .param("tenant", tenantId.value()).param("code", code)
                .query(JdbcExternalSystemRepository::map).optional();
    }

    @Override
    public List<ExternalSystem> list(TenantId tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM integrations.external_system WHERE tenant_id = :tenant ORDER BY code")
                .param("tenant", tenantId.value())
                .query(JdbcExternalSystemRepository::map).list();
    }

    @Override
    public void touchLastSyncAt(TenantId tenantId, String code, Instant when) {
        jdbc.sql("UPDATE integrations.external_system SET last_sync_at = :syncedAt WHERE tenant_id = :tenant AND code = :code")
                .param("syncedAt", Sql.timestamp(when)).param("tenant", tenantId.value()).param("code", code)
                .update();
    }

    private static ExternalSystem map(ResultSet rs, int rowNum) throws SQLException {
        return new ExternalSystem(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")), rs.getString("code"),
                rs.getString("display_name"), rs.getString("type"), rs.getString("mode"),
                Sql.instantOrNull(rs, "last_sync_at"), Sql.instant(rs, "created_at"));
    }
}
