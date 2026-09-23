package com.palcheck.integrations.application.provider;

/**
 * Test and demo controls over an otherwise fully deterministic pull (docs/PHASE_2_IMPLEMENTATION_PLAN.md section
 * 4.5). {@link #baseline()} is what a normal sync uses; the flags exist so specific, named scenarios (a changed
 * value, a source-side duplicate, a transient provider outage) are reproducible in a test rather than random.
 */
public record WorkdayPullOptions(boolean changeSupportAnalystTitle, boolean duplicateSupportAnalyst,
        boolean temporaryFailureForRegionalEngineer) {

    public static WorkdayPullOptions baseline() {
        return new WorkdayPullOptions(false, false, false);
    }
}
