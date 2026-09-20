package com.palcheck.platform.web;

import com.palcheck.platform.logging.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * First filter in the chain. Establishes request and correlation identifiers (accepting a client-supplied
 * correlation ID only when it matches a strict pattern, which prevents log injection and unbounded input),
 * publishes them through the MDC and response headers, and writes exactly one access-log line per request.
 *
 * <p>Access logging records the method, the matched route <em>template</em>, the status and the duration. It never
 * records the query string, headers, cookies or bodies, because those can carry personal data or credentials.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_ATTRIBUTE = CorrelationFilter.class.getName() + ".requestId";
    public static final String CORRELATION_ID_ATTRIBUTE = CorrelationFilter.class.getName() + ".correlationId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("com.palcheck.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The request ID is always generated server-side; only the correlation ID may be supplied by the caller.
        String requestId = UUID.randomUUID().toString();
        String correlationId = safeOrElse(request.getHeader(CORRELATION_ID_HEADER), requestId);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(LogKeys.REQUEST_ID, requestId);
        MDC.put(LogKeys.CORRELATION_ID, correlationId);

        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logAccess(request, response, started);
            MDC.clear();
        }
    }

    /** Health probes are polled constantly, so they are logged at debug level only. */
    private void logAccess(HttpServletRequest request, HttpServletResponse response, long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern.toString() : "unmatched";
        boolean probe = request.getRequestURI().startsWith("/actuator/health");
        var event = probe ? ACCESS_LOG.atDebug() : ACCESS_LOG.atInfo();
        event.addKeyValue("http.method", request.getMethod())
                .addKeyValue("http.route", route)
                .addKeyValue("http.status", response.getStatus())
                .addKeyValue("durationMs", millis)
                .log("request completed");
    }

    private static String safeOrElse(String candidate, String fallback) {
        return candidate != null && SAFE_ID.matcher(candidate).matches() ? candidate : fallback;
    }
}
