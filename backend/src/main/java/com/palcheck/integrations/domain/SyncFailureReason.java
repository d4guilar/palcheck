package com.palcheck.integrations.domain;

/**
 * The closed set of controlled failure classes a Phase 2 sync item can end in (docs/PHASE_2_IMPLEMENTATION_PLAN.md
 * section 4.5). Every one is reachable from the mock provider or the import path; none is a generic catch-all.
 */
public enum SyncFailureReason {
    IMPORT_FAILED,
    UNKNOWN_LEGAL_ENTITY,
    MISSING_REQUIRED_FIELD,
    INVALID_MANAGER_REFERENCE,
    DUPLICATE_EXTERNAL_RECORD,
    TEMPORARY_PROVIDER_FAILURE
}
