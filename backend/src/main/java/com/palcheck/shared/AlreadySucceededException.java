package com.palcheck.shared;

/**
 * A replay was requested for an integration operation that already succeeded (ADR-0027). Distinct from
 * {@link ConflictException}: nothing is stale here, replaying would simply risk repeating a completed business
 * action, so replay is refused outright rather than retried.
 */
public class AlreadySucceededException extends ApplicationException {

    public AlreadySucceededException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
