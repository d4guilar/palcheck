# Architecture Decision Records

Format, from the brief:

```
# ADR-XXXX: Title
Status: Proposed / Accepted / Superseded
Date:
Context:
Decision:
Alternatives Considered:
Consequences: Positive / Negative
Reconsider When:
Related Decisions:
```

Rules ([AGENTS.md](../../AGENTS.md) section 7): significant decisions only; only Daniel moves an ADR from Proposed to Accepted; Accepted ADRs override agent preference; to change an Accepted ADR, write a new one that
supersedes it. Every ADR answers the brief's eight questions: what problem, why this, alternatives, tradeoffs, what would change it, operational burden, does complexity justify itself, can Daniel explain it in an interview.
Each ADR has an **Interview line**, a one-sentence answer Daniel could give.

**Status legend (2026-09-19).** `Proposed` until Daniel says otherwise. `Accepted in part` means Daniel approved specific decisions of an ADR; each such ADR carries an **Acceptance record** table with per-decision status (`ACCEPTED`, `PROVISIONAL` = followed by Phase 1 pending explicit confirmation, `Proposed`). ADRs 0001 to 0017 remain Proposed except where an amendment or implementation note records an accepted refinement or how Phase 1 built it; original reasoning is preserved everywhere. Daniel opened **Phase 1: Authenticated Workforce Foundation** on 2026-09-19 ([../PHASE_1_IMPLEMENTATION_PLAN.md](../PHASE_1_IMPLEMENTATION_PLAN.md)).

| # | Title | Status | Notes |
|---|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith | Proposed | 7 modules + 2 deferred; rules for module boundaries |
| [0002](0002-java-spring-boot.md) | Java and Spring Boot | Proposed | Partly a career-goal decision, stated honestly |
| [0003](0003-postgresql-and-tenancy.md) | PostgreSQL and tenant isolation | Proposed | Includes the multi-tenancy decision the brief omitted |
| [0004](0004-hris-adapter-boundary.md) | HRIS adapter boundary and translation pipeline | Proposed | Adapter, mapper, `NormalizedWorker`, capability flags |
| [0005](0005-source-of-truth-ownership.md) | Source-of-truth ownership and provenance | Proposed | Ownership policy vs. provenance |
| [0006](0006-event-driven-integration.md) | Event-driven integration semantics | Proposed | At-least-once, idempotent consumers, internal vs. published events |
| [0007](0007-transactional-outbox.md) | Transactional outbox | Proposed | |
| [0008](0008-broker-none-yet.md) | Broker: none yet | Proposed | Kafka vs. Redpanda vs. none; exit criteria |
| [0009](0009-frontend-architecture.md) | Frontend architecture | Proposed | React + TypeScript, headless components, i18n |
| [0010](0010-ui-principles-and-accessibility.md) | UI principles and accessibility commitments | Proposed | Short by design; details in UI_SYSTEM.md |
| [0011](0011-country-policy-architecture.md) | Country policy architecture | Proposed | Capability-based packs; second-instance rule |
| [0012](0012-legal-concept-registry.md) | Legal Concept Registry | Proposed | Five entities; content as files |
| [0013](0013-authentication.md) | Authentication | Proposed | OIDC resource server; provider deferred |
| [0014](0014-authorization.md) | Authorization | Proposed | Permission x scope |
| [0015](0015-data-lineage.md) | Data lineage | Proposed | Computed view, not per-value storage |
| [0016](0016-operations-model.md) | Enterprise operations model | Proposed | Derived from real flows; phased |
| [0017](0017-primary-product-mode.md) | Primary product mode: standalone-first | Proposed | Daniel's decision; recorded with mitigations |
| [0018](0018-module-transactions-and-persistence.md) | Module transactions and persistence ownership | Accepted in part | Codex: resolve transaction/DAG conflicts, tenant references and migration ordering |
| [0019](0019-durable-delivery-and-effect-identity.md) | Durable delivery and business effect identity | Accepted in part | Codex: fan-out, operation identity, ambiguous recovery and replay |
| [0020](0020-foundation-first-delivery-plan.md) | Workforce foundation before provisioning | Accepted in part | Codex: narrower first milestone, build/persistence defaults and browser sessions |
| [0021](0021-registry-history-and-behavior-gates.md) | Registry history and behavior eligibility | Accepted in part | Codex: historical expiry versus withdrawal and unavailable coverage |
| [0022](0022-phase1-technical-baseline.md) | Phase 1 technical baseline | Proposed (implemented; confirmation requested) | Claude: JDBC, packages + ArchUnit, backend OIDC session, revision/audit/idempotency design, RLS deferred, embedded PostgreSQL for tests. Has an implementation-outcome section |
| [0023](0023-manager-graph-concurrency.md) | Serialize manager graph validation within a tenant | Proposed | Implemented Phase 1 race fix; owner acceptance pending |

## Second engineering review

[CODEX_ARCHITECTURE_REVIEW.md](../CODEX_ARCHITECTURE_REVIEW.md) assesses all original ADRs and classifies them KEEP / MODIFY / DEFER, with REMOVE decisions for unnecessary adoption candidates. ADRs 0018–0021 propose amendments to the earlier **Proposed** records; none is automatically Accepted or Superseded. Daniel's decisions D1–D5 were given in his Phase 1 opening message as six approved refinements (recorded in each ADR's acceptance record); the baseline documents were reconciled on 2026-09-19. Original reasoning is preserved.

## Deviations from the brief's candidate list

- **0003** is broadened to include tenant isolation, which the brief never decided but which cannot be retrofitted.
- **0017** is new: the standalone-vs-overlay driver is architecturally significant and was decided in this session.
- The brief's candidate **0010 "UI Design Philosophy"** is kept but short; the substance lives in [UI_SYSTEM.md](../UI_SYSTEM.md).
- 0013 and 0014 are kept separate because authentication is *deferred* while the authorization model is *decided now*.
- If any ADR seems trivial to Daniel, merge it into a neighbor rather than padding it.

## Not yet decided (no ADR until there is something to decide)

Contract-first vs. code-first APIs (Phase 1 is code-first with the API table in the plan; revisit when a second client or published events exist); content file format for the registry; identity provider product for production; hosting and deployment model; how Person deletion, retention and erasure work; whether and when to adopt row-level security.

**Settled by Phase 1** (recorded in [ADR-0022](0022-phase1-technical-baseline.md), pending Daniel's confirmation): build tool (Maven), Spring Modulith vs. ArchUnit (ArchUnit), message-format library for UI strings (`react-intl`), browser token handling (backend session).
