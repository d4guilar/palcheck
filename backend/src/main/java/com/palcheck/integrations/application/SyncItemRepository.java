package com.palcheck.integrations.application;

import com.palcheck.integrations.domain.SyncItem;
import com.palcheck.shared.TenantId;
import java.util.UUID;

public interface SyncItemRepository {

    void insert(SyncItem item);

    void markUpdated(TenantId tenantId, UUID itemId);

    PageResult<SyncItem> listByRun(TenantId tenantId, UUID syncRunId, int page, int size);
}
