package com.palcheck.integrations.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A very small in-process stand-in for an identity-provisioning system (Daniel's Phase 3 brief). Never a real
 * identity provider; always succeeds unless a test or a demo has explicitly forced a scenario for this employment,
 * matching the Phase 2 {@code WorkdayPullOptions} precedent for controlled, test-only failure injection.
 */
@Component
public class IdentitySimulator {

    public enum Scenario { TEMPORARY_OUTAGE }

    private final Map<String, Scenario> forced = new ConcurrentHashMap<>();

    /** Test/dev-only: forces the given scenario the next time this employment is provisioned. */
    public void forceScenario(String employmentId, Scenario scenario) {
        forced.put(employmentId, scenario);
    }

    public void clearScenario(String employmentId) {
        forced.remove(employmentId);
    }

    public SimulationOutcome provision(String employmentId) {
        Scenario scenario = forced.remove(employmentId);
        if (scenario == Scenario.TEMPORARY_OUTAGE) {
            return SimulationOutcome.transientFailure("Identity provisioning service temporarily unavailable.");
        }
        return SimulationOutcome.succeeded();
    }
}
