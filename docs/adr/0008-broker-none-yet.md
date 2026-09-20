# ADR-0008: Message Broker: None Yet (Kafka vs. Redpanda vs. No Broker)

**Status:** Proposed
**Date:** 2026-09-19

## Context

The brief's event flow ends in "Kafka or Redpanda" while instructing not to choose one automatically and to document whether either is justified. At present there is one developer, one deployable, three simulated consumers, and a
single database that already provides transactions and `SKIP LOCKED`. A broker adds a stateful system to install, secure, monitor, upgrade, and reason about, and its benefits (fan-out to independent consumers, long replay, partitioned ordering, backpressure) have no
current consumer.

## Decision

**Do not adopt a broker in Phase 1 or Phase 2.** Use the transactional outbox with an in-process dispatcher ([ADR-0007](0007-transactional-outbox.md)) behind an `EventPublisher` port.

**Exit criteria** (adopt a broker when at least one is true and measured, not predicted):

1. **Consumers outside the monolith**: separate deployables, teams, or languages need the events.
2. **Sustained throughput or lag** beyond what the database-backed dispatcher handles at acceptable latency, shown by outbox-lag metrics.
3. **Long-history replay** to new consumers, beyond what outbox retention should hold.
4. **Independent fan-out with different consumer speeds and backpressure** that in-process delivery cannot isolate.
5. **Ordering/partitioning guarantees** across services that the outbox cannot provide cheaply.

**If adopting:** prefer the simplest option that meets the criteria; the outbox stays as the producer-side guarantee, and the dispatcher becomes the broker producer. Choose Kafka vs. Redpanda at that time by re-evaluating the points below.

## Alternatives Considered

| Option | Assessment |
|---|---|
| **No broker (chosen)** | Lowest operational burden; fully sufficient for current consumers; keeps the port so migration is local |
| **Apache Kafka** | The industry standard; Apache-licensed; strong ecosystem and replay/partition semantics. Cost: a cluster (or managed service) to operate, plus optionally a schema registry and Connect; heavy for local development. Kafka's exactly-once features apply within Kafka pipelines, not end-to-end to external systems |
| **Redpanda** | Kafka-API-compatible platform, typically simpler to run locally and operate as a single binary. Licensing and feature terms differ from Apache Kafka (*Unverified*; check at decision time). Same conceptual burden (topics, partitions, consumer groups) |
| **RabbitMQ / cloud queues (SQS, Pub/Sub) / NATS** | Simpler queue semantics can fit fan-out without a log; less replay. A serious option if criteria 1 and 4 appear without criterion 3 |
| **Database-only forever** | Acceptable for a long time; stops being right when the exit criteria are met |

## Consequences

**Positive**

- No stateful infrastructure to run; local setup is `PostgreSQL + app`.
- The interview answer is stronger: a clear decision, explicit criteria, and a low-cost path to adopt one.
- The complexity budget goes to correctness (idempotency, ownership, localization).

**Negative**

- No broker on the résumé; addressed by the reasoning, not by adding one for optics ([AGENTS.md](../../AGENTS.md) section 6).
- Replay depth is bounded by outbox retention.
- Migration later requires care (dual-running, idempotent consumers) though the port limits blast radius.
- The database carries dispatch load; watch outbox lag and table size.

## Reconsider When

Any exit criterion above becomes true and is supported by metrics; or a customer or partner requires an event stream interface; or the operations team (Daniel) decides the learning value justifies a *time-boxed, clearly labelled* experiment on a branch, not on the main design.

## Interview line

"I didn't add Kafka because nothing needed it yet: the outbox gives atomicity, the database gives fan-out to three consumers, and I wrote down the measurable conditions that would make me adopt a broker and what changes when I do."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0007](0007-transactional-outbox.md) · [ADR-0016](0016-operations-model.md)
