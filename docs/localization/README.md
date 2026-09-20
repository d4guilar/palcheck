# Localization

Status: Phase 0 draft. **Contains no legal rules.** Every legal concept is `UNVERIFIED`, `RESEARCH REQUIRED`, or `NOT IMPLEMENTED`.

PalCheck's thesis is *translate, do not distort* ([PRODUCT_VISION.md](../PRODUCT_VISION.md)). This directory holds the strategy and architecture for
doing that across Latin American countries, starting with Costa Rica, and the research scaffolding that keeps legal content honest.

## Reading order

1. [LATAM_STRATEGY.md](LATAM_STRATEGY.md): what "localization" means, why country is not always the unit, how a second country is added safely.
2. [COUNTRY_POLICY_ARCHITECTURE.md](COUNTRY_POLICY_ARCHITECTURE.md): the technical design (assignment step 7).
3. [LEGAL_CONCEPT_REGISTRY.md](LEGAL_CONCEPT_REGISTRY.md): the registry design (assignment step 8).
4. [costa-rica/README.md](costa-rica/README.md): the first target; then its [GLOSSARY](costa-rica/GLOSSARY.md),
   [LEGAL_RESEARCH_POLICY](costa-rica/LEGAL_RESEARCH_POLICY.md), and [RESEARCH_BACKLOG](costa-rica/RESEARCH_BACKLOG.md).

## Ground rules (from [AGENTS.md](../../AGENTS.md) section 4)

- Never invent law: no percentages, formulas, contribution rates, tax rules, severance or accrual rules, eligibility rules, deadlines, or compliance requirements.
- Keep **DOMAIN CONCEPT**, **LEGAL RULE**, and **SOFTWARE BEHAVIOR** as separate layers. Say which one you are writing.
- Native term plus English explanation: `Aguinaldo: Mandatory annual bonus (Costa Rica)`.
- If you cannot cite an authoritative source, write `RESEARCH REQUIRED`.
- This documentation is not legal advice.

## Status vocabularies

| Research | Implementation |
|---|---|
| `UNRESEARCHED` · `RESEARCHING` · `VERIFIED` · `DEPRECATED` | `DOCUMENTED` · `PARTIAL` · `IMPLEMENTED` · `DISABLED` |

Additional verification level (proposed in [LEGAL_CONCEPT_REGISTRY.md](LEGAL_CONCEPT_REGISTRY.md)): `NONE` · `PRIMARY_SOURCE_READ` · `PROFESSIONAL_REVIEWED`.
Anything shown as *behavior* requires the highest level.

## Current state

| Country | Status |
|---|---|
| Costa Rica | Structure and research plan only. All concepts `UNRESEARCHED`, `NOT IMPLEMENTED`. |
| Every other country | Not started, on purpose. |
