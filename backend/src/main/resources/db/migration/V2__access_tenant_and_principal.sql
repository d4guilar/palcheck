-- owner: access
-- Tenant is the isolation boundary and the only cross-schema foreign-key anchor (ADR-0018 decision 5, provisional).
CREATE TABLE access.tenant (
    id          uuid        PRIMARY KEY,
    slug        text        NOT NULL,
    name        text        NOT NULL,
    status      text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tenant_slug_unique UNIQUE (slug),
    CONSTRAINT tenant_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    CONSTRAINT tenant_name_length CHECK (char_length(name) BETWEEN 1 AND 200),
    CONSTRAINT tenant_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- A principal is a person who signs in. (issuer, subject) is the trusted identity from the identity provider;
-- it maps to exactly one tenant in Phase 1. Roles are held here; permissions are derived in code.
CREATE TABLE access.principal (
    id            uuid        PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES access.tenant (id),
    issuer        text        NOT NULL,
    subject       text        NOT NULL,
    display_name  text        NOT NULL,
    status        text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT principal_identity_unique UNIQUE (issuer, subject),
    CONSTRAINT principal_tenant_unique UNIQUE (tenant_id, id),
    CONSTRAINT principal_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 200),
    CONSTRAINT principal_status_valid CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE access.principal_role (
    principal_id  uuid NOT NULL REFERENCES access.principal (id),
    role          text NOT NULL,
    PRIMARY KEY (principal_id, role),
    CONSTRAINT principal_role_valid CHECK (role IN ('HR_ADMIN', 'HR_VIEWER'))
);
