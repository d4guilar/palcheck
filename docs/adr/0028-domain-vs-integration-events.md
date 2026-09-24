# ADR-0028: Domain Events, Integration Events, and the Publication Boundary (Phase 3)

**Status:** Proposed (implemented provisionally by Phase 3 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-22

## Context

Daniel's Phase 3 message explicitly asks for a clear distinction between an internal domain event and a stable integration-event contract, and warns against mechanically publishing every internal fact externally. [ADR-0006](0006-event-driven-integration.md) already separated internal domain events from versioned integration events in the abstract; this ADR fixes what actually crosses the boundary in Phase 3, in what shape, and why only one event does.

## Decision

1. **`EmploymentStarted` is a domain event**: a fact meaningful inside `workforce` (a new Employment now exists), never serialized, never crossing a module boundary as data. It is the trigger, not the payload.
2. **`EmploymentStartedIntegrationEvent`, schema version `v1`, is the one contract Phase 3 publishes.** [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) principle 1 ("PalCheck owns the contract") applies here exactly as it applies to the inbound `NormalizedWorker` in [ADR-0024](0024-hris-adapter-boundary-phase2.md): the outbound contract is PalCheck's own, versioned, and does not mirror any downstream system's shape.
3. **Only one event type is published in Phase 3, deliberately.** Daniel's brief explicitly warns against 20 event types; Phase 3 needs exactly one to prove the pipeline (durable append, publish, consume, retry, dead-letter, replay) works end to end. A second event type is a small, well-precedented addition once a second real business trigger needs one — not a gap in this design.
4. **The payload carries identifiers only**: `personId`, `employmentId`, `assignmentId`, `legalEntityId`, `orgUnitId`. No name, email, title, or any field a consumer might display; a consumer that needs a display value calls back into PalCheck's own authorized read API, the same way a human-triggered integration would. This directly follows Daniel's instruction ("avoid putting full employee snapshots into events") and keeps the event genuinely small and stable — a person's name changing does not require a new schema version of an event about their employment starting.
5. **The envelope (`eventId`, `eventType`, `schemaVersion`, `tenantId`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, `causationId`, `source`, `payload`) is fixed and shared by any future event type.** `schemaVersion` is a plain string (`"v1"`); additive fields within a version are allowed, a breaking change gets a new version string. A schema registry (Avro/Protobuf) is not introduced — one event type with a small, stable JSON payload does not justify one; revisit if a second, differently-evolving event type makes hand-checked compatibility unwieldy.
6. **`causationId` is the originating request's id; `correlationId` is its correlation id — the two existing, distinct values `platform.web.CorrelationFilter` already puts in the logging context for every request** (docs/PHASE_1_IMPLEMENTATION_PLAN.md section 9). They coincide unless a caller supplied its own correlation id, which is exactly the relationship those two ids already have for logging; nothing new was invented to fill the field. Nothing yet causes this event *from another event* (the trigger is always a direct HTTP command), so a future event-caused-by-event chain (a consumer's own side effect eventually causing a second published event) needs no envelope change, only a different source for the value.
7. **Which events cross the boundary is a deliberate, reviewed list, not a mechanical export of every domain event.** As of Phase 3, the list is exactly `EmploymentStarted`. `PersonCreated`, `AssignmentChanged`, and every other Phase 1/2 domain fact are not published, because nothing downstream needs them yet; adding one to the list is a product decision with its own contract design, not a default.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Publish every workforce domain event automatically (a generic "event bus" that mirrors internal events outward) | Explicitly rejected by Daniel's brief; also violates "PalCheck owns the contract" — an internal event's shape is free to change for internal reasons, which would then silently break external consumers |
| Include a full Person/Employment snapshot in the payload for consumer convenience | Rejected: grows the contract's surface and coupling to `workforce`'s internal shape for no proven need; identifiers plus a callback read is the smaller, more stable contract |
| Avro or Protobuf with a schema registry | Rejected for one event type at this scale; a real second, differently-versioned event type is the trigger to revisit, per ADR-0026 |
| Derive `causationId` from `eventId` of a hypothetical causing event even though none exists yet | Rejected: inventing a causation chain that does not exist would misrepresent what actually happened; equal-to-correlation is the honest value until a real chain exists |

## Consequences

**Positive:** the published contract is small, stable, and independent of `workforce`'s internal representation; a consumer's failure to display a name does not become a contract-versioning problem; the reviewed, short event list keeps the asynchronous surface area proportionate to what Phase 3 actually needs to prove.

**Negative:** a consumer needing a display value makes a second, synchronous call back into PalCheck's API (an extra round trip Phase 3's two simulations do not currently need, since they only need identifiers); no schema registry means compatibility is a documented convention, not a mechanically enforced one — acceptable at one event type, revisited if that changes.

**Operational burden:** none beyond what ADR-0026/0027 already introduce; this ADR is a naming, scope and contract-shape decision, not new infrastructure.

## Reconsider When

A second business trigger needs its own published event (design its contract with the same discipline, do not default to "just add a field to the existing one"); a consumer's need for display data becomes common enough that a read-back round trip is worth avoiding; schema compatibility across two or more independently-evolving event types becomes hard to track by hand.

## Interview line

"We publish exactly one event, in our own vocabulary, carrying only the identifiers a consumer needs to know what happened and where to look — not a snapshot of the person, so the contract doesn't have to change every time an unrelated field on Person changes."

## Related Decisions

[ADR-0004](0004-hris-adapter-boundary.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0024](0024-hris-adapter-boundary-phase2.md) · [ADR-0026](0026-broker-and-transactional-outbox.md) · [ADR-0027](0027-business-operation-identity-and-idempotent-consumers.md) · [Phase 3 plan](../PHASE_3_IMPLEMENTATION_PLAN.md)
