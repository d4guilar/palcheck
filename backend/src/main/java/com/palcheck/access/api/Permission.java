package com.palcheck.access.api;

/**
 * The Phase 1 permission set. Application services check permissions, never role names.
 * Scope in Phase 1 is tenant-wide only; narrower scopes (direct reports, org unit) are deferred (ADR-0014, review SEC-01).
 */
public enum Permission {
    VIEW_PEOPLE,
    VIEW_PERSON,
    CREATE_PERSON,
    EDIT_PERSON,
    VIEW_EMPLOYMENT,
    EDIT_EMPLOYMENT,
    VIEW_AUDIT_HISTORY,
    /** Phase 2: the systems catalog, sync history, running a sync, and field lineage (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 7). */
    VIEW_SYSTEMS,
    VIEW_SYNCS,
    RUN_SYNC,
    VIEW_LINEAGE
}
