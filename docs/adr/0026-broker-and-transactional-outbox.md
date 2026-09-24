# ADR-0026: Broker Choice and Transactional Outbox (Phase 3)

**Status:** Proposed (implemented provisionally by Phase 3 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-22

## Context

[ADR-0007](0007-transactional-outbox.md) and [ADR-0008](0008-broker-none-yet.md) already decided *that* PalCheck would eventually need a transactional outbox and *that* no broker was justified yet, with explicit exit criteria. [ADR-0018](0018-module-transactions-and-persistence.md) decision 3 already fixed *where* durable publication infrastructure would live if it existed: a technical `messaging` package, never owned by `integrations`. Daniel's Phase 3 message asks for the first real asynchronous effect, and explicitly asks that the broker choice be justified, not assumed from the prompt's own suggestion of Redpanda. The development machine has no container runtime (the same constraint every earlier phase's local-dev decisions recorded).

## Decision

1. **The exit criteria in ADR-0008 are now met**: there is a real asynchronous effect to deliver (two independent downstream consumers, each needing its own retry and failure state), and at-least-once delivery with idempotent consumers is a real requirement Phase 3 must demonstrate, not a hypothetical. A broker is justified now, for exactly this reason and no other.
2. **Broker: Apache Kafka, not Redpanda.** Redpanda's supported path is Linux (and macOS/Windows only through Docker or WSL2); this development machine has no container runtime and no WSL2 setup. Kafka, since version 3.3, runs in **KRaft mode** (no separate ZooKeeper process) as a single JVM process, with an official Windows distribution (`bin\windows\*.bat`). This is the same shape of decision as ADR-0022's Keycloak choice: a native, unpacked, single-process distribution stands in for a containerized service that cannot be verified here. Redpanda is not rejected as a technology — it would be a fine choice on Linux or macOS — it is rejected as a poor fit for *this* development environment, which is the criterion Daniel's message asked to be applied honestly rather than defaulting to the prompt's own suggestion.
3. **Tests use a real, embedded, in-process Kafka broker** (`spring-kafka-test`'s `EmbeddedKafkaBroker`), the same "real dependency, no external process" precedent `zonky embedded-postgres` already set for PostgreSQL. This is a genuine broker, not a mock of one: message ordering, partitioning, and consumer-group semantics are real.
4. **The transactional outbox lives in a new technical module, `messaging`**, depending only on `shared` and `platform` — never on `access`, `audit`, `workforce`, or `integrations`. It exposes one write port, `OutboxWriter.append(OutboxEventDraft)`, called with `Propagation.MANDATORY` inside the caller's own transaction, the same shape `audit.api.AuditRecorder` already uses. A command that writes business state, an audit entry, and now an outbox row does all three in the one transaction it already had; nothing about the transaction boundary changes.
5. **The outbox publisher and the Kafka producer client also live inside `messaging`**, not `integrations`, honoring ADR-0018 decision 3 exactly. `integrations` consumes: its own Kafka consumer client, in `integrations.infrastructure`, reads the topic `messaging` published to. Consuming a message is not owning publication infrastructure; `workforce` never depends on a Kafka client type either way — it only ever calls `OutboxWriter`.
6. **Delivery is at-least-once, stated as such everywhere it matters** (code comments, this ADR, the plan, structured logs): the publisher may publish the same outbox row's event more than once (a send that times out after the broker already received it is indistinguishable, locally, from one that did not); a consumer may therefore see the same `eventId` more than once. No claim of exactly-once is made anywhere.
7. **Claiming is claim-and-lease, not plain `FOR UPDATE SKIP LOCKED`** — a correction made during implementation to an earlier draft of this decision. `SELECT ... FOR UPDATE SKIP LOCKED` alone only holds its row lock for the life of the claiming statement's own (implicit, auto-commit) transaction; that transaction ends *before* the publisher can call the broker, because ADR-0018 decision 8 forbids network I/O inside a business transaction and this claim is not one. So the lock is gone by the time the network call happens, and a second claimer's identical `WHERE` clause would see the same row as eligible again. The actual query is one data-modifying CTE that both selects eligible rows `FOR UPDATE SKIP LOCKED` *and*, in the same atomic statement, pushes `next_attempt_at` forward by a short lease window (30 seconds) — so a concurrent claimer's own `next_attempt_at <= now()` filter excludes the leased rows even after the row lock itself has released. `JdbcOutboxRepository.claimEligible` and `JdbcIntegrationOperationRepository.claimDueRetries` both use this pattern; matches the same Postgres-native-concurrency-primitive spirit `assignments.lockReportingGraph` uses for a different problem, just extended with a lease because the protected work happens outside the claiming transaction. A crash between claim and outcome simply lets the lease expire and the row becomes eligible again — no separate recovery path is needed.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Redpanda | Rejected for this environment specifically (decision 2); would be reconsidered on Linux/macOS or once Docker/WSL2 is available |
| Defer the broker again, keep an in-process dispatcher (ADR-0008's original default) | Rejected: Daniel's Phase 3 message explicitly asks for a real broker and real at-least-once semantics; an in-process dispatcher cannot demonstrate independent consumer groups, redelivery, or a broker restart's effect on delivery |
| A managed cloud Kafka (Confluent Cloud, MSK) for "local" development | Rejected: needs a real account and network access; contradicts "local development should remain reproducible" |
| Publish directly inside the business transaction (no outbox, synchronous Kafka send in the same transaction) | Rejected: network I/O inside a business transaction is exactly what ADR-0018 decision 8 already forbids, and a broker outage would then fail the business write itself |
| A distributed lock (e.g., Postgres advisory lock across all publisher instances, or a leader election) for the publisher | Rejected as overengineering for Phase 3's scale; `FOR UPDATE SKIP LOCKED` already prevents double-claiming without needing every instance to agree on a leader |

## Consequences

**Positive:** the exact guarantee Daniel asked for is real and tested — a business write can never silently lose its integration event, because the event is durable the instant the business transaction commits, independent of the broker being up at that moment. The module boundary (`messaging` owns publication, `integrations` only consumes) keeps `workforce` and `integrations` decoupled from Kafka's own client types, so a future broker swap touches only `messaging.infrastructure`.

**Negative:** one new local process to run (Kafka) and one new dependency (`spring-boot-starter-kafka`); the publisher's polling interval adds a small, bounded latency between commit and publish (not a correctness issue, since durability is already guaranteed at commit); the local-without-Docker path is unverified on Daniel's machine end to end (R1 in the plan), mirroring Phase 2's own unverified-Compose status.

**Operational burden:** one more JVM process for local development; a scheduled publisher job (disabled in tests, invoked directly there, same pattern as `IdempotencyPurgeJob`).

## Reconsider When

A container runtime becomes available on the development machine (Kafka vs. Redpanda is worth revisiting then, purely on technical merits rather than environment constraints); a second, independently-scaled downstream justifies its own topic or partitioning strategy; publisher throughput at real volume needs batched claims instead of one row at a time; a schema registry becomes justified by a second event type with incompatible evolution needs.

## Interview line

"I picked Kafka over the prompt's suggested Redpanda because Redpanda has no native path on this Windows box without Docker, which isn't available here — the same reasoning that picked embedded PostgreSQL over Testcontainers earlier. The outbox lives in its own technical module that only workforce writes to and only the publisher inside that same module ever touches a Kafka client, so the business code has no idea a broker exists."

## Related Decisions

[ADR-0007](0007-transactional-outbox.md) · [ADR-0008](0008-broker-none-yet.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0022](0022-phase1-technical-baseline.md) · [ADR-0027](0027-business-operation-identity-and-idempotent-consumers.md) · [ADR-0028](0028-domain-vs-integration-events.md) · [Phase 3 plan](../PHASE_3_IMPLEMENTATION_PLAN.md)
