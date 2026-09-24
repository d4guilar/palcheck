-- owner: messaging (schema, outbox_event) / integrations (integration_operation)
-- Phase 3: durable events and asynchronous integration (docs/PHASE_3_IMPLEMENTATION_PLAN.md, ADR-0026, ADR-0027).
CREATE SCHEMA IF NOT EXISTS messaging;

-- The durable, transactional record of one integration-relevant fact (ADR-0026). Written in the same transaction
-- as the business change it describes; the publisher (a separate process) reads and updates it afterward.
-- `event_id` is transport identity (ADR-0027): stable across every publish attempt for this row, never reused
-- across a different business fact.
CREATE TABLE messaging.outbox_event (
    id               uuid        PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES access.tenant (id),
    event_id         uuid        NOT NULL UNIQUE,
    event_type       text        NOT NULL,
    schema_version   text        NOT NULL,
    aggregate_type   text        NOT NULL,
    aggregate_id     text        NOT NULL,
    occurred_at      timestamptz NOT NULL,
    correlation_id   text        NULL,
    causation_id     text        NULL,
    payload          jsonb       NOT NULL,
    status           text        NOT NULL,
    attempt_count    integer     NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz NOT NULL,
    last_error       text        NULL,
    published_at     timestamptz NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT outbox_event_status_valid CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);
-- The publisher's own claim query (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 4.1, `FOR UPDATE SKIP LOCKED`).
CREATE INDEX outbox_event_eligible ON messaging.outbox_event (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX outbox_event_by_tenant_aggregate ON messaging.outbox_event (tenant_id, aggregate_type, aggregate_id);

-- One row per business operation identity (ADR-0027), never one row per event or per attempt: a redelivered or
-- republished event, and an authorized replay, all converge here. `payload` is the same small identifier set the
-- originating event carried, kept so replay never needs to go back to the outbox or the broker.
CREATE TABLE integrations.integration_operation (
    tenant_id         uuid        NOT NULL REFERENCES access.tenant (id),
    consumer          text        NOT NULL,
    operation_key     text        NOT NULL,
    status            text        NOT NULL,
    attempt_count     integer     NOT NULL DEFAULT 0,
    last_error        text        NULL,
    next_attempt_at   timestamptz NULL,
    completed_at      timestamptz NULL,
    source_event_id   uuid        NULL,
    payload           jsonb       NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, consumer, operation_key),
    CONSTRAINT integration_operation_status_valid CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED_TRANSIENT', 'FAILED_PERMANENT', 'DEAD_LETTERED')
    )
);
-- The retry job's own claim query (one per consumer; docs/PHASE_3_IMPLEMENTATION_PLAN.md section 4.2).
CREATE INDEX integration_operation_due_retry ON integrations.integration_operation (consumer, next_attempt_at)
    WHERE status = 'FAILED_TRANSIENT';
