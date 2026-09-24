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

**Status legend (updated 2026-09-21).** `Proposed` until Daniel says otherwise. `Accepted in part` means Daniel approved specific decisions of an ADR; each such ADR carries an **Acceptance record** or a scoped acceptance note with per-decision status (`ACCEPTED`, `PROVISIONAL` = followed by Phase 1 pending explicit confirmation, `Proposed`). ADRs 0001 to 0017 retain their baseline status except where an acceptance record or amendment says otherwise; Q1 acceptance is explicitly scoped in ADRs 0003, 0009, 0013, 0020 and 0022; original reasoning is preserved everywhere. Daniel opened **Phase 1: Authenticated Workforce Foundation** on 2026-09-19 ([../PHASE_1_IMPLEMENTATION_PLAN.md](../PHASE_1_IMPLEMENTATION_PLAN.md)).

| # | Title | Status | Notes |
|---|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith | Proposed | 7 modules + 2 deferred; rules for module boundaries |
| [0002](0002-java-spring-boot.md) | Java and Spring Boot | Proposed | Partly a career-goal decision, stated honestly |
| [0003](0003-postgresql-and-tenancy.md) | PostgreSQL and tenant isolation | Accepted in part | Q1 accepts Phase 1 RLS deferral; other baseline proposals unchanged |
| [0004](0004-hris-adapter-boundary.md) | HRIS adapter boundary and translation pipeline | Proposed | Adapter, mapper, `NormalizedWorker`, capability flags |
| [0005](0005-source-of-truth-ownership.md) | Source-of-truth ownership and provenance | Proposed | Ownership policy vs. provenance |
| [0006](0006-event-driven-integration.md) | Event-driven integration semantics | Proposed | At-least-once, idempotent consumers, internal vs. published events |
| [0007](0007-transactional-outbox.md) | Transactional outbox | Proposed | |
| [0008](0008-broker-none-yet.md) | Broker: none yet | Proposed | Kafka vs. Redpanda vs. none; exit criteria |
| [0009](0009-frontend-architecture.md) | Frontend architecture | Accepted in part | Q1 accepts backend OIDC session (decision 8); other proposals unchanged |
| [0010](0010-ui-principles-and-accessibility.md) | UI principles and accessibility commitments | Proposed | Short by design; details in UI_SYSTEM.md |
| [0011](0011-country-policy-architecture.md) | Country policy architecture | Proposed | Capability-based packs; second-instance rule |
| [0012](0012-legal-concept-registry.md) | Legal Concept Registry | Proposed | Five entities; content as files |
| [0013](0013-authentication.md) | Authentication | Accepted in part | Q1 accepts Phase 1 backend OIDC session; production provider remains open |
| [0014](0014-authorization.md) | Authorization | Proposed | Permission x scope |
| [0015](0015-data-lineage.md) | Data lineage | Proposed | Computed view, not per-value storage |
| [0016](0016-operations-model.md) | Enterprise operations model | Proposed | Derived from real flows; phased |
| [0017](0017-primary-product-mode.md) | Primary product mode: standalone-first | Proposed | Daniel's decision; recorded with mitigations |
| [0018](0018-module-transactions-and-persistence.md) | Module transactions and persistence ownership | Accepted in part | Codex: resolve transaction/DAG conflicts, tenant references and migration ordering |
| [0019](0019-durable-delivery-and-effect-identity.md) | Durable delivery and business effect identity | Accepted in part | Codex: fan-out, operation identity, ambiguous recovery and replay |
| [0020](0020-foundation-first-delivery-plan.md) | Workforce foundation before provisioning | Accepted in part | Codex: narrower first milestone, build/persistence defaults and browser sessions |
| [0021](0021-registry-history-and-behavior-gates.md) | Registry history and behavior eligibility | Accepted in part | Codex: historical expiry versus withdrawal and unavailable coverage |
| [0022](0022-phase1-technical-baseline.md) | Phase 1 technical baseline | Accepted in part | Q1 choices and Q3 software/domain placeholders ACCEPTED; decision 6 / Q2 ACCEPTED WITH AMENDMENT (guaranteed retry window), 2026-09-21; other choices retain their status |
| [0023](0023-manager-graph-concurrency.md) | Serialize manager graph validation within a tenant | Proposed | Implemented Phase 1 race fix; owner acceptance pending |
| [0024](0024-hris-adapter-boundary-phase2.md) | Provider-neutral HRIS adapter boundary (Phase 2) | **Accepted** (Daniel, 2026-09-22, via the Phase 3 opening message treating Phase 2 as complete and accepted) | Claude: new `integrations` module, `workforce.api` write port, package-private provider DTOs, normalized contract kept small |
| [0025](0025-ownership-lineage-and-sync-model-phase2.md) | Ownership granularity, lineage storage and sync transaction model (Phase 2) | **Accepted** (Daniel, 2026-09-22, same basis as ADR-0024) | Claude: entity-level ownership (Employment/Assignment, not Person or per-field), lineage as separate metadata, one transaction per synced worker, two-pass manager resolution |
| [0026](0026-broker-and-transactional-outbox.md) | Broker choice and transactional outbox (Phase 3) | Proposed | Claude: Kafka over Redpanda for this environment, `messaging` module owns the outbox and publisher, atomic claim-and-lease (not plain `FOR UPDATE SKIP LOCKED`), at-least-once stated explicitly; implemented and tested |
| [0027](0027-business-operation-identity-and-idempotent-consumers.md) | Business operation identity, idempotent consumers, retry, dead-letter and replay (Phase 3) | Proposed | Claude: operation key from the aggregate not the event, one-row operation state, DB-backed dead-letter, replay guarded against re-running a completed operation; implemented and tested |
| [0028](0028-domain-vs-integration-events.md) | Domain events, integration events, and the publication boundary (Phase 3) | Proposed | Claude: `EmploymentStarted` is the only Phase 3 published event; identifiers-only payload; no schema registry at one event type; implemented and tested |

## Second engineering review

[CODEX_ARCHITECTURE_REVIEW.md](../CODEX_ARCHITECTURE_REVIEW.md) assesses all original ADRs and classifies them KEEP / MODIFY / DEFER, with REMOVE decisions for unnecessary adoption candidates. ADRs 0018–0021 propose amendments to the earlier **Proposed** records; none is automatically Accepted or Superseded. Daniel's decisions D1–D5 were given in his Phase 1 opening message as six approved refinements (recorded in each ADR's acceptance record); the baseline documents were reconciled on 2026-09-19. Original reasoning is preserved.

## Deviations from the brief's candidate list

- **0003** is broadened to include tenant isolation, which the brief never decided but which cannot be retrofitted.
- **0017** is new: the standalone-vs-overlay driver is architecturally significant and was decided in this session.
- The brief's candidate **0010 "UI Design Philosophy"** is kept but short; the substance lives in [UI_SYSTEM.md](../UI_SYSTEM.md).
- 0013 and 0014 are kept separate because the production identity-provider product remains deferred while the Phase 1 backend OIDC session is accepted through Q1; authorization is a separate decision.
- If any ADR seems trivial to Daniel, merge it into a neighbor rather than padding it.

## Not yet decided (no ADR until there is something to decide)

Contract-first vs. code-first APIs (Phase 1 is code-first with the API table in the plan; revisit when a second client or published events exist); content file format for the registry; identity provider product for production; hosting and deployment model; how Person deletion, retention and erasure work; whether and when to adopt row-level security after Phase 1 (Phase 1 deferral is accepted through Q1); the full field-level, tenant-configurable ownership policy in [DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md) (ADR-0025 implements a narrower Phase 2 subset only); a real out-of-process mock HRIS server; a second provider adapter.

**ACCEPTED through Q1 (Daniel, 2026-09-21)** in [ADR-0022](0022-phase1-technical-baseline.md): Spring JDBC over JPA; backend OIDC session over browser-held tokens; Maven/packages/ArchUnit instead of Spring Modulith; embedded PostgreSQL tests while no container runtime is available; Phase 1 RLS deferral. The implemented `react-intl` choice is outside Q1 and is not newly accepted by this resolution.

## Phase 2 independent review (2026-09-22)

[PHASE_2_CODEX_REVIEW.md](../PHASE_2_CODEX_REVIEW.md) recommended ACCEPT for Q1 (entity ownership), Q2 (separate permissions bundled into HR_ADMIN) and Q3 (integrations-owned UI); Daniel's Phase 3 opening message (2026-09-22), which describes Phase 2 as complete and accepted, is treated as accepting all three, and ADR-0024/0025 are updated to **Accepted** on that basis. Their review addenda record provider isolation, preserved Person identity, manager/concurrency fixes and explicit metadata/crash limits.
