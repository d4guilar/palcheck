package com.palcheck.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.palcheck.shared.FieldViolation;
import java.time.Instant;
import java.util.List;

/**
 * The one error representation returned by every API failure (RFC 9457 problem details plus PalCheck extensions).
 * Never carries stack traces, SQL, tokens or personal data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String code,
        String requestId,
        String correlationId,
        Instant timestamp,
        List<FieldViolation> errors,
        Long currentVersion,
        String ownedBySystem) {

    public static final String MEDIA_TYPE = "application/problem+json";
}
