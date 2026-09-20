# 09. Proposed Transaction and Delivery Boundaries

Status: Proposed by CODEX, 2026-09-19; **accepted in part by Daniel the same day** (atomic state + audit + durable publication participation, technical `messaging` ownership, business operation identity, explicit failure recovery). Only state + audit is implemented in Phase 1. Supplements the original diagrams; see [ADR-0018](../adr/0018-module-transactions-and-persistence.md) and [ADR-0019](../adr/0019-durable-delivery-and-effect-identity.md). Messaging/integrations arrive with provisioning, after the proposed foundation.

```mermaid
flowchart TB
  command["Authorized workforce command"]
  subgraph tx["One local database transaction"]
    state["workforce: state and revision"]
    receipt["workforce: command receipt"]
    audit["audit: change evidence"]
    publication["messaging: immutable publication and subscriber set"]
  end
  command --> state
  state --> receipt
  state --> audit
  state --> publication
  publication --> fanout["Short transaction: idempotent durable fan-out"]
  fanout --> delivery["Per-consumer delivery and fenced claim"]
  delivery --> intent["Consumer transaction: processed receipt plus operation intent"]
  intent --> send["Commit send intent; call simulated destination outside transaction"]
  send --> result{"Outcome known?"}
  result -->|yes| finalize["Short fenced result transaction"]
  result -->|no| reconcile["KEY retry / authoritative LOOKUP / NONE manual review"]
  reconcile --> finalize
```

Public APIs preserve ownership while participating in the same transaction; they do not permit another module to write private tables. `messaging` does not depend on workforce or integrations. Consumer event contracts are real declared dependencies. Frozen destination requests and operation identity survive retries and event-version changes.

```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> PROCESSING: fenced claim and recorded send intent
  PROCESSING --> SUCCEEDED: acknowledged
  PROCESSING --> RETRYING: known retryable failure
  RETRYING --> PROCESSING: due and within policy
  PROCESSING --> RECONCILING: ambiguous outcome or expired in-flight lease
  RECONCILING --> SUCCEEDED: confirmed accepted
  RECONCILING --> RETRYING: destination contract proves safe resend
  RECONCILING --> MANUAL_REVIEW: unresolved or NONE destination
  MANUAL_REVIEW --> SUCCEEDED: accepted effect confirmed with evidence
  MANUAL_REVIEW --> RETRYING: explicitly authorized safe resend
  MANUAL_REVIEW --> CANCELLED: resolved with reason
  PROCESSING --> DEAD_LETTERED: permanent rejection
  RETRYING --> DEAD_LETTERED: budget exhausted
  DEAD_LETTERED --> RETRYING: authorized retry after cause fixed
  PENDING --> CANCELLED: authorized cancellation
  SUCCEEDED --> [*]
  CANCELLED --> [*]
```

State names describe delivery software behavior, not research/implementation statuses. Attempts are append-oriented records under the operation; reusing an operation does not rewrite previous attempts. Local fencing cannot revoke a request already executing remotely. Full protocol and failure tests: [review §8](../CODEX_ARCHITECTURE_REVIEW.md#8-event-architecture-outbox-and-idempotency).

Validation: reviewed as text; Mermaid rendering remains **Unverified:** until a renderer is available. No dependency was installed during Phase 0.
