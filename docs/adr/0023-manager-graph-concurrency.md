# ADR-0023: Serialize manager graph validation within a tenant

Status: Proposed

Date: 2026-09-19

## Context

The Phase 1 assignment model rejects reporting cycles. Optimistic row versions protect two edits to the same assignment, but cannot protect the whole graph when concurrent commands change different assignments. A real PostgreSQL regression reproduced two individually valid edges committing a cycle. This violates an existing invariant; it does not introduce a new product feature.

## Decision

The authorized Phase 1 bug fix acquires a transaction-scoped PostgreSQL advisory lock keyed by tenant before reading and validating the reporting graph. Both workforce registration and assignment updates use the same repository operation. The service transaction retains the lock through the business write, audit, and idempotency receipt. Existing per-resource optimistic version checks remain.

Implementation of the fix is complete; this architectural record remains Proposed pending Daniel's acceptance. It does not supersede an Accepted ADR or authorize Phase 2.

## Alternatives Considered

- Row-version checks alone: insufficient across different edges, as the regression demonstrates.
- Lock only the proposed manager or edited row: can miss the other path involved in the concurrent cycle.
- Serializable isolation plus transaction retries: broader behavior change, retry/error semantics, and operational burden for this small invariant.
- Fine-grained path locks: higher deadlock and graph-change complexity without measured contention to justify it.

## Consequences

**Positive:** one understandable serialization point protects cycle validation across all current graph-changing commands; PostgreSQL releases the lock on commit/rollback; no lock table, new dependency, or external coordinator is required. The infrastructure repository owns PostgreSQL details and requires an existing transaction. The lock key derives from server-established tenant context.

**Negative:** graph changes within a tenant serialize, including some assignment edits that do not change the manager. Hash collisions can unnecessarily serialize different tenants without granting data access. Long transactions can hold up other edits. The solution depends on PostgreSQL and does not protect arbitrary manual SQL that bypasses application commands.

## Reconsider When

Measured lock waits or tenant write throughput become a problem; additional graph writers appear; batch operations or a different persistence engine are introduced. Every new graph mutation path must participate in this protocol. Monitor normal database lock waits before adding a specialized graph subsystem.

## Verification and operational burden

`ConcurrencyIT` races opposing manager changes against real PostgreSQL with a deterministic delay trigger: before the fix both committed; after it one succeeds and the other is rejected, leaving an acyclic graph. Existing stale-version and transaction/audit tests still pass. No new service is operated; transaction duration and lock waits are the relevant scaling signals.

## Related Decisions

[ADR-0018](0018-module-transactions-and-persistence.md), [ADR-0022](0022-phase1-technical-baseline.md), [Phase 1 review](../PHASE_1_CODEX_REVIEW.md#8-concurrency-review).

**Interview line:** A version protects one row; because two different rows can jointly create a reporting cycle, I serialize each tenant's graph validation and update in the same database transaction.
