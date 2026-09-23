package com.palcheck.support;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.application.ReferenceDataRepository;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.UUID;

/**
 * A test tenant with the legal entities and org units {@code SyncRunService}'s code-defined map expects
 * ("Northwind Costa Rica S.A." / "Northwind Panama S.A."; "Engineering" / "Support"), so the mock Workday fixture
 * set (see {@code MockWorkdayProvider}) resolves and imports cleanly.
 */
public record IntegrationsWorld(TestTenant tenant, LegalEntity costaRica, LegalEntity panama, OrgUnit engineering,
        OrgUnit support) {

    public static IntegrationsWorld create(com.palcheck.access.api.TenantProvisioning provisioning,
            ReferenceDataRepository reference, String label) {
        TestTenant tenant = TestTenant.create(provisioning, label);
        TenantId id = tenant.id();
        var costaRica = new LegalEntity(UUID.randomUUID(), id, "Northwind Costa Rica S.A.", "CR");
        var panama = new LegalEntity(UUID.randomUUID(), id, "Northwind Panama S.A.", "PA");
        var engineering = new OrgUnit(UUID.randomUUID(), id, null, "Engineering");
        var support = new OrgUnit(UUID.randomUUID(), id, null, "Support");
        reference.insert(costaRica);
        reference.insert(panama);
        reference.insert(engineering);
        reference.insert(support);
        return new IntegrationsWorld(tenant, costaRica, panama, engineering, support);
    }

    public TenantId tenantId() {
        return tenant.id();
    }
}
