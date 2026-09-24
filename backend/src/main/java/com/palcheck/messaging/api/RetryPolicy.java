package com.palcheck.messaging.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The one retry schedule Phase 3 shares between the outbox publisher and every consumer (ADR-0027 decision 4): a
 * documented simplification, not a claim that every future integration should use the same numbers.
 */
public record RetryPolicy(List<Duration> delays) {

    public RetryPolicy {
        delays = List.copyOf(delays);
    }

    public static RetryPolicy of(List<Duration> delays) {
        return new RetryPolicy(delays);
    }

    /** How many attempts this policy allows in total (the first try plus every retry). */
    public int maxAttempts() {
        return delays.size() + 1;
    }

    /** The delay before the next attempt, given how many attempts have already been made; empty once exhausted. */
    public Optional<Duration> delayAfter(int attemptsSoFar) {
        return attemptsSoFar < delays.size() ? Optional.of(delays.get(attemptsSoFar)) : Optional.empty();
    }
}
