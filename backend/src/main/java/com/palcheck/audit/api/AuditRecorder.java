package com.palcheck.audit.api;

/**
 * Records a change in the caller's transaction. It requires an existing transaction (propagation MANDATORY), so an
 * audit entry can never be written outside the business change it describes, and a failure to write it fails the
 * business operation. Audit is never recorded asynchronously after commit.
 */
public interface AuditRecorder {

    void record(AuditRecord record);
}
