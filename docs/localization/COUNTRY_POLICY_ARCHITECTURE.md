# Country Policy Architecture

> **Reconciliation notice (2026-09-19, Daniel-approved refinement 5):** [ADR-0021](../adr/0021-registry-history-and-behavior-gates.md) decisions 1 to 5 are accepted: historical applicability (a reviewed version that applied from date X to date Y) is distinct from content withdrawal (content later found incorrect); withdrawal wins over historical applicability; gaps yield `NOT_AVAILABLE`; no executable behavior without verified, professionally reviewed evidence. Where the text below requires gap-free coverage or forces expired versions to `DISABLED`, it is amended accordingly. **No executable Costa Rican legal behavior belongs in Phase 1**; all legal content remains `UNVERIFIED` / `RESEARCH REQUIRED`. [Review §13](../CODEX_ARCHITECTURE_REVIEW.md#13-localization-implementation-concerns) is followed for the first researched behavior.

Status: Phase 0 draft, **Proposed** ([ADR-0011](../adr/0011-country-policy-architecture.md)) · Output of assignment step 7.
**No legal rules are defined here.** This document defines *where* rules will live and *how* the system is prevented from distorting them.

Diagram: [06 country policy](../diagrams/06-country-policy.md). Owning module: `countrypolicy` ([MODULES.md](../MODULES.md) 3.3).
Strategy and rationale: [LATAM_STRATEGY.md](LATAM_STRATEGY.md). Registry: [LEGAL_CONCEPT_REGISTRY.md](LEGAL_CONCEPT_REGISTRY.md).

## 1. Goals and non-goals

**Goals**

- Represent local concepts faithfully, with native names and honest status.
- Provide one place where jurisdiction-specific behavior can live later, isolated from the global core.
- Make "not researched" and "not implemented" first-class, visible answers.
- Allow a second country to be added without rewriting the core and without silently inheriting the first country's assumptions.

**Non-goals (Phase 0 and Phase 1)**

- Computing anything statutory (no payroll, accruals, severance, deductions, overtime).
- A rules engine, a rules DSL, or an admin UI for editing rules.
- A shared "LATAM default" behavior.
- Any country other than Costa Rica.

## 2. Components

```
                          +------------------------------+
   PR-reviewed content -> |  Legal Concept Registry      |  facts, no behavior
   (files in repo)        |  Concept / Version / Source  |
                          |  Mapping / BehaviorBinding   |
                          +--------------+---------------+
                                         | binding (only when VERIFIED + PROFESSIONAL_REVIEWED)
                                         v
   workforce events  ->   +------------------------------+  -> annotations, derived facts
   (via published events) |  Policy Pack (per jurisdiction) |  -> hire requirements (tasks/fields)
                          |  manifest + capabilities        |  -> assessments (reproducible)
                          +--------------+---------------+
                                         |
                       local supplemental data (PALCHECK-owned, in workforce)
```

| Component | Nature | Phase 1 | Notes |
|---|---|---|---|
| **Legal Concept Registry** | Reference data | Read-only, Costa Rica concepts at `DOCUMENTED` | Content in reviewed files; database is a projection ([ADR-0012](../adr/0012-legal-concept-registry.md)) |
| **Policy Pack** | Behavior behind capability interfaces | **None** | Manifest may exist as documentation only |
| **Local data model** | Identifier types, currency, calendar, supplemental fields | Minimal shape (identifier `(type,country,value)`, `Money`) | Fields beyond that are research outputs |
| **Jurisdiction resolver** | Chooses pack and version for a fact | Trivial (Costa Rica) | Depends on legal entity country and employment country |
| **Terminology service** | Native term + English explanation for UI/API | Read from registry | Not the UI i18n bundle |
| **Workflow variants** | Local tasks in lifecycle processes | None | Supplied by packs later (`HireRequirements`) |

## 3. Policy pack shape (proposed, not final)

A pack is a **manifest plus independently versioned capabilities**. It is *not* one large interface with a method for every country concept
(that would make the second country implement dozens of irrelevant methods).

**Manifest.** `country`, `jurisdiction`, `packVersion`, `effectiveFrom/To`, the concept versions it relies on, the capabilities it implements, and its status.

**Candidate capabilities** (each optional; each added only when a real, verified need exists):

| Capability | Would answer | Depends on |
|---|---|---|
| `HireRequirements` | What local data and tasks does a hire in this jurisdiction need? | Verified concepts about mandatory data (RESEARCH REQUIRED) |
| `ConceptAnnotations` | Which local concepts apply to this Employment, and how should they be labelled? | Registry only |
| `WorkerClassificationCheck` | Does the recorded worker type conflict with local classification concepts? | Verified concepts, professional review |
| `PayrollHandoffProfile` | What must a payroll system be told for this jurisdiction? | Verified concepts, payroll partner input |
| `TerminationContext` | Which local termination concepts should a termination record surface? | Verified concepts, professional review |
| `LeaveCatalog` / `WorkingTimeProfile` | Which local leave and working-time concepts exist? | Verified concepts |

Design rules:

1. **A missing capability is a valid answer.** The API returns "not available for this jurisdiction", never a default.
2. **No inheritance between packs.** A pack may not extend another country's pack. Shared code arrives only via the second-instance rule.
3. **Capabilities return facts with provenance**: concept IDs, concept version, pack version, inputs. Never a bare value.
4. **No mutation of normalized data.** Outputs are annotations, requirements, or assessments.
5. **Pure where possible.** Evaluations are deterministic functions of inputs plus effective-dated content, so they can be reproduced and tested.

## 4. Selecting the pack and version

- Inputs: the Employment's legal entity **country**, the **country of employment** (may differ from the legal entity's), and the **effective date of the fact** being
  evaluated. Which of these determines applicability for each concept is itself RESEARCH REQUIRED.
- The version chosen is the one **effective at the date of the fact**, not "today". Retroactive changes must evaluate against the rules of the time.
- If no pack or no matching version exists, the outcome is `NOT_AVAILABLE`, displayed as such.

## 5. Assessments are records

Any evaluation produces a **PolicyAssessment** record: subject (Employment/event), capability, concept version(s), pack version, inputs (by reference, redacted),
result, timestamp, and status. Storing them makes results explainable and reproducible, and lets a corrected concept version trigger re-evaluation deliberately.
None exist in Phase 1.

## 6. Guardrails against distortion

**Accepted Phase 1 vocabulary boundary (Q3, 2026-09-21):** core employment status/type values are software/domain placeholders, not a country policy or legal classification. They must not determine statutory calculations, legal eligibility or compliance. Any future researched local vocabulary, metadata and explicit mappings belong to the separate country-policy concepts described here; similarly named values alone do not establish equivalence. See [DOMAIN_MODEL.md section 4.1](../DOMAIN_MODEL.md#41-accepted-phase-1-employment-vocabulary). No policy capability is implemented by this acceptance.

| Guardrail | Mechanism (when implemented) |
|---|---|
| No behavior on unverified law | A `BehaviorBinding` may be created only against a `ConceptVersion` with status `VERIFIED` and verification level `PROFESSIONAL_REVIEWED`; CI validates this over the registry files |
| No silent equivalence | Mapping a global field or another country's concept to a local one requires an explicit `ConceptMapping`; `UNKNOWN` blocks automatic translation |
| No default fallbacks | A missing concept or capability yields `NOT_AVAILABLE` in API and UI |
| Visible status | UI always shows research status and implementation status next to any local concept; unverified content is bannered as orientation only |
| Tests cite sources | Any test encoding a legal rule names the concept version and source it encodes; no such tests exist until verification |
| Change traceability | Registry content changes only by pull request, with citations and reviewer recorded |

## 7. Costa Rica in this architecture

- The only pack manifest is `CR`, and in Phase 0 it is *documentation only*.
- The registry holds Costa Rica concepts named in the brief (Aguinaldo, Vacaciones, Incapacidades, Jornadas laborales, Horas extra, CCSS, Deducciones, Preaviso,
  Cesantía, Liquidaciones, Feriados, contract types, CRC compensation, employer structures, payroll concepts), all `UNRESEARCHED` ([costa-rica/GLOSSARY.md](costa-rica/GLOSSARY.md)).
- Phase 1 surfaces them as a read-only "Learn" glossary with honest status labels, plus the minimal local data shape (identifier type, currency), and nothing computed.

## 8. Testing

| Kind | Purpose | Note |
|---|---|---|
| Registry schema and status-transition validation | Catch malformed content and illegal status jumps in CI | Can exist before any legal content is verified |
| Pack conformance suite | Every pack honors the capability contract (returns provenance, supports `NOT_AVAILABLE`) | Grows with capabilities |
| Golden tests for verified concepts | Encode a concept version's stated behavior | **Forbidden** until the concept is `VERIFIED` and professionally reviewed |
| Retroactivity tests | Evaluations use the version effective at the fact's date | Property-style |

## 9. Alternatives considered

| Alternative | Why not (for now) |
|---|---|
| **A general rules engine or DSL** (e.g., a business-rules product) | Powerful, opaque, and a product in itself; premature before a single rule is verified; hard to explain and to audit legally |
| **One country-policy interface with a method per concept** | God interface; the second country implements irrelevant methods or stubs them, which is how distortion starts |
| **Rules stored in the database and edited through an admin UI** | Bypasses PR review and citation discipline; legal content needs traceable review (may return for a real business with a controlled content-management workflow) |
| **Hard-coded `if country == CR` branches** | Fastest today, unmaintainable at country two, and impossible to audit |
| **"LATAM defaults" plus overrides** | Inherits unverified assumptions between countries |

## 10. Failure modes

| Failure | Effect | Response |
|---|---|---|
| Registry content drifts from the source law | Users see outdated concept | `lastVerifiedAt` and review cadence; deprecate rather than edit history; visible staleness marker |
| Two concept versions overlap or leave a gap | Ambiguous evaluation | Registry validation rejects overlapping and gapped effective ranges per concept and jurisdiction |
| Pack references a concept that is not verified | Behavior on unverified law | CI validation blocks binding |
| Wrong jurisdiction selected (entity vs. employment country) | Wrong context shown | Explicit inputs and documented resolution; surfaced in the assessment |
| Content edited outside review | Untraceable change | Repository protections; content lives only in reviewed files |

## 11. Open questions

- **CQ-01** Is applicability driven by the legal entity's country, the employment country, the worker's residence, or a combination, per concept? RESEARCH REQUIRED.
- **CQ-02** Capability granularity: are `LeaveCatalog` and `WorkingTimeProfile` two capabilities or one? Decide when the first is researched.
- **CQ-03** Should assessments be stored eagerly or computed on demand and cached? Recommendation: store on demand-triggered evaluation; revisit with volume.
- **CQ-04** Who owns tenant-specific policy configuration (company policies layered above statutory concepts)? Recommendation: `countrypolicy`, clearly separated from statutory content.
