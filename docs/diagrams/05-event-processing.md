# 05. Event Processing

> **Reconciliation notice (2026-09-19):** this diagram is the original Phase 0 proposal. Outbox ownership is amended (technical `messaging`, not `integrations`), event-ID-only idempotency is amended (business operation identity, [ADR-0019](../adr/0019-durable-delivery-and-effect-identity.md)), and the attempt lifecycle lacks the `RECONCILING` / `MANUAL_REVIEW` states shown in [diagram 09](09-proposed-transaction-and-delivery.md). **Nothing here is Phase 1 scope.**


Outbox to delivery, the attempt lifecycle, and the reference resilience scenario. No broker in Phase 1 ([ADR-0008](../adr/0008-broker-none-yet.md)); the publisher is behind a port.

## Flow

```mermaid
flowchart LR
  cmd["Command<br/>e.g. hire employee"]
  subgraph tx["One database transaction"]
    direction TB
    state["Business state<br/>(workforce, lifecycle)"]
    audit["Audit record"]
    outbox["Outbox row<br/>eventId, type, schemaVersion,<br/>aggregateId, correlationId, payload"]
  end
  disp["Dispatcher<br/>claims rows with SKIP LOCKED,<br/>lease on claim"]
  pub{{"EventPublisher port<br/>(in-process now, broker later)"}}
  c1["Consumer: Payroll"]
  c2["Consumer: Identity"]
  c3["Consumer: Equipment"]
  dlq[("Dead letters<br/>a state with an owner")]
  ops["operations<br/>health, event explorer"]

  cmd --> state
  state --> audit
  state --> outbox
  outbox --> disp --> pub
  pub --> c1
  pub --> c2
  pub --> c3
  c1 -.->|"exhausted or permanent"| dlq
  c2 -.-> dlq
  c3 -.-> dlq
  disp -.-> ops
  dlq -.-> ops
```

## Delivery attempt lifecycle

```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> PROCESSING: claimed by dispatcher
  PROCESSING --> SUCCEEDED: destination acknowledged
  PROCESSING --> FAILED: attempt failed
  FAILED --> RETRYING: retryable, policy allows
  RETRYING --> PROCESSING: backoff elapsed
  FAILED --> DEAD_LETTERED: permanent failure
  RETRYING --> DEAD_LETTERED: policy exhausted
  PENDING --> CANCELLED: operator cancel or superseded
  RETRYING --> CANCELLED: operator cancel or superseded
  SUCCEEDED --> [*]
  DEAD_LETTERED --> [*]
  CANCELLED --> [*]
```

## Reference scenario: `EmployeeHired`

Payroll and Identity succeed; Equipment fails once, then recovers. Every attempt carries the same correlation ID; each retry reuses the same idempotency key.

```mermaid
sequenceDiagram
  autonumber
  participant D as Dispatcher
  participant P as Payroll (simulated)
  participant I as Identity (simulated)
  participant E as Equipment (simulated)

  D->>P: deliver(EmployeeHired, key K1)
  P-->>D: SUCCEEDED
  D->>I: deliver(EmployeeHired, key K2)
  I-->>D: SUCCEEDED
  D->>E: deliver(EmployeeHired, key K3) attempt 1
  E-->>D: HTTP 503 (transient)
  Note over D,E: FAILED then RETRYING, backoff with jitter
  D->>E: deliver(EmployeeHired, key K3) attempt 2
  E-->>D: SUCCEEDED
```

## The dangerous case: unknown outcome

```mermaid
flowchart TD
  a["Attempt timed out after the request may have been processed"] --> b{"Destination idempotency mode"}
  b -->|"KEY"| k["Retry with the same key.<br/>Destination dedupes."]
  b -->|"LOOKUP"| l["Ask destination what happened<br/>to that key, then decide"]
  b -->|"NONE"| n["Do NOT retry automatically.<br/>Halt and ask a human to confirm."]
```

**How to read this.** The business change and its outbox row commit together, so a crash between them cannot lose or invent an event. Delivery is at-least-once; safety comes from idempotent processing, not from a claim of exactly-once. Retry, replay, and reprocess are
distinct operations ([INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) 6.4). Decisions: [ADR-0006](../adr/0006-event-driven-integration.md), [ADR-0007](../adr/0007-transactional-outbox.md).
