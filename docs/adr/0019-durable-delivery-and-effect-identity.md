# ADR-0019: Durable Delivery and Business Effect Identity

**Status:** Accepted in part (Daniel Aguilar, 2026-09-19); mechanics remain Proposed until the provisioning milestone
**Date:** 2026-09-19

## Acceptance record (2026-09-19, recorded by Claude on Daniel's instruction)

Daniel approved refinement 3 (business operation identity is separate from event identity) and refinement 4 (failure recovery must be explicit: partial imports, ambiguous delivery outcomes, reconciliation, retry, replay, duplicate delivery, manual recovery). These are **accepted architectural requirements, not Phase 1 implementation scope**.

| Decision | Status | Note |
|---|---|---|
| 1 | **ACCEPTED** (principle) | Domain events, integration events and technical publication records stay distinct concepts. Envelope details are decided when the first event is published. |
| 2 | Proposed | Durable fan-out mechanics are designed at the provisioning milestone. |
| 3 | **ACCEPTED** | A business operation has a stable logical identity (`operationKey` or equivalent) independent of event ID and schema version; duplicate deliveries of different event IDs for one business action must not cause duplicate downstream effects. **Phase 1 implements only command/request idempotency** (`Idempotency-Key` on workforce write APIs); it is not a distributed idempotency framework. |
| 4 to 9 | **ACCEPTED as requirements** | Fenced claims, ambiguity-aware recovery by destination capability (`KEY` / `LOOKUP` / `NONE`), bounded retry, ordered streams where needed, replay generations, and retention are binding requirements for any future integration work. They must be designed and tested with the provisioning and overlay milestones. |
| 10 | Proposed | Spring Modulith is compared against the same crash/ambiguity cases before dispatcher code is written; no second publication registry. |

Review section 7 (page-checkpoint barrier, missing records are reconciliation candidates and never inferred terminations, source-instance identity, ownership cutover) is likewise accepted as a **requirement for the overlay milestone**. See [../INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md). Amends ADR-0006 decision 3 (idempotency key derived from `(eventId, destination, operation)`) and ADR-0007 decision 1; original reasoning preserved.

> **Phase 1 Q2 clarification (2026-09-21):** [ADR-0022](0022-phase1-technical-baseline.md#q2-acceptance-with-amendment-2026-09-21) records a 24-hour guaranteed retry window for workforce write receipts. Beyond it, receipt availability is not guaranteed; it is not permission to repeat the business effect. This does not alter the accepted requirements below for future durable effect identity or authorize their implementation.

## Context

[Review EVT-01 and LIN-01](../CODEX_ARCHITECTURE_REVIEW.md) identify gaps in per-event deduplication, fan-out durability, lease recovery, frozen outbound data and projection replay. A database receipt cannot atomically commit with an external account or equipment order. Different event representations may describe the same business effect.

## Decision

1. Preserve internal domain events, versioned integration events and technical outbox records as distinct concepts. Add aggregate version and change identity to the envelope. Capture the subscriber set/routing revision when recording the publication.
2. Separate immutable publication, per-consumer delivery, append-oriented attempts, consumer receipt and destination operation. Idempotently materialize deliveries from the captured subscriber set before marking fan-out complete.
3. Consumer receipt and local effect/operation intent commit together. External operation identity is Tenant + destination instance + operation kind + business subject/occurrence, independent of event ID/schema version. Freeze the minimal request, version and hash once. Retries preserve both key and request.
4. Claim due work in a short transaction, use lease generation fencing, commit before I/O, and finalize only under the current generation. Fencing protects local state, not the remote effect. Record send intent before the network call; expired in-flight sends become ambiguous.
5. Recover by destination contract: `KEY` uses the same key within its retention guarantee; `LOOKUP` requires authoritative reconciliation, not an eventually consistent “not found”; `NONE` requires human resolution. Never translate lease expiry into unconditional resend.
6. Use per-destination bounded workers and policy-versioned retry records. Validation/configuration/authorization failures do not hot-loop. The review's §8 simulator policies are proposed engineering defaults and must not be represented as vendor behavior.
7. Where ordering is required, serialize Tenant + consumer + aggregate streams; failed/unknown predecessor work blocks later effects unless explicitly superseded safely. Timestamps and `SKIP LOCKED` alone do not enforce ordering.
8. Projection rebuilds use a new generation with separate deduplication state and exclude effect consumers. Effect repair is retry/reconcile of the existing operation. Dual event representations share fact/effect identity and must not trigger duplicate effects.
9. Retain safety receipts through the supported retry/replay horizon; reject unsafe replay after expiry. Payload retention is separate. Restored backups start with dispatch paused until external effects are reconciled. No outbox-as-permanent-event-store promise.
10. Introduce this machinery only with the provisioning milestone. Before writing it, compare the selected Spring Modulith release against the same crash/ambiguity acceptance cases; adopt it only if it replaces a mechanism, not as a second registry. Keep no broker until measured need.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Event-ID dedupe only | Handles message duplicates, not duplicated business facts or remote commit ambiguity. |
| Mark processed before the send | Crash loses the effect. |
| Mark processed only after the send | Crash can repeat the remote effect without destination protection. |
| Hold DB locks during remote calls | Adds latency, pool exhaustion and long locks without making the remote commit atomic. |
| Broker or distributed transaction now | Adds infrastructure and does not solve arbitrary external-system idempotency. |
| Generic durable workflow engine | Could own some execution mechanics later; current known flows do not justify its programming model. |

## Consequences

**Positive:** explicit crash windows, auditable recovery, independently failing consumers, meaningful deduplication and reproducible payloads. Lineage can point to the revision actually sent.

**Negative:** durable state and operational review are unavoidable costs. A destination without idempotency/authoritative lookup cannot offer automatic effectively-once effects. Frozen payloads require minimization, access control and retention. Custom dispatcher fit remains untested.

**Operational burden:** watch oldest pending/ambiguous age, expired leases, retry limits, dead letters and receipt retention; provide safe operator resolutions. This complexity is justified only once remote effects are demonstrated, not in the workforce foundation.

## Reconsider When

Measured throughput, external consumers or long retained replay justify a broker; Modulith demonstrably reduces code; orchestration becomes substantially branching; or destination capabilities make a safe integration infeasible.

## Interview line

"An event ID prevents processing a message twice; a stable business operation and a destination's idempotency contract prevent creating the account twice, including after a crash."

## Related Decisions

[0006](0006-event-driven-integration.md) · [0007](0007-transactional-outbox.md) · [0008](0008-broker-none-yet.md) · [0015](0015-data-lineage.md) · [0016](0016-operations-model.md) · [0018](0018-module-transactions-and-persistence.md) · [Review §8](../CODEX_ARCHITECTURE_REVIEW.md#8-event-architecture-outbox-and-idempotency)
