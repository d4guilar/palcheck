# ADR-0022: Phase 1 Technical Baseline

**Status:** Proposed (implemented provisionally by Phase 1 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-19

## Context

Daniel opened Phase 1 (authenticated workforce foundation) and asked Claude to decide, before implementing, whether Spring Modulith is worthwhile, whether an accepted ADR already chose an identity approach, and how to build a small, defensible vertical slice. ADR-0020 decisions 4 to 7 (Maven and packages with ArchUnit, Spring JDBC, backend OIDC session, local PostgreSQL plus a development identity provider) are provisional, and ADR-0003 leaves row-level security as a spike. No accepted ADR fixes these. The development machine has JDK 17, Node 24, PostgreSQL 17 as a local service, but **no container runtime** (Docker was not found), which affects the test-database and identity-provider choices.

## Decision

1. **Build and structure.** One Maven build with the Maven wrapper; ordinary Java packages as modules (`shared`, `platform`, `access`, `audit`, `workforce`); boundaries enforced by ArchUnit tests. **Spring Modulith is not adopted**: its event publication registry answers the deferred publication question (none in Phase 1), and package rules plus ArchUnit already verify the dependency direction. Revisit at the provisioning milestone against the crash and ambiguity cases in ADR-0019.
2. **Framework baseline.** Spring Boot 4.1.x on Java 17 (the installed LTS). Spring Boot 3.5 was not chosen for a new project because it is at or past open-source end of support in this timeline.
3. **Persistence.** Spring JDBC (`JdbcClient`) with explicit SQL repositories; no JPA. Rationale: every statement must show its tenant filter and version guard, audit must join the same transaction visibly, temporal and constraint-heavy SQL stays readable, and there are no lazy-loading or proxy surprises. Cost: hand-written mapping.
4. **Validation.** Field rules live in the domain (one implementation shared by create and patch) and produce `FieldViolation`s; Bean Validation is not used for field rules.
5. **Concurrency and history.** Per-row `version` with `expectedVersion` in the request body (`409 CONFLICT`); current-state tables authoritative; append-only `audit_entry` written by an `AuditRecorder` that requires an existing transaction; assignment carries effective dates so job changes can later close and open rows, but Phase 1 edits it in place.
6. **Command idempotency.** `Idempotency-Key` receipts in a technical `platform.idempotency_record` table, reserved inside the business transaction (concurrency-safe by unique index), scoped to tenant + principal + operation + key, with 24-hour expiry. Required on create, optional on patch. This is **command idempotency only**; business-operation identity for downstream effects (ADR-0019 decision 3) is accepted but not built.
7. **Authentication.** Spring Security `oauth2Login` with a same-origin, backend-managed session and CSRF protection; no browser-held tokens. The identity provider is Keycloak in development, behind standard OIDC configuration. Tenant and roles come from a trusted server-side `principal` mapping on `(issuer, subject)`, never from the request. Resource-server (bearer) support is not configured until a non-browser client exists.
8. **Authorization.** Permission checks in application services against an `Actor` that only `access` constructs; roles are code-defined bundles of permissions; tenant-wide scope only, with the scope carried in the type so narrower scopes extend it.
9. **Tenant isolation.** Application-level enforcement plus tenant-qualified composite foreign keys plus negative tests on every endpoint. **Row-level security is deferred**, not rejected.
10. **Test database.** Real PostgreSQL binaries started in-process (`zonky embedded-postgres`) instead of Testcontainers, because no container runtime is available on the development machine. `mock-oauth2-server` provides a real OIDC provider for the login-flow test. Testcontainers remains the preferred choice wherever a container runtime is standard (it is available on GitHub-hosted runners); the test-database access is isolated behind one support class so switching is local.
11. **Frontend.** React + TypeScript + Vite; native controls first; plain CSS custom properties as tokens; `react-intl` for externalized messages; TanStack Query for server state; no component library.
12. **Logging.** Spring Boot structured logging (ECS JSON) with MDC (`requestId`, `correlationId`, `tenantId`, `userId`) and SLF4J key/value pairs; personal values are never logged; enforced by a canary test.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Spring Modulith now | Adds a dependency for verification the ArchUnit tests already provide and for a publication registry with no Phase 1 use |
| JPA / Spring Data JDBC | JPA hides tenant filters and version guards behind an ORM and adds proxy semantics; Spring Data JDBC is a fair middle path but would add a second query style once explicit SQL is needed for search, paging and history |
| Browser-held tokens (SPA as public client) with a resource server | Valid, but exposes tokens to JavaScript in a data-sensitive product and adds a token lifecycle to get right; deferred until a bearer client exists |
| Implementing a small identity provider (Spring Authorization Server) | Review found this is not a shortcut to a finished provider; Keycloak is a maintained standard and stays a development detail |
| Row-level security now | Strong backstop, but needs a non-superuser runtime role, per-transaction tenant binding on pooled connections and pool-reuse tests; disproportionate for the slice |
| Testcontainers now | Preferred by Daniel "if feasible"; not feasible here (no container runtime) |
| H2 for tests | Rejected by review: cannot prove PostgreSQL constraint, trigger, concurrency or transaction semantics |
| Spring Boot 3.5 | Out of open-source support in this timeline |

## Consequences

**Positive:** every tenant filter, version check and audit write is visible in code and testable; a small dependency set; login and tenant mapping are testable without Docker; the design matches accepted ADR-0018/0019 boundaries so future messaging, integrations and provisioning add packages rather than rework.

**Negative:** hand-written SQL and mapping; ArchUnit rules protect boundaries only at test time; embedded PostgreSQL is a single pinned version; Compose and the Keycloak realm cannot be executed on this machine, so they carry an *unverified* label until run by Daniel; per-request principal lookup and in-memory sessions are simple but not scaled.

**Operational burden:** a JVM and Node toolchain, PostgreSQL, Keycloak; Flyway migrations; session state per instance.

## Reconsider When

Provisioning begins (compare Modulith with the explicit outbox); a bearer-token client appears; query complexity or reference-data joins make JDBC mapping costly; a container runtime becomes standard (switch tests to Testcontainers); real customer data approaches (row-level security, retention, erasure, encryption become blocking); sessions need to survive restarts or scale out.

## Implementation outcome (2026-09-19)

Every decision above was implemented as written. What building and testing it showed:

- **Decisions 3, 5, 6, 9 held up.** Explicit SQL made every tenant filter and version guard reviewable; the integration tests (real PostgreSQL) caught real races (concurrent same-key requests, concurrent edits, the unique-email race) that an in-memory database could not. Cost: hand-written SQL and row mapping (about 600 lines in `workforce/infrastructure`).
- **Decision 7 (backend session).** Works end to end against a real Keycloak (verified by browser tests) and against a mock OIDC server in the integration suite. The browser must use the Vite origin (`localhost:5173`) in development so the session stays same-origin; the realm allows only `5173` and `8080` redirect URIs.
- **Decision 10 (embedded PostgreSQL).** 92 integration tests run without a container runtime (about 24 seconds of summed test time on the development machine, plus a one-time download of the PostgreSQL binaries). The support class isolating the database is unchanged, so a switch to Testcontainers is still local.
- **Decision 11 (frontend).** Native controls plus a small token-based stylesheet passed a real-browser axe scan at WCAG 2.2 AA (including contrast and target size) after two fixes (a duplicate landmark name and small error-link targets). Tokens have an automated contrast test.
- **Decision 12 (logging).** ECS JSON is the default; the `dev` and `test` profiles switch to readable text. Observed on a running instance: `requestId`, `correlationId`, `tenantId`, `userId`, `operation` and `result` as fields, route templates instead of paths, and no tokens, cookies or names.
- **Not adopted, as planned:** Spring Modulith, JPA, Bean Validation for field rules, Testcontainers, row-level security.
- **Still unverified:** `docker-compose.yml` and the GitHub workflows, because no container runtime or CI run was available. Everything they run was run locally. This changes nothing in the decision; it is a verification gap, tracked in the plan (R2).
- **Awaiting Daniel:** explicit confirmation of decisions 3, 7, 9 and 10 (plan questions Q1 to Q3). Status stays Proposed until then.

## Interview line

"I chose the smallest set of tools that makes tenant isolation, optimistic concurrency and atomic audit visible in the code: explicit SQL, one transaction per command, an OIDC session held server-side, and tests against a real PostgreSQL."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0002](0002-java-spring-boot.md) · [ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0013](0013-authentication.md) · [ADR-0014](0014-authorization.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0019](0019-durable-delivery-and-effect-identity.md) · [ADR-0020](0020-foundation-first-delivery-plan.md) · [Phase 1 plan](../PHASE_1_IMPLEMENTATION_PLAN.md)
