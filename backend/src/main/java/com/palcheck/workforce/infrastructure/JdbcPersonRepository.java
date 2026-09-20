package com.palcheck.workforce.infrastructure;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.PersonRepository;
import com.palcheck.workforce.application.WorkEmailAlreadyInUseException;
import com.palcheck.workforce.domain.Person;
import com.palcheck.workforce.domain.PersonData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPersonRepository implements PersonRepository {

    private static final String COLUMNS = """
            id, tenant_id, first_name, middle_name, last_name, preferred_name, work_email, locale,
            version, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    JdbcPersonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Person p) {
        try {
            jdbc.sql("""
                            INSERT INTO workforce.person (id, tenant_id, first_name, middle_name, last_name,
                                preferred_name, work_email, locale, version, created_at, updated_at)
                            VALUES (:id, :tenant, :first, :middle, :last, :preferred, :email, :locale,
                                :version, :created, :updated)
                            """)
                    .param("id", p.id()).param("tenant", p.tenantId().value())
                    .param("first", p.data().firstName()).param("middle", p.data().middleName())
                    .param("last", p.data().lastName()).param("preferred", p.data().preferredName())
                    .param("email", p.data().workEmail()).param("locale", p.data().locale())
                    .param("version", p.version())
                    .param("created", Sql.timestamp(p.createdAt())).param("updated", Sql.timestamp(p.updatedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
    }

    @Override
    public Optional<Person> find(TenantId tenantId, UUID personId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workforce.person WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", personId)
                .query(JdbcPersonRepository::map).optional();
    }

    @Override
    public OptionalLong currentVersion(TenantId tenantId, UUID personId) {
        return jdbc.sql("SELECT version FROM workforce.person WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId.value()).param("id", personId)
                .query(Long.class).optional().map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    @Override
    public boolean workEmailInUse(TenantId tenantId, String workEmail, UUID excludingPersonId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM workforce.person
                                       WHERE tenant_id = :tenant AND work_email = :email
                                         AND (CAST(:excluding AS uuid) IS NULL OR id <> CAST(:excluding AS uuid)))
                        """)
                .param("tenant", tenantId.value()).param("email", workEmail).param("excluding", excludingPersonId)
                .query(Boolean.class).single();
    }

    @Override
    public boolean update(Person next, long expectedVersion) {
        try {
            int rows = jdbc.sql("""
                            UPDATE workforce.person
                               SET first_name = :first, middle_name = :middle, last_name = :last,
                                   preferred_name = :preferred, work_email = :email, locale = :locale,
                                   version = version + 1, updated_at = :updated
                             WHERE tenant_id = :tenant AND id = :id AND version = :expected
                            """)
                    .param("first", next.data().firstName()).param("middle", next.data().middleName())
                    .param("last", next.data().lastName()).param("preferred", next.data().preferredName())
                    .param("email", next.data().workEmail()).param("locale", next.data().locale())
                    .param("updated", Sql.timestamp(next.updatedAt()))
                    .param("tenant", next.tenantId().value()).param("id", next.id()).param("expected", expectedVersion)
                    .update();
            return rows == 1;
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
    }

    private static RuntimeException translate(DuplicateKeyException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("person_work_email_unique") ? new WorkEmailAlreadyInUseException() : e;
    }

    private static Person map(ResultSet rs, int rowNum) throws SQLException {
        return new Person(Sql.uuid(rs, "id"), TenantId.of(Sql.uuid(rs, "tenant_id")),
                new PersonData(rs.getString("first_name"), rs.getString("middle_name"), rs.getString("last_name"),
                        rs.getString("preferred_name"), rs.getString("work_email"), rs.getString("locale")),
                rs.getLong("version"), Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"));
    }
}
