package com.palcheck.integrations.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.integrations.domain.ExternalSystem;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The systems catalog (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 6). Phase 2 has exactly one configurable system
 * per tenant, the Workday simulation; there is no administration UI to create it, so the first read or sync lazily
 * provisions it, the same way a real catalog row would exist from setup.
 */
@Service
public class ExternalSystemService {

    private final Authorization authorization;
    private final ExternalSystemRepository systems;
    private final Clock clock;

    ExternalSystemService(Authorization authorization, ExternalSystemRepository systems, Clock clock) {
        this.authorization = authorization;
        this.systems = systems;
        this.clock = clock;
    }

    public List<ExternalSystem> list(Actor actor) {
        authorization.require(actor, Permission.VIEW_SYSTEMS);
        ensureWorkdaySimulation(actor.tenantId());
        return systems.list(actor.tenantId());
    }

    public ExternalSystem get(Actor actor, String code) {
        authorization.require(actor, Permission.VIEW_SYSTEMS);
        ensureWorkdaySimulation(actor.tenantId());
        return systems.findByCode(actor.tenantId(), code)
                .orElseThrow(() -> new NotFoundException("Unknown system."));
    }

    /**
     * Provisions the tenant's system row if it does not exist yet, without a permission check of its own: callers
     * that need a different permission (running or listing syncs) check that permission themselves first, then call
     * this so the same lazy provisioning applies everywhere the row is needed.
     */
    void ensureWorkdaySimulation(TenantId tenantId) {
        if (systems.findByCode(tenantId, ExternalSystem.WORKDAY_SIMULATION_CODE).isPresent()) {
            return;
        }
        try {
            systems.insert(ExternalSystem.workdaySimulation(tenantId, Instant.now(clock)));
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent first request; the row now exists either way.
        }
    }
}
