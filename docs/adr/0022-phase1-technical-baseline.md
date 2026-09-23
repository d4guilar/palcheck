# ADR-0022: Phase 1 Technical Baseline

**Status:** Accepted in part (Daniel Aguilar, 2026-09-21): Q1 choices in decisions 1, 3, 7, 9 and 10 are ACCEPTED; decision 6 is ACCEPTED WITH AMENDMENT through Q2; Q3 software/domain placeholder vocabulary is ACCEPTED as recorded below; remaining choices retain their prior status.
**Date:** 2026-09-19

## Q1 acceptance record (2026-09-21)

Daniel explicitly accepted Q1 as written in [the implementation plan](../PHASE_1_IMPLEMENTATION_PLAN.md#16-risks-and-open-questions): **Spring JDBC over JPA, backend OIDC session over browser-held tokens, Maven with packages and ArchUnit instead of Spring Modulith, embedded PostgreSQL for tests while no container runtime is available, row-level security deferred.**

| Decision / Q1 scope | Status | Implementation checked on 2026-09-21 |
|---|---|---|
| 1: Maven, ordinary packages and ArchUnit; no Spring Modulith | **ACCEPTED** | `backend/pom.xml`, Maven wrapper, package layout and `ArchitectureTest` |
| 3: Spring JDBC over JPA | **ACCEPTED** | JDBC dependency and `JdbcClient` repositories with explicit tenant predicates; no JPA dependency |
| 7: backend OIDC session over browser-held tokens | **ACCEPTED** | `SecurityConfig.oauth2Login`, CSRF/session handling and the same-origin Vite proxy; existing `OidcLoginFlowIT` |
| 9: defer RLS for Phase 1 | **ACCEPTED** | Migrations contain no RLS policies; tenant-scoped repositories, composite foreign keys, `TenantIsolationIT` and `WorkforceSchemaIT` provide application/constraint enforcement |
| 10: embedded PostgreSQL tests while no container runtime is available | **ACCEPTED** | `io.zonky.test:embedded-postgres` and shared `TestPostgres` support start real PostgreSQL; no H2 or Testcontainers dependency |

Acceptance changes decision status, not runtime behavior. No implementation or test changes are required. The container-runtime condition and reconsideration triggers remain; RLS is deferred, not permanently rejected. The backend session choice keeps OAuth tokens out of application JavaScript; standard provider logout still uses a browser redirect with an ID-token hint.

**Scope boundary:** Q1 did not accept decision 6; Daniel separately accepted it WITH AMENDMENT through Q2 on 2026-09-21 as recorded below. Employment vocabulary is separately accepted through Q3 below. Decisions 2, 4, 5, 8, 11 and 12 and ancillary provider/tool details are not newly accepted by these resolutions; any earlier accepted constraints they reference still apply. Q1 also does not accept ADR-0018's unrelated provisional decisions, ADR-0020's remaining preconditions/local-environment details, or ADR-0023. Phase 1 completion and Phase 2 authorization remain separate.

## Q2 acceptance with amendment (2026-09-21)

**Decision 6: ACCEPTED WITH AMENDMENT by Daniel.** Required-on-create and optional-on-patch keys retain their current tenant + principal + operation namespace. The 24-hour period is a **guaranteed retry window**. During that window, equivalent requests replay the same logical result without another mutation; materially different payloads under the same scoped key return 422 IDEMPOTENCY_CONFLICT. Current-resource reconstruction and current authorization remain the response contract.

After the window, original receipt availability is no longer guaranteed. Removing a receipt does not remove the original business effect, prove it never occurred, or make a repeat safe/equivalent. Clients must reconcile uncertain outcomes before another business action. No permanent deduplication or distributed idempotency platform is implied.

The 24-hour default is a product tradeoff: predictable next-day recovery from interruptions versus bounded receipt storage. It is not a general distributed-systems rule. `palcheck.idempotency.ttl` remains the compatible setting, documented in application.yml and [the API contract](../PHASE_1_IMPLEMENTATION_PLAN.md#guaranteed-retry-window-api-contract-and-configuration). It can be lengthened without schema changes; startup rejects values below the accepted 24-hour minimum. Stored `expires_at` values remain authoritative for existing receipts when configuration changes.

**Implementation correction:** complete the original receipt with a full window from write completion, rather than spending the window while the write executes; reject configuration below 24 hours. Replays never reset the deadline. Database column/index names describe retention accurately and are retained; the Java duration is named `guaranteedRetryWindow`. Existing migration V5 is immutable and unchanged. Real-PostgreSQL clock-controlled tests cover the deadline, post-window non-equivalence, completion delay, namespaces, collisions and duplicate races. Q3 is a separate acceptance below; Phase 2 remains unauthorized.

## Q3 acceptance record (2026-09-21)

**ACCEPTED by Daniel.** The plan's exact question was: "Confirm employment `status` and `employmentType` placeholder values (they are software placeholders, not legal classifications)." The existing values are `PENDING`, `ACTIVE`, `ENDED` for status and `FULL_TIME`, `PART_TIME` for employment type.

These are **SOFTWARE / DOMAIN PLACEHOLDERS** supporting Phase 1 workforce behavior. They are not complete or legally authoritative Costa Rican employment categories, must not drive statutory calculations, and imply no legal eligibility or compliance. Core software vocabulary and country-specific legal vocabulary remain separate. Future verified research may require richer local classifications, mappings, additional metadata, country-policy concepts or different terminology; no legal equivalence is inferred and no replacement taxonomy is invented now.

**Why this choice:** keep a small, usable record lifecycle and classification while legal research remains unverified. Treating these labels as legal categories would assert unsupported meaning; removing them would remove useful Phase 1 software behavior. The tradeoff is that future researched requirements may need explicit mapping or a deliberate migration. Revisit WC-1/WC-4, working-time and termination research during authorized Costa Rica policy work. No new runtime component or operational burden is introduced.

**Implementation checked:** Java enums, EmploymentData validation, database constraints, API parsing/serialization, TypeScript types, creation/editing forms, directory/status tags and history labels use the same values. `ENDED` requiring an end date is a software integrity rule. No statutory calculation, eligibility or compliance behavior is attached. Values, schema and business logic are unchanged; enum/type comments, API/domain/localization documentation and the existing employment-type form hint clarify the boundary. Existing creation/editing tests now verify the hint is accessible to the corresponding control. Legal research status and unrelated ADR decisions remain unchanged.

## Context

Daniel opened Phase 1 (authenticated workforce foundation) and asked Claude to decide, before implementing, whether Spring Modulith is worthwhile, whether an accepted ADR already chose an identity approach, and how to build a small, defensible vertical slice. At the original decision date, ADR-0020 decisions 4 to 7 and ADR-0003's RLS spike left these choices unresolved. The Q1 acceptance record above now settles Maven/packages/ArchUnit, JDBC, backend OIDC sessions, embedded PostgreSQL testing and RLS deferral. Local-environment details outside Q1 retain their earlier status. The development machine has JDK 17, Node 24, PostgreSQL 17 as a local service, but **no container runtime** (Docker was not found), which affects the test-database and identity-provider choices.

## Decision

1. **Build and structure.** One Maven build with the Maven wrapper; ordinary Java packages as modules (`shared`, `platform`, `access`, `audit`, `workforce`); boundaries enforced by ArchUnit tests. **Spring Modulith is not adopted**: its event publication registry answers the deferred publication question (none in Phase 1), and package rules plus ArchUnit already verify the dependency direction. Revisit at the provisioning milestone against the crash and ambiguity cases in ADR-0019.
2. **Framework baseline.** Spring Boot 4.1.x on Java 17 (the installed LTS). Spring Boot 3.5 was not chosen for a new project because it is at or past open-source end of support in this timeline.
3. **Persistence.** Spring JDBC (`JdbcClient`) with explicit SQL repositories; no JPA. Rationale: every statement must show its tenant filter and version guard, audit must join the same transaction visibly, temporal and constraint-heavy SQL stays readable, and there are no lazy-loading or proxy surprises. Cost: hand-written mapping.
4. **Validation.** Field rules live in the domain (one implementation shared by create and patch) and produce `FieldViolation`s; Bean Validation is not used for field rules.
5. **Concurrency and history.** Per-row `version` with `expectedVersion` in the request body (`409 CONFLICT`); current-state tables authoritative; append-only `audit_entry` written by an `AuditRecorder` that requires an existing transaction; assignment carries effective dates so job changes can later close and open rows, but Phase 1 edits it in place.
6. **Command idempotency.** `Idempotency-Key` receipts in a technical `platform.idempotency_record` table, reserved inside the business transaction (concurrency-safe by unique index), scoped to tenant + principal + operation + key, with a **guaranteed retry window** of at least 24 hours from original write completion (Q2 ACCEPTED WITH AMENDMENT). Receipt availability after that window is not guaranteed and does not establish safe/equivalent repetition. Required on create, optional on patch. This is **command idempotency only**; business-operation identity for downstream effects (ADR-0019 decision 3) is accepted but not built.
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
- **Owner resolution (2026-09-21):** Q1 choices in decisions 1, 3, 7, 9 and 10 are ACCEPTED. Q2 is ACCEPTED WITH AMENDMENT; Q3 is ACCEPTED as software/domain placeholders. See the separate scoped acceptance records above.

## Interview line

"I chose the smallest set of tools that makes tenant isolation, optimistic concurrency and atomic audit visible in the code: explicit SQL, one transaction per command, an OIDC session held server-side, and tests against a real PostgreSQL."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0002](0002-java-spring-boot.md) · [ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0013](0013-authentication.md) · [ADR-0014](0014-authorization.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0019](0019-durable-delivery-and-effect-identity.md) · [ADR-0020](0020-foundation-first-delivery-plan.md) · [Phase 1 plan](../PHASE_1_IMPLEMENTATION_PLAN.md)
