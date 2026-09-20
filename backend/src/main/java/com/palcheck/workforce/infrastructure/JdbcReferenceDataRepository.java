package com.palcheck.workforce.infrastructure;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.ReferenceDataRepository;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReferenceDataRepository implements ReferenceDataRepository {

    private final JdbcClient jdbc;

    JdbcReferenceDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LegalEntity legalEntity) {
        jdbc.sql("INSERT INTO workforce.legal_entity (id, tenant_id, name, country) VALUES (:id, :tenant, :name, :country)")
                .param("id", legalEntity.id()).param("tenant", legalEntity.tenantId().value())
                .param("name", legalEntity.name()).param("country", legalEntity.country()).update();
    }

    @Override
    public void insert(OrgUnit orgUnit) {
        jdbc.sql("INSERT INTO workforce.org_unit (id, tenant_id, parent_id, name) VALUES (:id, :tenant, :parent, :name)")
                .param("id", orgUnit.id()).param("tenant", orgUnit.tenantId().value())
                .param("parent", orgUnit.parentId()).param("name", orgUnit.name()).update();
    }

    @Override
    public List<LegalEntity> legalEntities(TenantId tenantId) {
        return jdbc.sql("SELECT id, tenant_id, name, country FROM workforce.legal_entity WHERE tenant_id = :tenant ORDER BY name, id")
                .param("tenant", tenantId.value())
                .query((rs, n) -> new LegalEntity(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                        rs.getString("name"), rs.getString("country")))
                .list();
    }

    @Override
    public List<OrgUnit> orgUnits(TenantId tenantId) {
        return jdbc.sql("SELECT id, tenant_id, parent_id, name FROM workforce.org_unit WHERE tenant_id = :tenant ORDER BY name, id")
                .param("tenant", tenantId.value())
                .query((rs, n) -> new OrgUnit(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                        Sql.uuid(rs, "parent_id"), rs.getString("name")))
                .list();
    }

    @Override
    public Optional<LegalEntity> findLegalEntity(TenantId tenantId, UUID legalEntityId) {
        return jdbc.sql("SELECT id, tenant_id, name, country FROM workforce.legal_entity WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", legalEntityId)
                .query((rs, n) -> new LegalEntity(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                        rs.getString("name"), rs.getString("country")))
                .optional();
    }

    @Override
    public Optional<OrgUnit> findOrgUnit(TenantId tenantId, UUID orgUnitId) {
        return jdbc.sql("SELECT id, tenant_id, parent_id, name FROM workforce.org_unit WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", orgUnitId)
                .query((rs, n) -> new OrgUnit(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                        Sql.uuid(rs, "parent_id"), rs.getString("name")))
                .optional();
    }
}
