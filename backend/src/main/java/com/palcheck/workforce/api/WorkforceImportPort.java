package com.palcheck.workforce.api;

import com.palcheck.access.api.Actor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The only door into workforce data for an external synchronization (ADR-0024, ADR-0025). Every write goes through
 * the same domain validation, optimistic concurrency and audit trail a human edit does; the differences are that the
 * audit actor is recorded as a system rather than a person, and Employment/Assignment are marked as owned by that
 * system so an ordinary human edit is refused afterward. Person identity is initialized on creation, remains locally
 * editable, and is preserved on subsequent imports.
 *
 * <p>{@code actor} is the human who triggered the synchronization (their tenant scopes the write and their
 * permission is checked, exactly like any other command); the audit entry itself never names them.
 */
public interface WorkforceImportPort {

    /**
     * Imports or updates one external worker (pass 1 of a sync: everything except the manager link, which depends on
     * other workers in the same run — see {@link #linkManager}).
     */
    ImportOutcome importWorker(Actor actor, ImportRequest request);

    /** Pass 2: links a previously imported worker's current assignment to its manager's employment. */
    ManagerLinkOutcome linkManager(Actor actor, String sourceSystem, String sourceDisplayName, String externalWorkerId,
            String managerExternalWorkerId);

    record ImportRequest(String sourceSystem, String sourceDisplayName, String externalWorkerId, String mappingVersion,
            String legalEntityName, PersonFields person, EmploymentFields employment,
            AssignmentFields assignment) {

        public record PersonFields(String firstName, String middleName, String lastName, String workEmail,
                String locale) {
        }

        /**
         * {@code status} and {@code employmentType} are PalCheck's own vocabulary spelled as plain strings (the
         * exact enum names, e.g. {@code "ACTIVE"}, {@code "FULL_TIME"}) so this port's request shape never exposes a
         * {@code workforce.domain} type across the module boundary (ADR-0024 decision 5). An unrecognized value is a
         * {@link ImportOutcome.Reason#MISSING_REQUIRED_FIELD} rejection, not a parse exception.
         */
        public record EmploymentFields(String status, String employmentType, String country, LocalDate startDate,
                LocalDate endDate) {
        }

        public record AssignmentFields(String title, String orgUnitName) {
        }
    }

    sealed interface ImportOutcome {

        record Imported(UUID personId, UUID employmentId, UUID assignmentId, Action action,
                List<String> changedFields) implements ImportOutcome {
        }

        record Rejected(Reason reason, String detail) implements ImportOutcome {
        }

        enum Action { CREATED, UPDATED, UNCHANGED }

        enum Reason { UNKNOWN_LEGAL_ENTITY, MISSING_REQUIRED_FIELD, DUPLICATE_EXTERNAL_RECORD }
    }

    sealed interface ManagerLinkOutcome {

        record Linked(UUID assignmentId) implements ManagerLinkOutcome {
        }

        record Unchanged(UUID assignmentId) implements ManagerLinkOutcome {
        }

        record Rejected(Reason reason, String detail) implements ManagerLinkOutcome {
        }

        enum Reason { WORKER_NOT_FOUND, MANAGER_NOT_FOUND, CYCLE }
    }
}
