package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.FieldChange;
import com.palcheck.workforce.domain.OrgUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Makes assignment changes readable in the change history. The domain diff refers to organizational units and managers
 * by id; history is read by people, so the entry stores the names as they were when the change happened (a snapshot,
 * which stays truthful even if a unit is later renamed).
 */
@Component
class AssignmentAuditLabels {

    private final ReferenceDataRepository reference;
    private final EmploymentRepository employments;
    private final PersonRepository people;

    AssignmentAuditLabels(ReferenceDataRepository reference, EmploymentRepository employments,
            PersonRepository people) {
        this.reference = reference;
        this.employments = employments;
        this.people = people;
    }

    List<FieldChange> label(TenantId tenant, List<FieldChange> changes) {
        return changes.stream().map(change -> switch (change.field()) {
            case "orgUnitId" -> new FieldChange("orgUnit", unitName(tenant, change.before()),
                    unitName(tenant, change.after()));
            case "managerEmploymentId" -> new FieldChange("manager", managerName(tenant, change.before()),
                    managerName(tenant, change.after()));
            default -> change;
        }).toList();
    }

    private String unitName(TenantId tenant, String id) {
        if (id == null) {
            return null;
        }
        return reference.findOrgUnit(tenant, UUID.fromString(id)).map(OrgUnit::name).orElse(id);
    }

    private String managerName(TenantId tenant, String employmentId) {
        if (employmentId == null) {
            return null;
        }
        return employments.find(tenant, UUID.fromString(employmentId))
                .flatMap(e -> people.find(tenant, e.personId()))
                .map(p -> p.data().displayName())
                .orElse(employmentId);
    }
}
