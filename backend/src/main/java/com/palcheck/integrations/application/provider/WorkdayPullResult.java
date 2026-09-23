package com.palcheck.integrations.application.provider;

import com.palcheck.integrations.domain.SyncFailureReason;
import java.util.List;

/**
 * What a pull returns: workers to map and write, and failures the provider itself already knows about (a duplicate
 * delivery, a transient outage for one worker) that never need to reach the mapper or {@code workforce.api} at all.
 */
record WorkdayPullResult(List<WorkdayWorkerRecord> workers, List<Failure> failures) {

    record Failure(String workerId, SyncFailureReason reason, String detail) {
    }
}
