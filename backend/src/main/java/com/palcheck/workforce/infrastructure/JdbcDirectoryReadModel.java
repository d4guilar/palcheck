package com.palcheck.workforce.infrastructure;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.DirectoryItem;
import com.palcheck.workforce.application.ManagerCandidate;
import com.palcheck.workforce.application.DirectoryReadModel;
import com.palcheck.workforce.application.EmploymentView;
import com.palcheck.workforce.application.PageResult;
import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.EmploymentStatus;
import com.palcheck.workforce.domain.EmploymentType;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.PersonData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Display-oriented joins over workforce tables (all owned by this module). Every statement filters by tenant on
 * every table it touches, and the tenant filter is applied before counting and paging.
 */
@Repository
class JdbcDirectoryReadModel implements DirectoryReadModel {

    private static final String SEARCH_PREDICATE = """
             AND (workforce.unaccent(normalize(p.first_name || ' ' || p.last_name, NFC)) ILIKE :like ESCAPE '\\'
                  OR workforce.unaccent(normalize(COALESCE(p.preferred_name, '') || ' ' || p.last_name, NFC)) ILIKE :like ESCAPE '\\'
                  OR workforce.unaccent(normalize(p.work_email, NFC)) ILIKE :like ESCAPE '\\')
            """;

    private final JdbcClient jdbc;

    JdbcDirectoryReadModel(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<DirectoryItem> search(TenantId tenantId, String query, int page, int size) {
        boolean filtered = query != null && !query.isBlank();
        String predicate = filtered ? SEARCH_PREDICATE : "";
        String like = filtered ? searchPattern(query) : null;

        var countSpec = jdbc.sql("SELECT count(*) FROM workforce.person p WHERE p.tenant_id = :tenant" + predicate)
                .param("tenant", tenantId.value());
        if (filtered) {
            countSpec = countSpec.param("like", like);
        }
        long total = countSpec.query(Long.class).single();

        var listSpec = jdbc.sql("""
                        SELECT p.id AS person_id, p.first_name, p.preferred_name, p.last_name, p.work_email,
                               e.id AS employment_id, e.status, le.name AS legal_entity_name,
                               a.title, ou.name AS org_unit_name
                          FROM workforce.person p
                          LEFT JOIN LATERAL (
                                SELECT e2.id, e2.tenant_id, e2.legal_entity_id, e2.status
                                  FROM workforce.employment e2
                                 WHERE e2.tenant_id = p.tenant_id AND e2.person_id = p.id
                                 ORDER BY (e2.status = 'ENDED'), e2.start_date DESC, e2.id
                                 LIMIT 1) e ON TRUE
                          LEFT JOIN workforce.legal_entity le
                                 ON le.tenant_id = e.tenant_id AND le.id = e.legal_entity_id
                          LEFT JOIN workforce.assignment a
                                 ON a.tenant_id = e.tenant_id AND a.employment_id = e.id AND a.effective_to IS NULL
                          LEFT JOIN workforce.org_unit ou
                                 ON ou.tenant_id = a.tenant_id AND ou.id = a.org_unit_id
                         WHERE p.tenant_id = :tenant
                        """ + predicate + """
                         ORDER BY lower(p.last_name), lower(p.first_name), p.id
                         LIMIT :limit OFFSET :offset
                        """)
                .param("tenant", tenantId.value()).param("limit", size).param("offset", (long) page * size);
        if (filtered) {
            listSpec = listSpec.param("like", like);
        }
        List<DirectoryItem> items = listSpec.query((rs, n) -> new DirectoryItem(
                Sql.uuid(rs, "person_id"),
                PersonData.displayName(rs.getString("first_name"), rs.getString("preferred_name"),
                        rs.getString("last_name")),
                rs.getString("work_email"), rs.getString("title"), rs.getString("org_unit_name"),
                rs.getString("legal_entity_name"),
                rs.getString("status") == null ? null : EmploymentStatus.valueOf(rs.getString("status")),
                Sql.uuid(rs, "employment_id"))).list();
        return new PageResult<>(items, page, size, total);
    }

    private String searchPattern(String query) {
        // Fold before escaping: even a dictionary substitution cannot introduce a LIKE wildcard.
        String folded = jdbc.sql("SELECT workforce.unaccent(normalize(CAST(:q AS text), NFC))")
                .param("q", query.strip()).query(String.class).single();
        return Sql.likeContains(folded);
    }

    @Override
    public PageResult<ManagerCandidate> managers(TenantId tenantId, String query, int page, int size,
            UUID excludingEmploymentId) {
        String from = """
                 FROM workforce.employment e
                 JOIN workforce.person p ON p.tenant_id = e.tenant_id AND p.id = e.person_id
                 JOIN workforce.legal_entity le ON le.tenant_id = e.tenant_id AND le.id = e.legal_entity_id
                 LEFT JOIN workforce.assignment a ON a.tenant_id = e.tenant_id AND a.employment_id = e.id
                      AND a.effective_to IS NULL
                WHERE e.tenant_id = :tenant
                  AND (CAST(:excluded AS uuid) IS NULL OR e.id <> CAST(:excluded AS uuid))
                """;
        boolean filtered = query != null && !query.isBlank();
        if (filtered) from += SEARCH_PREDICATE;
        var params = new java.util.HashMap<String, Object>();
        params.put("tenant", tenantId.value());
        params.put("excluded", excludingEmploymentId);
        if (filtered) params.put("like", searchPattern(query));
        long count = jdbc.sql("SELECT count(*)" + from).params(params).query(Long.class).single();
        var items = jdbc.sql("""
                SELECT e.id, p.id AS person_id, p.first_name, p.preferred_name, p.last_name,
                       a.title, le.name AS legal_entity_name, e.status
                """ + from + " ORDER BY lower(p.last_name), lower(p.first_name), p.id, e.id LIMIT :size OFFSET :offset")
                .params(params).param("size", size).param("offset", (long) page * size)
                .query((rs, n) -> new ManagerCandidate(Sql.uuid(rs, "id"), Sql.uuid(rs, "person_id"),
                        PersonData.displayName(rs.getString("first_name"), rs.getString("preferred_name"), rs.getString("last_name")),
                        rs.getString("title"), rs.getString("legal_entity_name"), rs.getString("status"))).list();
        return new PageResult<>(items, page, size, count);
    }

    @Override
    public List<EmploymentView> employmentsOf(TenantId tenantId, UUID personId) {
        return jdbc.sql("""
                        SELECT e.id, e.tenant_id, e.person_id, e.legal_entity_id, e.status, e.employment_type,
                               e.country, e.start_date, e.end_date, e.version, e.created_at, e.updated_at,
                               e.owned_by_system AS e_owner, e.external_worker_id AS e_external_id,
                               e.last_synced_at AS e_synced,
                               le.name AS le_name, le.country AS le_country,
                               a.id AS a_id, a.org_unit_id AS a_org_unit_id, a.title AS a_title,
                               a.manager_employment_id AS a_manager, a.effective_from AS a_from,
                               a.effective_to AS a_to, a.version AS a_version,
                               a.created_at AS a_created, a.updated_at AS a_updated,
                               a.owned_by_system AS a_owner, a.last_synced_at AS a_synced,
                               ou.parent_id AS ou_parent, ou.name AS ou_name,
                               mp.id AS mgr_person_id, mp.first_name AS mgr_first,
                               mp.preferred_name AS mgr_preferred, mp.last_name AS mgr_last
                          FROM workforce.employment e
                          JOIN workforce.legal_entity le
                            ON le.tenant_id = e.tenant_id AND le.id = e.legal_entity_id
                          LEFT JOIN workforce.assignment a
                            ON a.tenant_id = e.tenant_id AND a.employment_id = e.id AND a.effective_to IS NULL
                          LEFT JOIN workforce.org_unit ou
                            ON ou.tenant_id = a.tenant_id AND ou.id = a.org_unit_id
                          LEFT JOIN workforce.employment me
                            ON me.tenant_id = a.tenant_id AND me.id = a.manager_employment_id
                          LEFT JOIN workforce.person mp
                            ON mp.tenant_id = me.tenant_id AND mp.id = me.person_id
                         WHERE e.tenant_id = :tenant AND e.person_id = :person
                         ORDER BY e.start_date DESC, e.id
                        """)
                .param("tenant", tenantId.value()).param("person", personId)
                .query((rs, n) -> mapView(rs, tenantId)).list();
    }

    private static EmploymentView mapView(ResultSet rs, TenantId tenantId) throws SQLException {
        Employment employment = new Employment(Sql.uuid(rs, "id"), tenantId, Sql.uuid(rs, "person_id"),
                Sql.uuid(rs, "legal_entity_id"),
                new EmploymentData(EmploymentStatus.valueOf(rs.getString("status")),
                        EmploymentType.valueOf(rs.getString("employment_type")), rs.getString("country"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
                rs.getLong("version"), Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"),
                rs.getString("e_owner"), rs.getString("e_external_id"), Sql.instantOrNull(rs, "e_synced"));
        LegalEntity entity = new LegalEntity(employment.legalEntityId(), tenantId, rs.getString("le_name"),
                rs.getString("le_country"));

        EmploymentView.AssignmentView assignmentView = null;
        UUID assignmentId = Sql.uuid(rs, "a_id");
        if (assignmentId != null) {
            UUID orgUnitId = Sql.uuid(rs, "a_org_unit_id");
            Assignment assignment = new Assignment(assignmentId, tenantId, employment.id(),
                    rs.getObject("a_from", LocalDate.class), rs.getObject("a_to", LocalDate.class),
                    new AssignmentData(rs.getString("a_title"), orgUnitId, Sql.uuid(rs, "a_manager")),
                    rs.getLong("a_version"), Sql.instant(rs, "a_created"), Sql.instant(rs, "a_updated"),
                    rs.getString("a_owner"), Sql.instantOrNull(rs, "a_synced"));
            OrgUnit unit = new OrgUnit(orgUnitId, tenantId, Sql.uuid(rs, "ou_parent"), rs.getString("ou_name"));
            EmploymentView.ManagerRef manager = null;
            UUID managerEmployment = Sql.uuid(rs, "a_manager");
            if (managerEmployment != null) {
                manager = new EmploymentView.ManagerRef(managerEmployment, Sql.uuid(rs, "mgr_person_id"),
                        PersonData.displayName(rs.getString("mgr_first"), rs.getString("mgr_preferred"),
                                rs.getString("mgr_last")));
            }
            assignmentView = new EmploymentView.AssignmentView(assignment, unit, manager);
        }
        return new EmploymentView(employment, entity, assignmentView);
    }
}
