package com.palcheck.integrations.application;

import com.palcheck.integrations.domain.SyncRun;
import com.palcheck.shared.TenantId;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository {

    void insert(SyncRun run);

    /** Writes the run's final status and counts; never called mid-run (ADR-0025 decision 6). */
    void complete(SyncRun run);

    Optional<SyncRun> find(TenantId tenantId, UUID id);

    PageResult<SyncRun> listBySystem(TenantId tenantId, UUID systemId, int page, int size);
}
