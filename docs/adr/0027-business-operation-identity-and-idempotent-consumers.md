# ADR-0027: Business Operation Identity, Idempotent Consumers, Retry, Dead-Letter and Replay (Phase 3)

**Status:** Proposed (implemented provisionally by Phase 3 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-22

## Context

[ADR-0019](0019-durable-delivery-and-effect-identity.md) decision 3 already decided, in the abstract, that a downstream idempotency key must be "stable across retries and derived from the business operation... independent of event ID and schema version," amending the original ADR-0006 wording that would have derived it from `(eventId, destination, operation)`. Phase 3 is the first place this is actually built: two consumers (Identity Provisioning, Equipment Provisioning) must each perform their side effect at most once per business fact, survive at-least-once delivery, retry transient failures on their own schedule, and give an authorized human a safe way to recover a permanently failed one.

## Decision

1. **Business operation identity is the aggregate, not the event.** `IDENTITY_PROVISION:{employmentId}` and `EQUIPMENT_PROVISION:{assignmentId}` — stable regardless of how many times, or with how many different `eventId`s, the same underlying fact is delivered. This is ADR-0019 decision 3 made concrete for the first time.
2. **One row per operation**, `integrations.integration_operation`, keyed `(tenantId, consumer, operationKey)`, holding `status`, `attemptCount`, `lastError`, `nextAttemptAt`, `completedAt`, `sourceEventId`, and the minimal `payload` needed to retry or replay without going back to Kafka. Consciously **not** a per-attempt history table (Daniel's own "idempotent consumer model" section describes exactly this one-row shape); if audit-grade per-attempt history is ever needed, that is a deliberate later addition, not a gap in this design.
3. **Status model**: `PENDING` (an attempt is due or in flight) -> `SUCCEEDED` (terminal; a later delivery for the same key is acknowledged and does nothing else) or `FAILED_TRANSIENT` (retried by schedule) -> `DEAD_LETTERED` (transient retries exhausted) or `FAILED_PERMANENT` (a permanent failure, no retry attempted at all). `DEAD_LETTERED` and `FAILED_PERMANENT` behave identically to everything except how they were reached (kept as two labels because Daniel's brief uses both and they answer different operator questions — "did this fail outright, or did we give up after trying" — even though the recovery action is the same).
4. **Retry happens at the operation level, on a schedule, never by asking Kafka to redeliver.** A `ConsumerRetryJob` per consumer polls `FAILED_TRANSIENT` rows whose `nextAttemptAt` is due and calls the exact same processing method the `@KafkaListener` calls, directly. This keeps retry timing exact and independent of the broker's own redelivery behavior (which Kafka does not offer per-message delayed redelivery for, without a second retry topic this phase does not need). The shared backoff schedule is `5s, 30s, 2m, 10m` (Daniel's own example numbers), configurable via `palcheck.messaging.retry-delays`; one shared policy for both consumers is a documented Phase 3 simplification (plan R3), not a permanent architectural constraint.
5. **A database status column stands in for a physical Kafka dead-letter topic.** A DLQ topic's usual value — replaying by offset, a separate consumer group for triage — is not needed at Phase 3's scale (two consumers, one event type); a queryable, tenant-scoped status column is simpler to build a UI over and to test, and it is what the operation-identity table already needed to have anyway. Reconsidered if a real destination's failure volume ever needs Kafka-native DLQ tooling (ADR-0026 "Reconsider When").
6. **Replay re-attempts using the stored payload, not the original event.** `POST /api/integration-operations/{consumer}/{operationKey}/replay` is refused on a `SUCCEEDED` operation (the guard that makes "replay never duplicates a completed operation" a fact, not a hope) and otherwise re-runs the same processing method with a fresh attempt, incrementing `attemptCount` and clearing `lastError` on success. It never changes `operationKey`. It is audited through the existing `audit.api.AuditRecorder` (entity type `INTEGRATION_OPERATION`, a UUID derived deterministically from the operation key via `UUID.nameUUIDFromBytes`, so the append-only history has a stable identity to attach to) — a genuinely different kind of fact from a workforce edit, so it does not appear in a person's own change history, only in the operation's own trail.
7. **Ambiguous/unknown outcomes are not specially modelled in Phase 3.** [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 6.3's "never resend blindly after an unknown outcome" is about a *real* downstream that might not honor an idempotency key; both Phase 3 simulations are in-process calls that check `operationKey` themselves before acting (the `idempotency = KEY` capability from that document's connector-port table), so an accidental re-attempt is safe by construction and can be handled exactly like `FAILED_TRANSIENT`. A future real, out-of-process destination without that guarantee would need the `LOOKUP`/`NONE` handling that document already specifies — explicitly future work, not a gap here.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Use `eventId` (or `eventId` + consumer) as the idempotency key | Explicitly rejected by Daniel's brief and by ADR-0019 decision 3: a republished event (new `eventId`, same underlying fact) would then execute the side effect again |
| Retry by relying on Kafka's own redelivery (nack and let the broker redeliver later) | Rejected: Kafka has no per-message delayed redelivery without a second "retry" topic and its own consumer, which is more moving parts than a scheduled DB poll for two consumers and one shared policy |
| A physical `*.dlq` topic per consumer | Rejected for Phase 3's scale (decision 5); revisit if consumer count or failure volume grows |
| Per-consumer configurable retry policies now | Deferred (Daniel's brief explicitly allows a shared default); the policy is read from configuration, so adding a per-consumer override later does not need a redesign |
| Replay by re-publishing the original event through the outbox/broker | Rejected as unnecessary indirection: the operation row already has everything needed to retry; going back through Kafka would also fail once the topic's retention window passes, which replay should not depend on |

## Consequences

**Positive:** duplicate delivery, redelivery after a crash, and a deliberate operator replay all converge on the same, provably-at-most-once side effect; the retry timer is exact and testable without waiting on real Kafka redelivery timing; replay is simple to build a UI over later because it needs no broker access.

**Negative:** the stored `payload` on `integration_operation` is a second copy of the same small identifier set the outbox event carried (a deliberate, small duplication in exchange for replay not depending on Kafka retention); one shared retry policy may not fit a future third consumer with a very different SLA (documented, not hidden).

**Operational burden:** one scheduled job per consumer (disabled in tests, invoked directly there); no new external service beyond the broker ADR-0026 already introduces.

## Reconsider When

A consumer's failure profile needs its own retry schedule; failure volume or triage workflow needs Kafka-native DLQ tooling; a third consumer's business logic genuinely needs per-attempt history rather than a counter; a real, out-of-process destination without key-based idempotency is added (the `LOOKUP`/`NONE` handling becomes real work, not a documented non-case).

## Interview line

"Every downstream operation has an identity built from the record it's about, never from the message that told us about it, so retries, redeliveries and a human clicking replay all land on the same row and can only ever perform the side effect once — the row itself is small enough to be both the retry state and the dead-letter state, so I didn't need a separate topic or a separate table for either."

## Related Decisions

[ADR-0006](0006-event-driven-integration.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0019](0019-durable-delivery-and-effect-identity.md) · [ADR-0025](0025-ownership-lineage-and-sync-model-phase2.md) · [ADR-0026](0026-broker-and-transactional-outbox.md) · [ADR-0028](0028-domain-vs-integration-events.md) · [Phase 3 plan](../PHASE_3_IMPLEMENTATION_PLAN.md)
