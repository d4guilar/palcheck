package com.palcheck.shared;

/** Base type for expected, client-visible failures. The message is safe to show to the caller. */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    protected ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
