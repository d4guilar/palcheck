package com.palcheck.shared;

/** The closed set of API error categories. HTTP status mapping lives in the platform web layer. */
public enum ErrorCode {
    VALIDATION_ERROR,
    UNAUTHENTICATED,
    FORBIDDEN,
    TENANT_ACCESS_DENIED,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    CONFLICT,
    IDEMPOTENCY_CONFLICT,
    PAYLOAD_TOO_LARGE,
    INTERNAL_ERROR
}
