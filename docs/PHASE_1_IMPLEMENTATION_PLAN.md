# Phase 1 Implementation Plan: Authenticated Workforce Foundation

Status: **Phase 1 implemented; awaiting Daniel's review** (opened by Daniel Aguilar, 2026-09-19) · Owner of decisions: Daniel · Author: Claude Code · Last updated: 2026-09-19
Architecture baseline: [ARCHITECTURE.md](ARCHITECTURE.md), [MODULES.md](MODULES.md) section 2a, [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [SECURITY.md](SECURITY.md), [CODEX_ARCHITECTURE_REVIEW.md](CODEX_ARCHITECTURE_REVIEW.md), ADR-0018 to ADR-0021 (accepted in part) and [ADR-0022](adr/0022-phase1-technical-baseline.md) (Proposed).

This plan is the contract for Phase 1. Anything not in it is out of scope. Each implementation step ends with tests, a worklog entry and, if reality differs from this plan, an update to this file.

> **Codex review follow-up (2026-09-19):** Phase 1 fixes and UI polish are complete; Daniel's acceptance remains pending. [PHASE_1_CODEX_REVIEW.md](PHASE_1_CODEX_REVIEW.md) records independent findings and final test results. Section 18 below records the implementation delta; earlier delivery counts remain historical evidence.

## 1. Objective and definition of done

**Objective.** One complete, usable vertical slice: an authenticated, authorized user can create, view and edit **synthetic** workforce records inside their Tenant. Changes respect tenant boundaries and permissions, preserve revision history, prevent lost updates, support command idempotency, produce durable audit records that commit atomically with the business change, and emit useful structured logs, all covered by meaningful automated tests.

**A permitted user can:** sign in; enter their Tenant; list people; open a workforce record; view employment and assignment; create a record; edit permitted fields; save safely; see an understandable change history; and receive clear errors for unauthenticated, forbidden, not found, tenant-boundary, concurrent-modification and invalid-request cases.

**Definition of done** (Daniel's list; status column updated as work lands):

| # | Criterion | Evidence (all run and passing on 2026-09-19) | Status |
|---|---|---|---|
| 1 | Runs locally from documented instructions | [README](../README.md) and [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md); the whole stack (PostgreSQL 17, Keycloak 26.7.4, backend `dev` profile, Vite) was started from them and driven by the browser suite | **done**; `docker-compose.yml` itself not yet executed (R2) |
| 2 | Authentication works | `OidcLoginFlowIT` (real authorization-code flow against a mock OIDC server), `AuthenticationIT`; Playwright signs in through real Keycloak | done |
| 3 | Tenant context is server-enforced | Principal mapping on `(issuer, subject)`; no endpoint or body accepts a tenant; ArchUnit rule that every repository method takes `TenantId`; `TenantIsolationIT`; `WorkforceSchemaIT` composite-FK tests; Playwright cross-tenant test | done |
| 4 | Authorization works | `AuthorizationMatrixIT` (every endpoint, every role, denied callers leave no trace), `RoleTest`; frontend hides unavailable controls; Playwright viewer test | done |
| 5 | Synthetic People records can be viewed | `DemoDataSeederIT`; list and detail ITs; Playwright | done |
| 6 | Permitted users can create/edit workforce data | `PeopleCreateAndReadIT` (13), `PeopleEditIT` (10); frontend create and edit tests; Playwright create then edit | done |
| 7 | Cross-tenant access is rejected | `TenantIsolationIT` (every endpoint; cross-tenant id answers exactly like a missing id), `WorkforceSchemaIT`, Playwright (a second tenant opens the first tenant's URL: "Record not found") | done |
| 8 | Unauthorized access is rejected | `AuthenticationIT` (401 JSON, missing CSRF, suspended tenant, disabled principal), `AuthorizationMatrixIT`; Playwright sign-out then 401 | done |
| 9 | Input validation is consistent | One domain implementation shared by create and patch (`PersonDataTest`, `PersonPatchTest`, `EmploymentDataTest`), ITs for the problem shape, unknown fields rejected; frontend error-summary tests | done |
| 10 | Optimistic concurrency prevents lost updates | `ConcurrencyIT` (stale version, independent parts, two simultaneous edits give one success and one conflict); frontend conflict tests; Playwright stale-tab test | done |
| 11 | Relevant write retries are idempotent | `IdempotencyIT` (11: replay, key reuse with a different body, failed request keeps its key, scope, expiry, concurrent same-key and same-email), `FingerprintTest`; frontend tests that a lost-response retry reuses the key | done |
| 12 | Mutations create atomic audit history | `AuditIT` (forced audit failure leaves no person, employment, assignment, audit or receipt row, and an edit leaves the version unchanged; audit cannot be written outside a business transaction; entries cannot be updated, deleted or truncated) | done |
| 13 | API errors follow one model | Problem-details assertions across the ITs (code, `requestId`, `correlationId`, field errors, `currentVersion`); frontend classifies by `code` | done |
| 14 | Structured logs exist | `LoggingCanaryIT` (canary personal values never appear; identifiers do); ECS JSON with `requestId`, `correlationId`, `tenantId`, `userId`, `operation`, `result` observed on a running instance; no token, cookie or name in a real run's log | done |
| 15 | Health endpoints work | `ApplicationHealthIT` (liveness without auth, readiness reflects the database, no other actuator endpoint exposed) | done |
| 16 | Frontend is keyboard accessible | Component tests with axe; **real-browser axe scan (WCAG 2.0 to 2.2 A/AA including contrast and target size) of sign-in, list, create, create with errors, detail, detail editing and the no-workspace page, plus sign-in, list and an error state in the dark scheme**; a keyboard-only journey (skip link, search, open record, edit, cancel); focus-management tests; reduced-motion check | done for everything automatable. **A manual screen-reader pass has not been performed** |
| 17 | Automated tests cover critical behavior | Backend 150 (58 unit including 15 architecture rules, 92 integration on real PostgreSQL); frontend 92; browser end-to-end 12 | done |
| 18 | No real employee/employer data | All seed and test data fictional; every email on a reserved domain (`.example`, `example.test`); grep review 2026-09-19; no real credentials committed (`.env` untracked, placeholders in `.env.example`) | done |
| 19 | No unverified Costa Rican law is executable | No legal behavior implemented; grep of code, config and tests for legal terms and country-specific branching found none (`CR` appears only as seed data). Placeholder statuses and types are labelled as software placeholders in the domain and in the UI | done |
| 20 | Documentation matches the system | Step 13 cleanup: README, LOCAL_DEVELOPMENT, ARCHITECTURE section 11, SECURITY section 17, ADR-0022 outcome notes, ADR index, this plan | done, pending Daniel's review |

## 2. Scope

**In:** `access` (tenant, principal mapping, roles to permissions), `audit`, `workforce` (Legal Entity, Org Unit, Person, Employment, Assignment), technical `platform` and `shared` packages; OIDC sign-in with a development identity provider; People list, record detail, create and edit, change history; PostgreSQL + Flyway; health endpoints; Docker Compose for PostgreSQL and Keycloak; focused CI.

**Out (Daniel's explicit non-goals):** payroll, Workday/SAP/Oracle connectors and simulators, Kafka/Redpanda/any broker, outbox dispatch, event publication with no Phase 1 use, integration simulator, CCSS/aguinaldo/vacation/cesantía/preaviso calculations, country-policy execution, provisioning and equipment workflows, incidents, SLO dashboards, Prometheus/Grafana/tracing platform, microservices, mobile app, and any real data or unverified law.

**Deliberately not built although the architecture allows it** (recorded so it is not mistaken for an omission): row-level security; manager-hierarchy or org-unit scoped permissions; bitemporal queries; job-change and termination processes; Person deletion; export; principal/role administration UI; multi-tenant membership; national identifiers, birth dates, bank, compensation or health fields; governed-field ownership policy (everything is `PALCHECK`-owned); Spanish UI (strings are externalized).

## 3. Accepted architecture as applied to Phase 1

### 3.1 Final dependency direction

See [MODULES.md](MODULES.md) section 2a and the diagram at the end of [diagrams/03-module-map.md](diagrams/03-module-map.md). In Phase 1:

```
shared  <-  platform  <-  { access, audit, workforce }
access -> audit.api          workforce -> access.api, audit.api
audit  -> (platform only)    access    -> (platform, audit.api)
```

- Durable publication is technical `messaging` infrastructure. It is **not created** in Phase 1: no asynchronous effect exists and no meaningless events are invented. The transaction model is preserved: `workforce` application services own one local transaction; `audit` joins it with `Propagation.MANDATORY`; a future publication abstraction would join it the same way. `integrations` (later) consumes published events and never owns publication infrastructure.
- `audit` never depends on `access` or `workforce`. The person-history endpoint is composed in a `workforce` application facade that checks permission and record existence, then calls `audit.api`.
- Modules expose only their `api` package. Everything else is internal. ArchUnit tests enforce this (section 12).

### 3.2 Package structure (single Maven artifact, `com.palcheck`)

```
backend/src/main/java/com/palcheck/
  PalCheckApplication.java
  shared/                     kernel: TenantId, ApplicationException hierarchy, ErrorCode, FieldViolation
  platform/                   technical, no business rules
    web/                      problem-details handler, request-id and correlation filter, access log
    idempotency/              IdempotencyService, repository, purge job
    logging/                  MDC keys, operation log helper
    time/                     Clock bean
  access/
    api/                      Actor, Permission, Authorization, ActorResolver contract, SessionInfo
    internal/                 role catalog, principal repository, OIDC mapping, security config, argument resolver, session controller
  audit/
    api/                      AuditRecorder, AuditRecord, AuditChange, AuditHistory, AuditEntryView
    internal/                 repository (JDBC), append-only trigger lives in migration
  workforce/
    api/                      (empty in Phase 1: reserved for future published contracts)
    domain/                   Person, Employment, Assignment, LegalEntity, OrgUnit, rules and value objects
    application/              command and query services, repository ports
    infrastructure/           JDBC repositories
    web/                      REST controllers, request/response DTOs, patch readers
backend/src/main/resources/db/migration/   one global Flyway stream (each file names its owner)
web/                          React + TypeScript (Vite)
docker-compose.yml, infra/    PostgreSQL + Keycloak for local development
```

### 3.3 Database ownership

One PostgreSQL database, **one schema per actual owner**, one globally ordered Flyway stream, applied versioned migrations immutable.

| Schema | Owner | Tables | Notes |
|---|---|---|---|
| `access` | `access` | `tenant`, `principal`, `principal_role` | Tenant is the anchor other schemas reference |
| `workforce` | `workforce` | `legal_entity`, `org_unit`, `person`, `employment`, `assignment` | Tenant-qualified composite foreign keys; no cross-tenant association is representable |
| `audit` | `audit` | `audit_entry` | Append-only (trigger rejects update and delete) |
| `platform` | `platform` | `idempotency_record` | Technical command receipts; holds no domain data, only a result reference |

Rules: only the owner reads or writes its tables; no cross-module repository access; cross-schema foreign keys are limited to the tenant anchor (`access.tenant`); no cascading deletes; other cross-module references are by ID and resolved through the owner's API.

## 4. Domain model

Vocabulary is fixed: **Tenant, Legal Entity, Organizational Unit, Person, Employment, Assignment.** Not collapsed into one Employee table; "Employee" is a role, not an entity. The bare word "organization" is not used (see [DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 3).

All identifiers are application-generated UUIDs. Timestamps are `timestamptz` (UTC instants); business dates are `date` with no time-zone interpretation in Phase 1 (no clock-driven transitions exist). Every workforce table has `tenant_id`, `created_at`, `updated_at`; mutable aggregates also have `version` (starts at 1).

| Entity | Fields (Phase 1) | Invariants |
|---|---|---|
| **Tenant** (`access`) | id, slug, name, status (`ACTIVE`/`SUSPENDED`) | slug unique, lowercase kebab; suspended tenant denies access |
| **Principal** (`access`) | id, tenantId, issuer, subject, displayName, status; roles | `(issuer, subject)` unique; belongs to exactly one tenant (multi-tenant membership deferred) |
| **Legal Entity** (`workforce`) | id, tenantId, name, country (ISO 3166-1 alpha-2) | name unique per tenant. Seed-only; no rules attach to it in Phase 1 |
| **Org Unit** (`workforce`) | id, tenantId, parentId (nullable), name | name unique per tenant; parent in the same tenant (composite FK); seed-only |
| **Person** (`workforce`) | id, tenantId, firstName, middleName?, lastName, preferredName?, workEmail, locale, version | first/last name required; `lastName` may hold several family names; `workEmail` normalized to lowercase, unique per tenant; `locale` from a small allow-list. No personal email, birth date, national ID or other sensitive data |
| **Employment** (`workforce`) | id, tenantId, personId, legalEntityId, status, employmentType, country, startDate, endDate?, version | Person and Legal Entity in the same tenant (composite FKs); legal entity immutable after creation; `ENDED` requires `endDate`; `endDate >= startDate`; a Person may have several Employments over time (rehire is a new Employment) |
| **Assignment** (`workforce`) | id, tenantId, employmentId, orgUnitId, title, managerEmploymentId?, effectiveFrom, effectiveTo?, version | Employment, Org Unit and manager in the same tenant; manager is not the employee and creates no reporting cycle; **at most one current assignment (`effective_to IS NULL`) per employment** (partial unique index); `effectiveFrom` is not before the employment start |

Notes on placeholder vocabularies (**SOFTWARE BEHAVIOR, not legal concepts**): employment `status` is `PENDING`, `ACTIVE` or `ENDED`; `employmentType` is `FULL_TIME` or `PART_TIME`. They are organizational classifications, not legal contract or worker types (Costa Rica worker-type research WC-1 will decide the real taxonomy). The country field records where the employment is located; no rule branches on it.

**History semantics.** Current-state tables are authoritative. The assignment carries `effectiveFrom`/`effectiveTo` so a future job change closes one row and opens another; in Phase 1 editing the assignment is an in-place *correction* with an audit entry and a version bump. Audit is evidence of change, not a bitemporal query model ([ADR-0018](adr/0018-module-transactions-and-persistence.md) decision 7, provisional).

## 5. Persistence design

**Access approach:** Spring JDBC (`JdbcClient`) with explicit repositories, one style ([ADR-0022](adr/0022-phase1-technical-baseline.md)). Every repository method takes a `TenantId`; every SQL statement on a tenant-owned table filters by it. ArchUnit forbids repository methods without a `TenantId` parameter (documented exceptions only).

**Optimistic concurrency.** Each mutable aggregate has `version`. Updates run `UPDATE ... WHERE tenant_id = ? AND id = ? AND version = ?` and increment `version`. Zero rows updated is resolved inside the same transaction: absent in this tenant is `NOT_FOUND`, otherwise `CONFLICT` with the current version. Clients send `expectedVersion` in the request body. A patch that changes nothing does not increment the version and writes no audit entry.

**Audit model** (`audit.audit_entry`, append-only): `id`, `tenant_id`, `occurred_at`, `actor_type` (`USER`/`SYSTEM`), `actor_id`, `actor_display`, `operation` (`CREATE`/`UPDATE`), `entity_type` (`PERSON`/`EMPLOYMENT`/`ASSIGNMENT`), `entity_id`, `entity_version`, `record_type`/`record_id` (the history key; the Person for all three entity types), `change_set_id` (one per command, so a create groups its three entries), `changes` (jsonb list of `{field, before, after}` as text), `reason`, `command` (e.g. `person.create`), `request_id`, `correlation_id`. A trigger rejects `UPDATE` and `DELETE`. `AuditRecorder.record` is `@Transactional(propagation = MANDATORY)`, so it cannot run outside a business transaction, and a failure aborts the business operation.

**Idempotency** (`platform.idempotency_record`). Scope: `(tenant_id, principal_id, operation, key)`. The reservation row is inserted **inside the business transaction** with `INSERT ... ON CONFLICT DO NOTHING`, so concurrent requests with the same key serialize on the unique index and converge: the loser waits, then sees the committed result. A rolled-back business transaction rolls back its reservation, so a failed request leaves nothing behind.

| Aspect | Behavior |
|---|---|
| Header | `Idempotency-Key` (1 to 128 characters of `[A-Za-z0-9_.:-]`, UUID recommended). **Required on `POST /api/people`**, optional on `PATCH` endpoints; never applied to reads |
| Request identity | SHA-256 of a canonical, deterministic rendering of the parsed command (independent of field order and whitespace) |
| Same key + same request | Returns the original outcome (same status, `Location`) with `Idempotency-Replayed: true`. The body is the resource's **current** representation (no personal data is copied into the receipt), read under the caller's current authorization |
| Same key + different request | `422 IDEMPOTENCY_CONFLICT` |
| Different principal or tenant, same key | Independent (scope includes both) |
| Expiry | 24 hours (configurable). Expired same-key rows are removed at reservation time, and an hourly job purges the rest |
| Not covered | Cross-operation deduplication; business-operation identity for downstream effects (accepted requirement, later milestones) |

## 6. Authentication

Standards-based **OIDC authorization code flow** with Spring Security `oauth2Login`, **same-origin backend-managed session** ([ADR-0022](adr/0022-phase1-technical-baseline.md); ADR-0020 decision 5 provisional). Tokens never reach browser JavaScript. Keycloak is a development identity provider behind standard OIDC configuration; PalCheck code depends on no Keycloak type, and the domain never sees the IdP.

- **Flow.** SPA calls `GET /api/session`. `401` means sign in: the SPA navigates the browser to `/oauth2/authorization/keycloak`. After login the browser returns to the app with a session cookie (`HttpOnly`, `SameSite=Lax`, `Secure` outside local development).
- **Session and CSRF.** Session fixation protection on. CSRF protection uses the cookie-token pattern for SPAs (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header on mutating requests). Restarting the single instance ends sessions (documented; persistence is not justified yet).
- **Logout.** A native `POST /logout` form includes the CSRF cookie value as `_csrf`. Spring invalidates the session and redirects directly to provider logout. No token-bearing redirect URL is returned in JSON to application JavaScript; the standard ID-token hint still travels in provider-directed browser navigation.
- **Tenant mapping (trusted, server-side).** After authentication, the verified `(issuer, subject)` maps to a `principal` row, which fixes the tenant and roles. **Tenant is never read from a URL, header or body.** Unmapped or suspended-tenant principals receive `403 TENANT_ACCESS_DENIED`. Roles are re-read per request, so revocation takes effect immediately (a short-TTL cache is a later optimization).
- **Local development.** Keycloak (`palcheck` realm) imported from `infra/keycloak`, users with fixed subjects matching the demo seed. No credentials are committed beyond fixed, obviously-synthetic development values supplied through `.env.example`.
- **Bearer API clients** (OAuth2 resource server) are not needed by Phase 1 and are not configured.

## 7. Authorization and tenant isolation

**Permissions** (checked in application services, not scattered role comparisons): `VIEW_PEOPLE`, `VIEW_PERSON`, `CREATE_PERSON`, `EDIT_PERSON`, `VIEW_EMPLOYMENT`, `EDIT_EMPLOYMENT`, `VIEW_AUDIT_HISTORY`.

**Roles** are named bundles resolved in code (`Role` to permission set); the database stores only which roles a principal holds:

| Role | Permissions |
|---|---|
| `HR_ADMIN` | all seven |
| `HR_VIEWER` | `VIEW_PEOPLE`, `VIEW_PERSON`, `VIEW_EMPLOYMENT` |

Scope in Phase 1 is **tenant-wide only**. The `Actor` type carries permissions with an explicit `TENANT` scope so future scopes (direct reports, org unit) extend rather than replace it; none are built ([ADR-0014](adr/0014-authorization.md), review SEC-01). List, search and count queries are tenant-scoped at the repository before pagination. Creating a record requires `CREATE_PERSON` **and** `EDIT_EMPLOYMENT` (it also creates an employment and an assignment).

**Trusted actor.** Application services accept an `Actor` that only the `access` module constructs after resolving the authenticated principal; controllers never build one, and ArchUnit forbids constructing `Actor` outside `access`. There is no ambient thread-local.

**Tenant isolation layers** (defense in depth):

1. Tenant comes only from the authenticated principal mapping (section 6).
2. Every repository method requires `TenantId`; every statement filters by it.
3. Tenant-qualified composite foreign keys make a mixed-tenant association unrepresentable in the database (tested with raw SQL).
4. Cross-tenant object IDs return `404 NOT_FOUND`, indistinguishable from nonexistent IDs, so existence is not disclosed. Cross-tenant references inside a request body (for example an org unit ID from another tenant) fail validation as unknown references.
5. Automated negative tests exercise **every** endpoint with another tenant's principal.
6. **Row-level security is deferred** (not implemented): it needs a non-superuser runtime role, transaction-scoped tenant binding on the borrowed connection and pool-reuse tests, which are disproportionate for Phase 1. It remains a hardening candidate ([ADR-0003](adr/0003-postgresql-and-tenancy.md), [ADR-0022](adr/0022-phase1-technical-baseline.md)).

## 8. API surface

Unversioned JSON API under `/api` (the SPA is the only client). All endpoints require authentication, except the health probes.

| Method and path | Permission | Purpose |
|---|---|---|
| `GET /api/session` | authenticated + mapped principal | current user, tenant and permission list (bootstrap) |
| `GET /api/people?q=&page=&size=` | `VIEW_PEOPLE` | paged directory: name, title, org unit, legal entity, employment status; search on name and work email |
| `POST /api/people` | `CREATE_PERSON` + `EDIT_EMPLOYMENT` | create Person + Employment + Assignment atomically (`Idempotency-Key` required) |
| `GET /api/people/{personId}` | `VIEW_PERSON` | person detail with `version` |
| `PATCH /api/people/{personId}` | `EDIT_PERSON` | edit person fields with `expectedVersion` |
| `GET /api/people/{personId}/employments` | `VIEW_EMPLOYMENT` | employments with legal entity and current assignment (each with its own `version`) |
| `PATCH /api/employments/{employmentId}` | `EDIT_EMPLOYMENT` | edit employment status, type, country, dates |
| `GET /api/manager-candidates?q=&page=&size=&excludingEmploymentId=` | `VIEW_PEOPLE` + `VIEW_EMPLOYMENT` | tenant-scoped employment candidates; default 20, maximum 100; accent-insensitive search |
| `PATCH /api/assignments/{assignmentId}` | `EDIT_EMPLOYMENT` | edit title, org unit, manager |
| `GET /api/people/{personId}/audit?page=&size=` | `VIEW_AUDIT_HISTORY` | change history, newest first |
| `GET /api/legal-entities`, `GET /api/org-units` | `VIEW_PEOPLE` | tenant reference lists for forms |
| `GET /actuator/health/liveness`, `/readiness` | none | operational probes |

Deviation from Daniel's sketch (documented, cleaner boundary): employment is read through `GET /api/people/{id}/employments` (plural; rehire is a real case) and assignment edits use their own resource so each has its own version. Detail endpoints are split by permission (`VIEW_PERSON` vs. `VIEW_EMPLOYMENT`) rather than returning conditionally filtered bodies. The directory list carries employment summary columns under `VIEW_PEOPLE` by design.

**Write semantics.** `PATCH` is a JSON merge-patch subset: absent field means unchanged; `null` clears an optional field; `null` on a required field is a validation error; unknown fields are rejected (this also blocks a client-supplied `tenantId`). Every write body may carry an optional `reason` (at most 500 characters) that is stored in the audit entry.

**Error model.** RFC 9457 `application/problem+json` with extensions `code`, `requestId`, `correlationId`, and `errors[]` (`field`, `code`, `message`) for validation. Stack traces and internals are never exposed.

| `code` | HTTP | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | malformed JSON, unknown field, rule violation, unknown or wrong-tenant reference, duplicate work email |
| `UNAUTHENTICATED` | 401 | no valid session (JSON, never a redirect, on `/api`) |
| `FORBIDDEN` | 403 | authenticated but lacking the permission; failed CSRF check |
| `TENANT_ACCESS_DENIED` | 403 | principal unmapped, disabled, or its tenant suspended |
| `NOT_FOUND` | 404 | absent in the caller's tenant (includes other tenants' IDs) and unknown routes |
| `CONFLICT` | 409 | stale `expectedVersion`; includes `currentVersion` |
| `IDEMPOTENCY_CONFLICT` | 422 | same key, different request |
| `INTERNAL_ERROR` | 500 | anything unexpected (generic message) |

Other framework errors map into the same body (`METHOD_NOT_ALLOWED` 405 is the only additional code). Security-filter errors (401, 403, CSRF) use the same writer.

## 9. Logging and health

- **Request context.** A first-in-chain filter always generates the request ID on the server (a client-supplied `X-Request-Id` is ignored), accepts `X-Correlation-Id` only if it matches a strict pattern (1 to 64 characters of letters, digits and `._:-`; otherwise it uses the request ID), puts both in the MDC, echoes both in response headers, and clears the MDC when the request ends.
- **Identity in logs.** After principal resolution the MDC also carries `tenantId` and `userId` (internal UUIDs).
- **Format.** Structured JSON (Spring Boot's built-in structured logging, ECS format) outside local development; readable console format for local runs. Business events log `operation`, `result` and entity IDs through SLF4J key/value pairs; one access-log line per request with method, route pattern, status and duration.
- **Never logged:** tokens, cookies, request or response bodies, workforce values (names, emails). A canary test creates records with a canary string in personal fields and asserts it appears in no log line, including failure paths.
- **Health.** `/actuator/health/liveness` (process alive) and `/actuator/health/readiness` (includes database availability). Only `health` is exposed; details hidden; no other actuator endpoint is reachable.

## 10. Frontend (`web/`)

React + TypeScript + Vite. Native semantic controls first; no component library. Plain CSS with semantic custom properties (light and dark, `forced-colors`, `prefers-reduced-motion`); system font stack with size-specific tracking; no Apple assets. All user-visible strings externalized through `react-intl` (English catalog only; adding Spanish is an addition). Server state via TanStack Query.

| Route | Content |
|---|---|
| (bootstrap) | `GET /api/session`: unauthenticated shows a sign-in page; `TENANT_ACCESS_DENIED` shows a clear message with sign out; a mid-session `401` shows "session expired" |
| `/people` | search field, table (Name, Title, Org unit, Legal entity, Status), pagination; loading, empty and error states |
| `/people/new` | create form (identity, employment, assignment) with inline and summary validation and an idempotency key per submission attempt |
| `/people/:personId` | record: Identity, Employment, Assignment (org unit, manager), Change history; each editable section is its own form bound to its own resource and version; conflict banner "This record changed since you opened it. Review the latest version before saving." with a reload action |

Accessibility is Phase 1 scope: landmarks, headings, associated labels, error summary that receives focus, `aria-describedby` on invalid fields, live-region status messages, visible focus, keyboard-operable everything, submit disabled while pending, reduced-motion safe. No dashboard, no metrics cards, no integration or legal screens.

**As built.** Everything above exists. Details worth knowing: the skip link is the first Tab stop on a fresh load, and focus moves to the new page heading only when the person navigates to another page (not on first load, redirects, paging or searching), waiting for the heading if the page is still loading; the error summary takes focus and links to each invalid control; a retry after a lost response reuses the same `Idempotency-Key` while a change of content gets a new one; a `401` from any call (other than the session check itself) returns the person to sign-in with an explanation; the people table scrolls horizontally inside its container on narrow screens (there is no dedicated mobile layout). Messages are in one English catalog (`web/src/i18n/en.ts`); server validation messages are shown as sent, and their stable codes are what a later localization would key on.

## 11. Synthetic demo data

Fictional tenants only, created by a `dev`-profile seeder through the real application services (so seed data has audit history): **Acme Latin America** (`acme`; legal entities Acme Costa Rica S.A. and Acme Panama S.A.; org units Engineering with Platform and Product, Finance, People, Operations; about ten people with Costa Rican names) and **Globex Andina** (`globex`; a second, smaller tenant for isolation tests and demos). Emails use the reserved `.example` domain. No real coworkers, customers, employer data or identifiers. Demo IdP users: `hr.admin@acme.example` (`HR_ADMIN`), `hr.viewer@acme.example` (`HR_VIEWER`), `hr.admin@globex.example` (`HR_ADMIN`, other tenant), and `unmapped@acme.example` (authenticates but has no PalCheck principal).

## 12. Test strategy

No coverage-percentage target. Real PostgreSQL wherever transactions, constraints or concurrency matter; no H2. Deterministic clock; no sleeps for synchronization.

| Layer | What is proven |
|---|---|
| Unit | Domain validation and patch application (`PersonDataTest`, `PersonPatchTest`, `EmploymentDataTest`); canonical request hashing (`FingerprintTest`); role-to-permission mapping (`RoleTest`); correlation-header sanitization and MDC clearing (`CorrelationFilterTest`). Problem mapping is asserted through the integration tests |
| Integration (real PostgreSQL, Spring context) | Create and update happy paths; validation; not found; **tenant isolation on every endpoint**; authorization matrix; sequential and **parallel** concurrent modification; **audit atomicity** (forced audit failure leaves no person, employment, assignment, audit or idempotency row); idempotent create retry, key reuse with a different body, concurrent same-key requests; unique-email race; append-only audit (update and delete rejected); composite-FK cross-tenant rejection via raw SQL; problem-details shape; CSRF; log canary; health probes |
| Authentication | Full OIDC authorization-code login against a test identity provider (mock OAuth2 server) through to a session, principal mapping and `GET /api/session`, plus unmapped-principal denial |
| Architecture (ArchUnit) | Dependency direction, only `api` visible across modules, no framework in `domain`, no repository call without `TenantId`, `Actor` constructed only in `access`, no provider or vendor types, `audit` independent of `access`/`workforce` |
| Frontend | Component tests (Vitest, Testing Library, `vitest-axe`) through a fake network at the `fetch` boundary, not by mocking internals: session gate, list (loading, empty, error, search, paging), create (validation summary and focus, success, retry key reuse, double-submit guard, forbidden), detail and edit (identity, employment, assignment, conflict and reload, server validation, viewer without controls, history), focus management, the API client (CSRF, idempotency key, problem parsing, ambiguous outcome, session probe), and a color-contrast check of the design tokens |
| End to end (Playwright, real Chrome, real Keycloak, PostgreSQL and backend) | Sign in, create a record, edit it, see it in history and search; a stale second tab is refused with the conflict message and does not overwrite; a viewer has no way to change anything; a second tenant cannot open the first tenant's record; an identity with no workspace sees nothing; sign-out ends the session server-side; create-form validation summary. A separate accessibility spec runs axe-core inside the real browser (WCAG 2.0 to 2.2 A/AA, including color contrast and target size) on seven screens in the light scheme and three in the dark scheme, drives a keyboard-only journey, and checks reduced motion |

**Original Claude delivery results (2026-09-19; superseded by independent review evidence in section 18).** Backend 150 tests (58 unit and architecture, 92 integration), frontend 92, end to end 12, all passing; frontend lint and type check clean.

**Test database.** PostgreSQL real binaries started in-process (`zonky embedded-postgres`) rather than Testcontainers, because no container runtime is available on the development machine (Docker was not found). This satisfies "real PostgreSQL, no H2"; the tradeoff and the swap path are in ADR-0022.

## 13. Local development and CI

- `docker compose up -d` starts PostgreSQL and Keycloak (from `.env`, copied from `.env.example`). Backend: `cd backend && ./mvnw spring-boot:run` (profile `dev` seeds demo data). Frontend: `cd web && npm ci && npm run dev`. Exact commands, ports, users, reset and troubleshooting are in the README as each piece lands.
- Requires JDK 17+ and Node 24 (tested). Maven is provided by the wrapper. No Kafka, Grafana, Prometheus or Workday simulator is needed.
- **CI** (GitHub Actions): [ci.yml](../.github/workflows/ci.yml) runs backend `mvn verify` and frontend typecheck, lint, tests and build. Browser end-to-end runs as a separate, manually dispatched [e2e.yml](../.github/workflows/e2e.yml) because it needs Docker for Keycloak; **neither workflow has been run on GitHub yet** (the same commands were run locally). No deployment pipeline. Dependency and secret scanning workflows are now prepared (section 18), but have not run on GitHub.

## 14. Dependencies (each justified; versions verified on Maven Central and npm on 2026-09-19)

**Backend:** Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Jackson 3): `webmvc`, `security`, `security-oauth2-client`, `jdbc`, `flyway` (+ `flyway-database-postgresql`), `actuator`; PostgreSQL JDBC driver. Java 17 (installed LTS). **Test:** Spring Boot test starters, `zonky embedded-postgres`, ArchUnit, `mock-oauth2-server`. Not used: JPA/Hibernate, Spring Data, Spring Modulith, MapStruct, Bean Validation for field rules (rules live in the domain so create and patch share them), Lombok, Testcontainers (no container runtime here).

**Frontend:** React, React DOM, React Router, TanStack Query, `react-intl`, Vite, TypeScript; test and quality tooling: Vitest, jsdom, Testing Library (`react`, `user-event`, `jest-dom`), `vitest-axe` and `axe-core` (component and real-browser accessibility checks), Playwright (end to end), oxlint. No component library, CSS framework or icon set.

## 15. Implementation steps

| Step | Deliverable | Exit criteria | Status |
|---|---|---|---|
| 1 | Reconcile Phase 0 docs with approved refinements | Notices, ADR acceptance records, roadmap, gate updated; links resolve | done |
| 2 | This plan and ADR-0022 | Reviewed against Daniel's message | done |
| 3 | Backend, frontend and local-dev foundation | Builds run; app starts; health works; Compose files written; CI added | done |
| 4 | Authentication and tenant context | Login integration test; principal mapping; 401/403 JSON | done |
| 5 | Workforce persistence | Migrations; repositories; composite-FK tests | done |
| 6 | People read experience | List, detail, employments, reference lists; isolation and permission tests | done |
| 7 | Create and edit commands | Validation; create and patch; tests | done |
| 8 | Audit and history | Atomic audit; history endpoint; append-only test | done |
| 9 | Concurrency and idempotency | Conflict, parallel and idempotency tests | done |
| 10 | Frontend create, edit, detail | Pages against the real API contract | done |
| 11 | Accessibility and error-handling pass | Automated checks and a keyboard-only journey recorded; manual screen-reader pass **not** performed (section 17) | done |
| 12 | Focused integration and end-to-end tests | The Playwright suite (12 tests) passes against the real stack | done |
| 13 | Documentation cleanup and Phase 1 review | Every doc matches the system; definition-of-done table filled with evidence | done, pending Daniel's review |

## 16. Risks and open questions

- **R1** Spring Boot 4 / Spring Security 7 / Jackson 3 are new to this codebase; API surface may differ from documentation I know. Mitigation: small surface, verify with tests, read primary docs on first use.
- **R2** Docker is not installed on this machine, so `docker-compose.yml` and the GitHub `e2e.yml` workflow could not be executed here. The Keycloak realm was verified by importing it into a locally unpacked Keycloak 26.7.4, and the entire sign-in and browser flow ran against that plus a local PostgreSQL 17. **Status: realm verified; Compose wiring and both workflows unverified until run** (labelled in their file headers and in the local-development guide).
- **R3** Embedded PostgreSQL binary versions may differ slightly from the development database version; both are recent major versions, and CI uses the same embedded binaries.
- **R4** Per-request principal lookup adds one query per API call; acceptable now, measure before caching.
- **R5** Single-instance in-memory sessions log users out on restart (30-minute idle timeout). Accepted for Phase 1; the reconsider trigger is in ADR-0022.
- **Q1 (Daniel)** Confirm the provisional choices in ADR-0022: Spring JDBC over JPA, backend OIDC session over browser-held tokens, Maven with packages and ArchUnit instead of Spring Modulith, embedded PostgreSQL for tests while no container runtime is available, row-level security deferred.
- **Q2 (Daniel)** Confirm `Idempotency-Key` being **required** on create (optional on patch), and 24-hour receipt expiry.
- **Q3 (Daniel)** Confirm employment `status` and `employmentType` placeholder values (they are software placeholders, not legal classifications).

## 17. Findings during implementation and known limitations

**Defects found by the tests and fixed before completion** (recorded because they show where the tests earn their keep):

- A `401` from the session check itself notified the "session ended" listener, which re-ran the session check, which returned `401`, in an endless request loop. Found when the browser-level session test ran out of memory. The session check is now marked and does not notify.
- After a mid-session `401` the cached session data was still present, so the app kept showing the signed-in view. A `401` now takes precedence.
- Focus did not move to the page heading when navigating to a page that starts in a loading state, and moving focus on first load skipped the skip link. Focus now goes to the first heading that appears after a real navigation only.
- Two problems only a real browser can see: the record page had two landmarks named "Employment", and the links in the error summary were smaller than the 24 px target size of WCAG 2.2.

**Known limitations** (none blocks the Phase 1 goal; each is a candidate for later work):

- Docker Compose, `e2e.yml` and `ci.yml` have not been executed on Docker or GitHub (R2). Everything they run was run locally.
- Accessibility: automated checks and a keyboard-only journey, but **no manual screen-reader pass** and browser tests run in Chrome only.
- **Resolved in Codex review:** manager selection now searches and pages employment candidates server-side; there is no first-100 people cutoff.
- **Resolved in Codex review:** people and manager search fold accents through PostgreSQL unaccent, preserving stored spelling. Folded substring matching still scans tenant candidates; large-tenant performance is unmeasured.
- **Improved in Codex review:** the workspace header, simplified directory columns, and single-column forms reflow at small widths; 320px browser checks pass.
- Remaining security items: row-level security, rate limiting, and auditing history *reads*. Sessions are in memory. The review added a 64 KiB API write-body cap and prepared dependency/secret workflows; hosted execution/settings remain unverified. See [SECURITY.md](SECURITY.md).
- The browser suite needs the seeded development database and adds fictional people on each run; it is not self-contained.
- Employment status and type values are software placeholders, not legal classifications; nothing depends on them legally.

## 18. Codex Phase 1 review follow-up

Implementation notes were recorded in the review before changes. No new business module, database table, or application dependency was introduced. The bundled PostgreSQL extension is a deployment requirement, documented in [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).

- Migration V6 installs `unaccent` in `workforce`; NFC normalization plus accent/case folding applies consistently to query/count and manager lookup. Fold query text before wildcard escaping. No stored-name rewrite or misleading immutable function wrapper.
- Manager candidates page employments rather than arbitrarily choosing the first employment of the first 100 people. The native-control picker debounces 300ms, pages 20, and retains off-page selections.
- Manager graph mutations take a tenant-scoped transaction advisory lock before cycle validation; optimistic resource versions still apply. See [ADR-0023](adr/0023-manager-graph-concurrency.md), a Proposed record of the Phase 1 bug fix.
- Edit forms retain their record/version snapshot across background refresh. Explicit reload replaces the draft; reload failure preserves it.
- API write bodies have a 64 KiB bound, including unknown length; framework web/security/JDBC logging defaults to INFO; logout uses the framework redirect through a CSRF-protected native form.
- The compact workspace layout, grouped record rows/timeline, restrained native form controls, dark appearance, and responsive layout are documented in [UI_SYSTEM.md](UI_SYSTEM.md).
- Required create / optional patch idempotency and 24-hour expiry are unchanged. Replay returns the currently authorized resource represented by the saved reference, not a historical response snapshot. Expiry ends receipt-based retry protection; no perpetual exactly-once guarantee exists.
- CI security and Dependabot configuration is prepared; no hosted workflow/settings verification is claimed.

Final verification: **155 backend (60 unit/architecture, 95 integration), 94 frontend, 15 Chrome E2E**; typecheck, lint, production build pass. npm audit reported zero advisories; Gitleaks source scan found none. No Git history exists in this workspace. Remaining limits and recommendations are in [review sections 18-22](PHASE_1_CODEX_REVIEW.md#18-test-results). Q1-Q3 remain Daniel's decisions; the review recommends ACCEPT / AMEND / ACCEPT respectively.
