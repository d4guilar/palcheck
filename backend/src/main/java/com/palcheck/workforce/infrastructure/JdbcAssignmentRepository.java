package com.palcheck.workforce.infrastructure;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.AssignmentRepository;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssignmentRepository implements AssignmentRepository {

    private static final String COLUMNS = """
            id, tenant_id, employment_id, org_unit_id, title, manager_employment_id, effective_from, effective_to,
            version, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    JdbcAssignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockReportingGraph(TenantId tenantId) {
        // Hash collisions only serialize unrelated tenants; they cannot weaken isolation.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:tenant AS text), 73421))")
                .param("tenant", tenantId.value()).query((rs, n) -> 1).single();
    }

    @Override
    public void insert(Assignment a) {
        jdbc.sql("""
                        INSERT INTO workforce.assignment (id, tenant_id, employment_id, org_unit_id, title,
                            manager_employment_id, effective_from, effective_to, version, created_at, updated_at)
                        VALUES (:id, :tenant, :employment, :unit, :title, :manager, :from, :to,
                            :version, :created, :updated)
                        """)
                .param("id", a.id()).param("tenant", a.tenantId().value()).param("employment", a.employmentId())
                .param("unit", a.data().orgUnitId()).param("title", a.data().title())
                .param("manager", a.data().managerEmploymentId()).param("from", a.effectiveFrom())
                .param("to", a.effectiveTo()).param("version", a.version())
                .param("created", Sql.timestamp(a.createdAt())).param("updated", Sql.timestamp(a.updatedAt()))
                .update();
    }

    @Override
    public Optional<Assignment> find(TenantId tenantId, UUID assignmentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workforce.assignment WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", assignmentId)
                .query(JdbcAssignmentRepository::map).optional();
    }

    @Override
    public Optional<Assignment> findCurrent(TenantId tenantId, UUID employmentId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM workforce.assignment
                        WHERE tenant_id = :tenant AND employment_id = :employment AND effective_to IS NULL
                        """)
                .param("tenant", tenantId.value()).param("employment", employmentId)
                .query(JdbcAssignmentRepository::map).optional();
    }

    @Override
    public Map<UUID, Assignment> findCurrent(TenantId tenantId, Collection<UUID> employmentIds) {
        Map<UUID, Assignment> result = new HashMap<>();
        if (employmentIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT " + COLUMNS + """
                         FROM workforce.assignment
                        WHERE tenant_id = :tenant AND employment_id IN (:ids) AND effective_to IS NULL
                        """)
                .param("tenant", tenantId.value()).param("ids", employmentIds)
                .query(JdbcAssignmentRepository::map).list()
                .forEach(a -> result.put(a.employmentId(), a));
        return result;
    }

    @Override
    public OptionalLong currentVersion(TenantId tenantId, UUID assignmentId) {
        return jdbc.sql("SELECT version FROM workforce.assignment WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", assignmentId)
                .query(Long.class).optional().map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    @Override
    public boolean update(Assignment next, long expectedVersion) {
        int rows = jdbc.sql("""
                        UPDATE workforce.assignment
                           SET org_unit_id = :unit, title = :title, manager_employment_id = :manager,
                               version = version + 1, updated_at = :updated
                         WHERE tenant_id = :tenant AND id = :id AND version = :expected
                        """)
                .param("unit", next.data().orgUnitId()).param("title", next.data().title())
                .param("manager", next.data().managerEmploymentId()).param("updated", Sql.timestamp(next.updatedAt()))
                .param("tenant", next.tenantId().value()).param("id", next.id()).param("expected", expectedVersion)
                .update();
        return rows == 1;
    }

    @Override
    public boolean reportingChainReaches(TenantId tenantId, UUID startEmploymentId, UUID targetEmploymentId) {
        // UNION (not UNION ALL) removes duplicates, so the walk terminates even if bad data ever contained a cycle.
        return jdbc.sql("""
                        WITH RECURSIVE chain(employment_id) AS (
                            SELECT CAST(:start AS uuid)
                            UNION
                            SELECT a.manager_employment_id
                              FROM workforce.assignment a
                              JOIN chain c ON a.employment_id = c.employment_id
                             WHERE a.tenant_id = :tenant AND a.effective_to IS NULL
                               AND a.manager_employment_id IS NOT NULL
                        )
                        SELECT EXISTS (SELECT 1 FROM chain WHERE employment_id = CAST(:target AS uuid))
                        """)
                .param("tenant", tenantId.value()).param("start", startEmploymentId).param("target", targetEmploymentId)
                .query(Boolean.class).single();
    }

    @Override
    public Optional<LocalDate> earliestEffectiveFrom(TenantId tenantId, UUID employmentId) {
        return jdbc.sql("""
                        SELECT min(effective_from) FROM workforce.assignment
                         WHERE tenant_id = :tenant AND employment_id = :employment
                        """)
                .param("tenant", tenantId.value()).param("employment", employmentId)
                .query((rs, n) -> rs.getObject(1, LocalDate.class)).optional();
    }

    private static Assignment map(ResultSet rs, int rowNum) throws SQLException {
        return new Assignment(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                Sql.uuid(rs, "employment_id"), rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                new AssignmentData(rs.getString("title"), Sql.uuid(rs, "org_unit_id"),
                        Sql.uuid(rs, "manager_employment_id")),
                rs.getLong("version"), Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"));
    }
}
