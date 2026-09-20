package com.palcheck.workforce.web;

import com.palcheck.audit.api.AuditActor;
import com.palcheck.audit.api.AuditChange;
import com.palcheck.audit.api.AuditEntryView;
import com.palcheck.audit.api.AuditHistory;
import com.palcheck.workforce.application.DirectoryItem;
import com.palcheck.workforce.application.EmploymentView;
import com.palcheck.workforce.application.PageResult;
import com.palcheck.workforce.domain.LegalEntity;
import com.palcheck.workforce.domain.OrgUnit;
import com.palcheck.workforce.domain.Person;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes of the workforce API. Explicit DTOs: no domain object or persistence type is serialized directly. */
final class Dtos {

    private Dtos() {
    }

    record ManagerCandidateDto(UUID employmentId, UUID personId, String displayName, String title, String legalEntityName, String status) { }

    record PersonDto(UUID id, String firstName, String middleName, String lastName, String preferredName,
            String displayName, String workEmail, String locale, long version, Instant createdAt, Instant updatedAt) {
    }

    record DirectoryItemDto(UUID personId, String displayName, String workEmail, String title, String orgUnitName,
            String legalEntityName, String employmentStatus, UUID employmentId) {
    }

    record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    record ItemsDto<T>(List<T> items) {
    }

    record LegalEntityDto(UUID id, String name, String country) {
    }

    record OrgUnitDto(UUID id, UUID parentId, String name, String path) {
    }

    record OrgUnitRefDto(UUID id, String name) {
    }

    record ManagerDto(UUID employmentId, UUID personId, String displayName) {
    }

    record AssignmentDto(UUID id, String title, OrgUnitRefDto orgUnit, ManagerDto manager, LocalDate effectiveFrom,
            long version, Instant updatedAt) {
    }

    record EmploymentDto(UUID id, UUID personId, LegalEntityDto legalEntity, String status, String employmentType,
            String country, LocalDate startDate, LocalDate endDate, long version, Instant createdAt,
            Instant updatedAt, AssignmentDto currentAssignment) {
    }

    record ActorDto(String type, UUID id, String displayName) {
    }

    record ChangeDto(String field, String before, String after) {
    }

    record AuditEntryDto(UUID id, UUID changeSetId, Instant occurredAt, ActorDto actor, String command,
            String operation, String entityType, UUID entityId, long entityVersion, List<ChangeDto> changes,
            String reason, String requestId) {
    }

    static PersonDto person(Person p) {
        var d = p.data();
        return new PersonDto(p.id(), d.firstName(), d.middleName(), d.lastName(), d.preferredName(), d.displayName(),
                d.workEmail(), d.locale(), p.version(), p.createdAt(), p.updatedAt());
    }

    static DirectoryItemDto item(DirectoryItem i) {
        return new DirectoryItemDto(i.personId(), i.displayName(), i.workEmail(), i.title(), i.orgUnitName(),
                i.legalEntityName(), i.employmentStatus() == null ? null : i.employmentStatus().name(),
                i.employmentId());
    }

    static <S, T> PageDto<T> page(PageResult<S> page, java.util.function.Function<S, T> mapper) {
        return new PageDto<>(page.items().stream().map(mapper).toList(), page.page(), page.size(),
                page.totalItems(), page.totalPages());
    }

    static LegalEntityDto legalEntity(LegalEntity e) {
        return new LegalEntityDto(e.id(), e.name(), e.country());
    }

    static EmploymentDto employment(EmploymentView v) {
        var e = v.employment();
        AssignmentDto assignment = null;
        if (v.currentAssignment() != null) {
            var a = v.currentAssignment();
            assignment = new AssignmentDto(a.assignment().id(), a.assignment().data().title(),
                    new OrgUnitRefDto(a.orgUnit().id(), a.orgUnit().name()),
                    a.manager() == null ? null
                            : new ManagerDto(a.manager().employmentId(), a.manager().personId(),
                                    a.manager().displayName()),
                    a.assignment().effectiveFrom(), a.assignment().version(), a.assignment().updatedAt());
        }
        return new EmploymentDto(e.id(), e.personId(), legalEntity(v.legalEntity()), e.data().status().name(),
                e.data().employmentType().name(), e.data().country(), e.data().startDate(), e.data().endDate(),
                e.version(), e.createdAt(), e.updatedAt(), assignment);
    }

    static AuditEntryDto audit(AuditEntryView e) {
        AuditActor actor = e.actor();
        return new AuditEntryDto(e.id(), e.changeSetId(), e.occurredAt(),
                new ActorDto(actor.type().name(), actor.id(), actor.displayName()), e.command(),
                e.operation().name(), e.entityType(), e.entityId(), e.entityVersion(),
                e.changes().stream().map(Dtos::change).toList(), e.reason(), e.requestId());
    }

    private static ChangeDto change(AuditChange c) {
        return new ChangeDto(c.field(), c.before(), c.after());
    }

    static PageDto<AuditEntryDto> auditPage(AuditHistory.Page page) {
        int totalPages = page.size() == 0 ? 0 : (int) ((page.totalItems() + page.size() - 1) / page.size());
        return new PageDto<>(page.items().stream().map(Dtos::audit).toList(), page.page(), page.size(),
                page.totalItems(), totalPages);
    }

    /** Builds a display path such as "Engineering / Platform" for each unit from the flat list. */
    static List<OrgUnitDto> orgUnits(List<OrgUnit> units) {
        var byId = new java.util.HashMap<UUID, OrgUnit>();
        units.forEach(u -> byId.put(u.id(), u));
        return units.stream().map(u -> new OrgUnitDto(u.id(), u.parentId(), u.name(), path(u, byId))).toList();
    }

    private static String path(OrgUnit unit, java.util.Map<UUID, OrgUnit> byId) {
        var names = new java.util.ArrayDeque<String>();
        OrgUnit cursor = unit;
        int guard = 0;
        while (cursor != null && guard++ < 50) {
            names.addFirst(cursor.name());
            cursor = cursor.parentId() == null ? null : byId.get(cursor.parentId());
        }
        return String.join(" / ", names);
    }
}
