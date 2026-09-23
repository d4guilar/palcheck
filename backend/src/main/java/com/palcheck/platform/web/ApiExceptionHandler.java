package com.palcheck.platform.web;

import com.palcheck.shared.ApplicationException;
import com.palcheck.shared.ConflictException;
import com.palcheck.shared.ErrorCode;
import com.palcheck.shared.ExternallyOwnedException;
import com.palcheck.shared.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to one {@link ApiProblem} shape. Expected {@link ApplicationException}s carry a safe message;
 * framework exceptions get generic text; anything else becomes INTERNAL_ERROR without leaking details. Unexpected
 * exceptions are logged by type and database error code only, because their messages can contain personal values
 * (a unique-constraint violation, for example, echoes the duplicated key).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemWriter problems;

    public ApiExceptionHandler(ProblemWriter problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<Object> validation(ValidationException ex, HttpServletRequest request) {
        return respond(problems.build(request, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, ex.getMessage(),
                ex.violations(), null));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> conflict(ConflictException ex, HttpServletRequest request) {
        return respond(problems.build(request, HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), null,
                ex.currentVersion()));
    }

    @ExceptionHandler(ExternallyOwnedException.class)
    ResponseEntity<Object> externallyOwned(ExternallyOwnedException ex, HttpServletRequest request) {
        return respond(problems.build(request, ProblemWriter.statusFor(ErrorCode.EXTERNALLY_OWNED),
                ErrorCode.EXTERNALLY_OWNED, ex.getMessage(), null, null, ex.ownedBySystem()));
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<Object> application(ApplicationException ex, HttpServletRequest request) {
        return respond(problems.build(request, ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        LOG.atError()
                .addKeyValue("exception", ex.getClass().getName())
                .addKeyValue("dbErrorCode", databaseErrorCode(ex))
                .addKeyValue("result", "error")
                .log("Unhandled exception while processing request");
        return respond(problems.build(request, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));
    }

    /** Spring MVC's own exceptions: malformed JSON, unsupported method or media type, unknown route, and so on. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest webRequest) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ErrorCode code;
        String detail;
        if (status == HttpStatus.NOT_FOUND) {
            code = ErrorCode.NOT_FOUND;
            detail = "The requested resource was not found.";
        } else if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            code = ErrorCode.METHOD_NOT_ALLOWED;
            detail = "The HTTP method is not allowed for this resource.";
        } else if (status.is5xxServerError()) {
            code = ErrorCode.INTERNAL_ERROR;
            detail = "An unexpected error occurred.";
        } else {
            code = ErrorCode.VALIDATION_ERROR;
            detail = "The request could not be processed. Check the request format and content type.";
        }
        return respond(problems.build(request, status, code, detail, null, null));
    }

    private static ResponseEntity<Object> respond(ApiProblem problem) {
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(ApiProblem.MEDIA_TYPE))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem);
    }

    private static String databaseErrorCode(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
