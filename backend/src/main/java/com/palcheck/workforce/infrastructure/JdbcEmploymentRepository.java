package com.palcheck.workforce.infrastructure;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.EmploymentRepository;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmploymentRepository implements EmploymentRepository {

    private static final String COLUMNS = """
            id, tenant_id, person_id, legal_entity_id, status, employment_type, country, start_date, end_date,
            version, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    JdbcEmploymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Employment e) {
        jdbc.sql("""
                        INSERT INTO workforce.employment (id, tenant_id, person_id, legal_entity_id, status,
                            employment_type, country, start_date, end_date, version, created_at, updated_at)
                        VALUES (:id, :tenant, :person, :entity, :status, :type, :country, :start, :end,
                            :version, :created, :updated)
                        """)
                .param("id", e.id()).param("tenant", e.tenantId().value()).param("person", e.personId())
                .param("entity", e.legalEntityId()).param("status", e.data().status().name())
                .param("type", e.data().employmentType().name()).param("country", e.data().country())
                .param("start", e.data().startDate()).param("end", e.data().endDate())
                .param("version", e.version())
                .param("created", Sql.timestamp(e.createdAt())).param("updated", Sql.timestamp(e.updatedAt()))
                .update();
    }

    @Override
    public Optional<Employment> find(TenantId tenantId, UUID employmentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workforce.employment WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", employmentId)
                .query(JdbcEmploymentRepository::map).optional();
    }

    @Override
    public List<Employment> findByPerson(TenantId tenantId, UUID personId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM workforce.employment WHERE tenant_id = :tenant AND person_id = :person
                         ORDER BY start_date DESC, id
                        """)
                .param("tenant", tenantId.value()).param("person", personId)
                .query(JdbcEmploymentRepository::map).list();
    }

    @Override
    public OptionalLong currentVersion(TenantId tenantId, UUID employmentId) {
        return jdbc.sql("SELECT version FROM workforce.employment WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", employmentId)
                .query(Long.class).optional().map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    @Override
    public boolean update(Employment next, long expectedVersion) {
        int rows = jdbc.sql("""
                        UPDATE workforce.employment
                           SET status = :status, employment_type = :type, country = :country,
                               start_date = :start, end_date = :end, version = version + 1, updated_at = :updated
                         WHERE tenant_id = :tenant AND id = :id AND version = :expected
                        """)
                .param("status", next.data().status().name()).param("type", next.data().employmentType().name())
                .param("country", next.data().country()).param("start", next.data().startDate())
                .param("end", next.data().endDate()).param("updated", Sql.timestamp(next.updatedAt()))
                .param("tenant", next.tenantId().value()).param("id", next.id()).param("expected", expectedVersion)
                .update();
        return rows == 1;
    }

    static Employment map(ResultSet rs, int rowNum) throws SQLException {
        return new Employment(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")), Sql.uuid(rs, "person_id"),
                Sql.uuid(rs, "legal_entity_id"),
                new EmploymentData(EmploymentStatus.valueOf(rs.getString("status")),
                        EmploymentType.valueOf(rs.getString("employment_type")), rs.getString("country"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
                rs.getLong("version"), Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"));
    }
}
