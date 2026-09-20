package com.palcheck.shared;

/** The same idempotency key was reused with a different request. */
public class IdempotencyConflictException extends ApplicationException {

    public IdempotencyConflictException(String message) {
        super(ErrorCode.IDEMPOTENCY_CONFLICT, message);
    }
}
