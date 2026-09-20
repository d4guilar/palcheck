# ADR-0018: Module Transactions and Persistence Ownership

**Status:** Accepted in part (Daniel Aguilar, 2026-09-19); remaining decisions Proposed or Provisional, see the acceptance record
**Date:** 2026-09-19

## Acceptance record (2026-09-19, recorded by Claude on Daniel's instruction)

Daniel's Phase 1 opening message approved implementation-review refinements 1 (atomic business change + audit + durable publication intent) and 2 (outbox ownership must not belong to integrations; document the final dependency direction). Per-decision status:

| Decision | Status | Phase 1 consequence |
|---|---|---|
| 1 | **ACCEPTED** | One application command owns one local transaction; audit participates. Phase 1 implements **business state + audit atomically**. No durable publication is written because Phase 1 has no asynchronous effect; no meaningless events are invented. Module boundaries preserve the future model. |
| 2 | **ACCEPTED for Phase 1** | Record creation is a `workforce` command; there is no `lifecycle` module until it owns process data. |
| 3 | **ACCEPTED** | Durable publication is technical platform infrastructure (`messaging`), never owned by `integrations`; integrations consumes published events. **Not implemented in Phase 1.** |
| 4 | **ACCEPTED** | `workforce` and `access` may depend on `audit`'s recording API; `audit` never depends on `access` or `workforce`; authorized audit reads are composed by the calling application facade. |
| 5 | **PROVISIONAL** | Followed by Phase 1 under Daniel's database-ownership instruction (schemas per owner, tenant-qualified references, tenant-anchor foreign key, no cascading deletes). Explicit confirmation requested. |
| 6 | **PROVISIONAL** | One globally ordered Flyway stream, each migration declares its owner. Confirmation requested. |
| 7 | **PROVISIONAL** | Stable IDs, per-row `version` for optimistic concurrency, valid-date columns; audit is *evidence*, not a bitemporal query model. Confirmation requested. |
| 8 | **ACCEPTED** | No network I/O inside a business transaction; audit and (later) publication are never committed independently of the business change. |

The final dependency direction is documented in [../PHASE_1_IMPLEMENTATION_PLAN.md](../PHASE_1_IMPLEMENTATION_PLAN.md) section 3 and [../MODULES.md](../MODULES.md). Amends ADR-0001 rules 3 and 4 (transaction rule) and ADR-0007 decisions 1 and 2 (outbox ownership); the original reasoning in those ADRs is preserved.

## Context

[Review TX-01 and DAT-01](../CODEX_ARCHITECTURE_REVIEW.md) identify contradictions between the one-module transaction rule, atomic audit/outbox writes, and an integrations-owned outbox that workforce cannot call without a cycle. Extraction constraints also leave tenant-reference integrity and migration ordering underspecified. This proposal amends Proposed ADRs; it does not supersede an Accepted decision.

## Decision

1. Retain a modular monolith and owner-only table writes. One application command owns one local transaction. Explicit audit recording and durable event append participate in that transaction through narrow APIs using the same datasource/transaction manager.
2. Initial hire is a workforce application command; workforce publishes its committed business fact. Defer a separate lifecycle module until approvals, resumable process steps or independent process data justify it. A future process can explicitly coordinate local APIs in one transaction; remote waits are persisted steps outside that transaction.
3. Move durable-publication infrastructure to a technical `messaging` package that depends only on shared identifiers/envelopes and consumer ports, never workforce or integrations. Integrations consumes workforce API/event contracts. Typed event consumption is an explicit code dependency.
4. Access can depend on audit recording; audit storage does not depend on access. Authorized audit read facades compose the two. No hidden reciprocal dependencies.
5. Keep one PostgreSQL database and schemas for actual data owners. Permit tenant-qualified intra-module FKs and a documented cross-schema FK to the Tenant anchor; prohibit destructive cascades across owners. Other cross-module references require an explicit integrity/deletion policy. Modules read through owner APIs, not ad hoc cross-schema joins.
6. One globally ordered Flyway migration stream and history authority; each migration declares its owner. One deployment runner migrates before replicas start. Applied versioned migrations are immutable.
7. Preserve stable aggregate IDs, immutable revision identity, valid business-date ranges and recording metadata. Use optimistic revision checks. Evaluate active-timeline non-overlap constraints in the focused Phase 1 experiment. Do not present audit alone as bitemporal query support.
8. No network I/O inside a business transaction. No independently committed audit/outbox records for an atomic command. §5–6 of the review is the detailed implementation plan; [diagram 09](../diagrams/09-proposed-transaction-and-delivery.md) illustrates it.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Keep strict one-module transactions | Requires a saga even for atomic audit/hire/publication and contradicts the intended guarantee. |
| Put everything in integrations | Removes ownership clarity and keeps the workforce/integrations cycle. |
| Producer-specific outbox tables | Viable; multiple poll sources/migration conventions add work for this small monolith. Reconsider at extraction. |
| Merge all modules | Avoids cycles by removing meaningful boundaries; loses testing and ownership benefits. |
| No cross-module FKs anywhere | Makes even the Tenant anchor an application-only invariant; future extraction does not justify that current cost. |
| One public schema / separate database per module | First weakens namespace ownership; second destroys simple atomicity and adds operations. Neither needed now. |

## Consequences

**Positive:** the dependency DAG can be implemented as written; audit/publication atomicity is explicit; constraints protect tenant relationships; deployment ordering is deterministic. No additional runtime framework is required.

**Negative:** extraction requires moving publication infrastructure and replacing documented local transactions/FKs. Tests must enforce APIs, shared transaction participation and dependency direction. Schemas do not provide process isolation. Temporal correction semantics still require an experiment.

**Operational burden:** one database and migration runner; bounded history growth and retention design. Architecture tests plus real-database rollback/concurrency tests are required. This complexity is justified by integrity claims already present in the product.

## Reconsider When

A module actually needs independent deployment/security, history queries require full bitemporality, migration ownership spans independently released teams, or the messaging API grows domain-specific responsibilities.

## Interview line

"Modules own their data, but a local business command can atomically write its audit and durable publication; I isolated the publication mechanism so workforce never depends on its integrations."

## Related Decisions

[0001](0001-modular-monolith.md) · [0003](0003-postgresql-and-tenancy.md) · [0005](0005-source-of-truth-ownership.md) · [0006](0006-event-driven-integration.md) · [0007](0007-transactional-outbox.md) · [0014](0014-authorization.md) · [0016](0016-operations-model.md) · [0019](0019-durable-delivery-and-effect-identity.md) · [0020](0020-foundation-first-delivery-plan.md)
