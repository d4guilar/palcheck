-- owner: platform
-- Command receipts for request idempotency. A row is inserted inside the business transaction, so a rolled-back command
-- leaves no receipt, and concurrent requests with the same key serialize on the primary key and converge.
-- The table holds a reference to the result (type and id), never a copy of personal data.

CREATE TABLE platform.idempotency_record (
    tenant_id        uuid        NOT NULL REFERENCES access.tenant (id),
    principal_id     uuid        NOT NULL,
    operation        text        NOT NULL,
    idempotency_key  text        NOT NULL,
    request_hash     text        NOT NULL,
    result_type      text,
    result_id        uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, principal_id, operation, idempotency_key),
    CONSTRAINT idempotency_key_length CHECK (char_length(idempotency_key) BETWEEN 1 AND 128)
);

CREATE INDEX idempotency_by_expiry ON platform.idempotency_record (expires_at);
