# ADR-0020: Workforce Foundation Before Provisioning

**Status:** Accepted in part (Daniel Aguilar, 2026-09-19): decisions 1 to 3 and 8 accepted; decisions 4 to 7 followed provisionally by Phase 1, confirmation requested
**Date:** 2026-09-19

## Acceptance record (2026-09-19, recorded by Claude on Daniel's instruction)

Daniel approved refinement 6 (smaller implementation milestones) and opened **Phase 1: Authenticated Workforce Foundation**. Phase 1 is explicitly *not* payroll, Workday integration, event broker infrastructure, provisioning, incident management, integration topology, full localization, legal automation, or an observability platform.

| Decision | Status | Note |
|---|---|---|
| 1 | **ACCEPTED** | Phase 1 scope is the authenticated workforce foundation. Daniel's message refines it (Person, Employment, Assignment, Legal Entity, Org Unit; create/view/edit; audit history; optimistic concurrency; command idempotency). |
| 2 | **ACCEPTED** | Outbox runtime, separate lifecycle/operations modules, destination simulators, HRIS sync and registry projection/UI are deferred. |
| 3 | **ACCEPTED** | Foundation, then provisioning, then overlay. Numbering of the later milestones is Daniel's to assign; existing gates are not renumbered here. |
| 4 | Provisional | Followed: one Maven build with a wrapper, ordinary Java packages with ArchUnit tests, **Spring JDBC** with explicit repositories. Spring Modulith and JPA are not used. Recorded in [ADR-0022](0022-phase1-technical-baseline.md). |
| 5 | Provisional | Followed: same-origin OIDC login with a backend-managed session (Spring Security), CSRF protection, no browser-held tokens. Recorded in ADR-0022. |
| 6 | Provisional | Followed with decisions instead of open-ended spikes; row-level security is **deferred** (see ADR-0022). |
| 7 | Provisional | Followed: PostgreSQL + Keycloak (development identity provider) via Docker Compose; backend and frontend run on the host. |
| 8 | **ACCEPTED** | Legal gates intact; no invented rules; no sensitive fields added for demonstration; synthetic record IDs are application IDs, not national IDs. |

## Context

Claude's Phase 1 includes seven modules, temporal processing, three destinations/idempotency modes, operations, a registry projection and four screens. [Review SCP-01](../CODEX_ARCHITECTURE_REVIEW.md) recommends separating foundational security/data correctness from that portfolio milestone. Daniel's standalone-first direction is unchanged. Only Daniel can amend phase scope or open Phase 1.

## Decision

1. Propose Phase 1 as an authenticated workforce foundation: access/workforce/audit, minimal Person/Employment/Assignment and Legal Entity/Org Unit references, tenant-scoped read/edit/audit, valid dates/revisions, default `PALCHECK` governed-field policy, atomic audit/command receipts, synthetic data, logs/health and an accessible People record/view/edit flow.
2. Defer outbox runtime, separate lifecycle/operations modules, destination simulators, future-effective dispatch, HRIS sync and registry projection/UI until their own milestone. Do not create empty infrastructure or provider/country implementations.
3. Follow foundation with a provisioning milestone proving independent destination failure, safe retry and crash recovery under ADR-0019. Follow that with the overlay milestone proving checkpoint-safe sync and ownership cutover. Daniel chooses whether these are Phase 1B/2 or another numbering; existing roadmap gates are not changed by this proposal.
4. Recommend one Maven build, ordinary Java packages and ArchUnit tests; Spring JDBC with explicit repositories for the initial persistence scope. Reconsider mapping tooling only with demonstrated pain. Versions and compatibility are chosen after the phase opens.
5. Prefer same-origin OIDC login with backend-managed browser sessions in the same application, rather than requiring browser-held tokens. External identity provider remains responsible for authentication/passwords. Distinguish OIDC login from optional later OAuth2 resource-server support for bearer API clients. CSRF/cookie/logout/revocation behavior is part of acceptance, not deferred hardening.
6. Run only focused preconditions: temporal/history integrity and tenant/RLS before persistence, authentication/session before authenticated UI. Outbox evaluation waits for provisioning; registry loader/file-format work waits for actual content delivery. No mandatory comparative experiment for every build/UI tool.
7. Keep PostgreSQL + one development IdP as local dependencies; backend/frontend run on the host first. Add GitHub Actions with actual code, tests and builds. No broker, dashboard stack or full mock environment to load People.
8. Keep the legal research track independent and all gates intact. Do not add sensitive fields or invented legal rules for demonstration. Phase 1 synthetic record IDs are application IDs, not national IDs.

Detailed scope, exclusions and acceptance scenarios: [review §15](../CODEX_ARCHITECTURE_REVIEW.md#15-recommended-phase-1-scope).

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Original hire-to-provision phase unchanged | Strong portfolio demonstration but a large first acceptance boundary. |
| Empty backend/frontend shells only | Small but proves neither data integrity nor authenticated useful behavior. |
| Foundation with auth/tenancy deferred | Fast apparent progress, expensive and risky retrofit. |
| Keep all eight spikes first | Delays usable flow with comparisons for features not yet needed. |
| SPA tokens + resource server | Valid alternative; requires a careful browser token lifecycle. Session direction simplifies the first same-origin application but requires CSRF and session management. |
| JPA everywhere / jOOQ immediately / multi-module build | Viable if future requirements justify their conventions; unnecessary default choices for the first slice. |

## Consequences

**Positive:** smaller observable milestone, early integrity/security evidence, simpler startup and debugging. Future architecture is constrained by real behavior rather than empty scaffolding.

**Negative:** resilience and localization UI appear later; less immediate portfolio breadth. Explicit JDBC entails mapping work; package boundaries rely on tests. Sessions create server state and single-instance restarts may log users out until persistence is justified.

**Operational burden:** maintain JVM/Node builds, database and IdP; test login/isolation, migrations and restore instructions. Complexity is justified because each part supports the one delivered flow. No runtime component exists solely to signal future scale.

## Reconsider When

Daniel prioritizes the complete resilience demonstration as the first release, the minimal data model cannot represent researched needs, browser/API clients require a different auth model, or mapping/build maintenance outweighs these defaults.

## Interview line

"I proved authenticated tenant-isolated workforce changes and audit first, then introduced durable integrations in a separate milestone with explicit failure tests."

## Related Decisions

[0001](0001-modular-monolith.md) · [0002](0002-java-spring-boot.md) · [0009](0009-frontend-architecture.md) · [0011](0011-country-policy-architecture.md) · [0012](0012-legal-concept-registry.md) · [0013](0013-authentication.md) · [0016](0016-operations-model.md) · [0017](0017-primary-product-mode.md) · [0018](0018-module-transactions-and-persistence.md) · [ROADMAP](../ROADMAP.md)
