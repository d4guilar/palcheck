package com.palcheck.workforce.application;

import com.palcheck.platform.idempotency.Fingerprint;
import com.palcheck.workforce.domain.AssignmentData;
import com.palcheck.workforce.domain.EmploymentData;
import com.palcheck.workforce.domain.PersonData;
import java.util.UUID;

/**
 * Creates a Person together with their first Employment and current Assignment in one atomic operation.
 * The values are as supplied by the caller; the service normalizes and validates them.
 */
public record RegisterWorkerCommand(PersonData person, UUID legalEntityId, EmploymentData employment,
        AssignmentData assignment, String reason) {

    /** Identity of the request for idempotency: the same content always yields the same fingerprint. */
    Fingerprint fingerprint() {
        // Fingerprint the normalized content, so requests that differ only in case or surrounding whitespace match.
        PersonData p = person.normalized();
        EmploymentData e = employment.normalized();
        AssignmentData a = assignment.normalized();
        return Fingerprint.of("person.create")
                .part("firstName", p.firstName()).part("middleName", p.middleName())
                .part("lastName", p.lastName()).part("preferredName", p.preferredName())
                .part("workEmail", p.workEmail()).part("locale", p.locale())
                .part("legalEntityId", legalEntityId)
                .part("status", e.status()).part("employmentType", e.employmentType())
                .part("country", e.country()).part("startDate", e.startDate()).part("endDate", e.endDate())
                .part("title", a.title()).part("orgUnitId", a.orgUnitId())
                .part("managerEmploymentId", a.managerEmploymentId())
                .part("reason", Reasons.clean(reason));
    }
}
