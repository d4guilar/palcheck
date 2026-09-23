package com.palcheck.integrations.application.provider;

import java.time.LocalDate;

/**
 * A worker exactly as the (mock) Workday-shaped provider would send it: its own codes and field names, never seen
 * outside this package (INTEGRATION_ARCHITECTURE.md principle 2; ADR-0024 decision 4). {@code businessTitle},
 * {@code companyCode} and {@code departmentCode} are the kind of provider-specific vocabulary a real adapter would
 * also have to translate.
 */
record WorkdayWorkerRecord(String workerId, String companyCode, String departmentCode, String firstName,
        String lastName, String workEmail, String businessTitle, String employmentStatusCode, String workerTypeCode,
        String managerWorkerId, LocalDate hireDate) {
}
