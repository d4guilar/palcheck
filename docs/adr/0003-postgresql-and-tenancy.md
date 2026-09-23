# ADR-0003: PostgreSQL and Tenant Isolation

**Status:** Accepted in part (Daniel Aguilar, Q1, 2026-09-21): Phase 1 RLS deferral is accepted; other proposals retain their prior status.
**Date:** 2026-09-19

> **Implementation note (2026-09-19):** Phase 1 enforces tenant isolation in the application (every repository method takes a `TenantId`, checked by ArchUnit), with tenant-qualified composite foreign keys and negative tests on every endpoint. **Row-level security is deferred, not spiked; Daniel accepted this Phase 1 choice through Q1 on 2026-09-21** ([ADR-0022](0022-phase1-technical-baseline.md) decision 9). Original reasoning below is retained.

## Context

Employment data is highly relational (Person, Employment, Assignment, Org Unit, Legal Entity), needs transactional consistency (state, audit, and outbox commit together), and benefits from constraints on effective-dated ranges.
The brief chose PostgreSQL for the stack but did not decide **multi-tenancy**, which cannot be retrofitted cheaply and which any real customer would require (a tenant is a customer of PalCheck; see [DOMAIN_MODEL.md](../DOMAIN_MODEL.md) section 3).

## Decision

1. **PostgreSQL** is the single system of record: one database, **one schema per module** ([ADR-0001](0001-modular-monolith.md)), Flyway migrations owned by each module.
2. **Pooled multi-tenancy**: all tenants share tables; **every tenant-owned table carries `tenant_id`**, and every repository method requires a tenant. Tenant comes from the authenticated principal at the edge, never from client input.
3. **Row-level security (RLS)** as a *backstop*, not the only control: **deferred for Phase 1, accepted through Q1 on 2026-09-21** ([ADR-0022](0022-phase1-technical-baseline.md), decision 9). This amends the original Phase 0 proposal to spike it in Phase 1. Before real customer data, revisit runtime roles, tenant binding, pool reuse, migrations and query plans; application isolation remains required.
4. **Automated cross-tenant negative tests** for every endpoint are mandatory from the first endpoint.
5. **Global reference data** (Legal Concept Registry projection) has no `tenant_id` and is read-only at runtime.
6. Effective-dated history uses a pattern chosen by spike (open question OQ-03 in [DOMAIN_MODEL.md](../DOMAIN_MODEL.md)); PostgreSQL range types with exclusion constraints are a leading candidate for preventing overlapping ranges. *Unverified until spiked.*
7. Money uses decimal types with an explicit currency; instants in UTC, business dates as local dates ([ARCHITECTURE.md](../ARCHITECTURE.md) section 4).
8. **No production schema is created in Phase 0.**

Why PostgreSQL fits: employment data is relational and needs constraints and transactions; PostgreSQL is mature, widely operated, well supported by Testcontainers, has strong features for this domain (range types, row-level security, JSONB for `extensions`, `SKIP LOCKED` for the outbox dispatcher),
and offers a realistic path to production hosting.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **MySQL/MariaDB** | Capable, but fewer built-in features for this domain (range constraints, RLS, `SKIP LOCKED` maturity varies) |
| **A document database** | Poor fit for relational, constraint-heavy, transactional HR data; audit and effective-dating harder to keep consistent |
| **Event sourcing as the primary store** | Attractive for audit and history, but a large complexity and learning cost; audit + effective dating + outbox cover the need without it. Revisit only for a specific aggregate |
| **Schema-per-tenant** | Strong isolation and easy per-tenant erasure, but migrations across N schemas and connection management become an operational burden; good fit if a few large tenants appear |
| **Database-per-tenant** | Strongest isolation and residency story; highest cost; a possible premium tier, not a starting point |
| **Application-only tenant filtering** (no RLS) | Simple, but one missed filter is a data breach; hence tests always and RLS as backstop if affordable |

## Consequences

**Positive**

- One transactional store makes the outbox, audit, and workforce writes atomic.
- Tenant isolation is designed in from the first migration.
- Familiar, testable with real PostgreSQL in CI.

**Negative**

- Pooled tenancy means a query bug can leak across tenants; mitigated by required tenant parameters, tests, and possibly RLS.
- RLS adds operational and performance complexity and can surprise developers.
- No cross-schema joins constrains reporting; needs explicit read models.
- Noisy-neighbor effects between tenants; per-tenant limits will eventually be needed.
- Per-tenant erasure and data residency are harder than with schema- or database-per-tenant.

## Reconsider When

- A customer requires data residency or stronger isolation than pooled tenancy offers (move that tenant to its own schema or database).
- RLS spike shows unacceptable cost, or a bypass risk that cannot be controlled.
- The number of tenants or the size of a single tenant makes shared tables a scaling or operational issue.
- The effective-dating spike shows a temporal-table or event-sourced model serves better.

## Interview line

"Relational, transactional, constraint-heavy data, so PostgreSQL; pooled tenancy with a required tenant on every query, tests that try to cross tenants on every endpoint, and an explicitly accepted Phase 1 RLS deferral with a review trigger before real customer data."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0007](0007-transactional-outbox.md) · [ADR-0014](0014-authorization.md) · [SECURITY.md](../SECURITY.md)
