package com.palcheck.audit.api;

import com.palcheck.shared.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * Reads audit entries. It does not authorize: audit never depends on the access module, so the calling application
 * facade must check permission and that the record exists in the tenant before calling.
 */
public interface AuditHistory {

    /** Entries for a record, newest first. {@code page} is zero-based. */
    Page find(TenantId tenantId, String recordType, UUID recordId, int page, int size);

    record Page(List<AuditEntryView> items, int page, int size, long totalItems) {
    }
}
