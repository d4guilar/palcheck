# Legal Concept Registry

> **Reconciliation notice (2026-09-19, Daniel-approved refinement 5):** [LOC-01](../CODEX_ARCHITECTURE_REVIEW.md#loc-01--expiry-and-deprecation-conflict-with-historical-evaluation) is resolved by accepted parts of [ADR-0021](../adr/0021-registry-history-and-behavior-gates.md): expiry and supersession are historical applicability; withdrawal is a separate trust state and must never be modeled as an ordinary effective-date end; `UNRESEARCHED` / `DOCUMENTED` are derived display metadata when no version or binding exists. The five entities are a conceptual separation, not a requirement for five tables. No registry projection, loader or UI exists in Phase 1, and no legal rule is verified or authorized as behavior. Baseline text is retained.

Status: Phase 0 draft, **Proposed** ([ADR-0012](../adr/0012-legal-concept-registry.md)) · Output of assignment step 8.
**This document defines a container for legal knowledge. It contains none.**

Owning module: `countrypolicy` ([MODULES.md](../MODULES.md) 3.3). Consumers: UI "Learn" views, `lifecycle` (later), policy packs (later).
Related: [COUNTRY_POLICY_ARCHITECTURE.md](COUNTRY_POLICY_ARCHITECTURE.md), [costa-rica/LEGAL_RESEARCH_POLICY.md](costa-rica/LEGAL_RESEARCH_POLICY.md).

## 1. Purpose

1. **Educate.** Let an international reader see what a local concept *is*, in its native name plus a plain English explanation, with honest status.
2. **Bound behavior.** Be the only thing software behavior may attach to, and only when the underlying law is verified and professionally reviewed.
3. **Record equivalence.** Say how a local concept relates to global primitives and to other countries' concepts, including when it does *not* map.

## 2. From the brief's single record to five entities

The brief's `CountryPolicyConcept` puts identity, legal content, source, versioning, and implementation status into one record. That breaks down because:
a concept is stable while its legal definition changes over time; one concept rests on several sources; and implementation status describes *our software*, not the concept.

| Entity | Layer | Holds |
|---|---|---|
| **Concept** | DOMAIN CONCEPT | Stable identity: `id`, `country/jurisdiction`, `nativeName`, `aliases`, `englishName`, `category`, `summary`, `orientationNote` |
| **ConceptVersion** | LEGAL RULE | Effective-dated statement of what the law says: `conceptId`, `versionNumber`, `effectiveFrom`, `effectiveTo`, `legalStatement`, `sources[]`, `researchStatus`, `verificationLevel`, `verifiedBy`, `lastVerifiedAt`, `ambiguities[]` |
| **LegalSource** | evidence | `id`, `title`, `issuingBody`, `instrumentType`, `citation` (article/section), `jurisdiction`, `dateOfInstrument`, `retrievedAt`, `contentHash`, `locator` (URL or snapshot path, when verified), `tier` |
| **ConceptMapping** | translation | `fromConcept` -> `toGlobalPrimitive` or `toConcept` (other country), `equivalence` (`EXACT`/`PARTIAL`/`NONE`/`UNKNOWN`), `differences[]`, `basis`, `researchStatus` |
| **BehaviorBinding** | SOFTWARE BEHAVIOR | `conceptVersionId`, `packCapability`, `packVersion`, `implementationStatus`, `enabled`, `notes` |

Brief field to new home:

| Brief field | Now |
|---|---|
| `id`, `country`, `nativeName`, `englishName`, `category`, `summary` | Concept |
| `detailedExplanation` | Concept `orientationNote` (non-authoritative) and ConceptVersion `legalStatement` (authoritative once verified) |
| `legalBasis`, `sourceReference` | ConceptVersion `sources[]` -> LegalSource (many) |
| `effectiveFrom`, `effectiveTo`, `version` | ConceptVersion |
| `researchStatus`, `lastVerifiedAt` | ConceptVersion |
| `implementationStatus` | BehaviorBinding |

## 3. Statuses and gates

**Research status** (per ConceptVersion): `UNRESEARCHED` -> `RESEARCHING` -> `VERIFIED` -> `DEPRECATED`.

**Verification level** (added; per ConceptVersion): `NONE` · `PRIMARY_SOURCE_READ` (author read the primary sources and cited them) · `PROFESSIONAL_REVIEWED`
(a qualified local professional reviewed this version; reviewer and date recorded).

**Implementation status** (per BehaviorBinding): `DOCUMENTED` · `PARTIAL` · `IMPLEMENTED` · `DISABLED`.

Gates (validated in CI once content exists):

| Transition | Requirement |
|---|---|
| `UNRESEARCHED` -> `RESEARCHING` | An owner and a research task recorded |
| `RESEARCHING` -> `VERIFIED` | At least one tier-1 or tier-2 source cited with locator; `verifiedBy` recorded; ambiguities listed rather than resolved by guessing |
| Display as authoritative "Legal" content | `VERIFIED` (otherwise only as bannered orientation) |
| Create a `BehaviorBinding` at `PARTIAL`/`IMPLEMENTED` | ConceptVersion `VERIFIED` **and** `PROFESSIONAL_REVIEWED` |
| `IMPLEMENTED` -> stays valid | Concept version still effective and not `DEPRECATED`; otherwise binding forced to `DISABLED` |
| `VERIFIED` -> `DEPRECATED` | A superseding version, or explicit withdrawal with reason |

Everything is `UNRESEARCHED` and `DOCUMENTED` today.

## 4. The three-layer rule, enforced structurally

Because DOMAIN (Concept), LEGAL (ConceptVersion), and SOFTWARE (BehaviorBinding) live in different entities with different gates, it is structurally hard to
confuse them: a UI cannot show a "legal rule" that has no ConceptVersion, and software cannot act on a concept that has no verified version.
`orientationNote` is deliberately labelled as **non-authoritative orientation** until a ConceptVersion is verified.

## 5. Illustrative entry (all content UNVERIFIED; orientation text is taken from the brief)

**Concept**

| Field | Value |
|---|---|
| id | `cr.aguinaldo` *(illustrative)* |
| country | Costa Rica |
| nativeName | Aguinaldo |
| englishName | Mandatory annual bonus |
| category | Compensation |
| summary | A country-specific statutory compensation concept *(orientation, from the brief; UNVERIFIED)* |
| orientationNote | Must not be treated as a discretionary performance bonus *(from the brief; UNVERIFIED)* |

**ConceptVersion**: none yet. `researchStatus: UNRESEARCHED`, `verificationLevel: NONE`, no sources, no effective dates, no `legalStatement`.

**ConceptMapping** (illustrative): `cr.aguinaldo` -> global primitive `Compensation (recurring or one-off payment element)`, equivalence `UNKNOWN`.

**BehaviorBinding**: none. Implementation: documentation only.

The UI would render: **Aguinaldo** · Mandatory annual bonus, Costa Rica · Compensation · *Research: Unresearched · Implementation: Documentation only* · a banner:
"Orientation only. Not verified. Not legal advice."

## 6. Content as files, database as projection

**Decision (proposed):** the registry's source of truth is **versioned files in the repository**, changed only by pull request; the database holds a *read-only projection* loaded
at deploy time. Reasons:

- Legal content needs **review, citation, and history**. Git provides authorship, diffs, and PR review for free; a bespoke admin workflow would have to rebuild them.
- CI can **validate** schema, source presence, effective-date consistency, and status transitions before content ships.
- The registry is **global reference data** (no tenant column); a projection avoids tenant-scoped write paths for content that no tenant should edit.
- It is honest about what a solo project can maintain.

Costs: non-developers cannot edit content directly; a real business with a professional reviewer would eventually need a controlled content-management workflow
(recorded as a reconsider-when trigger). Repository location of the content (a top-level content directory vs. under `docs/`) is decided in [ADR-0012](../adr/0012-legal-concept-registry.md); the file format (a structured
text format with a JSON Schema) is chosen in Phase 1. No content files exist in Phase 0.

## 7. Query surface (conceptual)

- List concepts by country/jurisdiction and category, with status filters.
- Get a concept with its versions **as of a date** (default today), sources, mappings, and bindings.
- Search by native name, English name, or alias (accent- and case-insensitive).
- Crosswalk: concepts related to a global primitive, or to another country's concept, with equivalence.
- Coverage summary: counts by research and implementation status (feeds the educational "how much is verified?" view).

All read-only in the product. Registry data is not sensitive, but the projection is still served behind authentication and tenant context like everything else.

## 8. Security and integrity

- Content changes only via reviewed PRs; repository protections apply. The projection loader rejects files that fail validation.
- Sources record `retrievedAt` and `contentHash` so later disagreement with a source can be investigated.
- Tenant-specific policy (a company's own rules layered above statutory concepts) is **not** registry content; it is tenant configuration in `countrypolicy`.

## 9. Failure modes

| Failure | Effect | Control |
|---|---|---|
| Concept text drifts from source | Users misled | Verification records, staleness marker on old `lastVerifiedAt`, deprecation instead of silent edit |
| Overlapping/gapped versions | Ambiguous "as of" answers | Validation rejects overlaps and unexplained gaps per concept and jurisdiction |
| Binding to unverified concept | Behavior on unverified law | CI gate (section 3) |
| Equivalence asserted without research | Distortion | `UNKNOWN` default; `EXACT` requires cited basis on both sides |
| Registry treated as legal advice | Liability | Consistent banners; not-legal-advice language in UI and docs |

## 10. Testing

Schema validation; status-transition and gate validation; effective-range consistency; alias/search behavior; projection idempotence (loading the same files twice yields
the same state); a fixture registry with *fake* concepts for tests (never real legal content in test fixtures until verified).

## 11. Open questions

- **RQ-01** Content file format and directory layout (decide in Phase 1 with a spike).
- **RQ-02** How to record a professional reviewer without exposing personal data in a public repo (role and date vs. name).
- **RQ-03** Should `orientationNote` exist at all, or should unverified concepts show only names and category? Recommendation: keep it, always bannered, because education is the registry's purpose; Codex to challenge.
- **RQ-04** Language of `legalStatement`: Spanish original plus English rendering, with the original authoritative? Recommendation: yes, original authoritative.
