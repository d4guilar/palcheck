# Legal Research Policy (Costa Rica)

Status: Phase 0 draft, **Proposed** · Governs how any Costa Rican legal content enters PalCheck.
Related: [../LEGAL_CONCEPT_REGISTRY.md](../LEGAL_CONCEPT_REGISTRY.md), [AGENTS.md](../../../AGENTS.md) section 4.

## 1. Purpose

Make it impossible to accidentally present invented, outdated, or second-hand law as fact, and impossible to build software behavior on law that has not been verified
and reviewed. This policy applies equally to Daniel, Claude, Codex, and any other contributor.

## 2. Principles

1. **Never invent law.** No statutory percentages, formulas, contribution rates, tax rules, severance formulas, accrual rules, overtime formulas, eligibility rules, deadlines,
   or compliance requirements without a cited authoritative source. "From memory", "typically", and "approximately" are not sources.
2. **Cite or mark.** A claim either has a citation or is marked `UNVERIFIED` / `RESEARCH REQUIRED`.
3. **Ambiguity is data.** Where sources conflict or are unclear, record the ambiguity; do not resolve it by guessing.
4. **Three layers, always.** State whether you are writing a domain concept, a legal rule, or software behavior.
5. **Spanish original is authoritative.** Quote or cite the Spanish text where meaning matters; English is a rendering.
6. **AI output is not a source.** Text produced by any language model, including the authors of this repository's documents, must never be cited as support for a legal claim.
7. **PalCheck is not a law firm.** Nothing here is legal advice, and product language must say so.

## 3. Source hierarchy

| Tier | Source type | Use |
|---|---|---|
| **T1** | Primary law and official publications: constitution, statutes, regulations, decrees, and the official gazette; the state's official legal-information database | Authoritative basis for a `ConceptVersion` |
| **T2** | Authoritative agency publications and guidance from the bodies that administer the topic (labor ministry, social-security institution, tax administration, occupational-risk insurer, central bank for currency, data-protection authority) | Authoritative for the agency's own procedures; confirm against T1 |
| **T3** | Official judicial decisions interpreting the law | Needed where interpretation matters; cite the decision and date |
| **T4** | Professional secondary sources: law-firm and accounting-firm publications, academic commentary | Orientation and leads only; never the sole basis |
| **T5** | Everything else: blogs, forums, social media, vendor marketing, language-model output | Never a source |

**Candidate institutions per tier** are listed here as *starting points to be confirmed*, not as verified sources. Exact names, scope, and locations (URLs) are added only after
each is checked, in `sources/`:

- Legislative and gazette: the national legislature; the official gazette (La Gaceta); the state's legal-information system (SCIJ, administered by the Procuraduría General de la República).
- Administering bodies: Ministerio de Trabajo y Seguridad Social (MTSS); Caja Costarricense de Seguro Social (CCSS); Ministerio de Hacienda (tax administration);
  Instituto Nacional de Seguros (INS) for occupational-risk insurance; Banco Central de Costa Rica (currency); the data-protection authority (name and scope to be confirmed).
- Courts: the Judicial Branch (labor-matter decisions).

*Unverified:* whether each institution's remit is as described above. The list exists to focus research, not to assert it.

## 4. Verification levels

| Level | Meaning | Allows |
|---|---|---|
| `NONE` | Nothing verified | Show name and orientation, bannered |
| `PRIMARY_SOURCE_READ` | The author read the cited T1/T2 sources and recorded citations and ambiguities. Cross-checked by a second person or agent where possible. | Show as a *sourced but unreviewed* legal statement, clearly labelled |
| `PROFESSIONAL_REVIEWED` | A qualified Costa Rican labor lawyer and/or accountant reviewed this concept version; reviewer role and date recorded | The **only** level that permits software behavior |

A second-pass review by Daniel or another AI agent counts as cross-checking. It is **not** professional review.

## 5. Research workflow

1. **Pick** a concept from [RESEARCH_BACKLOG.md](RESEARCH_BACKLOG.md) (priority order) and set it to `RESEARCHING`.
2. **Gather T1/T2 sources.** Record for each: title, issuing body, instrument type, article/section, date of instrument, retrieval date, locator, and a content hash (or snapshot reference).
3. **Write the statement** of what the sources say, quoting Spanish where meaning matters and minimizing paraphrase; identify effective dates and known amendments.
4. **Check interpretation** against T3 where relevant.
5. **List ambiguities and open questions.** Do not resolve them by assumption.
6. **Propose `VERIFIED`** via pull request with sources attached; a second reviewer confirms every claim maps to a cited passage.
7. **Seek professional review** before any behavior is proposed. Record reviewer role, date, and the reviewed concept version.
8. **Record** the work in the worklog with the sources used.

## 6. Change management

- Law changes. Each `ConceptVersion` has `effectiveFrom/To` and `lastVerifiedAt`. Old versions are **deprecated, not edited**, so history stays truthful.
- A review cadence and a change-monitoring method (subscriptions to official publications, periodic re-verification) must exist before any behavior is enabled. Cadence is to be decided, not asserted here.
- If a source is found to contradict a verified concept, the concept is downgraded to `RESEARCHING` and any binding is set to `DISABLED` until re-verified.

## 7. What counts as invented law (examples of forbidden text)

Any of the following without a citation and the matching verification level: a percentage, a rate, a day count, a month count, a deadline, a formula, a threshold, an eligibility
condition, a statement that something "is required", "is mandatory in all cases", "must be paid by" a date, or a comparison to another country's rules. *Describing a concept's name
and category is fine; describing its requirements is not.*

## 8. Source storage

Citations and metadata live in `sources/` once it exists. Storing copies of official texts may be restricted by terms of use (*Unverified*; check before copying). When in doubt, store the
citation, locator, retrieval date, and hash, not the text.

## 9. Standard disclaimer (to be reused in docs and UI)

> PalCheck provides organizational and educational information about employment concepts. It is not legal, tax, or accounting advice. Concepts marked Unverified or
> Unresearched have not been checked against authoritative sources.

## 10. Open questions

- **LR-01** Who is the professional reviewer of record, on what terms, and at what cost? Blocking for *any* behavior; not blocking for documentation or Phase 1.
- **LR-02** How is professional review recorded in a public repository without exposing personal data (role and date vs. name)?
- **LR-03** Re-verification cadence and monitoring method.
- **LR-04** Whether official texts may be snapshotted in the repository, and under what terms.
