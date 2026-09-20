# ADR-0011: Country Policy Architecture

**Status:** Proposed
**Date:** 2026-09-19

## Context

PalCheck's differentiator is translating between a global workforce model and country-specific concepts without distortion. Costa Rica is the only initial target; other countries follow. The brief warns against both assuming countries are identical and building generic abstractions before
real differences are understood. It also forbids inventing law. The design must let country two be added safely, and make "not researched" and "not implemented" honest, visible answers.

## Decision

1. **Localization is five layers** (presentation locale, terminology, jurisdiction rules, local data, workflow variants), owned separately ([LATAM_STRATEGY.md](../localization/LATAM_STRATEGY.md) section 2). English-first UI is a layer-1 decision only.
2. **Jurisdiction** is the unit (country-level to start), not a hard-coded `country`.
3. **Policy packs are a manifest plus independently versioned capabilities**, not one interface with a method per concept. A missing capability returns `NOT_AVAILABLE`; there are **no defaults and no inheritance** between packs, and no "LATAM default".
4. **Second-instance rule**: nothing shared is extracted from one country. Before extracting, document (not build) a second country's equivalent and compare via `ConceptMapping` equivalence (`EXACT`/`PARTIAL`/`NONE`/`UNKNOWN`). Only `EXACT` survives into shared code.
5. **Pack outputs are annotations, requirements, and assessments** with provenance (concept IDs, concept version, pack version, inputs). They never mutate normalized data. Assessments are stored records so results are reproducible.
6. **Selection by effective date of the fact**, so retroactive changes use the rules of their time.
7. **Behavior is gated by the Legal Concept Registry** ([ADR-0012](0012-legal-concept-registry.md)): a binding requires a `VERIFIED` and `PROFESSIONAL_REVIEWED` concept version.
8. **Phase 0 and Phase 1 contain no policy behavior.** The registry is read-only; the only local data shapes are identifier `(type,country,value)`, `Money`, and jurisdiction-aware reference structures.
9. **Costa Rica only.** No other country is researched or stubbed until the checklist in [LATAM_STRATEGY.md](../localization/LATAM_STRATEGY.md) section 6 is started deliberately.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Rules engine / DSL** | Opaque, premature, hard to audit legally, a product in itself |
| **One `CountryPolicy` interface with a method per concept** | God interface; the second country implements irrelevant methods or stubs them, which is how distortion starts |
| **Rules in the database edited via an admin UI** | Bypasses PR review and citation discipline; may return for a real business with a controlled content workflow |
| **`if country == CR` branches** | Fast today, impossible to audit or extend |
| **A LATAM default with per-country overrides** | Imports unverified assumptions across countries |
| **Design the multi-country model up front from research on all countries** | Violates the brief (no premature abstraction) and legal-safety rules |

## Consequences

**Positive**

- Adding a country adds a pack and registry content, not core changes; "not available" is a first-class answer.
- The design cannot present unverified law as behavior.
- The Costa Rica work teaches the shape of the problem without locking in a template.

**Negative**

- Capability granularity will need revision as research proceeds.
- Real behavior is blocked on research and professional review; product value from `countrypolicy` in Phase 1 is educational only.
- Duplicated code between packs is *accepted* until the second-instance rule justifies sharing; reviewers will feel the urge to abstract early.

## Reconsider When

- A second country is documented and comparison shows a stable shared shape (extract) or shows most concepts `PARTIAL` (confirm thin core / fat packs).
- Capabilities multiply beyond what manifests can express.
- A real customer requires behavior before professional review is available (do not comply; escalate to Daniel).

## Interview line

"Countries share categories but not shapes, so I don't share code until a second country proves it; packs expose small capabilities, a missing one returns 'not available' instead of a default, and behavior only attaches to law that's verified and professionally reviewed."

## Related Decisions

[ADR-0012](0012-legal-concept-registry.md) · [ADR-0004](0004-hris-adapter-boundary.md) · [COUNTRY_POLICY_ARCHITECTURE.md](../localization/COUNTRY_POLICY_ARCHITECTURE.md)
