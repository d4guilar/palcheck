# ADR-0012: Legal Concept Registry

**Status:** Proposed
**Date:** 2026-09-19

> **Amendment (2026-09-19, Daniel-approved refinement; see [ADR-0021](0021-registry-history-and-behavior-gates.md)):** decision 3, which forced every expired or deprecated binding to `DISABLED`, is amended. Historical applicability (a reviewed version that applied from date X to date Y) is distinct from content withdrawal (content later found incorrect). Normal effective-date history must not represent incorrect content. No executable legal behavior exists or is authorized in Phase 1. Original text preserved.

## Context

The brief describes a `CountryPolicyConcept` record with identity, explanation, legal basis, one source, effective dates, statuses, and a version. It should educate international users, keep native terms, and prevent legal behavior from outrunning research.
Combining those concerns in one record blurs the brief's own three layers (domain concept, legal rule, software behavior) and cannot express multiple sources, changing law over time, or the relationship to global primitives and other countries.

## Decision

1. **Five entities**: `Concept` (domain), `ConceptVersion` (legal, effective-dated), `LegalSource` (evidence, many per version), `ConceptMapping` (equivalence to a global primitive or another country's concept), `BehaviorBinding` (software behavior and implementation status).
2. **Statuses**: research (`UNRESEARCHED`, `RESEARCHING`, `VERIFIED`, `DEPRECATED`) per ConceptVersion; **verification level** (`NONE`, `PRIMARY_SOURCE_READ`, `PROFESSIONAL_REVIEWED`) per ConceptVersion; implementation (`DOCUMENTED`, `PARTIAL`, `IMPLEMENTED`, `DISABLED`) per BehaviorBinding.
3. **Gates** validated in CI: `VERIFIED` requires cited tier-1/2 sources and a recorded verifier; **any behavior binding requires `VERIFIED` and `PROFESSIONAL_REVIEWED`**; a binding whose concept version is deprecated or expired is forced to `DISABLED`.
4. **`ConceptMapping.equivalence`** defaults to `UNKNOWN`; `EXACT` requires a cited basis on both sides.
5. **`orientationNote`** is non-authoritative text always shown with an "unverified" banner until a ConceptVersion is verified.
6. **Content as files, database as projection**: the registry's source of truth is versioned files in the repository, changed only by pull request and validated in CI; the database holds a read-only projection ([LEGAL_CONCEPT_REGISTRY.md](../localization/LEGAL_CONCEPT_REGISTRY.md) 6). Repository location (a top-level content directory or under `docs/`) and file format (structured text plus JSON Schema) are chosen in Phase 1 with a spike; **no content files are created in Phase 0**.
7. **Global reference data**: no tenant column; tenant-specific policy configuration is separate and lives in `countrypolicy`.
8. **Research follows** [LEGAL_RESEARCH_POLICY.md](../localization/costa-rica/LEGAL_RESEARCH_POLICY.md): source tiers, citations, ambiguity as data, professional review before behavior, AI output never a source.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **The brief's single record** | Conflates identity, law, evidence, and software; cannot express versions or multiple sources |
| **Registry in the database with an admin UI** | Loses review, citation, and history that Git provides; would need a bespoke workflow; possible later for a real business with a professional reviewer |
| **A third-party legal-content or compliance API** | Unverified availability, cost, and coverage; PalCheck's value includes the translation model and its honesty about status; consider later as a *source*, not as the registry |
| **Unstructured documentation only** | Cannot gate behavior or power the UI |
| **Combine Concept and ConceptVersion** | Loses stable identity across legal changes |

## Consequences

**Positive**

- The three layers are structurally separate, so the UI cannot show "law" without a version and software cannot act without a verified, reviewed one.
- Educational value from day one, safely: names, categories, and honest status.
- Every change is reviewed, cited, and diffable.

**Negative**

- Non-developers cannot edit content directly.
- More entities and validation than a single table; the process feels heavy for a small content set.
- Verification and professional review are real bottlenecks and costs.
- `orientationNote` risks being read as authoritative despite banners.

## Reconsider When

- A professional reviewer needs an editing workflow (introduce a controlled content-management layer that still produces reviewed, citable changes).
- Content volume outgrows file-based review.
- Codex or Daniel judges `orientationNote` too risky to show (fall back to names and category only).

## Interview line

"I separate what a concept is, what the law says about it, and what my software does with it, into three entities with three gates, so it's structurally impossible to compute something from law that hasn't been verified and professionally reviewed."

## Related Decisions

[ADR-0011](0011-country-policy-architecture.md) · [ADR-0015](0015-data-lineage.md) · [LEGAL_CONCEPT_REGISTRY.md](../localization/LEGAL_CONCEPT_REGISTRY.md)
