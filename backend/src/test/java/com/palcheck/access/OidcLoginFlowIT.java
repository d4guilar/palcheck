package com.palcheck.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.access.api.Role;
import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.support.TestPostgresInitializer;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real OpenID Connect authorization-code flow, end to end over HTTP: the application redirects the browser to an
 * identity provider, the provider returns an authorization code, the application exchanges it and establishes a
 * session, the server-side principal mapping resolves the tenant, and logout ends the session. The identity provider
 * is a real OIDC server (mock-oauth2-server) started in-process, so no container runtime is needed. It stands in for
 * Keycloak; the application only knows the issuer URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = TestPostgresInitializer.class)
class OidcLoginFlowIT {

    private static final String ISSUER_ID = "palcheck";
    private static MockOAuth2Server idp;

    @BeforeAll
    static void startIdentityProvider() throws IOException {
        idp = new MockOAuth2Server();
        idp.start();
    }

    @AfterAll
    static void stopIdentityProvider() {
        idp.shutdown();
    }

    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry registry) throws IOException {
        if (idp == null) {
            idp = new MockOAuth2Server();
            idp.start();
        }
        registry.add("spring.security.oauth2.client.provider.oidc.issuer-uri", () -> idp.issuerUrl(ISSUER_ID).toString());
        registry.add("spring.security.oauth2.client.registration.oidc.client-secret", () -> "flow-test-secret");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @LocalServerPort
    int port;

    @Autowired
    TenantProvisioning provisioning;

    CookieManager cookies;
    HttpClient browser;

    @BeforeEach
    void newBrowser() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Test
    void signsInThroughTheIdentityProviderAndResolvesTheTenantFromTheServerSideMapping() throws Exception {
        String subject = "user-" + UUID.randomUUID();
        var tenant = provisioning.createTenant("flow-" + UUID.randomUUID().toString().substring(0, 8), "Flow tenant");
        provisioning.createPrincipal(tenant.id(), idp.issuerUrl(ISSUER_ID).toString(), subject, "Flow Admin",
                Set.of(Role.HR_ADMIN));

        // Before signing in the API answers 401 with a JSON problem, not a redirect.
        assertThat(get("/api/session").statusCode()).isEqualTo(401);

        signIn(subject);

        var session = get("/api/session");
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains("\"slug\":\"" + tenant.slug() + "\"").contains("Flow Admin")
                .contains("VIEW_PEOPLE");
    }

    @Test
    void anIdentityWithoutAPalCheckPrincipalSignsInButIsDeniedAnyTenant() throws Exception {
        signIn("stranger-" + UUID.randomUUID());

        var session = get("/api/session");
        assertThat(session.statusCode()).isEqualTo(403);
        assertThat(session.body()).contains("TENANT_ACCESS_DENIED");
    }

    @Test
    void logoutRequiresCsrfAndRedirectsTheDocumentWithoutReturningTokensInJson() throws Exception {
        String subject = "user-" + UUID.randomUUID();
        var tenant = provisioning.createTenant("logout-" + UUID.randomUUID().toString().substring(0, 8), "Logout tenant");
        provisioning.createPrincipal(tenant.id(), idp.issuerUrl(ISSUER_ID).toString(), subject, "Logout User",
                Set.of(Role.HR_VIEWER));
        signIn(subject);
        assertThat(get("/api/session").statusCode()).isEqualTo(200);

        // Without the CSRF header the logout is refused and the session survives.
        var refused = post("/logout", Map.of());
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(get("/api/session").statusCode()).isEqualTo(200);

        var rejectedForm = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf=wrong")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rejectedForm.statusCode()).isEqualTo(403);
        assertThat(get("/api/session").statusCode()).isEqualTo(200);

        var accepted = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf=" + java.net.URLEncoder.encode(cookie("XSRF-TOKEN"), java.nio.charset.StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(302);
        assertThat(accepted.headers().firstValue("Location").orElseThrow()).startsWith(idp.issuerUrl(ISSUER_ID).toString());
        assertThat(accepted.body()).doesNotContain("redirectUrl", "id_token_hint");
        assertThat(get("/api/session").statusCode()).isEqualTo(401);
    }

    // --- a minimal browser -------------------------------------------------------------------------------------

    private void signIn(String subject) throws Exception {
        idp.enqueueCallback(new DefaultOAuth2TokenCallback(ISSUER_ID, subject, "JWT", List.of("palcheck-web"),
                Map.of(), 3600));
        var start = get("/oauth2/authorization/oidc");
        assertThat(start.statusCode()).isEqualTo(302);
        String authorizeUrl = start.headers().firstValue("Location").orElseThrow();
        assertThat(authorizeUrl).startsWith(idp.issuerUrl(ISSUER_ID).toString());

        // The identity provider (non-interactive) redirects straight back with an authorization code.
        var authorized = browser.send(HttpRequest.newBuilder(URI.create(authorizeUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authorized.statusCode()).isEqualTo(302);
        String callbackUrl = authorized.headers().firstValue("Location").orElseThrow();
        assertThat(callbackUrl).contains("/login/oauth2/code/oidc").contains("code=");

        // The application exchanges the code and establishes the session.
        var callback = browser.send(HttpRequest.newBuilder(URI.create(callbackUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location").orElse("")).doesNotContain("login=failed");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> headers)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::header);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElseThrow();
    }
}
