package com.palcheck.integrations.application.provider;

import com.palcheck.integrations.domain.NormalizedWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Translates one provider-shaped {@link WorkdayWorkerRecord} into PalCheck's own {@link NormalizedWorker}
 * (INTEGRATION_ARCHITECTURE.md section 3.3). Code-list values are mapped, not copied; anything this fixture's small
 * map does not recognize is passed through as-is and reported in {@code mappingReport}, so a later import failure
 * ("unrecognized employment status") is traceable to a specific unmapped source value rather than a silent guess.
 */
@Component
class WorkdayMapper {

    static final String MAPPING_VERSION = "workday-mock-v1";

    NormalizedWorker map(WorkdayWorkerRecord source) {
        List<String> unmapped = new ArrayList<>();
        String status = mapCode(source.employmentStatusCode(), "employmentStatusCode", unmapped, code -> switch (code) {
            case "Active_Employee" -> "ACTIVE";
            default -> null;
        });
        String employmentType = mapCode(source.workerTypeCode(), "workerTypeCode", unmapped, code -> switch (code) {
            case "Regular" -> "FULL_TIME";
            case "Part_Time" -> "PART_TIME";
            default -> null;
        });
        String country = switch (source.companyCode()) {
            case MockWorkdayProvider.COMPANY_CODE_COSTA_RICA -> "CR";
            case MockWorkdayProvider.COMPANY_CODE_PANAMA -> "PA";
            default -> "CR"; // A company code with no known country still needs a structurally valid one; the
                              // company code itself is resolved to a legal entity separately and fails there.
        };
        String locale = switch (source.companyCode()) {
            case MockWorkdayProvider.COMPANY_CODE_COSTA_RICA -> "es-CR";
            case MockWorkdayProvider.COMPANY_CODE_PANAMA -> "es";
            default -> "en";
        };

        var person = new NormalizedWorker.Person(source.firstName(), null, source.lastName(), source.workEmail(),
                locale);
        var employment = new NormalizedWorker.Employment(status, employmentType, java.util.Map.of("CR-01", "Northwind Costa Rica S.A.", "PA-01", "Northwind Panama S.A.").getOrDefault(source.companyCode(), source.companyCode()), country,
                source.hireDate(), null);
        var assignment = new NormalizedWorker.Assignment(source.businessTitle(), java.util.Map.of("ENG", "Engineering", "SUP", "Support").getOrDefault(source.departmentCode(), source.departmentCode()));
        return new NormalizedWorker("workday-simulation", source.workerId(), MAPPING_VERSION, person, employment,
                assignment, source.managerWorkerId(), new NormalizedWorker.MappingReport(List.copyOf(unmapped)), java.util.Map.of("firstName", "First_Name", "lastName", "Last_Name", "workEmail", "Email_Address", "status", "Employment_Status", "employmentType", "Worker_Type", "title", "Business_Title"));
    }

    /** Applies {@code mapping}; a null source or an unrecognized code is reported and passed through as-is. */
    private static String mapCode(String sourceCode, String fieldName, List<String> unmapped,
            Function<String, String> mapping) {
        if (sourceCode == null) {
            unmapped.add(fieldName);
            return null;
        }
        String mapped = mapping.apply(sourceCode);
        if (mapped == null) {
            unmapped.add(fieldName + ":" + sourceCode);
            return sourceCode;
        }
        return mapped;
    }
}
