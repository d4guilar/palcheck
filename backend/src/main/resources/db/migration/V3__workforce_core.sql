-- owner: workforce
-- Core workforce model: Legal Entity, Org Unit, Person, Employment, Assignment (docs/PHASE_1_IMPLEMENTATION_PLAN.md section 4).
--
-- Tenant isolation is enforced structurally as well as in application code: every child references its parent with a
-- tenant-qualified composite foreign key, so an association across tenants cannot be stored. Nothing cascades on delete
-- (history is never destroyed as a side effect). Mutable aggregates carry a `version` for optimistic concurrency.
-- The only cross-schema foreign key is to the tenant anchor (ADR-0018 decision 5, provisional).

CREATE TABLE workforce.legal_entity (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES access.tenant (id),
    name        text        NOT NULL,
    country     char(2)     NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT legal_entity_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT legal_entity_name_unique UNIQUE (tenant_id, name),
    CONSTRAINT legal_entity_name_length CHECK (char_length(name) BETWEEN 1 AND 200),
    CONSTRAINT legal_entity_country_format CHECK (country ~ '^[A-Z]{2}$')
);

CREATE TABLE workforce.org_unit (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES access.tenant (id),
    parent_id   uuid,
    name        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT org_unit_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT org_unit_name_unique UNIQUE (tenant_id, name),
    CONSTRAINT org_unit_name_length CHECK (char_length(name) BETWEEN 1 AND 200),
    CONSTRAINT org_unit_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    -- MATCH SIMPLE: a root unit (NULL parent) is not checked; a child must have a parent in the same tenant.
    CONSTRAINT org_unit_parent_fk FOREIGN KEY (tenant_id, parent_id) REFERENCES workforce.org_unit (tenant_id, id)
);

-- The human being. No job data, and deliberately no sensitive fields (no national ID, birth date, bank or health data).
CREATE TABLE workforce.person (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES access.tenant (id),
    first_name      text        NOT NULL,
    middle_name     text,
    last_name       text        NOT NULL,
    preferred_name  text,
    work_email      text        NOT NULL,
    locale          text        NOT NULL,
    version         bigint      NOT NULL DEFAULT 1,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT person_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT person_first_name_length CHECK (char_length(first_name) BETWEEN 1 AND 100),
    CONSTRAINT person_middle_name_length CHECK (middle_name IS NULL OR char_length(middle_name) BETWEEN 1 AND 100),
    CONSTRAINT person_last_name_length CHECK (char_length(last_name) BETWEEN 1 AND 150),
    CONSTRAINT person_preferred_name_length CHECK (preferred_name IS NULL OR char_length(preferred_name) BETWEEN 1 AND 100),
    CONSTRAINT person_work_email_length CHECK (char_length(work_email) BETWEEN 3 AND 254),
    CONSTRAINT person_work_email_lowercase CHECK (work_email = lower(work_email)),
    CONSTRAINT person_locale_length CHECK (char_length(locale) BETWEEN 2 AND 12),
    CONSTRAINT person_version_positive CHECK (version >= 1)
);
CREATE UNIQUE INDEX person_work_email_unique ON workforce.person (tenant_id, work_email);
CREATE INDEX person_directory_order ON workforce.person (tenant_id, lower(last_name), lower(first_name), id);

-- The relationship between a Person and a Legal Entity over time. A rehire is a new Employment for the same Person.
-- `status` and `employment_type` are software placeholders, not legal classifications (research item WC-1).
CREATE TABLE workforce.employment (
    id               uuid        PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES access.tenant (id),
    person_id        uuid        NOT NULL,
    legal_entity_id  uuid        NOT NULL,
    status           text        NOT NULL,
    employment_type  text        NOT NULL,
    country          char(2)     NOT NULL,
    start_date       date        NOT NULL,
    end_date         date,
    version          bigint      NOT NULL DEFAULT 1,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT employment_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT employment_person_fk FOREIGN KEY (tenant_id, person_id) REFERENCES workforce.person (tenant_id, id),
    CONSTRAINT employment_legal_entity_fk FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES workforce.legal_entity (tenant_id, id),
    CONSTRAINT employment_status_valid CHECK (status IN ('PENDING', 'ACTIVE', 'ENDED')),
    CONSTRAINT employment_type_valid CHECK (employment_type IN ('FULL_TIME', 'PART_TIME')),
    CONSTRAINT employment_country_format CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT employment_dates_ordered CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT employment_ended_has_end_date CHECK (status <> 'ENDED' OR end_date IS NOT NULL),
    CONSTRAINT employment_version_positive CHECK (version >= 1)
);
CREATE INDEX employment_by_person ON workforce.employment (tenant_id, person_id);
CREATE INDEX employment_by_legal_entity ON workforce.employment (tenant_id, legal_entity_id);

-- The organizational and job placement of an Employment. `effective_from`/`effective_to` let a future job change close
-- one row and open the next; Phase 1 edits the current row in place as a correction (audited, versioned).
CREATE TABLE workforce.assignment (
    id                     uuid        PRIMARY KEY,
    tenant_id              uuid        NOT NULL REFERENCES access.tenant (id),
    employment_id          uuid        NOT NULL,
    org_unit_id            uuid        NOT NULL,
    title                  text        NOT NULL,
    manager_employment_id  uuid,
    effective_from         date        NOT NULL,
    effective_to           date,
    version                bigint      NOT NULL DEFAULT 1,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT assignment_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT assignment_employment_fk FOREIGN KEY (tenant_id, employment_id) REFERENCES workforce.employment (tenant_id, id),
    CONSTRAINT assignment_org_unit_fk FOREIGN KEY (tenant_id, org_unit_id) REFERENCES workforce.org_unit (tenant_id, id),
    CONSTRAINT assignment_manager_fk FOREIGN KEY (tenant_id, manager_employment_id) REFERENCES workforce.employment (tenant_id, id),
    CONSTRAINT assignment_title_length CHECK (char_length(title) BETWEEN 1 AND 150),
    CONSTRAINT assignment_not_own_manager CHECK (manager_employment_id IS NULL OR manager_employment_id <> employment_id),
    CONSTRAINT assignment_dates_ordered CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT assignment_version_positive CHECK (version >= 1)
);
-- At most one current assignment (open-ended) per employment.
CREATE UNIQUE INDEX assignment_one_current_per_employment ON workforce.assignment (tenant_id, employment_id) WHERE effective_to IS NULL;
CREATE INDEX assignment_by_employment ON workforce.assignment (tenant_id, employment_id);
CREATE INDEX assignment_by_org_unit ON workforce.assignment (tenant_id, org_unit_id);
CREATE INDEX assignment_by_manager ON workforce.assignment (tenant_id, manager_employment_id);
