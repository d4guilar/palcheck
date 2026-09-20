# ADR-0021: Registry History and Behavior Eligibility

**Status:** Accepted in part (Daniel Aguilar, 2026-09-19): decisions 1 to 5 accepted; 6 to 8 remain Proposed
**Date:** 2026-09-19

## Acceptance record (2026-09-19, recorded by Claude on Daniel's instruction)

Daniel approved refinement 5: country-policy content must distinguish **(A) historical applicability** (a verified rule applied from date X through date Y) from **(B) content withdrawal** (information later found incorrect that must no longer be trusted). Normal effective-date history must never be used to represent incorrect legal content. **No executable Costa Rican legal behavior belongs in Phase 1.**

| Decision | Status |
|---|---|
| 1 to 4 | **ACCEPTED**: applicability vs. trust/withdrawal, retained research vocabulary plus explicit metadata, evaluator selects by fact date and current authorization, gaps yield `NOT_AVAILABLE` and ambiguity is explicit. |
| 5 | **ACCEPTED**: no executable binding without verified, professionally reviewed version evidence; `UNRESEARCHED` / `DOCUMENTED` are derived display metadata when no version or binding exists. |
| 6 to 8 | Proposed: assessment reproducibility, runtime withdrawal gating and content-release mechanics are designed before any behavior ships. |

Amends ADR-0012 decision 3 (which disabled every expired or deprecated binding); original reasoning preserved.

## Context

[Review LOC-01](../CODEX_ARCHITECTURE_REVIEW.md) identifies a software contradiction: selecting a version effective at a past fact's date conflicts with disabling all versions that have expired today. Blanket gap rejection risks fabricating coverage. The conceptual model also attaches statuses to versions/bindings that do not yet exist.

**DOMAIN CONCEPT:** stable concept identity and explanatory content. **LEGAL RULE:** still `UNVERIFIED` / `RESEARCH REQUIRED`; this ADR asserts no law or statutory applicability. **SOFTWARE BEHAVIOR:** the proposed version-selection and safety mechanics below. No legal behavior is implemented or authorized by this ADR.

## Decision

1. Preserve immutable reviewed concept/source/code references. Separate business-date applicability from trust/withdrawal. Normal supersession or expiry must not itself destroy historical eligibility; withdrawal for error explicitly blocks execution pending renewed professional review.
2. Retain the required research vocabulary `UNRESEARCHED`, `RESEARCHING`, `VERIFIED`, `DEPRECATED`. Add explicit metadata for deprecation reason, successor, withdrawal and retained review evidence rather than inventing another research status. A historically eligible deprecated version must have evidence that its exact content was previously verified and professionally reviewed and has not been withdrawn.
3. A runtime evaluator selects by the fact's date **and** current authorization to execute that exact reviewed version. A deprecated version is never eligible merely because it once existed. Withdrawal wins over historical applicability. Historical read/explanation and executable evaluation are separate permissions/operations.
4. Missing coverage returns `NOT_AVAILABLE`; competing applicable versions return an explicit ambiguity outcome. Do not fill gaps or invent effective dates. Content validation detects overlaps/gaps for review, but cannot manufacture legal coverage.
5. No executable `BehaviorBinding` without verified, professionally reviewed version evidence and explicit enablement. Before a binding/version exists, display `UNRESEARCHED` and `DOCUMENTED` as derived concept display metadata; do not create dummy legal versions or executable bindings to carry those labels.
6. Assessments reference immutable input revisions, exact concept/content/code versions and predecessor assessment when corrected. Revised knowledge creates a new assessment; it does not overwrite the evidence for an old result.
7. Content CI validates structure/evidence and runtime validates eligibility against the deployed content release and withdrawal state. A stale projection must not silently authorize withdrawn behavior. Detailed release/withdrawal delivery is required before any behavior can ship.
8. Keep registry content in documentation now. Introduce structured reviewed files and a database projection only when a product reader/evaluator needs them. Five conceptual entities do not mandate five tables in the foundation.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Disable all expired versions | Prevents lawful-history evaluation regardless of review quality; contradicts the proposed as-of contract. |
| Always use latest version | Silently reinterprets historical facts. |
| Permit every old reviewed version forever | Cannot respond safely to discovered errors or withdrawn sources. |
| Require complete contiguous coverage | Encourages undocumented assumptions instead of an honest unavailable result. |
| Build a generic legal rules engine now | No verified behavior exists to justify it. |

## Consequences

**Positive:** historical explanations remain reproducible, unavailable knowledge is explicit, and verification gates apply to executable content rather than wall-clock freshness alone.

**Negative:** applicability and trust require separate metadata and tests. A professional reviewer must determine whether a particular historical version remains appropriate; software cannot infer that from dates. Withdrawals need an operational content-release process before real behavior exists.

**Operational burden:** immutable content releases, review evidence, withdrawal propagation, historical tests and assessment retention. This is justified for future legal behavior but adds no runtime work to Phase 1. PalCheck documentation remains not legal advice.

## Reconsider When

Authoritative research or professional review requires a different applicability model, source changes cannot be represented without richer version metadata, or operational withdrawal guarantees cannot be maintained safely.

## Interview line

"A rule's historical date range and whether its content is still trusted are different; I retain reviewed history, stop withdrawn behavior, and return unavailable instead of inventing coverage."

## Related Decisions

[0011](0011-country-policy-architecture.md) · [0012](0012-legal-concept-registry.md) · [Country policy architecture](../localization/COUNTRY_POLICY_ARCHITECTURE.md) · [Legal research policy](../localization/costa-rica/LEGAL_RESEARCH_POLICY.md) · [review §13](../CODEX_ARCHITECTURE_REVIEW.md#13-localization-implementation-concerns)
