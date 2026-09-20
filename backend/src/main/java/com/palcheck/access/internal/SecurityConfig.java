package com.palcheck.access.internal;

import com.palcheck.platform.web.ProblemWriter;
import com.palcheck.shared.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;

/**
 * Authentication and session policy (ADR-0022): standards-based OIDC authorization-code login with a same-origin,
 * backend-managed session. Tokens never reach browser JavaScript. Every failure on an authenticated route is a JSON
 * problem, never a redirect to a login page: the single-page app decides what to show.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemWriter problems,
            ClientRegistrationRepository registrations) throws Exception {

        var logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");

        AuthenticationEntryPoint unauthenticated = (request, response, ex) ->
                problems.write(request, response, ErrorCode.UNAUTHENTICATED, "Sign in to continue.");

        AccessDeniedHandler denied = (request, response, ex) -> {
            String detail = ex instanceof CsrfException
                    ? "The request could not be verified. Reload the page and try again."
                    : "You do not have permission to do this.";
            problems.write(request, response, ErrorCode.FORBIDDEN, detail);
        };

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied))
                .oauth2Login(login -> login
                        // Disables the generated /login page; the single-page app starts login itself.
                        .loginPage("/oauth2/authorization/oidc")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, ex) -> response.sendRedirect("/?login=failed")))
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutHandler))
                .sessionManagement(Customizer.withDefaults());
        return http.build();
    }
}
