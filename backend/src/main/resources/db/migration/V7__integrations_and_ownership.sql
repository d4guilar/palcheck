-- owner: platform (schema) / workforce (ownership columns) / integrations (its own tables)
-- Phase 2: external HRIS translation foundation (docs/PHASE_2_IMPLEMENTATION_PLAN.md, ADR-0024, ADR-0025).
CREATE SCHEMA IF NOT EXISTS integrations;

-- Ownership is recorded on the Employment and the Assignment, not per field and not on the Person (ADR-0025
-- decision 1): a synced record names the system that owns it and, for Employment, the external worker identity that
-- makes a re-sync idempotent instead of creating a duplicate. NULL means PalCheck-owned, the default for every
-- existing and future locally created record.
ALTER TABLE workforce.employment
    ADD COLUMN owned_by_system   text        NULL,
    ADD COLUMN external_worker_id text       NULL,
    ADD COLUMN last_synced_at    timestamptz NULL,
    ADD CONSTRAINT employment_ownership_pair CHECK (
        (owned_by_system IS NULL AND external_worker_id IS NULL)
        OR (owned_by_system IS NOT NULL AND external_worker_id IS NOT NULL)
    );

-- One external worker maps to exactly one Employment per tenant per source system (ADR-0025 decision 4): this
-- partial unique index is the "have we seen this worker before" lookup a re-sync uses, so nothing else is needed.
CREATE UNIQUE INDEX employment_external_identity ON workforce.employment (tenant_id, owned_by_system, external_worker_id)
    WHERE owned_by_system IS NOT NULL;

ALTER TABLE workforce.assignment
    ADD COLUMN owned_by_system text        NULL,
    ADD COLUMN last_synced_at  timestamptz NULL;

-- The one external system Phase 2 configures: a labelled simulation, never a real provider (ADR-0024 decision 7).
CREATE TABLE integrations.external_system (
    id                uuid        PRIMARY KEY,
    tenant_id         uuid        NOT NULL REFERENCES access.tenant (id),
    code              text        NOT NULL,
    display_name      text        NOT NULL,
    type              text        NOT NULL,
    mode              text        NOT NULL,
    last_sync_at      timestamptz NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT external_system_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT external_system_tenant_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT external_system_mode_valid CHECK (mode IN ('SIMULATION')),
    CONSTRAINT external_system_type_valid CHECK (type IN ('HRIS'))
);

-- One synchronization attempt. `status` summarizes what the run's items ended up being (ADR-0025 decision 6: one
-- transaction per worker, never one for the whole run, so this row is written only after every item is settled).
CREATE TABLE integrations.sync_run (
    id             uuid        PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES access.tenant (id),
    system_id      uuid        NOT NULL,
    started_at     timestamptz NOT NULL,
    completed_at   timestamptz NULL,
    status         text        NOT NULL,
    records_seen   integer     NOT NULL DEFAULT 0,
    created_count  integer     NOT NULL DEFAULT 0,
    updated_count  integer     NOT NULL DEFAULT 0,
    unchanged_count integer    NOT NULL DEFAULT 0,
    failed_count   integer     NOT NULL DEFAULT 0,
    triggered_by   uuid        NULL,
    CONSTRAINT sync_run_system_fk FOREIGN KEY (tenant_id, system_id) REFERENCES integrations.external_system (tenant_id, id),
    CONSTRAINT sync_run_status_valid CHECK (status IN ('RUNNING', 'SUCCEEDED', 'SUCCEEDED_WITH_FAILURES', 'FAILED'))
);
CREATE INDEX sync_run_by_system ON integrations.sync_run (tenant_id, system_id, started_at DESC);

-- One worker's outcome within a run. `failure_reason` is the closed set of controlled failure classes the mock
-- provider and the import path can produce (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 4.5); no provider payload
-- is stored here, only enough to explain the outcome.
CREATE TABLE integrations.sync_item (
    id                    uuid        PRIMARY KEY,
    sync_run_id           uuid        NOT NULL REFERENCES integrations.sync_run (id),
    tenant_id             uuid        NOT NULL,
    external_worker_id    text        NOT NULL,
    status                text        NOT NULL,
    action                text        NULL,
    failure_reason        text        NULL,
    failure_detail        text        NULL,
    target_person_id      uuid        NULL,
    target_employment_id  uuid        NULL,
    occurred_at           timestamptz NOT NULL,
    CONSTRAINT sync_item_status_valid CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT sync_item_action_valid CHECK (action IS NULL OR action IN ('CREATED', 'UPDATED', 'UNCHANGED', 'MANAGER_LINKED')),
    CONSTRAINT sync_item_failure_reason_valid CHECK (failure_reason IS NULL OR failure_reason IN
        ('UNKNOWN_LEGAL_ENTITY', 'MISSING_REQUIRED_FIELD', 'INVALID_MANAGER_REFERENCE', 'DUPLICATE_EXTERNAL_RECORD',
         'TEMPORARY_PROVIDER_FAILURE')),
    CONSTRAINT sync_item_outcome_shape CHECK (
        (status = 'SUCCEEDED' AND action IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND action IS NULL AND failure_reason IS NOT NULL)
    )
);
CREATE INDEX sync_item_by_run ON integrations.sync_item (sync_run_id, occurred_at, id);
CREATE INDEX sync_item_by_tenant_worker ON integrations.sync_item (tenant_id, external_worker_id);

-- The *current* source of one field on one workforce record (ADR-0025 decision 3): metadata for the "where did this
-- value come from" question, never consulted to decide whether a write is allowed. One row per field; a later
-- import replaces it in place, exactly like provenance in docs/DATA_OWNERSHIP_AND_LINEAGE.md section 3.
CREATE TABLE integrations.field_lineage (
    tenant_id         uuid        NOT NULL,
    entity_type       text        NOT NULL,
    entity_id         uuid        NOT NULL,
    field_path        text        NOT NULL,
    source_system     text        NOT NULL,
    source_field      text        NOT NULL,
    mapping_version   text        NOT NULL,
    sync_run_id       uuid        NOT NULL REFERENCES integrations.sync_run (id),
    sync_item_id      uuid        NOT NULL REFERENCES integrations.sync_item (id),
    imported_at       timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, entity_type, entity_id, field_path),
    CONSTRAINT field_lineage_entity_type_valid CHECK (entity_type IN ('PERSON', 'EMPLOYMENT', 'ASSIGNMENT'))
);
