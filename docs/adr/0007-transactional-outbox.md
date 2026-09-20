# ADR-0007: Transactional Outbox

**Status:** Proposed
**Date:** 2026-09-19

> **Amendment (2026-09-19, Daniel-approved refinement; see [ADR-0018](0018-module-transactions-and-persistence.md)):** decisions 1 and 2 said the outbox is owned by `integrations`. That is **rejected**: it created a dependency cycle (workforce would need to call an integrations-owned API while integrations depends on workforce). Durable publication is technical platform infrastructure (`messaging`); domain modules publish through a publication abstraction; `integrations` may consume published events but does not own the infrastructure. The outbox is **not implemented in Phase 1** (no asynchronous effect exists yet). Original text preserved.

## Context

Committing business state and publishing an event are two operations on two systems. If the process crashes between them, either the state exists without its event (downstream never learns of a hire) or the event exists without the state (downstream acts on
something that did not happen). This is the dual-write problem. PalCheck must prevent DB-write-success/event-publication-failure inconsistency (brief).

## Decision

1. In the **same database transaction** as the business change, insert a row into an **outbox** table owned by `integrations` (through a small API that other modules call; they never write the table directly).
2. A **dispatcher** (background profile, in-process) polls the outbox, **claims rows with `FOR UPDATE SKIP LOCKED`** under a **lease**, delivers through the `EventPublisher` port, and records per-consumer delivery state and attempts.
3. **Polling with an optional wake-up hint** (for example PostgreSQL `LISTEN/NOTIFY`) to reduce latency; correctness never depends on the hint.
4. **Retention**: delivered outbox rows and attempt history are archived or purged by a scheduled job under a configurable policy; undelivered and dead-lettered rows are never purged automatically.
5. **The publisher is behind a port**, so replacing the in-process fan-out with a broker ([ADR-0008](0008-broker-none-yet.md)) changes the dispatcher, not producers or consumers.
6. **Evidence, not assertion:** failure-injection tests crash the process between commit and dispatch, run two dispatchers, and duplicate deliveries, and assert the behavior ([ARCHITECTURE.md](../ARCHITECTURE.md) section 8).
7. **Evaluate before building:** whether Spring Modulith's event publication registry gives equivalent, sufficiently explicit behavior is *Unverified* and assigned to Codex as a Phase 1 spike; an explicit outbox is the default because it is portable, visible in the schema, and easy to explain.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Dual write** (commit, then publish) | Loses events on crash; the problem being solved |
| **Publish before commit / in the transaction to a broker** | Broker cannot participate in the DB transaction; produces phantom events on rollback |
| **Change-data-capture (log tailing, e.g., Debezium)** | Robust and low-latency, but requires broker and connector infrastructure; overkill now; a candidate transport later |
| **`afterCommit` in-process listener only** | Fast, but loses the event if the process dies right after commit |
| **Spring Modulith event publication registry** | Similar idea, less explicit and less portable; evaluate rather than adopt blindly |
| **Event sourcing** | See [ADR-0006](0006-event-driven-integration.md) |

## Consequences

**Positive**

- Atomicity between state and event without a distributed transaction.
- Works without a broker; testable with a real PostgreSQL.
- The outbox table is itself observable (lag, oldest undelivered), which feeds health.

**Negative**

- Extra table, writes, and a dispatcher to operate; the outbox grows and needs retention and index care.
- Polling adds latency versus push; mitigated by a wake-up hint.
- At-least-once delivery means duplicates: consumers must be idempotent ([ADR-0006](0006-event-driven-integration.md)).
- Ordering across aggregates is not guaranteed.

## Reconsider When

- Outbox lag under real load is unacceptable even with tuning (consider CDC transport).
- A broker is adopted and log-tailing becomes simpler than polling.
- The Spring Modulith registry, once evaluated, covers the requirements with less code and no loss of clarity.

## Interview line

"State and event commit in one transaction, and a dispatcher delivers afterwards, so a crash can delay an event but never lose or invent one; consumers are idempotent because delivery is at-least-once."

## Related Decisions

[ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0008](0008-broker-none-yet.md)
