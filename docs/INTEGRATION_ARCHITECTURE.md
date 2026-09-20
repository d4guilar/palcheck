# Integration Architecture

> **Reconciliation notice (2026-09-19, Daniel-approved refinements 2, 3 and 4):** (a) the transactional outbox is **not owned by `integrations`** (section 5.1 is amended): durable publication is technical `messaging` infrastructure; `integrations` consumes published events. (b) Business operation identity is separate from event identity: external-effect idempotency keys derive from a stable logical operation, not from `eventId` (section 5.4 amended). (c) Failure recovery is an accepted requirement for all future integration work: partial imports, ambiguous delivery outcomes, reconciliation, retry, replay, duplicate delivery and manual recovery; [review §7 and §8](CODEX_ARCHITECTURE_REVIEW.md#7-integration-feasibility-normalization-ownership-and-lineage) (page-checkpoint barrier, missing records are never inferred terminations, fenced claims, ambiguity-aware recovery) are binding requirements for the provisioning and overlay milestones. **None of this is Phase 1 implementation scope**; Phase 1 has no integrations and no event dispatch. Baseline text is retained for reasoning. See [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md).

Status: Phase 0 draft, **Proposed** ([ADR-0004](adr/0004-hris-adapter-boundary.md), [ADR-0006](adr/0006-event-driven-integration.md),
[ADR-0007](adr/0007-transactional-outbox.md), [ADR-0008](adr/0008-broker-none-yet.md)) · Output of assignment step 5.

Diagrams: [04 HRIS integration](diagrams/04-hris-integration.md) · [05 event processing](diagrams/05-event-processing.md).
Ownership and lineage: [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md). Owning module: `integrations` ([MODULES.md](MODULES.md) 3.4).

## 1. Scope

Two directions, one module:

- **Inbound.** An external HRIS (Workday-shaped reference first) sends workforce data into PalCheck: adapt, land, map, reconcile, write.
  Phase 2. Relevant to overlay mode; also useful for migrating a standalone tenant in.
- **Outbound.** PalCheck workforce events cause work in downstream systems (payroll, identity, equipment, later benefits and
  notifications): outbox, dispatch, deliver, retry, recover. Phase 1. Central to the resilience story in both modes.

Neither direction may distort meaning. The rules below apply to both.

## 2. Principles

1. **PalCheck owns the contract.** The normalized shape (`NormalizedWorker`) is defined by PalCheck and versioned. Providers adapt to it, not the reverse.
2. **Provider types never leave the adapter.** No provider DTO, enum, or field name appears in `workforce`, `lifecycle`, `countrypolicy`, or the UI.
3. **Nothing is silently dropped or overwritten.** Unmapped data is preserved and reported; externally owned data is never overwritten by PalCheck users;
   PalCheck-owned data is never overwritten by a sync.
4. **Every effect is idempotent.** At-least-once delivery plus idempotent processing. No exactly-once claims.
5. **Every step is recorded** with `correlationId` and `causationId` so "what happened, why, and did it recover" is answerable.
6. **Simulated is labelled.** Anything that pretends to be a real system says so wherever it appears.
7. **Least surprise for operators.** Retry, replay, and reprocess are separate, named operations with separate safety rules.

## 3. Inbound translation pipeline

```
External HRIS -> Adapter -> Raw landing -> Mapper -> Reconciliation -> workforce write -> Policy enrichment -> Orchestration
```

| Stage | Responsibility | Output | Owner | Recorded | Typical failure class |
|---|---|---|---|---|---|
| **1. Adapter** | Protocol, authentication, paging, rate limits, provider DTOs; discover changes (full, delta, webhook) | Provider DTOs (package-private) | `integrations` (adapter package per provider) | Request/response metadata, latency, status | Transient (network, 5xx, rate limit), auth |
| **2. Raw landing** | Keep the provider payload so mapping can be replayed or debugged without re-pulling | Landed record (redacted/encrypted, retention-limited) keyed by sync run, external ID, content hash | `integrations` | Hash, size, retention deadline | Storage, over-retention |
| **3. Mapper** | Versioned mapping from provider DTO to `NormalizedWorker`; code-list mapping; validation | `NormalizedWorker` + `extensions` + unmapped report | `integrations` | Mapping version, unmapped/invalid fields | Data-quality (missing/invalid/unknown code) |
| **4. Reconciliation** | Identity resolution, change detection, ordering, conflict handling, terminations, rehires, future-dated changes | A decision per record: `APPLY`, `SKIP_OWNERSHIP`, `STALE`, `QUARANTINE`, `FAIL` | `integrations` (decisions), `workforce` (state) | Decision and reason per record | Conflict, ordering, ownership |
| **5. Workforce write** | Apply through `workforce`'s public API with a write context; enforce ownership; record provenance | Changed workforce state + change record | `workforce` | Provenance, audit, outbox row (one transaction) | Invariant violation |
| **6. Policy enrichment** | Add local context as annotations/derived facts referencing a policy version | Annotations (never mutations) | `countrypolicy` | Concept IDs, policy version, inputs | Missing local supplemental data |
| **7. Orchestration** | Turn resulting events into downstream work | Outbox events, delivery attempts | `integrations` | Attempts, statuses | See section 6 |

### 3.1 Adapter

- One adapter package per provider. It is the only code that knows the provider's protocol and vocabulary.
- **Capability flags** declare what the provider can do, so the core never assumes: `supportsDeltaSync`, `supportsWebhooks`,
  `supportsWriteBack`, `supportsBulk`, `rateLimitProfile`, and, for outbound systems, `idempotency = KEY | LOOKUP | NONE`.
- Credentials are secret *references*, resolved at runtime; never stored in plain configuration or logs.
- Connector base URLs are administrator-supplied and therefore an SSRF surface: allow-list schemes and hosts, block internal ranges,
  enforce TLS.

### 3.2 Raw landing

- **Purpose.** Replaying a corrected mapping without re-pulling; debugging a data-quality failure; proving what the provider sent.
- **Cost.** It stores personal data. Proposal: land the full payload only for records that failed mapping or reconciliation and for a
  configurable sampling window; for successes keep hash, size, and metadata. Retention is short and enforced by a job.
  *Open for Codex:* is this the right default, or should full landing be all-or-nothing per tenant?

### 3.3 Mapper and `NormalizedWorker`

The contract (conceptual; JSON Schema will be the source of truth in Phase 2):

| Block | Content |
|---|---|
| `externalRef` | `system`, `externalId`, source version or last-modified stamp |
| `person` | names, identifiers `(type, country, value)`, contact points, date of birth |
| `employment` | legal entity reference, worker type, start/end, status with effective date |
| `assignment` | org unit reference, position/job, manager reference (external ID), location, schedule reference; each effective-dated |
| `compensation` | *(deferred)* |
| `extensions` | Provider-specific data preserved as-is and never interpreted by the core |
| `mappingReport` | Fields mapped, transformed, ignored, and invalid, with the mapping version |

Rules:

- **No local legal fields** in `NormalizedWorker`. Local context comes from enrichment and supplemental data, not from pretending the provider had it.
- **Mappings are versioned.** Provenance records the mapping version. Start with explicit code mappers plus a documented field table;
  move to declarative definitions only if a second provider demonstrates the need (second-instance rule).
- **Code lists are mapped, not copied.** Provider job codes, statuses, and reasons map to PalCheck reference data or are preserved unmapped.
- **Lossy is allowed; silent is not.** Every dropped or altered value appears in the `mappingReport`.

### 3.4 Reconciliation

| Concern | Approach |
|---|---|
| Identity | `ExternalIdentityLink (tenant, system, externalId <-> PalCheck IDs)`; match by link, never by name. Creating a link is an explicit, audited step. |
| New / changed / unchanged | Content hash and source version. Unchanged is a fast no-op. |
| Ordering | Use source version or timestamp. An older update than the last applied one is `STALE` and recorded, not applied. |
| Deltas vs. full | Delta with a watermark when supported; periodic full sync to correct drift. Deletions in a full sync are *detected*, then treated as terminations or quarantined; never silently deleted. |
| Future-dated changes | Stored with their effective date; the workforce scheduler makes them effective. |
| Terminations and rehires | Termination ends an Employment; a rehire is a new Employment for the same Person, linked by identity. |
| Conflicts | Ownership policy decides. `EXTERNAL` field: apply. `PALCHECK` field with a different provider value: record a `Divergence`, do not overwrite. |
| Quarantine | Records that cannot be safely applied wait for a human, with the reason and payload reference. |

### 3.5 Workforce write

The sync calls `workforce` with a **write context** `(actorKind = SYNC, sourceSystem, syncRunId, mappingVersion, correlationId)`.
`workforce` alone enforces ownership policy, stores provenance, writes the audit record, and emits events, all in one transaction.
The integration layer never touches workforce tables.

### 3.6 Policy enrichment

- Triggered by events from `workforce`, not by the sync directly, so enrichment behaves the same regardless of where data came from.
- **Produces annotations** (`countrypolicy`-owned, keyed by workforce IDs, referencing concept IDs and the policy version). They never
  mutate normalized data. Ownership of annotations is `DERIVED`.
- Local supplemental data that no provider holds (for example, a local enrolment reference; whether it exists at all is RESEARCH REQUIRED)
  is `PALCHECK`-owned, attached to the Employment, and collected through a lifecycle task when missing.
- Phase 1 has **no policy packs and no behavior**; enrichment is documented, not built.

### 3.7 Orchestration

Downstream work is described in section 5. The important property: enrichment and downstream systems react to *events*, so the same
orchestration runs whether the change came from a PalCheck user or a sync.

## 4. Connector ports (conceptual)

Two ports, deliberately small. Signatures are described in prose; no code is implied.

**HRIS inbound port** (per provider adapter)

| Operation | Purpose | Required capability |
|---|---|---|
| `describe` | Report capabilities and supported scopes | always |
| `pullWorkers(scope, cursor)` | Pages of provider workers, full or since a watermark | delta for incremental |
| `pullOrganizations(cursor)` | Org structure | always |
| `getWorker(externalId)` | Targeted refresh | optional |
| `receiveWebhook(request)` | Verify signature, translate to a pull hint | `supportsWebhooks` |
| `pushChange(change)` | Write PalCheck-owned data back | `supportsWriteBack`, off by default |

**Downstream delivery port** (per destination system)

| Operation | Purpose | Notes |
|---|---|---|
| `describe` | Capabilities, idempotency mode, limits | `idempotency = KEY | LOOKUP | NONE` |
| `deliver(request, idempotencyKey)` | Perform the effect once | Returns `SUCCEEDED`, `FAILED(retryable|permanent)`, or `UNKNOWN` |
| `lookup(idempotencyKey)` | Ask what happened to an earlier attempt | Required for `LOOKUP` mode; resolves `UNKNOWN` |
| `healthProbe` | Cheap liveness/latency check | Feeds `operations` health |

`UNKNOWN` (accepted but no reply, timeout after send) is the dangerous case; see section 6.3.

## 5. Outbound: events, outbox, dispatch

Business transaction and its outbox row commit together, then a dispatcher delivers.

### 5.1 Transactional outbox ([ADR-0007](adr/0007-transactional-outbox.md))

Problem: writing state and then publishing an event are two operations; a crash between them loses or invents events. Solution: insert the
outbox row **in the same database transaction** as the business change. *(Amended 2026-09-19, [ADR-0018](adr/0018-module-transactions-and-persistence.md) decision 3: the outbox is technical `messaging` infrastructure that domain modules publish through; it is not owned by `integrations`, and it is not implemented in Phase 1.)* Conceptual outbox record: `outboxId`, `tenantId`, `eventId`,
`eventType`, `schemaVersion`, `aggregateId`, `payload`, `correlationId`, `causationId`, `occurredAt`, `createdAt`, and per-consumer delivery state.

### 5.2 Dispatcher ([ADR-0008](adr/0008-broker-none-yet.md))

- Polls the outbox in the API process (profile-activated), claims rows with `FOR UPDATE SKIP LOCKED`, and delivers to each subscribed consumer
  through the `EventPublisher`/delivery port. A stuck `PROCESSING` claim expires after a lease and is retried.
- **No broker in Phase 1.** The publisher is behind a port so a broker can replace the in-process fan-out without changing producers or consumers.
- **Ordering.** Not guaranteed globally. Guaranteed per aggregate only where a consumer declares it needs it (dispatch per aggregate is sequential
  for such consumers). Default: consumers tolerate reordering, using `occurredAt` and aggregate version.

### 5.3 Delivery attempt state machine

`PENDING -> PROCESSING -> SUCCEEDED`
`PROCESSING -> FAILED -> RETRYING -> PROCESSING ...` (repeat)
`FAILED|RETRYING -> DEAD_LETTERED` (policy exhausted, or permanent failure)
`PENDING|RETRYING -> CANCELLED` (operator or superseded)

Each attempt records: attempt number, destination/consumer, started and finished times, latency, outcome, failure reason and class, next retry
time, idempotency key, correlation ID, and payload *metadata* (not payload values). Diagram: [05](diagrams/05-event-processing.md).

### 5.4 Idempotency ([ADR-0006](adr/0006-event-driven-integration.md))

- **Key.** *(amended 2026-09-19, [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md) decision 3)* Stable across retries and derived from the **business operation** (tenant + destination instance + operation kind + subject/occurrence), independent of event ID and schema version. ~~Derived from `(eventId, destination, operation)`.~~
- **Consumer side.** A processed-events record keyed by `(consumer, eventId)` makes a second delivery a no-op.
- **Destination side.** The key is sent to the downstream system. What the downstream does with it depends on its `idempotency` capability:
  - `KEY`: the system dedupes; automatic retry is safe.
  - `LOOKUP`: after an `UNKNOWN` outcome, call `lookup` and reconcile *before* any resend.
  - `NONE`: the system cannot protect us; after an `UNKNOWN` outcome, automatic retry is **disabled** and a human confirms.
- **Wording.** "At-least-once delivery with idempotent processing gives effectively-once *for that effect, where the destination honors the key*."
  That is the strongest claim the design makes, and it is stated conditionally on purpose.

## 6. Failure handling

### 6.1 Classification drives behavior

| Class | Examples | Behavior |
|---|---|---|
| Transient | Timeout before send, connection reset, 5xx, rate limit (respect `Retry-After`) | Retry with exponential backoff and jitter, up to a policy limit |
| Permanent | Validation rejection, unknown recipient, authorization failure | Dead-letter immediately; no retry storm |
| Unknown outcome | Timeout or disconnect after the request may have been processed | Reconcile via `lookup` or human, then decide (6.3) |
| Data-quality (inbound) | Missing or invalid mapped field | Quarantine with reason; do not apply |

Backoff parameters, maximum attempts, and maximum event age are **configuration chosen in Phase 1 and recorded there**. No numbers are
asserted in this document; they are engineering defaults, not facts.

### 6.2 Dead letters

A dead letter is a *state with an owner*, not a graveyard: it has a reason, a payload reference, an age, and permitted resolutions: **retry**
(after the cause is fixed), **supersede** (a newer event makes it moot), or **discard with reason**. Every resolution is audited. Dead-letter
count is a health signal ([ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md)).

### 6.3 Unknown outcomes

The classic duplicate-account or duplicate-payroll-action bug lives here. Rule: **never resend blindly after an unknown outcome.** Use the
destination's idempotency capability (6.1, 5.4). For `NONE`-mode destinations, stop and ask.

### 6.4 Retry vs. replay vs. reprocess

| Operation | Meaning | Same idempotency key? | Who may | Risk |
|---|---|---|---|---|
| **Retry** | Re-attempt a failed *delivery* of the same event to the same destination | Yes | `RETRY_INTEGRATION` | Low if destination honors key |
| **Replay** | Re-emit a *historical event* to a consumer (rebuild a projection, recover a consumer) | Yes, flagged `replay=true` | Elevated permission, audited | Side effects at destinations that ignore replays |
| **Reprocess** | Re-derive PalCheck state from landed raw data with a corrected mapping | n/a (new mapping version) | Elevated permission, audited | Overwrites derived values; ownership rules still apply |

Replays and reprocesses expose payloads, so they require a separate, audited permission ([SECURITY.md](SECURITY.md)).

### 6.5 Restarts and duplicates

Crash after commit, before dispatch: the outbox row is still there; the dispatcher resumes. Two dispatchers: `SKIP LOCKED` prevents most
double-claims; idempotent consumers absorb the rest. Crash mid-attempt: the lease expires and the attempt is retried under the same key.

### 6.6 Schema evolution

Published events and `NormalizedWorker` follow compatibility rules: additive changes within a major version; consumers ignore unknown fields; the
envelope carries `schemaVersion`; breaking changes create a new major version emitted alongside the old one for a deprecation window. Contract tests
guard this ([ARCHITECTURE.md](ARCHITECTURE.md) section 8).

## 7. Simulator (portfolio centerpiece, Phase 1)

Simulated downstream systems implement the downstream delivery port with controllable behavior.

- **Phase 1 systems:** Payroll, Identity Provider, Equipment. **Later:** Benefits, Notifications.
- **Behavior controls:** latency distribution; error rate; scheduled outage windows; timeouts *after* accepting (the unknown-outcome case);
  duplicate detection on or off; `idempotency` mode per system (to demonstrate all three); throttling.
- **Determinism.** Scenarios run from a seed so demos and tests reproduce exactly.
- **Honesty.** Every simulated system is labelled *Simulated* in the UI, API, and logs. Fault injection is enabled only in a demo/dev profile and
  is an administrator-only capability.
- **Reference scenario** (from the brief): `EmployeeHired` -> Payroll `SUCCEEDED`, Identity `SUCCEEDED`, Equipment `FAILED` (HTTP 503) ->
  automatic retry -> Equipment `SUCCEEDED`. Also demonstrated: the same with an unknown outcome against a `KEY` system (dedupes) and a `NONE`
  system (halts for human confirmation).

## 8. Reference HRIS (Phase 2): Workday-shaped, not Workday

- **What it is.** A separate mock process serving synthetic workers in a shape *inspired by* a large HRIS's worker/organization model, so the adapter
  and mapper are exercised across a real protocol boundary.
- **What it is not.** It is not the vendor's API and makes no fidelity claim. Detailed vendor API documentation is largely customer- or
  partner-gated (Unverified); nothing may be filled in from employer knowledge ([AGENTS.md](../AGENTS.md) section 5). It is labelled a reference implementation over
  synthetic data and does not imply partnership or certification.
- **What makes it valuable.** A catalog of *realistic failure shapes* the adapter must survive: paging, delta and full sync, out-of-order updates,
  duplicate records, renamed or removed fields, rate limiting, partial outages, large batches, future-dated and retroactive changes, rehires.
- **Tests.** Golden fixtures of synthetic provider payloads to `NormalizedWorker`, so the test asserts on PalCheck's contract, which is the part PalCheck controls.

## 9. Extending to other providers

Adding SAP SuccessFactors or Oracle HCM later should add **an adapter and a mapping** and change nothing in `workforce`, `lifecycle`, or the UI.
If it forces a change to `NormalizedWorker`, that is a *contract version* change reviewed as such. Before Phase 3, document (from public sources only) how
the second provider's model differs from the first; that comparison is what proves or corrects the contract (second-instance rule). What those
providers actually expose is Unverified and must be researched then.

## 10. Security of integrations

Secret references, not secrets, in configuration; least-privilege service accounts; TLS everywhere; webhook signature verification and replay protection;
rate limiting on inbound endpoints; payload redaction in logs, explorer, and metrics labels; tenant scoping on every connector and record; SSRF controls
(3.1); raw landing retention and encryption (3.2). See [SECURITY.md](SECURITY.md).

## 11. Observability of integrations

Metrics (per connector and destination): attempts, success/failure/retry counts, latency distribution, dead-letter count and age, outbox lag
(oldest undelivered), sync run duration and record counts, quarantine count. Logs: structured, with `correlationId`, `causationId`, `eventId`, `tenantId`,
`destination`, `attempt`. These answer: *What happened? Why? What caused it? Where did it fail? Was it retried? Did it recover?*

## 12. Open questions

- **IQ-01** Raw landing default (3.2): full-on-failure plus sampling, or configurable full/none per tenant?
- **IQ-02** Per-aggregate ordering (5.2): sequential dispatch per aggregate for opting-in consumers, or aggregate versioning at consumers only?
- **IQ-03** Should the simulator be in-process (Phase 1) or a container from the start so HTTP failure modes are real? Recommendation: in-process behind the
  port, then a real mock-server process arrives with the Phase 2 reference HRIS.
- **IQ-04** Where should mapping definitions live: code, or declarative files? Recommendation: code until a second provider exists.
- **IQ-05** Does Spring Modulith's event publication registry satisfy [ADR-0007](adr/0007-transactional-outbox.md), or is an explicit outbox clearer and more portable? Codex to evaluate.
