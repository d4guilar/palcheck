# ADR-0004: HRIS Adapter Boundary and Translation Pipeline

**Status:** Proposed
**Date:** 2026-09-19

## Context

PalCheck must integrate with external HR systems (Workday-shaped reference first; SAP SuccessFactors and Oracle HCM later) without letting any provider's vocabulary leak into the domain. The brief specifies Adapter, then Normalized Contract,
then Domain. Refinement is needed: separating protocol from mapping, preserving lossy data, reconciling changes, and handling providers that differ in capability. It also conflicts, mildly, with "no premature abstraction" when only one provider exists
([PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) C-01).

## Decision

1. **PalCheck owns the contract.** `NormalizedWorker` is a versioned shape defined by PalCheck (JSON Schema as source of truth). Providers adapt to it.
2. **Provider types never leave the adapter package** inside `integrations`. Architecture tests forbid provider types outside it.
3. **Split Adapter and Mapper.** Adapter: protocol, authentication, paging, provider DTOs. Mapper: versioned mapping from provider DTO to `NormalizedWorker`, code-list mapping, validation.
4. **Preserve, never silently drop.** Provider data without a home goes to `extensions` and is reported in a `mappingReport`.
5. **Pipeline** (detail in [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md)): Adapter -> Raw landing -> Mapper -> Reconciliation -> `workforce` write -> Policy enrichment -> Orchestration.
6. **Capability-flagged connector port** (`supportsDeltaSync`, `supportsWebhooks`, `supportsWriteBack`, `supportsBulk`, downstream `idempotency = KEY | LOOKUP | NONE`). The core never assumes a capability.
7. **Inbound is read-only by default.** Write-back is a capability, off by default, allowed only for fields PalCheck explicitly owns; it is a Phase 3+ topic.
8. **Second-instance rule**: only the contract and the port are fixed now. Mapping stays as explicit code with a documented field table until a second provider shows whether a declarative form is warranted.
9. **Workday is a Workday-shaped reference HRIS over synthetic data**, in mock mode first, with no fidelity, partnership, or certification claim, and no reliance on employer knowledge.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Use the provider's model as the domain model** | Fast, but couples the whole system to one vendor and makes overlay for other vendors a rewrite |
| **Fully generic connector framework with a declarative mapping DSL** | Premature with one provider; a DSL is a product and hard to debug |
| **Adapter only (no separate mapper)** | Conflates protocol with meaning; changing a mapping would mean touching protocol code and vice versa |
| **Two-way sync from the start** | Conflict resolution, loops, and provider write permissions; high risk for little early value |
| **An integration platform / iPaaS** | Vendor lock, cost, and it hides the engineering the project is meant to show |
| **Store only normalized data (no raw landing)** | Cannot replay a corrected mapping without re-pulling; harder to debug data-quality failures (cost: stored PII, mitigated by retention and redaction) |

## Consequences

**Positive**

- Domain is provider-free; a second provider adds an adapter and mapping, not domain changes.
- Data-quality problems are visible and recoverable (reports, quarantine, reprocess).
- Capability flags make provider differences explicit instead of hidden in `if` statements.

**Negative**

- More stages than a naive import; more to build and test.
- `NormalizedWorker` risks becoming a lowest-common-denominator model; `extensions` is a pressure valve, not a solution.
- Raw landing stores personal data and needs retention and encryption.
- The contract may need a version bump when a second provider arrives, and that is expected, not a failure.

## Reconsider When

- A second provider forces frequent contract changes (the shape is wrong) or demonstrates that a declarative mapping would remove repeated code.
- Raw landing's privacy cost outweighs its debugging value for a real deployment.
- Write-back becomes a real customer need.

## Interview line

"The domain never sees a provider's types: an adapter handles protocol, a versioned mapper produces PalCheck's own contract, unmapped data is preserved rather than dropped, and providers declare capabilities instead of the core assuming them."

## Related Decisions

[ADR-0005](0005-source-of-truth-ownership.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0011](0011-country-policy-architecture.md) · [ADR-0015](0015-data-lineage.md) · [ADR-0017](0017-primary-product-mode.md)
