package com.palcheck.support;

import com.palcheck.access.api.TenantProvisioning;
import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.ReferenceDataRepository;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.UUID;

/** A test tenant (admin + viewer) that also has one legal entity and two org units (a parent and a child). */
public record WorkforceWorld(TestTenant tenant, LegalEntity entity, OrgUnit unit, OrgUnit childUnit) {

    public static WorkforceWorld create(TenantProvisioning provisioning, ReferenceDataRepository reference,
            String label) {
        TestTenant tenant = TestTenant.create(provisioning, label);
        TenantId id = tenant.id();
        var entity = new LegalEntity(UUID.randomUUID(), id, "Acme Costa Rica S.A.", "CR");
        var unit = new OrgUnit(UUID.randomUUID(), id, null, "Engineering");
        var child = new OrgUnit(UUID.randomUUID(), id, unit.id(), "Platform");
        reference.insert(entity);
        reference.insert(unit);
        reference.insert(child);
        return new WorkforceWorld(tenant, entity, unit, child);
    }

    public TenantId tenantId() {
        return tenant.id();
    }
}
