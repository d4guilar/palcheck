package com.palcheck.integrations.infrastructure;

import com.palcheck.integrations.application.FieldLineageRepository;
import com.palcheck.integrations.domain.FieldLineage;
import com.palcheck.shared.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFieldLineageRepository implements FieldLineageRepository {

    private static final String COLUMNS = """
            tenant_id, entity_type, entity_id, field_path, source_system, source_field, mapping_version,
            sync_run_id, sync_item_id, imported_at
            """;

    private final JdbcClient jdbc;

    JdbcFieldLineageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(FieldLineage lineage) {
        jdbc.sql("""
                        INSERT INTO integrations.field_lineage (tenant_id, entity_type, entity_id, field_path,
                            source_system, source_field, mapping_version, sync_run_id, sync_item_id, imported_at)
                        VALUES (:tenant, :entityType, :entityId, :field, :source, :sourceField, :mappingVersion,
                            :run, :item, :imported)
                        ON CONFLICT (tenant_id, entity_type, entity_id, field_path) DO UPDATE
                           SET source_system = EXCLUDED.source_system, source_field = EXCLUDED.source_field,
                               mapping_version = EXCLUDED.mapping_version, sync_run_id = EXCLUDED.sync_run_id,
                               sync_item_id = EXCLUDED.sync_item_id, imported_at = EXCLUDED.imported_at
                        """)
                .param("tenant", lineage.tenantId().value()).param("entityType", lineage.entityType().name())
                .param("entityId", lineage.entityId()).param("field", lineage.fieldPath())
                .param("source", lineage.sourceSystem()).param("sourceField", lineage.sourceField())
                .param("mappingVersion", lineage.mappingVersion()).param("run", lineage.syncRunId())
                .param("item", lineage.syncItemId()).param("imported", Sql.timestamp(lineage.importedAt()))
                .update();
    }

    @Override
    public List<FieldLineage> listForEntity(TenantId tenantId, FieldLineage.EntityType entityType, UUID entityId) {
        return jdbc.sql("""
                        SELECT l.*, i.external_worker_id FROM integrations.field_lineage l
                        JOIN integrations.sync_item i ON i.tenant_id = l.tenant_id AND i.id = l.sync_item_id
                        WHERE l.tenant_id = :tenant AND l.entity_type = :type AND l.entity_id = :entityId
                        ORDER BY l.field_path
                        """)
                .param("tenant", tenantId.value()).param("type", entityType.name()).param("entityId", entityId)
                .query(JdbcFieldLineageRepository::map).list();
    }

    private static FieldLineage map(ResultSet rs, int rowNum) throws SQLException {
        return new FieldLineage(TenantId.of(Sql.uuid(rs, "tenant_id")),
                FieldLineage.EntityType.valueOf(rs.getString("entity_type")), Sql.uuid(rs, "entity_id"),
                rs.getString("field_path"), rs.getString("source_system"), rs.getString("source_field"),
                rs.getString("mapping_version"), Sql.uuid(rs, "sync_run_id"), Sql.uuid(rs, "sync_item_id"),
                Sql.instant(rs, "imported_at"), rs.getString("external_worker_id"));
    }
}
