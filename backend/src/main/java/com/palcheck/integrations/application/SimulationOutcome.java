package com.palcheck.integrations.application;

/**
 * What a downstream simulation decided (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 4.6). {@code TRANSIENT} and
 * {@code PERMANENT} are the only two failure classes a Phase 3 simulation actually produces; neither simulation has
 * a real network boundary to lose a response on, so nothing here models a genuinely ambiguous outcome (ADR-0027
 * decision 7 explains why that is not a gap).
 */
public record SimulationOutcome(Result result, String detail) {

    public enum Result { SUCCEEDED, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    public static SimulationOutcome succeeded() {
        return new SimulationOutcome(Result.SUCCEEDED, null);
    }

    public static SimulationOutcome transientFailure(String detail) {
        return new SimulationOutcome(Result.TRANSIENT_FAILURE, detail);
    }

    public static SimulationOutcome permanentFailure(String detail) {
        return new SimulationOutcome(Result.PERMANENT_FAILURE, detail);
    }
}
