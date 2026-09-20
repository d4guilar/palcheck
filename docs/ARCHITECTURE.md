# Architecture

> **Reconciliation notice (2026-09-19, Daniel-approved refinements 1, 2 and 6):** the transaction and dependency rules below are amended by accepted parts of [ADR-0018](adr/0018-module-transactions-and-persistence.md) and [ADR-0020](adr/0020-foundation-first-delivery-plan.md). One application command owns one local transaction and audit (and, later, durable publication) participates in it; durable publication is technical `messaging` infrastructure owned by no domain module and never by `integrations`; Phase 1 is the authenticated workforce foundation. Amended statements are marked *(amended)*. The final dependency direction is in [MODULES.md](MODULES.md) section 2a and [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 3. Baseline text is retained for its reasoning; section 11 (repository structure) was updated when Phase 1 completed.

Status: baseline decisions **Proposed** unless an ADR says otherwise (see [adr/README.md](adr/README.md)) · Phase 1 (authenticated workforce foundation) is implemented; section 11 shows the repository as it is.

Companion documents: [MODULES.md](MODULES.md) · [DOMAIN_MODEL.md](DOMAIN_MODEL.md) · [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) ·
[DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) · [SECURITY.md](SECURITY.md) · [diagrams](diagrams/).

> **Phase 1 review follow-up:** [PHASE_1_CODEX_REVIEW.md](PHASE_1_CODEX_REVIEW.md) records completed correctness/security fixes and UI polish. The package/transaction architecture is unchanged; search now uses bundled PostgreSQL unaccent, manager lookup is paginated, and manager graph changes serialize per tenant ([ADR-0023](adr/0023-manager-graph-concurrency.md)). Daniel's Q1-Q3 acceptance remains open.

## 1. Architectural drivers

Ranked. When two conflict, the higher one wins.

1. **Representational honesty.** Never distort a local concept and never invent a rule. This is the product's core promise.
2. **Data integrity and auditability.** HR data is transactional, relational, and accountable.
3. **Tenant isolation and data protection.** Employee data is sensitive by default.
4. **Visible resilience.** Downstream failure must be survivable, recoverable, and observable.
5. **Evolvability.** Module boundaries strong enough to extract a service later; a second country and a second HRIS provider
   addable without rewriting the core.
6. **Operability by one person.** Small blast radius, few moving parts, easy local run.
7. **Explainability.** Every significant choice can be defended in an interview, including why something was *not* used.

## 2. Architectural style: modular monolith

One deployable Spring Boot application, internally divided into modules with explicit public APIs. Reasons and alternatives:
[ADR-0001](adr/0001-modular-monolith.md).

### What a module is

A top-level Java package with:

- a **public API** (interfaces, commands, queries, DTOs, published events) that other modules may use;
- an **internal implementation** (entities, repositories, services) that nothing else may touch;
- its own **database schema** (see section 5), never accessed by another module.

### Rules (enforced by tests once code exists)

1. **Allowed dependencies are declared** in [MODULES.md](MODULES.md) and form an acyclic graph.
2. **Modules interact through public APIs and events only.** No reaching into another module's repositories or entities.
3. **No cross-module foreign keys and no cross-module joins.** Modules reference each other by ID. This is what keeps extraction possible.
4. *(amended 2026-09-19, [ADR-0018](adr/0018-module-transactions-and-persistence.md): one application command owns one local transaction; audit recording and, later, durable publication participate in it.)* ~~**One command, one transaction, one module.**~~ A command mutates one module's data in one database transaction. Cross-module effects
   happen through events, not through a wider transaction.
5. **Shared kernel stays tiny:** identifiers, `Money`, `Country`/`Jurisdiction`, `EffectiveRange`, `CorrelationContext`,
   `TenantId`. Anything else that wants to be "shared" is a design smell: find its owner.
6. **Internal domain events are not contracts.** Only *published integration events* are versioned, public, and stable
   ([ADR-0006](adr/0006-event-driven-integration.md)).
7. **Provider types never cross a module boundary.** External HRIS DTOs stay inside the adapter package of `integrations`
   ([ADR-0004](adr/0004-hris-adapter-boundary.md)).

**Enforcement options (decision for Phase 1, Codex to review):** ArchUnit tests, or Spring Modulith's module verification. Spring
Modulith also offers an event publication registry that resembles an outbox; whether it fits PalCheck's requirements or hides too
much is *Unverified* and should be evaluated against [ADR-0007](adr/0007-transactional-outbox.md) before adopting.

## 3. Containers

Diagram: [diagrams/02-container.md](diagrams/02-container.md).

| Container | Phase | Responsibility |
|---|---|---|
| **Web app** (React + TypeScript SPA) | 1 | UI, accessibility, design system, locale formatting. Talks only to the API. |
| **PalCheck API** (Spring Boot modular monolith) | 1 | All modules; REST/JSON API; background dispatchers and scheduled jobs. |
| **PostgreSQL** | 1 | System of record for all modules; one database, one schema per module. |
| **Identity provider (OIDC)** | 1 | Authentication. The API is an OIDC resource server; the IdP product is deferred ([ADR-0013](adr/0013-authentication.md)). |
| **Simulated downstream systems** (Payroll, Identity, Equipment) | 1 | Implement the connector port with controllable latency and faults. In-process first; a separate mock-server container only when a real HTTP boundary is worth it. |
| **Workday-shaped reference HRIS** | 2 | Mock external HRIS over synthetic data; a separate process so the adapter exercises real protocol and failure behavior. |
| **Message broker** | none | Not in the plan until [ADR-0008](adr/0008-broker-none-yet.md) exit criteria are met. |
| **Observability stack** (OpenTelemetry, Prometheus, Grafana) | later | Structured logs, correlation IDs and basic metrics first; the rest when there is more than one process or an SLO worth alerting on. |

Background work (outbox dispatcher, sync runs, scheduled jobs) runs **inside the API process**, activated by a Spring profile so it can
be split into its own process without code changes when needed. That replaces the brief's `workers/integration-worker` for now.

## 4. Cross-cutting decisions

- **API style.** REST + JSON, OpenAPI as the contract, errors as RFC 9457 Problem Details with a stable error code and the correlation ID.
  *Assumption:* contract-first vs. code-first is left to Phase 1.
- **Idempotency.** Unsafe operations accept an idempotency key; consumers de-duplicate on `(eventId, consumer)`. Never claim
  exactly-once; the guarantee is **at-least-once delivery plus idempotent processing** ([ADR-0006](adr/0006-event-driven-integration.md)).
- **Time.** Instants in UTC for "when did it happen" (`occurredAt`, `recordedAt`). Local dates for business effective dates
  (`effectiveDate`), interpreted in the jurisdiction's zone. Never mix them in one field.
- **Money.** `Money(amount, currency)` always; decimal arithmetic; no floating point. Currency list is data, not code.
- **Tenancy.** A `TenantId` is resolved from the authenticated principal at the edge, carried in a request context, and required by
  every repository call. Isolation approach in [ADR-0003](adr/0003-postgresql-and-tenancy.md).
- **Correlation.** Every request and event carries a `correlationId` and `causationId`. They appear in every log line, audit record, and
  delivery attempt.
- **Validation.** Bean Validation at the API edge; invariants in the domain; never trust provider data ([INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md)).
- **Localization.** Presentation locale (UI) is independent of jurisdiction (rules). UI strings are externalized from day one
  ([ADR-0009](adr/0009-frontend-architecture.md)); native legal terms come from the registry, not from UI bundles.
- **Configuration and secrets.** Twelve-factor style environment configuration; no secrets in the repo; a committed `.env.example`
  with placeholders only when code exists.
- **Feature flags.** Minimal and explicit: the only ones anticipated are simulator fault injection and connector enablement.

## 5. Data architecture

- **One PostgreSQL database**, **one schema per module**, Flyway migrations owned by the module. No cross-schema foreign keys.
- **Tenant column on every tenant-owned table.** Row-level security is proposed as defense in depth, not as the only control
  ([ADR-0003](adr/0003-postgresql-and-tenancy.md)).
- **Effective-dated history** for Employment status, Assignment, and Compensation; pattern chosen by spike (OQ-03 in [DOMAIN_MODEL.md](DOMAIN_MODEL.md)).
- **Global reference data** (Legal Concept Registry) has no tenant column. Its source of truth is versioned files in the repo, projected into
  the database ([ADR-0012](adr/0012-legal-concept-registry.md)).
- **Append-only** where required (audit, delivery attempts, outbox history) with retention and pseudonymization designed in
  ([SECURITY.md](SECURITY.md)).
- No production schema is created in Phase 0.

## 6. Integration and events (summary)

Business transaction and outbox row commit together; a dispatcher delivers to consumers at least once; consumers are idempotent; failures
retry with backoff and then dead-letter; operators can retry or replay with authorization and audit. Detail:
[INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md), diagrams
[04](diagrams/04-hris-integration.md) and [05](diagrams/05-event-processing.md).

## 7. Observability plan (phased)

| Phase | What | Answers |
|---|---|---|
| 1 | Structured JSON logs; `correlationId`, `tenantId`, `eventId` in every line; Micrometer metrics (delivery attempts, latency, retries, dead letters); health endpoints | What happened? Why? Did it retry and recover? |
| 2 | Add Workday-shaped connector sync metrics and sync-run records | Is the sync healthy? |
| later | OpenTelemetry traces across HTTP, outbox, dispatcher; Prometheus and Grafana; SLOs for measured SLIs | Where did it fail, across which hop? |

Logs never contain PII values ([SECURITY.md](SECURITY.md)).

## 8. Testing strategy

| Level | What | Notes |
|---|---|---|
| Unit | Domain invariants, state machines, mappers | Fast, no I/O. |
| Module | A module behind its public API with a real PostgreSQL (Testcontainers) | Primary confidence layer. |
| Architecture | Dependency rules, no cross-module access, no provider types outside adapters | ArchUnit or Modulith verification. |
| Contract | Connector port; `NormalizedWorker`; published event schemas | Schema compatibility checks for evolution. |
| Failure injection | Crash between business commit and dispatch; duplicate delivery; consumer timeout | Proves the outbox and idempotency claims with evidence, not assertion. |
| Property-based | Effective-dating overlaps, gaps, retroactive changes | Where example tests miss edge cases. |
| UI / accessibility | Keyboard flow, screen-reader semantics, reduced motion, contrast | Part of definition of done, not an audit at the end. |
| Legal behavior | **None until a concept is VERIFIED and professionally reviewed** | Tests must cite the source and version they encode. |

## 9. Failure modes (component level)

| Component | Failure | Effect | Response |
|---|---|---|---|
| API process | Crash after commit, before dispatch | Outbox row exists, undelivered | Dispatcher resumes on restart; at-least-once holds. |
| Dispatcher | Two instances run | Duplicate deliveries | `SKIP LOCKED` claiming plus idempotent consumers. |
| Downstream system | Down or slow | Attempts fail | Backoff, circuit state visible, dead-letter after policy, operator retry. |
| Downstream system | Accepts but times out | Unknown outcome | Idempotency key on retry; reconcile before re-sending. |
| HRIS sync | Partial page failure | Half-applied sync | Sync run is resumable and idempotent per record; errors recorded per record. |
| Mapping | Provider changes a field | Bad or missing data | Versioned mappings; unmapped fields preserved in `extensions`; data-quality error class. |
| Database | Unavailable | API unavailable | Health check fails; no silent fallback; no dual-write to elsewhere. |
| Clock skew | Effective-date jobs misfire | Wrong effective transition | Use database time for scheduling decisions; tests around date boundaries. |

## 10. Scaling and extraction path

Scale vertically first; PostgreSQL comfortably carries the modeled workload for a long time. When pressure appears, in order:

1. Split background work from request handling (same artifact, different profile).
2. Add read replicas or read-optimized projections for `operations` queries.
3. Extract `integrations` into its own deployable (I/O-bound, retry-heavy, different scaling profile). This is safe only if no other module joins its tables.
4. Introduce a broker between the monolith and extracted consumers ([ADR-0008](adr/0008-broker-none-yet.md) criteria).
5. Extract `audit` or `countrypolicy` only for a concrete reason (compliance isolation, independent release cadence).

`workforce` should be the *last* thing extracted; it is the transactional heart.

## 11. Repository structure

**As of Phase 1 (actual):**

```
palcheck/
├── README.md   MASTER_PROJECT_BRIEF.md   AGENTS.md   CLAUDE.md
├── docker-compose.yml        PostgreSQL 17 and Keycloak for local development (not yet executed on Docker)
├── .env.example              placeholder-only environment template (.env is untracked)
├── .github/workflows/        ci.yml (build and test), e2e.yml (manual browser suite)
├── infra/keycloak/           palcheck-realm.json: development realm with synthetic users
├── backend/                  Spring Boot 4, Java 17, one Maven artifact (com.palcheck)
│   ├── src/main/java/com/palcheck/
│   │   ├── shared/           tenant id, error codes, application exceptions
│   │   ├── platform/         request context and logging, problem details, command idempotency, clock (technical, no domain)
│   │   ├── access/           tenant, principal mapping, roles and permissions, OIDC session security (api/ is public, internal/ is not)
│   │   ├── audit/            append-only audit recorder and history reader (api/ is public)
│   │   └── workforce/        domain, application, infrastructure (JDBC), web: Person, Employment, Assignment, Legal Entity, Org Unit
│   └── src/main/resources/db/migration/   V1 schemas, V2 access, V3 workforce, V4 audit, V5 idempotency
├── web/                      React + TypeScript SPA, component tests (Vitest) and e2e/ (Playwright)
└── docs/                     PRODUCT_VISION, ARCHITECTURE, MODULES, DOMAIN_MODEL, INTEGRATION_ARCHITECTURE, ...,
                              PHASE_1_IMPLEMENTATION_PLAN, LOCAL_DEVELOPMENT, WORKLOG, adr/ (0001 to 0022), diagrams/, localization/
```

**Not created yet** (each appears when the phase that needs it starts):

| Path | Appears | Notes |
|---|---|---|
| `contracts/` | with the first published event or connector | JSON Schema for published events and `NormalizedWorker`. Phase 1 publishes nothing, so there is nothing to describe |
| Legal Concept Registry content files | with registry work | Location decided in [ADR-0012](adr/0012-legal-concept-registry.md) |
| technical `messaging` package | when a downstream effect exists | Durable publication inside the command transaction; never owned by `integrations` ([ADR-0018](adr/0018-module-transactions-and-persistence.md)) |
| `integrations`, `lifecycle`, `countrypolicy`, `operations` packages | later phases | See [MODULES.md](MODULES.md) |
| `mock-hris/` | Phase 2 | Workday-shaped reference HRIS over synthetic data |
| `observability/` | later | When OpenTelemetry, Prometheus and Grafana are adopted |

The build tool question is settled: Maven, with module boundaries as Java packages checked by ArchUnit tests, not Spring Modulith ([ADR-0022](adr/0022-phase1-technical-baseline.md)).

**Deviations from the brief's tree**, with reasons:

| Brief | Now | Why |
|---|---|---|
| `apps/web`, `backend/palcheck-api` | `web/`, `backend/` (as built) | Fewer nesting levels; one backend artifact, so no need for `palcheck-api` naming. |
| `workers/integration-worker` | deferred | Contradicts the modular monolith; a profile does the same job. |
| `packages/event-contracts, shared-types, design-tokens` | `contracts/`; tokens inside `web/` | A `packages/` workspace for one consumer each is empty complexity. |
| `infrastructure/{docker,postgres,messaging,observability,local}` | `infra/` (only the Keycloak realm so far); `docker-compose.yml` at the root | `messaging` has no content without a broker. |
| `scripts/` | when a script exists | Empty otherwise. |
| `docs/DATA_LINEAGE.md` | `docs/DATA_OWNERSHIP_AND_LINEAGE.md` | Ownership and lineage are one model. |
| `costa-rica/{COMPENSATION,LEAVE,TERMINATION,WORKING_TIME}.md` and `sources/` | `RESEARCH_BACKLOG.md`; `sources/` at first verified source | Four files of unanswered questions is one file; an empty directory is noise. |
