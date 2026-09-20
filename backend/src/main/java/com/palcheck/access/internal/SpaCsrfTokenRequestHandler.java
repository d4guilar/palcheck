package com.palcheck.access.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF token handling for a single-page app that keeps the token in a readable cookie and echoes it in a header
 * (the pattern documented by Spring Security). The SPA's logout form also echoes the raw cookie. It is constructed
 * in the browser, not reflected in a compressed server response. Other form posts use the BREACH-protected value.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        boolean logoutForm = request.getMethod().equals("POST")
                && request.getRequestURI().equals(request.getContextPath() + "/logout")
                && StringUtils.hasText(request.getParameter(csrfToken.getParameterName()));
        return (StringUtils.hasText(header) || logoutForm ? this.plain : this.xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
