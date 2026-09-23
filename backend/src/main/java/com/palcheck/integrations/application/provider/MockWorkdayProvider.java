package com.palcheck.integrations.application.provider;

import com.palcheck.integrations.domain.SyncFailureReason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A deterministic, in-process stand-in for a Workday-shaped HRIS (ADR-0024 decision 7: never a real integration).
 * Every worker is fictional, on a reserved email domain, and the fixed set is deliberately irregular so it exercises
 * every controlled failure class Phase 2 needs (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 4.5): one worker
 * references a company code no tenant maps, one has a blank required field, one references a manager nobody else in
 * the set has, and the pull options reproduce a changed value, a source-side duplicate and a transient outage on
 * request rather than by chance.
 *
 * <p>No HTTP, no credential, no real provider behavior is implied; {@code businessTitle}, {@code companyCode} and
 * the status/type codes below are this fixture's own invention, not researched Workday field names.
 */
@Component
public class MockWorkdayProvider {

    /** The two company codes this fixture set uses; {@code SyncRunService} maps them to legal entity names. */
    public static final String COMPANY_CODE_COSTA_RICA = "CR-01";
    public static final String COMPANY_CODE_PANAMA = "PA-01";
    /** Deliberately never mapped, so a worker referencing it always fails as {@code UNKNOWN_LEGAL_ENTITY}. */
    public static final String COMPANY_CODE_UNMAPPED = "XX-99";

    /** The two department codes this fixture set uses; {@code SyncRunService} maps them to org unit names. */
    public static final String DEPARTMENT_ENGINEERING = "ENG";
    public static final String DEPARTMENT_SUPPORT = "SUP";

    WorkdayPullResult pull(WorkdayPullOptions options) {
        List<WorkdayWorkerRecord> workers = new ArrayList<>();
        List<WorkdayPullResult.Failure> failures = new ArrayList<>();

        workers.add(new WorkdayWorkerRecord("W-1001", COMPANY_CODE_COSTA_RICA, DEPARTMENT_ENGINEERING, "Ana María",
                "Delgado Rojas", "ana.delgado@northwind.example", "Support Engineering Lead", "Active_Employee",
                "Regular", null, LocalDate.of(2024, 2, 1)));

        workers.add(new WorkdayWorkerRecord("W-1002", COMPANY_CODE_COSTA_RICA, DEPARTMENT_SUPPORT, "Carlos Enrique",
                "Jiménez Solano", "carlos.jimenez@northwind.example",
                options.changeSupportAnalystTitle() ? "Senior Support Analyst" : "Support Analyst",
                "Active_Employee", "Regular", "W-1001", LocalDate.of(2024, 3, 15)));
        if (options.duplicateSupportAnalyst()) {
            // The same worker id appearing twice in one pull: a source-side data-quality problem SyncRunService
            // must detect on its own (never a fresh create), not something the mapper or workforce.api sees twice.
            workers.add(new WorkdayWorkerRecord("W-1002", COMPANY_CODE_COSTA_RICA, DEPARTMENT_SUPPORT, "Carlos Enrique",
                    "Jiménez Solano", "carlos.jimenez@northwind.example", "Support Analyst", "Active_Employee",
                    "Regular", "W-1001", LocalDate.of(2024, 3, 15)));
        }

        // Blank required field: the mapper passes this through; ImportWorkerService rejects it, nothing is written.
        workers.add(new WorkdayWorkerRecord("W-1003", COMPANY_CODE_COSTA_RICA, DEPARTMENT_SUPPORT, "Daniela",
                "Solís Vargas", "daniela.solis@northwind.example", "", "Active_Employee", "Regular", "W-1001",
                LocalDate.of(2024, 4, 10)));

        // Unmapped company code: no tenant has a legal entity by that resolved name, so this always fails.
        workers.add(new WorkdayWorkerRecord("W-1004", COMPANY_CODE_UNMAPPED, DEPARTMENT_ENGINEERING, "Diego",
                "Mora Castillo", "diego.mora@northwind.example", "Field Technician", "Active_Employee", "Regular",
                null, LocalDate.of(2024, 5, 20)));

        // Manager reference to a worker id that exists nowhere in this pull: succeeds in pass 1, fails only its
        // manager link in pass 2.
        workers.add(new WorkdayWorkerRecord("W-1005", COMPANY_CODE_COSTA_RICA, DEPARTMENT_SUPPORT, "Valeria",
                "Rojas Bermúdez", "valeria.rojas@northwind.example", "Support Specialist", "Active_Employee",
                "Regular", "W-9999", LocalDate.of(2024, 6, 5)));

        if (options.temporaryFailureForRegionalEngineer()) {
            failures.add(new WorkdayPullResult.Failure("W-1006", SyncFailureReason.TEMPORARY_PROVIDER_FAILURE,
                    "The provider timed out returning this worker."));
        } else {
            workers.add(new WorkdayWorkerRecord("W-1006", COMPANY_CODE_PANAMA, DEPARTMENT_ENGINEERING, "Paola Fernanda",
                    "Vindas Chaves", "paola.vindas@northwind.example", "Regional Engineer", "Active_Employee",
                    "Part_Time", "W-1001", LocalDate.of(2024, 7, 12)));
        }

        return new WorkdayPullResult(List.copyOf(workers), List.copyOf(failures));
    }
}
