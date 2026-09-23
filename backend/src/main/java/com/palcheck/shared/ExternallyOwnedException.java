package com.palcheck.shared;

/**
 * A human tried to edit a field or record an external system owns (Phase 2 ownership enforcement). Distinct from
 * {@link ConflictException}: the record is not stale, it is simply not locally editable while a system owns it.
 */
public class ExternallyOwnedException extends ApplicationException {

    private final String ownedBySystem;

    public ExternallyOwnedException(String message, String ownedBySystem) {
        super(ErrorCode.EXTERNALLY_OWNED, message);
        this.ownedBySystem = ownedBySystem;
    }

    /** The stable code of the system that owns the record, for the client to name in its message. */
    public String ownedBySystem() {
        return ownedBySystem;
    }
}
