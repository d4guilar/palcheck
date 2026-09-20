# Costa Rica Research Backlog

Status: **Questions only. No answers.** This file replaces the brief's separate `COMPENSATION.md`, `LEAVE.md`, `TERMINATION.md`, and `WORKING_TIME.md`, because four files of unanswered
questions are one backlog. Split a topic out only when it has verified content.

Rules: [LEGAL_RESEARCH_POLICY.md](LEGAL_RESEARCH_POLICY.md). Everything below is `UNRESEARCHED`. A question is not a claim: do not read any question as implying an answer.

## Priority

| Priority | Meaning |
|---|---|
| **P1** | Decides the **shape of the domain model or a design boundary**. Needed before or during Phase 1 *data modeling*. No computation involved. |
| **P2** | Needed to show accurate, sourced *educational content* in the registry. |
| **P3** | Needed only for behavior (calculation, validation, automation). Requires professional review; not before Phase 3 at the earliest. |

"Unblocks" names the design decision each answer feeds.

## 1. Worker classification and contracts

| # | Question | Pri | Unblocks |
|---|---|---|---|
| WC-1 | What kinds of employment contract and worker relationship exist, and how are they distinguished? | P1 | `Employment.workerType` values ([DOMAIN_MODEL.md](../../DOMAIN_MODEL.md)) |
| WC-2 | How is the distinction between an employee and an independent contractor defined and assessed? | P1 | Contractor modeling; misclassification concept |
| WC-3 | Does contract type affect what data a hire requires? Which data? | P1 | `HireRequirements` capability shape; local supplemental data |
| WC-4 | Are there fixed-term or trial-period constructs that affect lifecycle statuses? | P1 | Status taxonomy (OQ-06) |

## 2. Employer structures

| # | Question | Pri | Unblocks |
|---|---|---|---|
| ES-1 | How are employers legally structured and registered for employment purposes? | P1 | `LegalEntity` attributes (OQ-02) |
| ES-2 | Which employer identifiers and registrations exist, and which are per-entity vs. per-establishment? | P1 | Identifiers model; multi-establishment support |
| ES-3 | Can one legal entity employ people across multiple work locations, and does that matter to rules? | P2 | `Location` model |

## 3. Identifiers and personal data

| # | Question | Pri | Unblocks |
|---|---|---|---|
| ID-1 | Which personal identifiers exist for citizens and for foreign workers, and what are their formats? | P1 | Identifier `(type, country, value)` types; synthetic-ID invalid-by-construction design |
| ID-2 | Which identifiers are tax, social-security, or employer-specific? | P1 | Identifier categories |
| ID-3 | What is the data-protection regime for employee data, including special categories and health-related data? | P1 | Sensitivity tiers; audit/retention/erasure design ([SECURITY.md](../../SECURITY.md)) |
| ID-4 | Are there retention or disclosure obligations for employment records? | P2 | Retention policy; audit pseudonymization design |
| ID-5 | Are there cross-border data-transfer considerations for hosting? | P2 | Hosting and tenancy decisions for a real business |

## 4. Currency and compensation

| # | Question | Pri | Unblocks |
|---|---|---|---|
| CO-1 | May compensation be denominated in currencies other than CRC? What follows from that? | P1 | `Money` model and reference data |
| CO-2 | What compensation *elements* exist conceptually (base, statutory payments, allowances, benefits in kind)? | P2 | Compensation module shape (deferred) |
| CO-3 | What is Aguinaldo, exactly, and what are its sources? | P2 | Registry entry; ConceptMapping equivalence |
| CO-4 | How does local compensation relate to the "payroll" handoff (what does a payroll system need to receive)? | P2 | `PayrollHandoffProfile`; downstream payroll contract |
| CO-5 | *(Computation-level questions about statutory payments and their calculation.)* | P3 | Behavior; not before professional review |

## 5. Working time

| # | Question | Pri | Unblocks |
|---|---|---|---|
| WT-1 | What working-time arrangements exist (*jornadas*) and how are they categorized? | P2 | Schedule reference on Assignment |
| WT-2 | How does overtime fit conceptually (what makes hours "extra")? | P2 | Registry entry |
| WT-3 | *(Computation-level questions about limits and overtime pay.)* | P3 | Behavior |

## 6. Leave

| # | Question | Pri | Unblocks |
|---|---|---|---|
| LV-1 | What leave types exist (vacations, incapacity, others), and how do they differ conceptually? | P2 | `timeandleave` module shape (deferred) |
| LV-2 | How does incapacity relate to social-security involvement and to health data? | P1 | Sensitivity tier for incapacity records; who may see them |
| LV-3 | What documentation accompanies each leave type conceptually? | P2 | Document handling (deferred) |
| LV-4 | *(Computation-level questions about accrual, entitlement, and balances.)* | P3 | Behavior |

## 7. Social security and deductions

| # | Question | Pri | Unblocks |
|---|---|---|---|
| SS-1 | What does the social-security institution administer, and what are the employer's and employee's roles at a conceptual level? | P2 | CCSS registry entry; local supplemental data |
| SS-2 | What local reference numbers or enrolments attach to an employment or an employer? | P1 | Local supplemental data fields |
| SS-3 | Which deductions are statutory vs. voluntary, at a conceptual level? | P2 | Deduction concepts; payroll handoff |
| SS-4 | Occupational-risk insurance: who administers it and how does it attach to employment? | P2 | Registry entry; supplemental data |
| SS-5 | *(Computation-level questions about contribution bases and rates.)* | P3 | Behavior |

## 8. Termination

| # | Question | Pri | Unblocks |
|---|---|---|---|
| TM-1 | What distinct ways can employment end, and what facts must be recorded for each? | P1 | Termination fields and reasons taxonomy; `EmployeeTerminated` payload |
| TM-2 | What are Preaviso, Cesantía, and Liquidación, and how do they relate to each other and to the ways employment ends? | P2 | Registry entries; equivalence with a global termination primitive |
| TM-3 | Which termination facts must downstream payroll and identity systems be told? | P2 | Downstream payload contracts |
| TM-4 | *(Computation-level questions about notice periods, severance, and settlement.)* | P3 | Behavior |

## 9. Calendar

| # | Question | Pri | Unblocks |
|---|---|---|---|
| CA-1 | How are public holidays (*feriados*) defined and published, and do they vary by region or sector? | P2 | Calendar reference data; jurisdiction granularity |
| CA-2 | How do holidays interact with working time and payroll at a conceptual level? | P3 | Behavior |

## 10. Reporting and obligations (awareness only)

| # | Question | Pri | Unblocks |
|---|---|---|---|
| RP-1 | Which recurring reports or filings exist for employers, and to whom? *(Awareness: PalCheck does not file anything.)* | P2 | Non-goal boundary; possible later capability |

## 11. Cross-cutting

| # | Question | Pri | Unblocks |
|---|---|---|---|
| XC-1 | Which sub-national or sector-specific variations exist for any concept above? | P1 | Jurisdiction granularity ([LATAM_STRATEGY.md](../LATAM_STRATEGY.md) section 3) |
| XC-2 | Which concepts apply based on legal-entity country vs. employment country vs. residence? | P1 | Jurisdiction resolver ([../COUNTRY_POLICY_ARCHITECTURE.md](../COUNTRY_POLICY_ARCHITECTURE.md) CQ-01) |
| XC-3 | Which concepts have official sources that are publicly accessible, and in what form? | P1 | Feasibility of the research pipeline; snapshot policy |

## Suggested order

Do **P1** first, in this order: WC-1, WC-2, ES-1, ID-1, ID-3, LV-2, TM-1, SS-2, CO-1, XC-1, XC-2, XC-3, ID-2, ES-2, WC-3, WC-4. These decide the domain model and are almost all about *what data exists*,
not about *what the rules compute*. Everything P3 waits for professional review ([LEGAL_RESEARCH_POLICY.md](LEGAL_RESEARCH_POLICY.md) section 4).
