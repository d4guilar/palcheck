package com.palcheck.integrations.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A very small in-process stand-in for an equipment-provisioning system. Never a real provider; always succeeds
 * unless a test or a demo has explicitly forced a scenario for this assignment.
 *
 * <p>{@code OUT_OF_STOCK} is transient and clears itself after one forced failure (restocking is assumed to
 * happen on its own by the next attempt). {@code UNSUPPORTED_EQUIPMENT_PROFILE} is permanent and stays forced
 * until something explicitly clears it — representing an operator fixing the configuration before replaying,
 * exactly as Daniel's brief describes ("fixes/selects recovery scenario, then replays").
 */
@Component
public class EquipmentSimulator {

    public enum Scenario { OUT_OF_STOCK, UNSUPPORTED_EQUIPMENT_PROFILE }

    private final Map<String, Scenario> forced = new ConcurrentHashMap<>();

    public void forceScenario(String assignmentId, Scenario scenario) {
        forced.put(assignmentId, scenario);
    }

    public void clearScenario(String assignmentId) {
        forced.remove(assignmentId);
    }

    public SimulationOutcome provision(String assignmentId) {
        Scenario scenario = forced.get(assignmentId);
        if (scenario == Scenario.OUT_OF_STOCK) {
            forced.remove(assignmentId);
            return SimulationOutcome.transientFailure("Requested equipment is temporarily out of stock.");
        }
        if (scenario == Scenario.UNSUPPORTED_EQUIPMENT_PROFILE) {
            return SimulationOutcome.permanentFailure("This assignment's equipment profile is not supported.");
        }
        return SimulationOutcome.succeeded();
    }
}
