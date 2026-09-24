# Phase 3 Implementation Plan: Durable Events and Asynchronous Integration

Status: **Phase 3 open** (opened by Daniel Aguilar, 2026-09-22) · Owner of decisions: Daniel · Author: Claude Code · Last updated: 2026-09-23
Architecture baseline: [ARCHITECTURE.md](ARCHITECTURE.md), [MODULES.md](MODULES.md) section 2a, [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) sections 5–6, [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md), [ADR-0026](adr/0026-broker-and-transactional-outbox.md), [ADR-0027](adr/0027-business-operation-identity-and-idempotent-consumers.md), [ADR-0028](adr/0028-domain-vs-integration-events.md).

Phase 3's opening message describes Phase 1 and Phase 2 as complete and accepted. That message is treated as Daniel's acceptance of both, exactly as his Phase 2 opening message was treated as acceptance of Phase 1; ADR-0024 and ADR-0025 are updated to **Accepted** on that basis (Q1–Q3 there already carried Codex's independent "accept" recommendation).

This plan is the contract for Phase 3, exactly as the Phase 1 and Phase 2 plans were for their phases. Anything not in it is out of scope.

## 1. Objective and definition of done

**Objective.** Prove that a committed workforce change can become a durable, asynchronously delivered integration event, processed by idempotent downstream consumers that survive retries, duplicate delivery and process restarts, with permanent failures visible and safely replayable — without losing the atomicity guarantees Phase 1 already built, and without turning the modular monolith into microservices.

**A technical user should eventually be able to:** perform a workforce action that generates an event; see it persisted durably and published; see per-consumer processing status; see correlation/causation back to the originating change; see a retry in progress; see a permanent failure that needs attention; replay it once authorized; confirm the replay did not duplicate the business action.

**Definition of done:**

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | Business state, audit and outbox commit atomically | `OutboxPublishingIT.hiringSomeoneCommitsExactlyOneOutboxRowInTheSameTransactionAsTheBusinessWrite`, `.aForcedOutboxInsertFailureRollsBackTheWholeHireCommand` | done |
| 2 | Outbox publication is durable | Same tests; `messaging.outbox_event` row exists and is read back with the original correlation/causation ids | done |
| 3 | A real broker is integrated | `EventPipelineIT` — real `spring-kafka-test` `EmbeddedKafkaBroker`, real consumer-group rebalancing observed in the run's own logs | done |
| 4 | Delivery semantics are explicitly at-least-once | Stated in ADR-0026/ADR-0028 and every relevant code comment; no exactly-once claim anywhere | done |
| 5 | Consumers are idempotent | `IntegrationOperationsIT.theSameOperationKeyFromTwoDifferentEventIdsExecutesOnce` | done |
| 6 | Business operation identity is separate from event identity | `OperationConsumer.operationKey` derived from the payload, never `eventId`; same test as #5 | done |
| 7 | Duplicate delivery never duplicates a business side effect | `IntegrationOperationsIT.aDuplicateDeliveryOfTheSameEventIsAcknowledgedWithoutASecondAttempt` | done |
| 8 | Transient failures retry | `IntegrationOperationsIT.aTransientFailureRetriesToSuccessWithoutWaitingOnRealBackoff` | done |
| 9 | Permanent failures reach a recoverable state | `IntegrationOperationsIT.aPermanentFailureDeadEndsWithoutAnAutomaticRetry` | done |
| 10 | Authorized replay works | `IntegrationOperationsIT.anAuthorizedReplayAfterAFixSucceedsAndIsAudited` | done |
| 11 | Replay never duplicates a completed operation | `IntegrationOperationsIT.replayingAnAlreadySucceededOperationIsRejected` (409 `CONFLICT`) | done |
| 12 | Correlation and causation propagate | `IntegrationOperationsIT.correlationAndCausationPropagateFromTheOriginatingRequestToTheOutboxEventAndAreVisibleThroughTheEventsApi` | done |
| 13 | Tenant isolation applies everywhere | `IntegrationOperationsIT.oneTenantCannotSeeAnotherTenantsIntegrationOperationsOrEvents` | done |
| 14 | Permissions protect technical operations | `IntegrationOperationsIT.onlyTheThreePhase3PermissionsGrantAccessToTheirEndpoints` and the correlation test's viewer-forbidden assertion | done |
| 15 | Event Explorer exists | Deferred to the next step (section 16) | pending |
| 16 | Operation status is visible | Backend API exists (`GET /api/integration-operations`, detail, replay, `/api/events/{id}`); no UI yet — deferred to section 16 | partial |
| 17 | E2E success/failure/replay flows pass | Deferred to the next step (section 16); backend equivalents of all three flows pass (criteria 8–11) | partial |
| 18 | No microservices introduced | One deployable; Kafka is the only new local process; documented in ADR-0026 | done |
| 19 | No real external provider introduced | Identity and Equipment stay in-process simulations, clearly labelled, `forceScenario` is test/dev-only | done |
| 20 | Documentation matches implementation | This plan, ADR-0026 (corrected: claim-and-lease, not plain `FOR UPDATE SKIP LOCKED`; `spring-boot-starter-kafka` not raw `spring-kafka`), ADR-0027/0028, WORKLOG | done |

Rows are updated to `done` as each step lands, exactly as the Phase 1 and Phase 2 plans were.

## 2. Scope

**In:** a new technical module `messaging` (outbox persistence, publisher, Kafka producer); a `workforce.api` call to append a durable event alongside the existing business write; one domain event (`EmploymentStarted`) and its integration-event contract; a Kafka topic and two `integrations`-owned consumers (Identity Provisioning, Equipment Provisioning) with idempotent operation tracking, scheduled retry with backoff, dead-lettering, and authorized replay; three new permissions.

**Out (Daniel's explicit Phase 3 non-goals):** microservices or independent deployables beyond the one new broker process; a service mesh; Kubernetes; distributed tracing infrastructure beyond structured logs and correlation/causation ids; a real payroll, identity or equipment provider; a real Workday production connector; legal calculations, CCSS, aguinaldo logic; incident management; a full SLO/error-budget platform; a production-grade schema registry; Event Explorer and operation-detail UI and E2E tests (explicitly deferred to the next step, section 16, matching the backend-first pattern of Phase 1 and Phase 2); Phase 4.

**Deliberately not built although the architecture allows it:** a payroll enrollment simulation (Daniel's brief made this optional; two consumers already demonstrate the pattern with independent failure modes — a third is a small, well-precedented addition, not new architecture); a physical Kafka dead-letter topic (a database status column is simpler, queryable and UI-friendly for Phase 3's scale — see ADR-0027); a schema registry (JSON envelopes with an explicit `schemaVersion` field are sufficient at one event type); a separate `integration_attempt` history table (Daniel's own "idempotent consumer model" section asks for a counter and `lastError` on one row, not per-attempt rows; added only if a later phase needs audit-grade attempt history); per-consumer configurable retry policies (one shared, documented default, per Daniel's own allowance); a fully general `EventPublisher` usable by any future event type beyond code-level extension (one event type does not justify a plugin registry).

## 3. Reconciling this Phase 3 with the Phase 0 integration design

[INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) sections 5–7 already designed a transactional outbox, an in-process dispatcher, a delivery-attempt state machine, and retry/replay/reprocess as distinct operations, all originally scoped to what that document called Phase 1 (outbound). None of it was built then; Phase 1 shipped no `integrations` and no event dispatch, and Phase 2 built only the *inbound* half (a sync writing into `workforce`), still with no outbox. This Phase 3 finally implements the deferred *outbound* half, narrowed to what proves the pattern: **one** event type, **one** topic, **two** consumers, a **shared** retry policy, and a **database-backed** dead-letter state rather than a physical DLQ topic (that section's "retry vs. replay vs. reprocess" distinction is honored: this phase implements retry and replay; reprocess — re-deriving state from a corrected mapping — has no use yet, since there is no landed raw payload to reprocess from). Where this plan differs from that baseline, this section is the reconciliation notice; the baseline document is updated with a pointer rather than rewritten.

## 4. Architecture

### 4.1 Module and dependency direction

Extends the accepted direction exactly as [MODULES.md](MODULES.md) section 2a already planned, now that a first asynchronous effect exists:

```
shared  <-  platform  <-  messaging  <-  { access, audit, workforce, integrations }
workforce -> access.api, audit.api, messaging.api        integrations -> access.api, audit.api, workforce.api, messaging.api
```

- **New module `messaging`**, technical infrastructure (no business rules), depending only on `shared` and `platform`. It owns schema `messaging` and the `outbox_event` table.
- `messaging.api` exposes exactly one write port, `OutboxWriter.append(OutboxEventDraft)`, called with `Propagation.MANDATORY` inside the caller's transaction — the same shape as `audit.api.AuditRecorder`, so a command that already knows how to write state, audit and (now) an outbox row in one transaction needed no new pattern, only one more narrow call.
- **`workforce` gains a dependency on `messaging.api`** (its first messaging dependency). `RegisterWorkerService` appends one `EmploymentStarted` outbox row in the same transaction as the Person/Employment/Assignment insert and their audit entries.
- **The outbox publisher and the Kafka producer live inside `messaging`**, not `integrations` — the exact rule [ADR-0018](adr/0018-module-transactions-and-persistence.md) decision 3 already fixed ("durable publication is technical `messaging` infrastructure... never owned by `integrations`"). `integrations` only *consumes*: a Kafka consumer (its own client, its own consumer groups) lives inside `integrations.infrastructure`, reading the same topic `messaging` published to. Consuming a message is not "owning publication infrastructure."
- `workforce` never depends on `integrations`, and `integrations` never writes a `workforce` table directly — unchanged from Phase 2 (ADR-0024), now additionally: `workforce` never depends on a Kafka client type, and `integrations`' consumer never depends on `messaging`'s producer internals (only on the envelope shape, via `messaging.api`).

### 4.2 Pipeline

```
RegisterWorkerService (one transaction)
   INSERT Person/Employment/Assignment  +  INSERT audit entries  +  OutboxWriter.append(EmploymentStarted)
   COMMIT
        |
        v
OutboxPublisherJob (messaging.application; scheduled poll, FOR UPDATE SKIP LOCKED)
   claims a PENDING row -> builds the IntegrationEvent envelope -> KafkaTemplate.send(topic, tenantId:aggregateId, envelope)
   send acknowledged -> mark PUBLISHED        send failed/ambiguous -> backoff, retry, or FAILED after the attempt limit
        |
        v
Kafka topic "palcheck.workforce.events.v1"
        |
        +--> IdentityProvisioningConsumer (@KafkaListener, its own consumer group)
        +--> EquipmentProvisioningConsumer (@KafkaListener, its own consumer group)
              each: derive operationKey -> integration_operation row (upsert) -> already SUCCEEDED? ack, no-op.
              otherwise attempt the simulation -> classify TRANSIENT/PERMANENT -> update the row -> ack the message either way
                        |
                        v
              ConsumerRetryJob (@Scheduled, per consumer): re-attempts FAILED_TRANSIENT rows whose next_attempt_at is due,
              by calling the same processing method directly — retry happens at the *operation* level, never by asking
              Kafka to redeliver, so the retry timer is exact and independent of broker redelivery timing.
```

### 4.3 Domain event vs. integration event

`EmploymentStarted` is the **domain event**: an internal fact ("this employment now exists"), meaningful only inside PalCheck. It is not published as-is. `EmploymentStartedIntegrationEvent` is the **integration event**: a stable, versioned contract for asynchronous consumers, produced by `RegisterWorkerService` when it appends the outbox row. Phase 3 publishes exactly this one domain event as exactly one integration event; nothing is published "because it happened" — [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) principle 1 ("PalCheck owns the contract") applies to integration events the same way it applies to the inbound `NormalizedWorker`. Adding a second published event later means naming and justifying it, not mechanically forwarding every internal event. Full rationale: [ADR-0028](adr/0028-domain-vs-integration-events.md).

**Payload is minimal by design** (Daniel's explicit instruction): `personId`, `employmentId`, `assignmentId`, `legalEntityId`, `orgUnitId` — identifiers only, no name, email, title or any personal value. A consumer that needs more calls back into PalCheck's own read API with its own authorization, exactly as a human-triggered integration would; the event's job is to say *that* something happened and *which* record, not to carry a snapshot that can drift from the current record.

### 4.4 Envelope

```
IntegrationEvent {
  eventId        UUID   — transport identity, unique per publish attempt (a redelivery reuses the same eventId)
  eventType      "EmploymentStarted"
  schemaVersion  "v1"
  tenantId
  aggregateType  "Employment"
  aggregateId    the employmentId
  occurredAt
  correlationId  — the request's correlation id (platform.web.CorrelationFilter); ties the event back to that request
  causationId    — the originating request's own id (its requestId); the two coincide unless the caller supplied its own correlation id, exactly as they already do for logging
  source         "palcheck.workforce"
  payload        { personId, employmentId, assignmentId, legalEntityId, orgUnitId }
}
```

`eventId` is **transport identity only**. It is never the key a consumer uses to decide whether it already did the work — that is `operationKey` (section 4.5). A redelivered message keeps its `eventId`; a *republished* (e.g., a stuck outbox row retried by the publisher) message gets a **new** `eventId` but the **same** `aggregateId`, so consumers relying on `operationKey` still converge correctly either way.

### 4.5 Business operation identity and idempotent consumers

Each consumer derives its own operation key from the aggregate, never from the event: `IDENTITY_PROVISION:{employmentId}` and `EQUIPMENT_PROVISION:{assignmentId}`. `integrations.integration_operation` is keyed `(tenantId, consumer, operationKey)`:

| Stored status | Meaning | On a new delivery for the same key |
|---|---|---|
| `PENDING` (transient, mid-attempt) | An attempt is in progress or was interrupted | Attempt again |
| `SUCCEEDED` | The business side effect happened | Acknowledge, do nothing — this is the duplicate-delivery guard |
| `FAILED_TRANSIENT` | The last attempt failed in a way expected to clear | Retried by `ConsumerRetryJob` on its own schedule, not by the new delivery |
| `FAILED_PERMANENT` | The last attempt failed in a way that will not clear on its own | Not retried automatically; a human replays it |
| `DEAD_LETTERED` | Retries under `FAILED_TRANSIENT` were exhausted without success | Same as `FAILED_PERMANENT`: needs a human |

Full rationale, including why a database status column stands in for a physical dead-letter topic: [ADR-0027](adr/0027-business-operation-identity-and-idempotent-consumers.md).

### 4.6 Failure classification and retry policy

- **Transient** (Identity: a forced `TEMPORARY_OUTAGE`; Equipment: a forced `OUT_OF_STOCK`): retried on a shared backoff schedule, configurable, default `5s, 30s, 2m, 10m` (Daniel's own example numbers) via `palcheck.messaging.retry-delays`. After the last configured delay is exhausted without success, the operation becomes `DEAD_LETTERED`.
- **Permanent** (Equipment: a forced `UNSUPPORTED_EQUIPMENT_PROFILE`): never retried automatically; the operation becomes `FAILED_PERMANENT` on the first attempt.
- **Unknown/ambiguous** (Daniel's brief names this as a real case: a downstream call sent, response lost): Phase 3's simulations never produce a genuinely ambiguous outcome (they are in-process calls with no network boundary to lose a response on), so nothing forces this path today. The design does not special-case it: because `operationKey` already makes an accidental re-attempt safe (the simulation itself checks the key, matching the `idempotency = KEY` capability from [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 4), an ambiguous outcome can be treated exactly like `FAILED_TRANSIENT` without the "never resend blindly" risk that document's section 6.3 warns about — that guidance is about a real destination that might *not* honor a key; ours does, by construction. A real, out-of-process destination without that guarantee would need the `LOOKUP`/`NONE` handling that document already specifies, which is future work for a real provider, not this simulation.
- **Controlled failure injection is test/dev-only**: each simulator exposes a `forceScenario(aggregateId, scenario)` call that production code never invokes; the Kafka-driven path always defaults to success unless a test or a demo explicitly forced otherwise (mirrors Phase 2's `WorkdayPullOptions` pattern and [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 7's "fault injection is enabled only in a demo/dev profile").

### 4.7 Replay

`POST /api/integration-operations/{consumer}/{operationKey}/replay` (permission `REPLAY_INTEGRATION_OPERATION`) re-attempts a `FAILED_PERMANENT` or `DEAD_LETTERED` operation using the payload already stored on its row — it does not need to go back to the outbox or the broker, so replay works even if the original event has aged out of the topic. It rejects (409, same `EXTERNALLY_OWNED`-style pattern of a specific, named error) a request to replay a `SUCCEEDED` operation, so replay can never duplicate a completed action. A successful replay is audited through the existing `audit.api.AuditRecorder` (entity type `INTEGRATION_OPERATION`, a deterministic UUID derived from the operation key so the append-only history has a stable record identity), naming the human who requested it — a workforce-audit entry is not duplicated, because a replay is an *integration operational* action, not a business edit.

## 5. Broker

**Apache Kafka**, not Redpanda, and this needed a real decision rather than following the brief's suggestion by default — full reasoning in [ADR-0026](adr/0026-broker-and-transactional-outbox.md). In short: Redpanda has no supported native path on this Windows development machine without Docker or WSL2, and no container runtime is available here (the same constraint recorded for Keycloak and embedded PostgreSQL in earlier phases); Kafka in KRaft mode (no separate ZooKeeper) runs as a bare JVM process with official Windows `.bat` scripts, matching the precedent already set for Keycloak (a native, unpacked distribution, no Docker). For automated tests, `spring-kafka-test`'s embedded broker runs a real, in-process Kafka for the duration of the test — the same "real dependency, no external process" precedent as `zonky embedded-postgres`.

Two new Maven dependencies, both justified here per AGENTS.md: `spring-boot-starter-kafka` (main) and `spring-boot-starter-kafka-test` (test scope), versions managed by Spring Boot 4.1.1's own dependency management (no explicit version pinned unless resolution requires it). The starters, not the raw `spring-kafka`/`spring-kafka-test` client libraries: Boot 4 split Kafka's autoconfiguration (the `KafkaTemplate`, listener container factory, etc.) into its own module, which only the starter pulls in — an earlier draft of this dependency depended on the raw client alone, which compiles but leaves every `@KafkaListener` bean and `KafkaTemplate` injection point unsatisfied at context startup; caught by `OutboxPublishingIT`/`IntegrationOperationsIT`/`EventPipelineIT` the first time they actually booted the full context (see docs/WORKLOG.md).

## 6. Persistence

New tables (`V9__messaging_and_integration_operations.sql`):

- `messaging.outbox_event` (`id, tenant_id, event_id, event_type, schema_version, aggregate_type, aggregate_id, occurred_at, correlation_id, causation_id, payload jsonb, status, attempt_count, next_attempt_at, published_at, created_at`). `status`: `PENDING`, `PUBLISHED`, `FAILED` (exhausted the publisher's own attempt limit — expected to be rare on a local broker; a real operational concern, not a business one).
- `integrations.integration_operation` (`tenant_id, consumer, operation_key, status, attempt_count, last_error, next_attempt_at, completed_at, source_event_id, payload jsonb, created_at, updated_at`; primary key `(tenant_id, consumer, operation_key)`).

No `integration_attempt` table (section 2); no event table beyond the outbox (the outbox row *is* the durable record of "this happened and needs publishing," and Kafka is the durable record of "this was delivered" once published — nothing else needs to remember every attempt forever).

## 7. API surface

| Method | Path | Purpose | Permission |
|---|---|---|---|
| `GET` | `/api/integration-operations` | List operations for the tenant, filterable by consumer/status, paged | `VIEW_INTEGRATION_OPERATIONS` |
| `GET` | `/api/integration-operations/{consumer}/{operationKey}` | One operation's current state and attempt history fields | `VIEW_INTEGRATION_OPERATIONS` |
| `POST` | `/api/integration-operations/{consumer}/{operationKey}/replay` | Re-attempt a failed/dead-lettered operation | `REPLAY_INTEGRATION_OPERATION` |
| `GET` | `/api/events/{eventId}` | Outbox event detail (status, envelope metadata, no payload values beyond identifiers) | `VIEW_EVENTS` |

A list-by-aggregate convenience (events for one employment) is deferred to the UI step (section 16), where it is easy to add once the Event Explorer needs it.

## 8. Authorization

Three new permissions, tenant-wide scope, bundled into `HR_ADMIN` only (matching the Phase 2 precedent for technical/integration actions): `VIEW_EVENTS`, `VIEW_INTEGRATION_OPERATIONS`, `REPLAY_INTEGRATION_OPERATION`.

## 9. Audit and logging

Replay is audited (section 4.7). Structured logs add `eventId`, `operationKey`, `consumer`, `attemptCount`, `result` alongside the existing `tenantId`, `requestId`, `correlationId`, `causationId`, `operation`. No event payload value is ever logged (the payload is identifiers only, but the rule is stated for when that changes).

## 10. Test strategy

Real PostgreSQL for every integration test, as in Phase 1 and 2; a real embedded Kafka broker (`spring-kafka-test`) wherever the test's point is to prove broker behavior. No coverage target.

| Layer | What is proven |
|---|---|
| Unit | Backoff schedule computation; operation-key derivation; envelope construction |
| Integration (real PostgreSQL) | Outbox row commits atomically with business state and audit; a forced outbox-insert failure rolls back the whole command; the publisher claims and marks a `PENDING` row `PUBLISHED`; a forced publish failure retries then eventually `FAILED`; a consumer processes an event exactly once even if delivered twice; the same operation key from two different event ids executes the side effect once; a forced transient failure retries to success; a forced permanent failure dead-letters without auto-retry; replay after dead-letter succeeds; replay after success is rejected; correlation/causation propagate from the originating request to the event; tenant isolation on every new table and endpoint; the permission matrix |
| Integration (real embedded Kafka) | An appended outbox row is actually published to and consumed from a real broker end to end, at least once |
| Architecture (ArchUnit) | `workforce` never depends on a Kafka client type; `messaging` depends only on `shared`/`platform`; `integrations`' consumer depends only on `messaging.api`, never `messaging.infrastructure`; nothing outside `messaging` touches its internals |
| Frontend / E2E | Deferred to the next step (section 16) |

## 11. Local development and CI

Docker Compose gains a `kafka` service (KRaft mode, single broker) alongside PostgreSQL and Keycloak; **not executed on this machine** (no container runtime), same unverified status the Compose file has always carried. The "without Docker" path in [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md) gains the native Kafka distribution, mirroring how Keycloak was verified there. CI: the new tests run inside the existing `mvn verify`; no new workflow file needed (the embedded broker needs no external service).

## 12. Dependencies

`spring-boot-starter-kafka` (main), `spring-boot-starter-kafka-test` (test scope) — the only additions; justified in section 5. No frontend dependency added this session (no frontend work this session — section 16).

## 13. Implementation steps

| Step | Deliverable | Exit criteria | Status |
|---|---|---|---|
| 1 | Review Phase 2 implementation and architecture | This plan and its ADRs reflect what exists | done |
| 2 | This plan, ADR-0026, ADR-0027, ADR-0028 | Reviewed against Daniel's Phase 3 message | done |
| 3 | Event boundaries and contracts (`messaging.api`, `IntegrationEvent`, `EmploymentStartedIntegrationEvent`) | Types compile; unit-testable | done |
| 4 | Transactional outbox persistence | Migration applies; `OutboxPublishingIT` atomic-commit test passes | done |
| 5 | Publisher | `OutboxPublishingService` claims and publishes a `PENDING` row; `OutboxPublishingIT` retry-then-fail test passes | done |
| 6 | Local broker (Kafka) | `EventPipelineIT` — a real embedded broker test proves publish-then-consume | done |
| 7 | `EmploymentStarted` wired into `RegisterWorkerService` | Creating a worker produces one outbox row and (once published) one Kafka message; proven end to end by `EventPipelineIT` | done |
| 8 | Identity Provisioning consumer | Processes the event; idempotent; transient retry — `IntegrationOperationsIT`, `EventPipelineIT` | done |
| 9 | Equipment Provisioning consumer | Same, plus a permanent-failure path — same tests | done |
| 10 | Operation idempotency | `IntegrationOperationsIT.aDuplicateDeliveryOfTheSameEventIsAcknowledgedWithoutASecondAttempt`, `.theSameOperationKeyFromTwoDifferentEventIdsExecutesOnce` | done |
| 11 | Retry and dead-letter | `IntegrationOperationsIT.aTransientFailureRetriesToSuccessWithoutWaitingOnRealBackoff`, `.aPermanentFailureDeadEndsWithoutAnAutomaticRetry` | done |
| 12 | Replay | `IntegrationOperationsIT.anAuthorizedReplayAfterAFixSucceedsAndIsAudited`, `.replayingAnAlreadySucceededOperationIsRejected` | done |
| 13 | Event Explorer UI | Pages against the real API contract | pending |
| 14 | Operation detail / replay UI | Status, attempts, replay action | pending |
| 15 | Frontend and E2E tests | Component tests; the three flows in Daniel's brief | pending |
| 16 | Documentation cleanup and Phase 3 review | Every doc matches the system; DoD table filled with evidence | done (this table and the DoD table; UI/frontend docs remain pending with steps 13–15) |

Backend test evidence for steps 3–12 (14 new backend tests, all passing; full counts in [WORKLOG.md](WORKLOG.md)): `com.palcheck.messaging.OutboxPublishingIT` (4), `com.palcheck.integrations.IntegrationOperationsIT` (9), `com.palcheck.integrations.EventPipelineIT` (1, real embedded Kafka).

Steps 3–12 (the backend vertical slice) are this session's deliverable; steps 13–16 are the explicitly stated next step (section 16), exactly matching how Phase 1 and Phase 2 each split backend from frontend.

## 14. Risks and open questions

- **R1** The broker runs as one extra local process; this is the first Phase 3 dependency that is not a bare JVM library, and the first local-dev step that cannot be fully verified on this machine end-to-end without either the native Kafka distribution or Docker (R2 mirrors Phase 2's Docker-unverified status).
- **R2** Database-backed dead-lettering (section 4.5) trades a physical DLQ topic's tooling (offset-based replay, consumer-group semantics) for query-ability and UI simplicity; if a later phase needs Kafka-native DLQ tooling, this is the reconsider point.
- **R3** The shared retry schedule (one policy for both consumers) is a documented simplification; a real second downstream with a different SLA would want its own schedule.
- **R4** No payroll simulation was added (Daniel's brief made it optional); adding one later is a small, well-precedented extension of the same consumer pattern.
- **Q1 (Daniel)** Confirm the database-backed dead-letter state over a physical Kafka DLQ topic.
- **Q2 (Daniel)** Confirm Kafka (not Redpanda) as the local broker for this environment, per ADR-0026's environment-specific reasoning.
- **Q3 (Daniel)** Confirm the shared, single retry policy for both consumers in Phase 3.

## 15. Non-negotiables carried over from Phase 1 and 2

Synthetic data only; no real credential; English UI with the existing message-catalog pattern (once the UI step lands); structured logs with no personal values; one problem-details error model; tenant taken only from the trusted server-side principal; no executable Costa Rican legal rule; incremental delivery with a worklog entry per step group; at-least-once delivery, never exactly-once claimed.

## 16. What this session delivers, and what is next

Steps 1–12 are complete and tested: the plan and ADRs, and a tested backend vertical slice (outbox, publisher, a real embedded Kafka broker, one published event, two idempotent consumers with retry, dead-letter and audited replay), backed by 14 new backend tests (`OutboxPublishingIT`, `IntegrationOperationsIT`, `EventPipelineIT`) plus the full pre-existing 197-test suite, all passing. Two real bugs were caught and fixed by these tests before being trusted: a missing `spring-boot-starter-kafka` dependency (the raw `spring-kafka` client alone compiles but leaves `KafkaTemplate`/`@KafkaListener` autoconfiguration entirely absent) and a Java text-block trailing-whitespace bug that concatenated `RETURNINGid` instead of `RETURNING id` in three `RETURNING`-clause queries — both recorded in [docs/WORKLOG.md](WORKLOG.md)'s Bugs fixed section. This plan does **not** deliver the Event Explorer, operation-detail UI, or E2E tests — those are steps 13–15, the explicitly stated next piece of work, following the exact backend-first pattern Phase 1 and Phase 2 both used.
