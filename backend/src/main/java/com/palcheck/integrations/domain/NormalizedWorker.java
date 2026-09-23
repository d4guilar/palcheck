package com.palcheck.integrations.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * The normalized contract for one external worker (ADR-0024 decision 5): what {@code integrations} needs, in
 * PalCheck's own vocabulary, with no provider field name, code value or DTO type surviving from the adapter. It is
 * an integration/application boundary type, not a second permanent domain model — nothing persists it as a whole,
 * and {@code employment.legalEntityName}/{@code assignment.orgUnitName} are resolved to PalCheck names inside the adapter using a small code-defined map, not by this contract or the mapper that produces it.
 */
public record NormalizedWorker(String sourceSystem, String externalWorkerId, String mappingVersion, Person person,
        Employment employment, Assignment assignment, String managerExternalWorkerId, MappingReport mappingReport, java.util.Map<String, String> sourceFields) {

    public record Person(String firstName, String middleName, String lastName, String workEmail, String locale) {
    }

    /** {@code status}/{@code employmentType} are already PalCheck vocabulary strings; {@code legalEntityName} is resolved by the adapter. */
    public record Employment(String status, String employmentType, String legalEntityName, String country,
            LocalDate startDate, LocalDate endDate) {
    }

    /** {@code orgUnitName} is resolved by the adapter. */
    public record Assignment(String title, String orgUnitName) {
    }

    /** Fields the mapper could not translate (INTEGRATION_ARCHITECTURE.md section 3.3: "lossy is allowed; silent is not"). */
    public record MappingReport(List<String> unmappedFields) {

        public static MappingReport clean() {
            return new MappingReport(List.of());
        }
    }
}
