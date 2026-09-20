-- owner: audit
-- Append-oriented change history. Written in the same transaction as the change it describes (ADR-0018 decision 1):
-- if the audit row cannot be stored, the business change is not stored either.
--
-- `record_type` / `record_id` is the history key (the Person, for changes to a person, their employments and their
-- assignments). `change_set_id` groups the entries written by one command. `changes` holds
-- [{"field": ..., "before": ..., "after": ...}] as text; only fields the application declares auditable are written.

CREATE TABLE audit.audit_entry (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES access.tenant (id),
    occurred_at     timestamptz NOT NULL,
    actor_type      text        NOT NULL,
    actor_id        uuid,
    actor_display   text        NOT NULL,
    command         text        NOT NULL,
    change_set_id   uuid        NOT NULL,
    operation       text        NOT NULL,
    entity_type     text        NOT NULL,
    entity_id       uuid        NOT NULL,
    entity_version  bigint      NOT NULL,
    record_type     text        NOT NULL,
    record_id       uuid        NOT NULL,
    changes         jsonb       NOT NULL,
    reason          text,
    request_id      text,
    correlation_id  text,
    CONSTRAINT audit_actor_type_valid CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT audit_operation_valid CHECK (operation IN ('CREATE', 'UPDATE')),
    CONSTRAINT audit_changes_is_array CHECK (jsonb_typeof(changes) = 'array'),
    CONSTRAINT audit_reason_length CHECK (reason IS NULL OR char_length(reason) <= 500)
);

CREATE INDEX audit_by_record ON audit.audit_entry (tenant_id, record_type, record_id, occurred_at DESC, id);

-- Append-only: history is never rewritten or removed by the application. Retention and erasure are a separate,
-- researched design (docs/SECURITY.md section 7); until then nothing may alter an entry.
CREATE FUNCTION audit.reject_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit.audit_entry is append-only (% is not allowed)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_entry_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit.audit_entry
    FOR EACH ROW EXECUTE FUNCTION audit.reject_modification();

CREATE TRIGGER audit_entry_no_truncate
    BEFORE TRUNCATE ON audit.audit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_modification();
