package com.palcheck.integrations.domain;

import com.palcheck.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/** One configured external system. Phase 2 has exactly one, {@code workday-simulation}, always {@code mode SIMULATION}. */
public record ExternalSystem(UUID id, TenantId tenantId, String code, String displayName, String type, String mode,
        Instant lastSyncAt, Instant createdAt) {

    public static final String TYPE_HRIS = "HRIS";
    public static final String MODE_SIMULATION = "SIMULATION";
    public static final String WORKDAY_SIMULATION_CODE = "workday-simulation";
    public static final String WORKDAY_SIMULATION_DISPLAY_NAME = "Workday Simulation";

    public static ExternalSystem workdaySimulation(TenantId tenantId, Instant now) {
        return new ExternalSystem(UUID.randomUUID(), tenantId, WORKDAY_SIMULATION_CODE,
                WORKDAY_SIMULATION_DISPLAY_NAME, TYPE_HRIS, MODE_SIMULATION, null, now);
    }
}
