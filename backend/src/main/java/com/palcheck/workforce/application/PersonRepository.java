package com.palcheck.workforce.application;

import com.palcheck.shared.TenantId;
import com.palcheck.workforce.domain.Person;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Persistence port for {@link Person}. Every method names the tenant; none can read across tenants. */
public interface PersonRepository {

    /** @throws WorkEmailAlreadyInUseException if another person in the tenant has this work email */
    void insert(Person person);

    Optional<Person> find(TenantId tenantId, UUID personId);

    OptionalLong currentVersion(TenantId tenantId, UUID personId);

    boolean workEmailInUse(TenantId tenantId, String workEmail, UUID excludingPersonId);

    /**
     * Stores {@code next} only if the stored version still equals {@code expectedVersion}, incrementing it.
     *
     * @return false when the row is absent in this tenant or its version has moved on
     * @throws WorkEmailAlreadyInUseException if another person in the tenant has this work email
     */
    boolean update(Person next, long expectedVersion);
}
