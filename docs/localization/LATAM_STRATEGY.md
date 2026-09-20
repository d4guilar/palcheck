# Latin America Localization Strategy

Status: Phase 0 draft, **Proposed** · Written to make the brief's Latin America vision concrete without asserting any legal fact.
Related: [ADR-0011](../adr/0011-country-policy-architecture.md), [PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) section 7.

## 1. The thesis, stated as a hypothesis

> Latin American countries share broadly similar *categories* of employment concepts while differing significantly in terminology, calculation,
> eligibility, legal basis, deadlines, practices, payroll behavior, social security, termination rules, and leave.

The first half (shared categories) is what would justify a shared core. The second half (real differences) is what would justify country packs.
PalCheck treats the *whole sentence* as a **hypothesis to be tested concept by concept**, not as a premise. The instrument for testing it is the
`ConceptMapping` with an explicit equivalence level ([LEGAL_CONCEPT_REGISTRY.md](LEGAL_CONCEPT_REGISTRY.md)):

| Equivalence | Meaning |
|---|---|
| `EXACT` | Same concept in meaning and in the ways that matter to software |
| `PARTIAL` | Same category, but differs in ways that matter (eligibility, trigger, timing, basis, ...) |
| `NONE` | No counterpart |
| `UNKNOWN` | Not yet researched (the default) |

If most mappings come back `PARTIAL`, the right architecture is a **thin shared core with fat country packs**. The design already allows that, which is why it
avoids fixing shared behavior early.

## 2. "Localization" is five different things

| Layer | What changes | Owner | Change rate | Where in PalCheck |
|---|---|---|---|---|
| **1. Presentation locale** | UI language, date/number/currency formatting | Product/UI | Rare | Web app; Intl formatting; externalized strings ([ADR-0009](../adr/0009-frontend-architecture.md)) |
| **2. Terminology** | Native term plus English explanation; aliases; per-country vocabulary | Content | With research | Legal Concept Registry (content, not UI bundles) |
| **3. Jurisdiction rules** | What the law requires and how it changes over time | Research + professional review | With the law | Policy packs (behavior, later) and registry (facts) |
| **4. Local data** | Identifier types, address forms, currency, calendars, employer structures | Domain | With research | `workforce` reference data and local supplemental data |
| **5. Workflow variants** | Country-specific tasks in hire, change, termination | Product | With research | `lifecycle` steps supplied by `countrypolicy` |

Confusing these is the most common localization mistake: translating strings (layer 1) does not localize employment (layers 3 to 5). PalCheck's English-first UI
is a layer-1 decision and says nothing about layers 2 to 5.

## 3. Jurisdiction, not just country

"Country" is the default unit, but some rules are sub-national, sector-specific, or vary by employer type. PalCheck models a **jurisdiction** (starting as
country-level) so that finer granularity does not require rewriting the concept. For Costa Rica, country-level is the starting *assumption*, not a finding;
whether any relevant rules vary within the country is RESEARCH REQUIRED.

## 4. Why Costa Rica first, and what "first" means

Doing one country deeply is how the shape of the problem is learned. "First" does **not** mean the Costa Rica model is the template for others.
It means:

- Costa Rica is the only country with a registry, glossary, and research backlog.
- Its structures may turn out to be Costa Rica-specific; they are not promoted to shared code until a second country confirms the shape.
- No other country is researched, sketched, or stubbed in code during Phase 0 or Phase 1.

## 5. The second-instance rule and the "Colombia test"

**Rule.** No shared implementation is extracted from a single instance. A capability becomes shared only after a *second* country's equivalent has been
documented and compared.

**Procedure** (run before extracting anything shared, and before adding country two):

1. Pick the shared thing under consideration (a capability interface, a data field, a workflow step).
2. Choose the next candidate country (Colombia is the running example; any will do).
3. **Document, do not build**, that country's equivalent concept: names, category, and the *questions* the Costa Rica model asks of it. All content `UNRESEARCHED` until sourced.
4. Compare: `EXACT` / `PARTIAL` / `NONE` per concept, and per data field.
5. Extract only what survived as `EXACT`. Keep `PARTIAL` differences in the packs. Record the decision in an ADR.

## 6. Adding a country later (checklist)

1. Create `docs/localization/<country>/` with README, glossary, research policy pointer, backlog.
2. Add its concepts to the registry at `UNRESEARCHED`; populate native names and aliases per country vocabulary.
3. Add `ConceptMapping` rows to global primitives and to existing countries' concepts, all `UNKNOWN`, then research.
4. Run the Colombia-test comparison for anything proposed as shared.
5. Add a policy pack manifest declaring only the capabilities that are researched and reviewed. Absence of a capability is an honest answer, never a default.
6. Add locale/reference data (identifier types, currency, calendar) as data, not code branches.
7. Professional review before any behavior is enabled.

## 7. Language and terminology

- Spanish is not one operational vocabulary; the same category can carry different names in different countries, and the same name can mean different things.
  Registry entries carry **native names per country** plus aliases. There is no global "Spanish term" for a concept.
- English explanations are written for an international reader who does not know the local law ("Native term + English explanation").
- English labels are never the only label, and a generic English label is never allowed to *replace* a native term where meaning would be lost.

## 8. Concept categories (taxonomy)

Used to group registry entries and research. Not a claim that every country has every category.

`Compensation` · `WorkingTime` · `Leave` · `Termination` · `SocialSecurity` · `TaxAndDeductions` · `Contracts` · `WorkerClassification` ·
`PayrollHandoff` · `Calendar` · `EmployerStructure` · `Identifiers` · `DataProtection` · `Reporting`

## 9. Countries named in the brief

Costa Rica (initial target, structure only) · Mexico · Colombia · Panama · Guatemala · El Salvador · Dominican Republic · Chile · Argentina · Peru · others.
**All except Costa Rica: NOT STARTED.** Nothing about their law is asserted anywhere in this repository.

## 10. What not to do

- Do not create a "LATAM default" that countries inherit from. Inheritance quietly imports assumptions.
- Do not fill a country's concept from "how it works in the neighboring country".
- Do not implement a mapping as `EXACT` on the strength of similar names.
- Do not put jurisdiction rules in UI translation files or in code branches keyed on country.
- Do not let a policy pack default when a concept is missing; say "not available for this country".

## 11. What a real business would additionally need (recorded, not built)

Professional legal review of every implemented concept and a process for monitoring change; an explicit not-legal-advice posture in product and contracts;
data-protection compliance and possibly data residency per country; a Spanish-language UI; local payroll-partner relationships; support in local business hours.
Costs and regimes are RESEARCH REQUIRED. See [PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) section 11.

## 12. Open questions

- **LS-01** Which concept categories matter for a Costa Rican hire in Phase 1 *data modeling* (not computation)? Answered by the P1 items in [costa-rica/RESEARCH_BACKLOG.md](costa-rica/RESEARCH_BACKLOG.md).
- **LS-02** How will professional review be obtained and paid for, and who is the reviewer of record?
- **LS-03** Is a difference-matrix (concepts by country, equivalence in each cell) worth maintaining before country two exists? Recommendation: create the template only when the second country is documented.
