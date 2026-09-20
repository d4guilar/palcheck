# ADR-0005: Source-of-Truth Ownership and Provenance

**Status:** Proposed
**Date:** 2026-09-19

## Context

When an external HRIS owns some data, PalCheck must not silently overwrite it, and must not let a sync silently overwrite PalCheck-owned data. The brief proposes per-field metadata (`sourceSystem`, `externalField`, `externalId`, `lastSynchronizedAt`, `ownershipMode`)
with modes `PALCHECK`, `EXTERNAL`, `DERIVED`. That block mixes two different things: *who may write a field* and *where a stored value came from*. Under standalone-first ([ADR-0017](0017-primary-product-mode.md)) the model must default to
"PalCheck owns everything" yet flip cleanly per field when a connector is enabled.

## Decision

1. Separate **ownership policy** from **provenance**.
   - **Ownership policy**: per tenant, per **governed field**, exactly one mode: `PALCHECK`, `EXTERNAL(system)`, or `DERIVED`. Default `PALCHECK`.
   - **Provenance**: recorded with each stored governed value: source system, source field, external ID, observed-at, recorded-at, sync run, mapping version, transformation ref, write context, content hash, ownership snapshot.
2. **Governed fields** are an explicit, short list (fields an external system could plausibly own), not every column.
3. **`workforce` is the only writer** of workforce data. Every write carries a **write context** (actor kind `USER|SYNC|SYSTEM|POLICY`, source, sync run, mapping version, correlation ID, reason). The write matrix in
   [DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md) 2.3 is enforced in the domain layer and covered by contract tests.
4. **Divergence is recorded, not applied**: a sync value differing from a `PALCHECK`-owned field creates a `Divergence`; a user edit to an `EXTERNAL` field is rejected with an actionable message.
5. **Ownership changes are explicit, previewed, audited operations** (e.g., enabling a connector), never a side effect of configuration; policy is pinned per sync run.
6. **Local supplemental data** is always `PALCHECK`-owned and attached to an Employment.
7. **Not in v1:** a `PROPOSE_ONLY` (change-request to the external owner) mode; it needs write-back.
8. Every change flows from **one change record** that produces audit, provenance, and event in one transaction.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Store ownership on each value** (as in the brief) | Duplicates policy across millions of values; changing policy would mean rewriting data |
| **Entity-level ownership** (a whole Employment owned by one system) | Too coarse; real overlays split fields (title from the HRIS, local data from PalCheck) |
| **Last-write-wins** | Silent overwrites are exactly the failure to prevent |
| **Field-level ownership on every column** | Configuration burden and false precision; most fields are never externally owned |
| **Event-sourced merge with per-field vector clocks** | Solves a harder problem than this product has |

## Consequences

**Positive**

- Standalone (all `PALCHECK`) and overlay (some `EXTERNAL`) are one model; the flip is configuration plus a previewed operation.
- Silent overwrites are structurally prevented and testable.
- The brief's metadata all has a home; history stays explainable after policy changes.

**Negative**

- Every write path must go through `workforce` with a context; convenience shortcuts are forbidden.
- Governed-field list and provenance storage need care to avoid becoming a second data model.
- `DERIVED` as an "ownership mode" is conceptually a provenance kind; it is kept for uniform write rules and could confuse readers.
- Divergence handling adds operator work (someone must reconcile).

## Reconsider When

- Real overlay customers need `PROPOSE_ONLY` before Phase 3.
- Provenance volume or query cost becomes a problem (revisit storage shape; LQ-02).
- Field-level granularity proves too fine or too coarse in the reference connector.

## Interview line

"I separate who may write a field from where a value came from: ownership is a per-tenant policy on a short list of governed fields, provenance travels with each stored value, and a single writer enforces the policy so a sync can never silently overwrite what PalCheck owns, or the reverse."

## Related Decisions

[ADR-0004](0004-hris-adapter-boundary.md) · [ADR-0015](0015-data-lineage.md) · [ADR-0017](0017-primary-product-mode.md) · [DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md)
