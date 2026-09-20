package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import java.util.List;
import java.util.UUID;

/** Read-side queries that join workforce tables for display. Tenant scoping happens before paging and counting. */
public interface DirectoryReadModel {

    /**
     * People in a tenant, ordered by family name then given name. {@code query} (may be null or blank) matches
     * given, family and preferred names and the work email, case- and accent-insensitively, as a substring.
     */
    PageResult<DirectoryItem> search(TenantId tenantId, String query, int page, int size);

    PageResult<ManagerCandidate> managers(TenantId tenantId, String query, int page, int size, UUID excludingEmploymentId);

    /** A person's employments with legal entity, current assignment, organizational unit and manager. */
    List<EmploymentView> employmentsOf(TenantId tenantId, UUID personId);
}
