package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Legal entities and organizational units of a tenant. Phase 1 creates them by seeding only (no administration UI). */
public interface ReferenceDataRepository {

    void insert(LegalEntity legalEntity);

    void insert(OrgUnit orgUnit);

    List<LegalEntity> legalEntities(TenantId tenantId);

    List<OrgUnit> orgUnits(TenantId tenantId);

    Optional<LegalEntity> findLegalEntity(TenantId tenantId, UUID legalEntityId);

    Optional<OrgUnit> findOrgUnit(TenantId tenantId, UUID orgUnitId);
}
