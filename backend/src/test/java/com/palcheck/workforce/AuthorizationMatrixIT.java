package com.palcheck.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.access.api.Role;
import com.palcheck.support.Api;
import com.palcheck.support.TestTenant;
import com.palcheck.support.WorkforceIntegrationTest;
import com.palcheck.support.WorkforceWorld;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Every endpoint against every kind of caller: anonymous, signed in with no roles, read-only viewer, administrator.
 * The expected status for each is stated explicitly, so any change in who may do what is a visible test change.
 */
class AuthorizationMatrixIT extends WorkforceIntegrationTest {

    private record Endpoint(String name, HttpMethod method, String path, Object body, int anonymous, int noRole,
            int viewer, int admin) {
    }

    private List<Endpoint> endpoints(WorkforceWorld w, Ids ids) {
        String person = "/api/people/" + ids.personId();
        return List.of(
                new Endpoint("list people", HttpMethod.GET, "/api/people", null, 401, 403, 200, 200),
                new Endpoint("get person", HttpMethod.GET, person, null, 401, 403, 200, 200),
                new Endpoint("get employments", HttpMethod.GET, person + "/employments", null, 401, 403, 200, 200),
                new Endpoint("get change history", HttpMethod.GET, person + "/audit", null, 401, 403, 403, 200),
                new Endpoint("legal entities", HttpMethod.GET, "/api/legal-entities", null, 401, 403, 200, 200),
                new Endpoint("org units", HttpMethod.GET, "/api/org-units", null, 401, 403, 200, 200),
                new Endpoint("edit person", HttpMethod.PATCH, person, Map.of("expectedVersion", 1), 401, 403, 403, 200),
                new Endpoint("edit employment", HttpMethod.PATCH, "/api/employments/" + ids.employmentId(),
                        Map.of("expectedVersion", 1), 401, 403, 403, 200),
                new Endpoint("edit assignment", HttpMethod.PATCH, "/api/assignments/" + ids.assignmentId(),
                        Map.of("expectedVersion", 1), 401, 403, 403, 200),
                new Endpoint("create person", HttpMethod.POST, "/api/people", "create", 401, 403, 403, 201));
    }

    @Test
    void everyEndpointEnforcesItsPermissions() throws Exception {
        var w = world("matrix");
        var ids = hire(w, "Ana", "Vargas");
        var noRole = TestTenant.signInPerson(provisioning, w.tenantId(), "No role");

        for (Endpoint e : endpoints(w, ids)) {
            assertThat(status(e, w, null, null)).as(e.name() + " anonymous").isEqualTo(e.anonymous());
            assertThat(status(e, w, noRole, null)).as(e.name() + " no role").isEqualTo(e.noRole());
            assertThat(status(e, w, w.tenant().viewer(), null)).as(e.name() + " viewer").isEqualTo(e.viewer());
            assertThat(status(e, w, w.tenant().admin(), null)).as(e.name() + " admin").isEqualTo(e.admin());
        }
    }

    @Test
    void deniedCallersLeaveNoTraceInTheData() throws Exception {
        var w = world("matrix-trace");
        var ids = hire(w, "Ana", "Vargas");
        long before = count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value());

        api.patch(w.tenant().viewer(), "/api/people/" + ids.personId(), Map.of("expectedVersion", 1, "firstName", "X"));
        api.post(w.tenant().viewer(), "/api/people", Api.createBody(w, "Bo", "Gus", Api.uniqueEmail("bo")), Api.newKey());

        assertThat(count("SELECT count(*) FROM audit.audit_entry WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM workforce.person WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM platform.idempotency_record WHERE tenant_id = ?", w.tenantId().value())).isEqualTo(1);
    }

    private int status(Endpoint e, WorkforceWorld w, TestTenant.Person as, Object unused) throws Exception {
        Object body = "create".equals(e.body())
                ? Api.createBody(w, "New", "Person", Api.uniqueEmail("new")) : e.body();
        if (as == null) {
            var actions = e.method() == HttpMethod.GET ? api.getAnonymous(e.path())
                    : api.sendAnonymous(e.method(), e.path(), body == null ? Map.of() : body);
            return actions.andReturn().getResponse().getStatus();
        }
        var actions = e.method() == HttpMethod.GET ? api.get(as, e.path())
                : api.send(e.method(), as, e.path(), body == null ? Map.of() : body,
                        e.method() == HttpMethod.POST ? Api.newKey() : null);
        return actions.andReturn().getResponse().getStatus();
    }

    @Test
    void rolesAreOnlyBundlesOfPermissions() {
        assertThat(Role.HR_ADMIN.permissions()).hasSize(11);
        assertThat(Role.HR_VIEWER.permissions()).hasSize(3);
    }
}
