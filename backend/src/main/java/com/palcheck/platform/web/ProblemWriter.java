package com.palcheck.platform.web;

import com.palcheck.shared.ErrorCode;
import com.palcheck.shared.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Builds and writes {@link ApiProblem} bodies, for both controller advice and security-filter failures. */
@Component
public class ProblemWriter {

    private static final String TYPE_PREFIX = "urn:palcheck:problem:";

    private final JsonMapper mapper;
    private final Clock clock;

    public ProblemWriter(JsonMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, TENANT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case IDEMPOTENCY_CONFLICT -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case EXTERNALLY_OWNED -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public ApiProblem build(HttpServletRequest request, HttpStatus status, ErrorCode code, String detail,
            List<FieldViolation> errors, Long currentVersion) {
        return build(request, status, code, detail, errors, currentVersion, null);
    }

    public ApiProblem build(HttpServletRequest request, HttpStatus status, ErrorCode code, String detail,
            List<FieldViolation> errors, Long currentVersion, String ownedBySystem) {
        return new ApiProblem(
                TYPE_PREFIX + code.name().toLowerCase().replace('_', '-'),
                title(code),
                status.value(),
                detail,
                code.name(),
                (String) request.getAttribute(CorrelationFilter.REQUEST_ID_ATTRIBUTE),
                (String) request.getAttribute(CorrelationFilter.CORRELATION_ID_ATTRIBUTE),
                Instant.now(clock),
                errors == null || errors.isEmpty() ? null : List.copyOf(errors),
                currentVersion,
                ownedBySystem);
    }

    public ApiProblem build(HttpServletRequest request, ErrorCode code, String detail) {
        return build(request, statusFor(code), code, detail, null, null, null);
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        write(response, build(request, code, detail));
    }

    public void write(HttpServletResponse response, ApiProblem problem) throws IOException {
        response.setStatus(problem.status());
        response.setContentType(ApiProblem.MEDIA_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), problem);
    }

    private static String title(ErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> "The request is not valid";
            case UNAUTHENTICATED -> "Authentication is required";
            case FORBIDDEN -> "You do not have permission to do this";
            case TENANT_ACCESS_DENIED -> "Your account is not assigned to an active tenant";
            case NOT_FOUND -> "The resource was not found";
            case METHOD_NOT_ALLOWED -> "The method is not allowed";
            case CONFLICT -> "The record changed since it was read";
            case IDEMPOTENCY_CONFLICT -> "The idempotency key was already used for a different request";
            case PAYLOAD_TOO_LARGE -> "The request is too large";
            case EXTERNALLY_OWNED -> "This record is managed by an external system";
            case INTERNAL_ERROR -> "An unexpected error occurred";
        };
    }
}
