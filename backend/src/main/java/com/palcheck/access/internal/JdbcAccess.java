package com.palcheck.access.internal;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Role;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the {@code access} schema: tenants, principals and their roles. Everything else reaches this data only
 * through the {@code access.api} package.
 */
@Repository
class JdbcAccess implements TenantProvisioning {

    /** A principal with the tenant it belongs to and the roles it holds, as resolved from (issuer, subject). */
    record PrincipalContext(UUID principalId, String displayName, boolean principalActive, UUID tenantId,
            String tenantSlug, String tenantName, boolean tenantActive, Set<Role> roles) {
    }

    private final JdbcClient jdbc;

    JdbcAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Resolves the trusted server-side mapping from an identity provider's (issuer, subject) to a principal. */
    @Transactional(readOnly = true)
    Optional<PrincipalContext> findContext(String issuer, String subject) {
        return jdbc.sql("""
                        SELECT p.id, p.display_name, p.status AS principal_status,
                               t.id AS tenant_id, t.slug, t.name AS tenant_name, t.status AS tenant_status,
                               COALESCE(string_agg(r.role, ','), '') AS roles
                        FROM access.principal p
                        JOIN access.tenant t ON t.id = p.tenant_id
                        LEFT JOIN access.principal_role r ON r.principal_id = p.id
                        WHERE p.issuer = :issuer AND p.subject = :subject
                        GROUP BY p.id, t.id
                        """)
                .param("issuer", issuer)
                .param("subject", subject)
                .query((rs, n) -> new PrincipalContext(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        "ACTIVE".equals(rs.getString("principal_status")),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("tenant_name"),
                        "ACTIVE".equals(rs.getString("tenant_status")),
                        parseRoles(rs.getString("roles"))))
                .optional();
    }

    private static Set<Role> parseRoles(String csv) {
        if (csv.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(Role::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    @Override
    @Transactional
    public TenantRef createTenant(String slug, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO access.tenant (id, slug, name, status) VALUES (:id, :slug, :name, 'ACTIVE')")
                .param("id", id).param("slug", slug).param("name", name).update();
        return new TenantRef(TenantId.of(id), slug, name);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantRef> findTenantBySlug(String slug) {
        return jdbc.sql("SELECT id, slug, name FROM access.tenant WHERE slug = :slug")
                .param("slug", slug)
                .query((rs, n) -> new TenantRef(TenantId.of(rs.getObject("id", UUID.class)), rs.getString("slug"),
                        rs.getString("name")))
                .optional();
    }

    @Override
    @Transactional
    public PrincipalRef createPrincipal(TenantId tenantId, String issuer, String subject, String displayName,
            Set<Role> roles) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO access.principal (id, tenant_id, issuer, subject, display_name, status)
                        VALUES (:id, :tenant, :issuer, :subject, :name, 'ACTIVE')
                        """)
                .param("id", id).param("tenant", tenantId.value()).param("issuer", issuer)
                .param("subject", subject).param("name", displayName).update();
        for (Role role : roles) {
            jdbc.sql("INSERT INTO access.principal_role (principal_id, role) VALUES (:id, :role)")
                    .param("id", id).param("role", role.name()).update();
        }
        return new PrincipalRef(id, tenantId, issuer, subject, displayName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PrincipalRef> findPrincipal(String issuer, String subject) {
        return jdbc.sql("""
                        SELECT id, tenant_id, issuer, subject, display_name
                        FROM access.principal WHERE issuer = :issuer AND subject = :subject
                        """)
                .param("issuer", issuer).param("subject", subject)
                .query((rs, n) -> new PrincipalRef(rs.getObject("id", UUID.class),
                        TenantId.of(rs.getObject("tenant_id", UUID.class)), rs.getString("issuer"),
                        rs.getString("subject"), rs.getString("display_name")))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Actor actorFor(UUID principalId) {
        var row = jdbc.sql("""
                        SELECT p.display_name, p.status AS principal_status, p.tenant_id,
                               COALESCE(string_agg(r.role, ','), '') AS roles
                        FROM access.principal p
                        LEFT JOIN access.principal_role r ON r.principal_id = p.id
                        WHERE p.id = :id
                        GROUP BY p.id
                        """)
                .param("id", principalId)
                .query((rs, n) -> new Object[] {rs.getString("display_name"), rs.getString("principal_status"),
                        rs.getObject("tenant_id", UUID.class), parseRoles(rs.getString("roles"))})
                .optional().orElseThrow(() -> new IllegalArgumentException("Unknown principal"));
        if (!"ACTIVE".equals(row[1])) {
            throw new IllegalArgumentException("Principal is not active");
        }
        @SuppressWarnings("unchecked")
        Set<Role> roles = (Set<Role>) row[3];
        return new Actor(principalId, TenantId.of((UUID) row[2]), (String) row[0], ActorResolver.permissionsOf(roles));
    }

    @Override
    @Transactional
    public void setTenantActive(TenantId tenantId, boolean active) {
        jdbc.sql("UPDATE access.tenant SET status = :status WHERE id = :id")
                .param("status", active ? "ACTIVE" : "SUSPENDED").param("id", tenantId.value()).update();
    }

    @Override
    @Transactional
    public void setPrincipalActive(UUID principalId, boolean active) {
        jdbc.sql("UPDATE access.principal SET status = :status WHERE id = :id")
                .param("status", active ? "ACTIVE" : "DISABLED").param("id", principalId).update();
    }
}
