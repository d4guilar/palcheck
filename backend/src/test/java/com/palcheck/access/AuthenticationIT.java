package com.palcheck.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.palcheck.access.api.Role;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.support.PalCheckIntegrationTest;
import com.palcheck.support.TestTenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authentication, the trusted principal-to-tenant mapping, and the API error model at the security boundary.
 * The OIDC login flow itself is exercised end to end in {@link OidcLoginFlowIT}.
 */
@PalCheckIntegrationTest
class AuthenticationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantProvisioning provisioning;

    @Test
    void anonymousApiRequestGetsJsonProblemNotARedirect() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void mappedAdministratorSeesTenantAndPermissions() throws Exception {
        var tenant = TestTenant.create(provisioning, "session");

        mvc.perform(get("/api/session").with(tenant.admin().login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.slug").value(tenant.slug()))
                .andExpect(jsonPath("$.user.displayName").value(tenant.admin().displayName()))
                .andExpect(jsonPath("$.permissions.length()").value(11))
                .andExpect(jsonPath("$.permissions[?(@ == 'VIEW_AUDIT_HISTORY')]").isNotEmpty());
    }

    @Test
    void viewerRoleGrantsOnlyReadPermissions() throws Exception {
        var tenant = TestTenant.create(provisioning, "viewer");

        mvc.perform(get("/api/session").with(tenant.viewer().login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.permissions[?(@ == 'EDIT_PERSON')]").isEmpty())
                .andExpect(jsonPath("$.permissions[?(@ == 'VIEW_AUDIT_HISTORY')]").isEmpty());
    }

    @Test
    void validIdentityWithoutAPalCheckPrincipalIsDeniedAccessToAnyTenant() throws Exception {
        mvc.perform(get("/api/session").with(TestTenant.unmappedLogin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void suspendedTenantDeniesItsPrincipals() throws Exception {
        var tenant = TestTenant.create(provisioning, "suspended");
        provisioning.setTenantActive(tenant.id(), false);

        mvc.perform(get("/api/session").with(tenant.admin().login()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void disablingAPrincipalTakesEffectOnTheNextRequest() throws Exception {
        var tenant = TestTenant.create(provisioning, "disabled");
        mvc.perform(get("/api/session").with(tenant.admin().login())).andExpect(status().isOk());

        provisioning.setPrincipalActive(tenant.admin().principalId(), false);

        mvc.perform(get("/api/session").with(tenant.admin().login()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void aPrincipalWithNoRolesIsAuthenticatedButHasNoPermissions() throws Exception {
        var tenant = TestTenant.create(provisioning, "norole");
        var nobody = TestTenant.signInPerson(provisioning, tenant.id(), "Nobody");

        mvc.perform(get("/api/session").with(nobody.login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(0));
        assertThat(Role.values()).hasSize(2);
    }

    @Test
    void mutatingRequestWithoutCsrfTokenIsRejectedAsForbiddenProblem() throws Exception {
        var tenant = TestTenant.create(provisioning, "csrf");

        mvc.perform(post("/api/session").with(tenant.admin().login()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unsupportedMethodWithValidCsrfIsAProblemWithTheSameShape() throws Exception {
        var tenant = TestTenant.create(provisioning, "method");

        mvc.perform(post("/api/session").with(tenant.admin().login()).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownRouteIsNotFoundProblem() throws Exception {
        var tenant = TestTenant.create(provisioning, "route");

        mvc.perform(get("/api/does-not-exist").with(tenant.admin().login()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wellFormedCorrelationIdIsEchoedAndAMaliciousOneIsReplaced() throws Exception {
        mvc.perform(get("/api/session").header("X-Correlation-Id", "trace-42.abc"))
                .andExpect(header().string("X-Correlation-Id", "trace-42.abc"))
                .andExpect(jsonPath("$.correlationId").value("trace-42.abc"));

        var result = mvc.perform(get("/api/session").header("X-Correlation-Id", "bad id\r\nX-Injected: 1"))
                .andReturn().getResponse();
        assertThat(result.getHeader("X-Correlation-Id")).matches("^[A-Za-z0-9._:-]{1,64}$")
                .isEqualTo(result.getHeader("X-Request-Id"));
    }
}
