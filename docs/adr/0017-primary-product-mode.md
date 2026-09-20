# ADR-0017: Primary Product Mode: Standalone-First, Overlay Seam

**Status:** Proposed (decision made by Daniel Aguilar on 2026-09-19; pending formal acceptance with the other Phase 0 ADRs)
**Date:** 2026-09-19

## Context

The brief defines two modes and calls their distinction fundamental: a **standalone** workforce platform (PalCheck is the system of record) and an **enterprise localization/orchestration layer** (an external HRIS is the system of record). They pull the domain in opposite directions, and
designing both equally invites a lowest-common-denominator model ([PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) C-02). The reviewer recommended **overlay-first**: it is the sharper wedge for the Latin America translation thesis, needs less breadth, and avoids competing with incumbent HRIS products.
**Daniel chose standalone-first.** That decision is final for planning purposes; this ADR records it, and the mitigations the choice requires.

## Decision

1. **Standalone is the primary design driver.** Phase 1 builds PalCheck as the system of record for a small organization: employees, employment, org structure, hire lifecycle, orchestration to downstream systems, audit, and the Costa Rica concept glossary.
2. **Overlay is a preserved seam**, kept alive by design constraints that are cheap now:
   - **Per-field ownership policy exists from day one**, default `PALCHECK` ([ADR-0005](0005-source-of-truth-ownership.md)).
   - **`Employment`, `Person`, and the rest of the core domain contain no provider concepts** ([ADR-0004](0004-hris-adapter-boundary.md)); `NormalizedWorker` is PalCheck's contract.
   - The **ownership flip** on connector enablement is a designed, previewed, audited operation.
3. **The seam is tested, not assumed:** the Phase 2 Workday-shaped reference connector must flip ownership on real fields **without changing the core domain**. If it requires domain changes, that is a finding to record and address, not to hide.
4. **PalCheck does not compute payroll**, in any mode. It represents payroll concepts and orchestrates hand-offs. This is a written non-goal ([PRODUCT_VISION.md](../PRODUCT_VISION.md)).
5. **Phase 1 stays a single vertical slice** (hire to provision), not breadth across leave, compensation, and onboarding, so standalone-first does not become a full HRIS build.
6. **The localization value must show in standalone too:** the Learn glossary, local data shapes, and (later) hire requirements deliver "translate, do not distort" without any external HRIS.

## Alternatives Considered

| Alternative | Why not chosen |
|---|---|
| **Overlay-first** (reviewer's recommendation) | Sharper wedge and narrower scope; not chosen by Daniel. Remains the natural next step at Phase 2 and the likely first *paid* motion for enterprises. |
| **Equal dual-mode from day one** | Highest risk of generic abstractions before real differences are understood, which the brief itself warns against |
| **Standalone only, no overlay seam** | Would foreclose the brief's stated enterprise direction and make the seam a rewrite |

## Consequences

**Positive**

- A demonstrable end-to-end product without needing any external system; the first customer profile (small employer) is realistic.
- The core model is exercised as a system of record, giving richer domain depth (records, lifecycle, audit) for the portfolio.
- The overlay seam is preserved cheaply.

**Negative**

- **Scope creep** toward payroll, leave, and onboarding breadth ("Pal" + "Paycheck" invites it); competing on breadth with established HRIS products for small employers.
- **Domain fills with standalone-only assumptions** unless the seam constraints are enforced and the Phase 2 flip test is honored.
- The Latin America *translation* differentiator is less visible until Phase 2 unless the glossary and local data shapes carry it.
- Selling to small organizations means different support economics than selling to enterprises ([PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) BIZ-06).

## Reconsider When

- The Phase 2 ownership-flip test fails in ways that require domain changes.
- Early conversations with prospective users show no demand for standalone but clear demand for overlay.
- Payroll or leave scope pressure threatens the vertical-slice discipline.
- A real customer profile appears that dictates the mode.

## Interview line

"I built PalCheck as a system of record first because it exercises the whole domain, but I kept the external-HRIS case alive with per-field ownership and a provider-free core, and I planned a reference connector specifically to test that seam."

## Related Decisions

[ADR-0004](0004-hris-adapter-boundary.md) · [ADR-0005](0005-source-of-truth-ownership.md) · [ADR-0011](0011-country-policy-architecture.md) · [MASTER_PROJECT_BRIEF.md](../../MASTER_PROJECT_BRIEF.md) Amendment A1
