package com.palcheck.integrations.application.provider;

import com.palcheck.integrations.domain.NormalizedWorker;
import com.palcheck.integrations.domain.SyncFailureReason;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The Workday-shaped adapter's public face (ADR-0024 decision 1 and 4): the only type in this package anything
 * outside it may reference. It pulls from the mock provider and maps every worker before returning, so
 * {@link WorkdayWorkerRecord} and {@link WorkdayPullResult} — the provider's own vocabulary — never cross this line.
 */
@Component
public class WorkdayAdapter {

    private final MockWorkdayProvider provider;
    private final WorkdayMapper mapper;

    public WorkdayAdapter(MockWorkdayProvider provider, WorkdayMapper mapper) {
        this.provider = provider;
        this.mapper = mapper;
    }

    public Result pull(WorkdayPullOptions options) {
        WorkdayPullResult raw = provider.pull(options);
        List<NormalizedWorker> workers = raw.workers().stream().map(mapper::map).toList();
        List<AdapterFailure> failures = raw.failures().stream()
                .map(f -> new AdapterFailure(f.workerId(), f.reason(), f.detail())).toList();
        return new Result(workers, failures);
    }

    public record Result(List<NormalizedWorker> workers, List<AdapterFailure> failures) {
    }

    public record AdapterFailure(String externalWorkerId, SyncFailureReason reason, String detail) {
    }
}
