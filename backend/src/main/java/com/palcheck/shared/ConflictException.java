package com.palcheck.shared;

/** Optimistic-concurrency failure: the record changed since the client read it. */
public class ConflictException extends ApplicationException {

    private final Long currentVersion;

    public ConflictException(String message, Long currentVersion) {
        super(ErrorCode.CONFLICT, message);
        this.currentVersion = currentVersion;
    }

    /** The version now stored, so the client can tell it is stale. May be null when not applicable. */
    public Long currentVersion() {
        return currentVersion;
    }
}
