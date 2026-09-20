package com.palcheck.workforce.application;

/**
 * Raised by persistence when the unique work-email rule is violated (including by a concurrent request that
 * won a race). The application service turns it into a field-level validation error.
 */
public class WorkEmailAlreadyInUseException extends RuntimeException {

    public WorkEmailAlreadyInUseException() {
        super("work email already in use");
    }
}
