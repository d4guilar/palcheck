package com.palcheck.workforce.application;

import com.palcheck.workforce.domain.EmploymentStatus;
import java.util.UUID;

/** One row of the people directory: a person with the summary of their most relevant employment, if any. */
public record DirectoryItem(UUID personId, String displayName, String workEmail, String title, String orgUnitName,
        String legalEntityName, EmploymentStatus employmentStatus, UUID employmentId) {
}
