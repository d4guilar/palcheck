package com.palcheck.workforce.application;

import com.palcheck.workforce.domain.Assignment;
import com.palcheck.workforce.domain.Employment;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.UUID;

/** An employment with the reference data needed to display it. */
public record EmploymentView(Employment employment, LegalEntity legalEntity, AssignmentView currentAssignment) {

    /** The current assignment with its organizational unit and manager; null fields when absent. */
    public record AssignmentView(Assignment assignment, OrgUnit orgUnit, ManagerRef manager) {
    }

    public record ManagerRef(UUID employmentId, UUID personId, String displayName) {
    }
}
