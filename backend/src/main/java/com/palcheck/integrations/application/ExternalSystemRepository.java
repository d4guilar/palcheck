package com.palcheck.integrations.application;

import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExternalSystemRepository {

    void insert(ExternalSystem system);

    Optional<ExternalSystem> findByCode(TenantId tenantId, String code);

    List<ExternalSystem> list(TenantId tenantId);

    void touchLastSyncAt(TenantId tenantId, String code, Instant when);
}
