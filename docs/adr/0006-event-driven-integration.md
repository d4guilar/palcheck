# ADR-0006: Event-Driven Integration Semantics

**Status:** Proposed
**Date:** 2026-09-19

> **Amendment (2026-09-19, Daniel-approved refinement; see [ADR-0019](0019-durable-delivery-and-effect-identity.md)):** decision 3 is amended. Business operation identity is separate from event identity: the idempotency key for an external effect derives from a stable logical operation (tenant + destination instance + operation kind + subject/occurrence), not from `eventId`. Failure recovery (ambiguous outcomes, reconciliation, retry, replay, duplicate delivery, manual recovery) is an accepted requirement for future integration work. None of this is Phase 1 implementation scope. Original text preserved.

## Context

A hire triggers payroll enrollment, identity provisioning, equipment requests, and more. These downstream operations **fail independently** (brief), so the hire must not depend on all of them succeeding synchronously. Workforce changes must be
communicated reliably without coupling `workforce` and `lifecycle` to each downstream system. Delivery guarantees must be stated honestly: the brief forbids claiming exactly-once processing without evidence.

## Decision

1. **Two kinds of events.**
   - **Internal domain events**: inside the monolith, not contracts, free to change (e.g., `EmploymentCreated`).
   - **Published integration events**: versioned, stable contracts emitted through the outbox ([ADR-0007](0007-transactional-outbox.md)) (e.g., `EmployeeHired`).
2. **Envelope** on every published event: `eventId`, `eventType`, `schemaVersion`, `aggregateId`, `occurredAt`, `correlationId`, `causationId`, `source`, `payload`, plus `tenantId`, `recordedAt`, and `effectiveAt`.
3. **Delivery is at-least-once. Processing is idempotent.** Consumers de-duplicate on `(consumer, eventId)`. Outbound effects carry an idempotency key derived from `(eventId, destination, operation)`.
4. **Destination idempotency modes** `KEY`, `LOOKUP`, `NONE`. After an *unknown outcome*: `KEY` retries with the same key; `LOOKUP` reconciles before resending; `NONE` never retries automatically and asks a human.
5. **Wording rule.** The strongest claim allowed is "effectively-once for that effect, where the destination honors the key". No exactly-once claims.
6. **Retry policy.** Classify failures (transient, permanent, unknown-outcome, data-quality); exponential backoff with jitter for transient failures; permanent failures dead-letter immediately; limits are configuration, recorded in Phase 1.
7. **Dead letters are a state with an owner**, with permitted resolutions (retry, supersede, discard with reason), all audited.
8. **Ordering** is not guaranteed globally; per-aggregate ordering only for consumers that declare the need; other consumers tolerate reordering.
9. **Schema evolution:** additive within a major version; consumers ignore unknown fields; breaking change = new major version emitted alongside the old for a deprecation window; contract tests enforce this.
10. **Retry, replay, and reprocess are separate operations** with different safety rules ([INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) 6.4).
11. **Crash and restart safety:** claims carry a lease; expired claims are retried under the same idempotency key.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Synchronous calls to each downstream inside the hire transaction** | Temporal coupling; one slow system blocks or fails the hire; no retry semantics; long-held transactions |
| **Publish straight to a broker after commit** | The dual-write problem: a crash between commit and publish loses the event ([ADR-0007](0007-transactional-outbox.md)) |
| **Change-data-capture on business tables** | Leaks table shape as the contract; needs extra infrastructure; possible later as the outbox transport |
| **A durable-execution / workflow engine for orchestration** (BPMN or code-first) | Provides retries, timers, and compensation, but is a large dependency and a second programming model; revisit if orchestration outgrows explicit state machines |
| **Event sourcing** | Too much complexity for this need; audit plus outbox meet the requirement |
| **Exactly-once as a goal** | Not achievable end-to-end across independent external systems; claiming it would be dishonest |

## Consequences

**Positive**

- Hire succeeds independently of downstream availability; failures are isolated, visible, and recoverable.
- Clear vocabulary (retry/replay/reprocess, idempotency modes) makes resilience explainable and testable.
- Internal refactoring is free because only published events are contracts.

**Negative**

- Eventual consistency: downstream state lags the hire; UI must show in-flight status honestly.
- Every consumer must be idempotent, and every destination's idempotency mode must be known.
- Operator workload for dead letters and unknown outcomes.
- Schema evolution discipline is real work.

## Reconsider When

- Orchestration logic becomes long-running and branching enough that state machines are painful (consider a workflow engine).
- A destination cannot support any idempotency mode and the risk is unacceptable (add a human-in-the-loop step or stop integrating it).
- Consumers outside the monolith appear ([ADR-0008](0008-broker-none-yet.md)).

## Interview line

"Downstream systems fail independently, so a hire commits locally and emits an event; delivery is at-least-once and processing idempotent, and I'm explicit that the guarantee is effectively-once only where the destination honors the idempotency key."

## Related Decisions

[ADR-0007](0007-transactional-outbox.md) · [ADR-0008](0008-broker-none-yet.md) · [ADR-0016](0016-operations-model.md) · [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md)
